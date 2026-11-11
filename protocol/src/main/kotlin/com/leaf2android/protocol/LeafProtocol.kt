package com.leaf2android.protocol

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

interface LeafTransport {
    suspend fun connect()
    suspend fun read(buffer: ByteArray): Int
    suspend fun write(bytes: ByteArray)
    fun close()
}

/** A single command in flight: old replies carry no transaction ID. Timeout closes the stream. */
class LeafProtocol(
    private val transport: LeafTransport,
    private val scope: CoroutineScope,
    val encoding: WireEncoding,
    private val log: (String) -> Unit = {},
    private val progress: (Int, Int) -> Unit = { _, _ -> },
    private val failed: (Throwable) -> Unit = {},
    private val maxAttachmentBytes: Int = 16 * 1024 * 1024,
) {
    private data class Pending(val command: LeafCommand, val reply: CompletableDeferred<WireFrame>)
    private val requests = Mutex()
    private val writes = Mutex()
    private val notifications = Channel<WireFrame>(128)
    val events = notifications.receiveAsFlow()
    @Volatile private var pending: Pending? = null
    @Volatile private var running = false
    private var reader: Job? = null

    suspend fun open() {
        transport.connect()
        running = true
        reader = scope.launch(Dispatchers.IO) {
            val framer = StreamFramer(maxAttachment = maxAttachmentBytes, progress = progress)
            try {
                val buffer = ByteArray(16 * 1024)
                while (isActive && running) {
                    val n = transport.read(buffer)
                    if (n < 0) { framer.finish(); throw ProtocolException("后背已断开蓝牙连接", "The back disconnected Bluetooth.") }
                    if (n == 0) continue
                    framer.feed(buffer, n) { frame ->
                        log("RX ${LabRedaction.redact(frame.header.toString(encoding.charset))} [${frame.attachment.size} bytes]")
                        val waiting = pending
                        if (waiting != null && waiting.command.accepts(frame, encoding)) {
                            waiting.reply.complete(frame)
                        } else if (notifications.trySend(frame).isFailure) {
                            throw ProtocolException("后背通知过快，会话已停止以避免照片错配", "Back notifications arrived too quickly. The session was stopped to avoid mismatching photos.")
                        }
                    }
                }
            } catch (e: Throwable) {
                if (running && e !is CancellationException) {
                    running = false
                    pending?.reply?.completeExceptionally(e)
                    transport.close()
                    notifications.close(e)
                    failed(e)
                }
            }
        }
    }

    suspend fun execute(command: LeafCommand, validateReply: Boolean = true): WireFrame = requests.withLock {
        if (!running) throw ProtocolException("蓝牙会话未连接", "The Bluetooth session is not connected.")
        val wait = Pending(command, CompletableDeferred())
        check(pending == null)
        pending = wait
        try {
            val response = withTimeout(command.timeoutMs) {
                write(command.bytes(encoding))
                log("TX ${LabRedaction.redact(command.text)}")
                wait.reply.await()
            }
            if (validateReply && (response.tag.equals("Error", true) || response.tag.equals("Nak", true)))
                throw ProtocolException("后背拒绝指令：${response.header.toString(encoding.charset)}", "The back rejected the command: ${response.header.toString(encoding.charset)}")
            val errorCode = response.value("error_code")
            if (validateReply && errorCode != null && errorCode.toLongOrNull() != 0L)
                throw ProtocolException("后背返回错误码 $errorCode：${command.text.substringBefore(' ')}", "The back returned error $errorCode: ${command.text.substringBefore(' ')}")
            response
        } catch (e: TimeoutCancellationException) {
            val failure = ProtocolException("后背响应超时；已断开，请重新连接，避免迟到回复与下一张照片错配", "The back response timed out. The session was disconnected; reconnect to prevent late responses from being assigned to the next photo.")
            close(); failed(failure); throw failure
        } catch (e: CancellationException) {
            close(); throw e
        } finally { pending = null }
    }

    suspend fun acknowledge() { if (running) write(Commands.ack) }
    suspend fun sendOnly(command: LeafCommand) = requests.withLock {
        if (!running) throw ProtocolException("蓝牙会话未连接", "The Bluetooth session is not connected.")
        write(command.bytes(encoding))
        log("TX ${LabRedaction.redact(command.text)} [send only]")
    }

    private suspend fun write(bytes: ByteArray) = writes.withLock { transport.write(bytes) }

    fun close() {
        running = false
        transport.close()
        reader?.cancel()
        pending?.reply?.cancel()
        notifications.close()
    }
}
