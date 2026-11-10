package com.leaf2android.data

import com.leaf2android.R
import com.leaf2android.localization.*

import android.content.Context
import android.graphics.BitmapFactory
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** The hashed path is never used as a camera filename. Preview bytes are preserved verbatim. */
class PhotoStore(context: Context) {
    private val directory = File(context.filesDir, "previews").apply { mkdirs() }
    private val index = AtomicFile(File(directory, "index.json"))
    fun load(): List<LeafPhoto> = try {
        val array = JSONArray(index.readFully().toString(Charsets.UTF_8))
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            val metadata = o.optJSONObject("metadata") ?: JSONObject()
            val file = File(directory, o.optString("cache"))
            LeafPhoto(path = o.getString("path"), cacheFile = file.takeIf { o.optString("cache").isNotEmpty() && it.isFile }?.absolutePath,
                width = o.optInt("width"), height = o.optInt("height"), bytes = o.optLong("bytes"), sha256 = o.optString("sha256"),
                receivedAt = o.optLong("time"), starred = o.optBoolean("starred"), localNote = o.optString("note"),
                metadata = metadata.keys().asSequence().associateWith { metadata.getString(it) }, source = PhotoSource.fromStored(o.optString("source", "CAMERA")))
        }
    } catch (_: Exception) { emptyList() }

    fun save(photos: List<LeafPhoto>) {
        val list = JSONArray()
        photos.forEach { p -> list.put(JSONObject().apply {
            put("path", p.path); put("cache", p.cacheFile?.let { File(it).name } ?: "")
            put("width", p.width); put("height", p.height); put("bytes", p.bytes); put("sha256", p.sha256)
            put("time", p.receivedAt); put("starred", p.starred); put("note", p.localNote)
            put("metadata", JSONObject(p.metadata)); put("source", p.source.name)
        }) }
        var stream: java.io.FileOutputStream? = null
        try { stream = index.startWrite(); stream.write(list.toString().toByteArray()); index.finishWrite(stream) }
        catch (e: Exception) { index.failWrite(stream); throw e }
    }
    fun put(path: String, bytes: ByteArray): LeafPhoto {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw LocalizedIOException(msg(R.string.the_attachment_is_not_a_decodable_preview_it_ha_5a4e0))
        if (bounds.outWidth.toLong() * bounds.outHeight > 100_000_000L) throw LocalizedIOException(msg(R.string.invalid_preview_dimensions_decoding_was_refused_ec29f))
        val file = File(directory, hash(path.toByteArray()) + ".preview")
        val atomic = AtomicFile(file)
        var stream: java.io.FileOutputStream? = null
        try { stream = atomic.startWrite(); stream.write(bytes); atomic.finishWrite(stream) }
        catch (e: Exception) { atomic.failWrite(stream); throw e }
        return LeafPhoto(path = path, cacheFile = file.absolutePath, width = bounds.outWidth, height = bounds.outHeight,
            bytes = bytes.size.toLong(), sha256 = hash(bytes), receivedAt = System.currentTimeMillis())
    }
    fun trim(photos: List<LeafPhoto>, limitMiB: Int, selected: String?): List<LeafPhoto> {
        var total = photos.filter { it.cacheFile != null }.sumOf { it.bytes }
        val evicted = mutableSetOf<String>()
        for (p in photos.sortedBy { it.receivedAt }) {
            if (total <= limitMiB * 1024L * 1024L) break
            if (p.path == selected || p.cacheFile == null) continue
            if (File(p.cacheFile).delete()) { evicted += p.path; total -= p.bytes }
        }
        return photos.map { if (it.path in evicted) it.copy(cacheFile = null) else it }
    }
    companion object { fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) } }
}
