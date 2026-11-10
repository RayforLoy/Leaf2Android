package com.leaf2android.data

import com.leaf2android.R
import com.leaf2android.localization.*

import android.content.Context
import android.graphics.*
import com.leaf2android.connection.BluetoothTransport
import com.leaf2android.protocol.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LeafRepository(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val preferences = context.getSharedPreferences("leaf", Context.MODE_PRIVATE)
    private val store = PhotoStore(context)
    private val storageLock = Mutex()
    private val mutableSettings = MutableStateFlow(readSettings())
    val settings = mutableSettings.asStateFlow()
    private val mutable = MutableStateFlow(SessionState())
    val state = mutable.asStateFlow()
    private val mutableLogs = MutableStateFlow<List<String>>(emptyList())
    val logs = mutableLogs.asStateFlow()
    private val viewports = mutableMapOf<String, Viewport>()
    private var protocol: LeafProtocol? = null
    private var connectionJob: Job? = null
    private var imageQueue: Channel<String>? = null
    private val queued = mutableSetOf<String>()
    private var generation = 0
    var lastAddress: String? = preferences.getString("lastAddress", null); private set
    var lastName: String = preferences.getString("lastName", BACK_FAMILY) ?: BACK_FAMILY; private set

    init {
        scope.launch {
            val photos = withContext(Dispatchers.IO) { store.load() }
            mutable.update { it.copy(photos = photos, selectedPath = preferences.getString("selected", null)
                ?.takeIf { path -> photos.any { p -> p.path == path } }) }
        }
    }
    private fun readSettings() = LeafSettings(
        style = runCatching { UiStyle.valueOf(preferences.getString("style", "MODERN")!!) }.getOrDefault(UiStyle.MODERN),
        encoding = runCatching { WireEncoding.valueOf(preferences.getString("encoding", "WESTERN")!!) }.getOrDefault(WireEncoding.WESTERN),
        insecure = preferences.getBoolean("insecure", false), autoFollow = preferences.getBoolean("autoFollow", true),
        keepAwake = preferences.getBoolean("keepAwake", true), cacheMiB = preferences.getInt("cacheMiB", 256).coerceIn(64, 1024),
        showHistogram = preferences.getBoolean("showHistogram", false), showHighlights = preferences.getBoolean("showHighlights", false),
        showShadows = preferences.getBoolean("showShadows", false),
    )
    fun updateSettings(update: (LeafSettings) -> LeafSettings) {
        val next = update(settings.value)
        mutableSettings.value = next
        preferences.edit().putString("style", next.style.name).putString("encoding", next.encoding.name)
            .putBoolean("insecure", next.insecure).putBoolean("autoFollow", next.autoFollow)
            .putBoolean("keepAwake", next.keepAwake).putInt("cacheMiB", next.cacheMiB).apply()
        preferences.edit().putBoolean("showHistogram", next.showHistogram).putBoolean("showHighlights", next.showHighlights)
            .putBoolean("showShadows", next.showShadows).apply()
    }
    fun viewport(path: String): Viewport = viewports[path] ?: Viewport()
    fun setViewport(path: String, viewport: Viewport) { viewports[path] = viewport }
    fun clearMessage() { mutable.update { it.copy(error = null, notice = null) } }
    fun showError(message: UiText) { mutable.update { it.copy(error = message) } }
    fun showError(message: String) = showError(UiText.raw(message))

    fun connect(address: String, name: String, transport: LeafTransport) {
        disconnect()
        val current = ++generation
        val configuration = settings.value
        lastAddress = address; lastName = name
        preferences.edit().putString("lastAddress", address).putString("lastName", name).apply()
        mutable.update { it.copy(phase = SessionPhase.CONNECTING, deviceName = name, deviceAddress = address,
            error = null, notice = null, camera = emptyMap(), entries = emptyList(), operation = msg(R.string.waiting_for_system_bluetooth_connection_and_pai_8f144), previewCount = 0) }
        val queue = Channel<String>(64)
        imageQueue = queue
        connectionJob = scope.launch {
            try {
                coroutineScope {
                    val p = LeafProtocol(transport, this, configuration.encoding,
                        log = { line -> scope.launch { log(line) } },
                        progress = { done, total -> mutable.update { if (generation == current) it.copy(received = done, expected = total) else it } },
                        failed = { e -> scope.launch { if (generation == current) fail(e) } })
                    protocol = p
                    launch {
                        try { p.events.collect { event -> onEvent(p, event, current) } }
                        catch (e: Exception) { if (e !is CancellationException && generation == current) fail(e) }
                    }
                    p.open()
                    mutable.update { it.copy(phase = SessionPhase.SYNCING, operation = msg(R.string.requestsync_currenttime_root_folder_62d40)) }
                    mergeCamera(p.execute(Commands.sync), configuration.encoding)
                    mergeCamera(p.execute(Commands.currentTime), configuration.encoding)
                    readFolder(p, "./")
                    mutable.update { it.copy(phase = SessionPhase.READY, operation = UiText.Empty, notice = msg(R.string.synchronization_complete_release_the_technical_de2c8)) }
                    launch {
                        for (path in queue) {
                            try { receiveImage(p, path, current) }
                            catch (e: Exception) {
                                if (e is CancellationException) throw e
                                showError(errorText(e, msg(R.string.could_not_read_preview_9fbde)))
                            } finally { queued.remove(path) }
                        }
                    }
                    awaitCancellation()
                }
            } catch (e: Exception) {
                if (e !is CancellationException && generation == current) fail(e)
            } finally { if (generation == current) { protocol?.close(); protocol = null }; transport.close() }
        }
    }
    fun disconnect() {
        generation++
        protocol?.close(); protocol = null
        connectionJob?.cancel(); connectionJob = null
        imageQueue?.close(); imageQueue = null; queued.clear()
        mutable.update { it.copy(phase = SessionPhase.DISCONNECTED, operation = UiText.Empty, received = 0, expected = 0) }
    }
    private fun fail(e: Throwable) {
        val message = when (e) {
            is SecurityException -> msg(R.string.nearby_devices_permission_was_revoked_grant_per_a7f2d)
            is TimeoutCancellationException -> msg(R.string.bluetooth_connection_timed_out_enable_back_wire_06205)
            else -> errorText(e, msg(R.string.the_bluetooth_session_was_interrupted_29fb2))
        }
        log("SESSION STOP $message")
        protocol?.close()
        mutable.update { it.copy(phase = SessionPhase.FAILED, error = message, operation = UiText.Empty, received = 0, expected = 0) }
        connectionJob?.cancel()
    }
    private fun mergeCamera(frame: WireFrame, encoding: WireEncoding) {
        val values = frame.values(encoding.charset).mapKeys { it.key.lowercase(Locale.ROOT) }
        mutable.update { it.copy(camera = it.camera + values) }
    }
    private suspend fun readFolder(p: LeafProtocol, path: String) {
        val normalized = if (path.endsWith('/')) path else "$path/"
        val frame = p.execute(Commands.directory(normalized))
        val entries = DirectoryParser.parse(frame, normalized, p.encoding)
        mutable.update { s ->
            val known = s.photos.map { it.path }.toSet()
            s.copy(folder = normalized, entries = entries, photos = s.photos + entries.filter { !it.isDirectory && it.path !in known }
                .map { LeafPhoto(it.path, metadata = it.cameraDate?.let { date -> mapOf("camera_directory_date" to date) } ?: emptyMap()) })
        }
        persist()
    }
    fun browse(path: String) = action(msg(R.string.reading_folder_cadc7)) { p -> readFolder(p, path) }
    fun refreshStatus() = action(msg(R.string.updating_back_status_7a7d4)) { p ->
        listOf(Commands.battery, Commands.exposures, Commands.totalFrames).forEach { mergeCamera(p.execute(it), p.encoding) }
    }
    fun refreshSelected() { state.value.selectedPath?.let { enqueueImage(it) } }
    private fun action(title: UiText, block: suspend (LeafProtocol) -> Unit) {
        scope.launch {
            val p = protocol
            if (p == null || !state.value.connected) { showError(msg(R.string.connect_to_the_back_and_complete_synchronizatio_fa018)); return@launch }
            try {
                mutable.update { it.copy(operation = title, error = null) }
                block(p)
            } catch (e: Exception) {
                if (e !is CancellationException) showError(errorText(e, msg(R.string.operation_failed , title)))
            } finally { mutable.update { it.copy(operation = UiText.Empty) } }
        }
    }

    private suspend fun onEvent(p: LeafProtocol, frame: WireFrame, current: Int) {
        if (current != generation) return
        when (frame.tag.lowercase(Locale.ROOT)) {
            "picturetaken", "imagechange" -> {
                p.acknowledge()
                mergeCamera(frame, p.encoding)
                val errorCode = frame.value("error_code")
                if (errorCode != null && errorCode.toLongOrNull() != 0L) {
                    showError(msg(R.string.the_back_reported_capture_error_1_s_its_preview_f45c3 , errorCode))
                    return
                }
                if (frame.value("busy").equals("yes", true) || frame.value("imageSaved").equals("no", true)) {
                    mutable.update { it.copy(notice = msg(R.string.the_back_is_processing_the_photo_waiting_for_a_d4e74)) }
                    return
                }
                val path = frame.value("file_name", p.encoding.charset)
                if (path.isNullOrBlank()) {
                    mutable.update { it.copy(notice = msg(R.string.a_capture_notification_had_no_file_name_refresh_3a060)) }
                } else {
                    Commands.safe(path)
                    mutable.update { s -> s.copy(photos = if (s.photos.any { it.path == path }) s.photos else s.photos + LeafPhoto(path)) }
                    enqueueImage(path)
                }
            }
            "cameraparamchange", "storagenotification", "powermanagement" -> {
                p.acknowledge(); mergeCamera(frame, p.encoding)
            }
            "pictureparamschange", "filesystemchange", "readytoshoot", "asynccommand", "beepnotification", "ifguiisactive" -> {
                p.acknowledge()
                mutable.update { it.copy(notice = msg(R.string.back_notification_1_s_7f452 , frame.tag)) }
            }
            "shutdown", "stop" -> { p.acknowledge(); fail(LocalizedIOException(msg(R.string.the_back_stopped_the_session_1_s_1f03d , frame.tag))) }
            "error", "nak" -> showError(msg(R.string.back_notification_1_s_7f452 , frame.header.toString(p.encoding.charset)))
        }
    }
    private fun enqueueImage(path: String) {
        if (!state.value.active) return
        if (!queued.add(path)) return
        if (imageQueue?.trySend(path)?.isSuccess != true) {
            queued.remove(path); showError(msg(R.string.the_preview_queue_is_full_refresh_the_selected_70392))
        }
    }
    private suspend fun receiveImage(p: LeafProtocol, path: String, current: Int) {
        mutable.update { it.copy(operation = msg(R.string.reading_1_s_d8014 , path.substringAfterLast('/')), received = 0, expected = 0) }
        val frame = p.execute(Commands.image(path))
        if (current != generation) return
        if (frame.value("size") != null) throw LocalizedIOException(msg(R.string.the_back_enabled_an_unverified_packetized_size_9dd88))
        val photo = withContext(Dispatchers.IO) { store.put(path, frame.attachment) }
        mutable.update { s ->
            val old = s.photos.firstOrNull { it.path == path }
            val updated = photo.copy(starred = old?.starred ?: false, localNote = old?.localNote ?: "", metadata = old?.metadata ?: emptyMap())
            s.copy(photos = s.photos.filterNot { it.path == path } + updated,
                selectedPath = if (settings.value.autoFollow || s.selectedPath == null) path else s.selectedPath,
                operation = UiText.Empty, received = 0, expected = 0, previewCount = s.previewCount + 1)
        }
        persist()
    }
    fun select(path: String) {
        mutable.update { it.copy(selectedPath = path) }
        preferences.edit().putString("selected", path).apply()
        if (state.value.selected?.cacheFile == null && state.value.connected) enqueueImage(path)
    }
    fun star(path: String) {
        mutable.update { s -> s.copy(photos = s.photos.map { if (it.path == path) it.copy(starred = !it.starred) else it }) }
        scope.launch { persist() }
    }
    fun localNote(path: String, text: String) {
        mutable.update { s -> s.copy(photos = s.photos.map { if (it.path == path) it.copy(localNote = text.take(4000)) else it }) }
        scope.launch { persist() }
    }
    fun requestMetadata(path: String) = action(msg(R.string.reading_photo_information_ebb12)) { p ->
        val frame = p.execute(Commands.pictureParams(path))
        val metadata = frame.values(p.encoding.charset).filterKeys { it !in setOf("attachment", "file_name") }
        mutable.update { s -> s.copy(photos = s.photos.map { if (it.path == path) it.copy(metadata = it.metadata + metadata) else it }) }
        if (frame.attachment.isNotEmpty()) log("INFO XMP attachment ${frame.attachment.size} bytes (not interpreted)")
        persist()
    }
    fun writeCameraNotes(path: String, copyright: String, note: String) = action(msg(R.string.writing_notes_to_back_18e71)) { p ->
        p.execute(Commands.notes(path, copyright, note))
        mutable.update { it.copy(notice = msg(R.string.the_back_acknowledged_the_note_write_read_photo_09981)) }
    }
    private suspend fun persist() {
        try { storageLock.withLock {
        val snapshot = state.value
        val trimmed = withContext(Dispatchers.IO) { store.trim(snapshot.photos, settings.value.cacheMiB, snapshot.selectedPath) }
        val evicted = trimmed.filter { it.cacheFile == null }.map { it.path }.toSet()
        mutable.update { s -> s.copy(photos = s.photos.map { if (it.path in evicted) it.copy(cacheFile = null) else it }) }
        val saved = state.value.photos
        withContext(Dispatchers.IO) { store.save(saved) }
        } } catch (e: Exception) {
            if (e is CancellationException) throw e
            showError(msg(R.string.could_not_save_the_local_cache_index_1_s_e3b8b , e.message))
        }
    }

    /** A labelled, locally generated image; never implies a successful camera session. */
    fun loadDemo() {
        if (state.value.active) { showError(msg(R.string.disconnect_from_the_back_before_opening_the_off_ec4a9)); return }
        scope.launch {
            try {
                val photo = withContext(Dispatchers.IO) {
                    val b = Bitmap.createBitmap(1440, 1080, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(b)
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                    paint.shader = LinearGradient(0f, 0f, 1440f, 1080f, intArrayOf(Color.rgb(29,45,52), Color.rgb(111,132,115), Color.rgb(212,195,150)), null, Shader.TileMode.CLAMP)
                    canvas.drawRect(0f, 0f, 1440f, 1080f, paint); paint.shader = null
                    paint.color = Color.rgb(231,210,155); canvas.drawCircle(1090f, 280f, 136f, paint)
                    paint.color = Color.rgb(32,61,54)
                    val mountain = Path().apply { moveTo(0f, 770f); lineTo(470f, 370f); lineTo(800f, 760f); lineTo(1140f, 580f); lineTo(1440f, 820f); lineTo(1440f, 1080f); lineTo(0f, 1080f); close() }
                    canvas.drawPath(mountain, paint)
                    paint.color = Color.rgb(188,206,182); paint.textSize = 48f
                    canvas.drawText("LEAF / OFFLINE DEMO", 80f, 935f, paint)
                    paint.textSize = 26f; canvas.drawText("Generated test image · no camera data", 80f, 988f, paint)
                    val bytes = ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.JPEG, 94, it) }.toByteArray(); b.recycle()
                    store.put("demo/offline-preview.jpg", bytes).copy(source = PhotoSource.OFFLINE_DEMO, metadata = emptyMap())
                }
                mutable.update { s -> s.copy(photos = s.photos.filterNot { it.path == photo.path } + photo,
                    selectedPath = photo.path, notice = msg(R.string.offline_demo_no_back_is_connected_this_generate_3eb0f)) }
                persist()
            } catch (e: Exception) { showError(errorText(e, msg(R.string.could_not_load_demo_61913))) }
        }
    }
    fun exportDiagnostics(onReady: (File) -> Unit) = scope.launch {
        try {
            val snapshot = state.value
            val content = buildString {
                appendLine("Leaf2Android ${com.leaf2android.BuildConfig.VERSION_NAME} diagnostic — ${Date()}")
                appendLine("Android ${android.os.Build.VERSION.RELEASE}; API ${android.os.Build.VERSION.SDK_INT}; ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                appendLine("Phase: ${snapshot.phase}; device: ${snapshot.deviceName}; address: ${snapshot.deviceAddress?.takeLast(5)?.let { "**:**:**:**:$it" }}")
                appendLine("SPP: ${BluetoothTransport.SPP}; encoding: ${settings.value.encoding}; insecure: ${settings.value.insecure}")
                appendLine("Aptus / Valeo hardware/firmware: not verified by this build; manual mechanical shutter")
                appendLine("Counts: ${snapshot.previewCount} received previews; folder ${snapshot.folder}")
                appendLine("Camera: ${snapshot.camera}")
                appendLine("Error: ${snapshot.error}")
                appendLine("\nLogs (control values may include filenames):")
                logs.value.forEach { appendLine(it) }
            }
            val file = withContext(Dispatchers.IO) {
                File(context.cacheDir, "exports").apply { mkdirs() }
                    .let { File(it, "leaf-diagnostics.txt") }.apply { writeText(content) }
            }
            onReady(file)
        } catch (e: Exception) { showError(msg(R.string.export_failed_1_s_3c3cf , e.message)) }
    }
    private fun log(raw: String) {
        val line = raw.replace(Regex("(?i)(pin|annotation|copyright)=([\"'])(.*?)\\2"), "$1=\"[redacted]\"").take(8192)
        val stamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT).format(Date())
        mutableLogs.update { (it + "$stamp $line").takeLast(300) }
    }
}
