package com.leaf2android.data

import com.leaf2android.protocol.LabRedaction
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class LabOutcome { SENDING, RECEIVED, SENT_ONLY, ERROR, CANCELLED }

data class LabRecord(
    val id: String,
    val time: String,
    val device: String,
    val encoding: String,
    val request: String,
    val templateId: String? = null,
    val requestHex: String? = null,
    val expectedTag: String,
    val expectedFile: String?,
    val timeoutMs: Long,
    val outcome: LabOutcome = LabOutcome.SENDING,
    val response: String? = null,
    val responseHex: String? = null,
    val responseTag: String? = null,
    val responseErrorCode: String? = null,
    val attachmentFile: String? = null,
    val attachmentBytes: Int = 0,
    val attachmentSha256: String? = null,
    val attachmentHex: String? = null,
    val error: String? = null,
) {
    fun json(): JSONObject = JSONObject().apply {
        put("id", id); put("time", time); put("device", device); put("encoding", encoding)
        put("request_redacted", LabRedaction.redact(request)); put("expected_tag", expectedTag); put("expected_file", expectedFile)
        put("template_id", templateId); put("request_hex_non_sensitive", requestHex)
        put("timeout_ms", timeoutMs); put("outcome", outcome.name)
        put("response_redacted", response?.let(LabRedaction::redact)); put("attachment_bytes", attachmentBytes)
        put("response_hex_non_sensitive", responseHex); put("response_tag", responseTag); put("response_error_code", responseErrorCode)
        put("attachment_sha256", attachmentSha256); put("attachment_hex_prefix", attachmentHex)
        put("error", error?.let(LabRedaction::redact))
        put("attachment_in_export", attachmentFile?.let { File(it).isFile } ?: false)
        put("response_proves_effect", false)
        put("first_frame_is_uncorrelated", expectedTag == "*")
    }
}

internal object LabExport {
    fun write(destination: File, records: List<LabRecord>, events: List<String>, transportLog: List<String>) {
        destination.parentFile?.mkdirs()
        val summary = JSONObject().apply {
            put("application", "Leaf2Android ${com.leaf2android.BuildConfig.VERSION_NAME}")
            put("kind", "user-initiated protocol experiments; replies are not verified feature support")
            put("records", JSONArray().also { array -> records.forEach { array.put(it.json()) } })
            put("events_redacted", JSONArray(events.map(LabRedaction::redact)))
            put("transport_log_redacted", JSONArray(transportLog.map(LabRedaction::redact)))
        }
        ZipOutputStream(destination.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("experiments.json")); zip.write(summary.toString(2).toByteArray()); zip.closeEntry()
            records.forEach { record -> record.attachmentFile?.let(::File)?.takeIf { it.isFile }?.let { file ->
                zip.putNextEntry(ZipEntry("attachments/${record.id}.bin"))
                file.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
            } }
        }
    }
}
