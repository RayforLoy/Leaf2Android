package com.leaf2android.ui

import com.leaf2android.R
import com.leaf2android.localization.*

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.leaf2android.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min

private data class PreviewAnalysis(val rgb: List<IntArray>, val high: Bitmap, val low: Bitmap)
private data class LoadedPreview(val bitmap: Bitmap, val analysis: PreviewAnalysis)

private fun loadPreview(path: String): LoadedPreview {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (bounds.outWidth.toLong() * bounds.outHeight / (sample.toLong() * sample) > 9_000_000) sample *= 2
    val bitmap = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 })
        ?: throw java.io.IOException(tr(R.string.the_cached_preview_cannot_be_decoded_read_it_ag_a1858))
    val width = min(512, bitmap.width)
    val height = max(1, bitmap.height * width / bitmap.width)
    val sampled = Bitmap.createScaledBitmap(bitmap, width, height, false)
    val pixels = IntArray(width * height)
    sampled.getPixels(pixels, 0, width, 0, 0, width, height)
    val rgb = List(3) { IntArray(256) }
    val high = IntArray(pixels.size)
    val low = IntArray(pixels.size)
    pixels.forEachIndexed { i, pixel ->
        val r = (pixel ushr 16) and 255; val g = (pixel ushr 8) and 255; val b = pixel and 255
        rgb[0][r]++; rgb[1][g]++; rgb[2][b]++
        if (maxOf(r, g, b) >= 250) high[i] = 0xB8FF563D.toInt()
        if (maxOf(r, g, b) <= 5) low[i] = 0xB844AAFF.toInt()
    }
    val highBitmap = Bitmap.createBitmap(high, width, height, Bitmap.Config.ARGB_8888)
    val lowBitmap = Bitmap.createBitmap(low, width, height, Bitmap.Config.ARGB_8888)
    if (sampled !== bitmap) sampled.recycle()
    return LoadedPreview(bitmap, PreviewAnalysis(rgb, highBitmap, lowBitmap))
}

@Composable
fun PhotoViewer(photo: LeafPhoto, repo: LeafRepository, connected: Boolean, modifier: Modifier = Modifier) {
    var loaded by remember(photo.path, photo.sha256) { mutableStateOf<LoadedPreview?>(null) }
    var loadError by remember(photo.path, photo.sha256) { mutableStateOf<String?>(null) }
    var viewport by remember(photo.path) { mutableStateOf(repo.viewport(photo.path)) }
    val options by repo.settings.collectAsStateWithLifecycle()
    val histogram = options.showHistogram
    val highlight = options.showHighlights
    val shadow = options.showShadows
    var info by rememberSaveable { mutableStateOf(false) }
    var notes by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) { uri ->
        if (uri != null && photo.cacheFile != null) scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { out -> File(photo.cacheFile).inputStream().use { it.copyTo(out) } }
                        ?: throw java.io.IOException(tr(R.string.could_not_open_the_save_location_6afef))
                }
            } catch (e: Exception) { repo.showError(tr(R.string.could_not_save_preview_1_s_3e10a , e.message)) }
        }
    }
    fun move(next: Viewport) { viewport = next; repo.setViewport(photo.path, next) }
    LaunchedEffect(photo.path, photo.cacheFile, photo.sha256) {
        loaded = null
        photo.cacheFile?.let { path ->
            try { loaded = withContext(Dispatchers.Default) { loadPreview(path) } }
            catch (e: Exception) { loadError = e.message ?: tr(R.string.could_not_load_preview_270c5) }
        }
    }
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(photo.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(photo.source.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (connected) IconButton(onClick = repo::refreshSelected) { Icon(Icons.Default.Refresh, tr(R.string.read_preview_again_e63d3)) }
            IconButton(onClick = { repo.star(photo.path) }) { Icon(if (photo.starred) Icons.Default.Star else Icons.Default.StarBorder, tr(R.string.local_flag_fdc55)) }
            IconButton(onClick = { info = true }) { Icon(Icons.Default.Info, tr(R.string.photo_information_8aa32)) }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(MaterialTheme.shapes.medium).background(Color(0xFF080B08)).testTag("photo-canvas")) {
            val preview = loaded
            if (preview == null) {
                Column(Modifier.align(Alignment.Center).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (photo.cacheFile != null && loadError == null) CircularProgressIndicator(Modifier.size(24.dp))
                    else Text(loadError ?: if (connected) tr(R.string.requesting_preview_20b1e) else tr(R.string.this_preview_is_not_cached_connect_to_the_back_c43ff), color = Color(0xFFC7D4C1))
                }
            } else {
                val density = LocalDensity.current
                val areaW = with(density) { maxWidth.toPx() }; val areaH = with(density) { maxHeight.toPx() }
                val fit = min(areaW / preview.bitmap.width, areaH / preview.bitmap.height)
                val fitW = preview.bitmap.width * fit; val fitH = preview.bitmap.height * fit
                fun bounded(zoom: Float, x: Float, y: Float): Viewport {
                    val z = zoom.coerceIn(1f, 12f)
                    val maxX = max(0f, (fitW * z - areaW) / 2) / fitW
                    val maxY = max(0f, (fitH * z - areaH) / 2) / fitH
                    return Viewport(z, x.coerceIn(-maxX, maxX), y.coerceIn(-maxY, maxY))
                }
                val display = bounded(viewport.zoom, viewport.x, viewport.y)
                val gestures = Modifier.fillMaxSize()
                    .pointerInput(photo.path, areaW, areaH) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            val old = bounded(viewport.zoom, viewport.x, viewport.y)
                            val z = (old.zoom * zoom).coerceIn(1f, 12f)
                            val ratio = z / old.zoom
                            val cx = centroid.x - areaW / 2; val cy = centroid.y - areaH / 2
                            move(bounded(z, ((old.x * fitW - cx) * ratio + cx + pan.x) / fitW,
                                ((old.y * fitH - cy) * ratio + cy + pan.y) / fitH))
                        }
                    }.pointerInput(photo.path, areaW, areaH) {
                        detectTapGestures(onDoubleTap = { point ->
                            if (viewport.zoom > 1.1f) move(Viewport())
                            else move(bounded(2f, -(point.x - areaW / 2) / fitW, -(point.y - areaH / 2) / fitH))
                        })
                    }
                Box(gestures, contentAlignment = Alignment.Center) {
                    val imageModifier = Modifier.size(with(density) { fitW.toDp() }, with(density) { fitH.toDp() })
                        .graphicsLayer { scaleX = display.zoom; scaleY = display.zoom; translationX = display.x * fitW; translationY = display.y * fitH }
                    Image(preview.bitmap.asImageBitmap(), photo.name, imageModifier.testTag("photo-image"), contentScale = ContentScale.FillBounds)
                    if (highlight) Image(preview.analysis.high.asImageBitmap(), tr(R.string.jpeg_highlight_warning_9186a), imageModifier, contentScale = ContentScale.FillBounds)
                    if (shadow) Image(preview.analysis.low.asImageBitmap(), tr(R.string.jpeg_shadow_warning_20ffe), imageModifier, contentScale = ContentScale.FillBounds)
                }
                Surface(color = Color(0xB8222A20), shape = MaterialTheme.shapes.extraSmall, modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp)) {
                    Text("%.1f×".format(display.zoom), Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = Color(0xFFE3ECDC), style = MaterialTheme.typography.labelSmall)
                }
                if (photo.isDemo) Text(tr(R.string.demo_watermark), Modifier.align(Alignment.TopStart).padding(12.dp), color = Color(0xFFA7B99C), style = MaterialTheme.typography.labelSmall)
            }
        }
        if (histogram && loaded != null) Histogram(loaded!!.analysis.rgb)
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
            IconButton(onClick = { move(Viewport()) }, enabled = loaded != null) { Icon(Icons.Default.FitScreen, tr(R.string.fit_to_screen_407f7)) }
            IconToggleButton(histogram, { value -> repo.updateSettings { it.copy(showHistogram = value) } }) { Icon(Icons.Default.BarChart, tr(R.string.jpeg_histogram)) }
            IconToggleButton(highlight, { value -> repo.updateSettings { it.copy(showHighlights = value) } }) { Icon(Icons.Default.LightMode, tr(R.string.jpeg_highlight_warning_9186a)) }
            IconToggleButton(shadow, { value -> repo.updateSettings { it.copy(showShadows = value) } }) { Icon(Icons.Default.Contrast, tr(R.string.jpeg_shadow_warning_20ffe)) }
            IconButton(onClick = { notes = true }) { Icon(Icons.Default.EditNote, tr(R.string.local_and_back_notes_7d42c)) }
            IconButton(onClick = { export.launch(photo.name.substringBeforeLast('.') + "-preview.jpg") }, enabled = photo.cacheFile != null) { Icon(Icons.Default.SaveAlt, tr(R.string.save_jpeg_preview_901e5)) }
        }
        if (highlight || shadow) Text(tr(R.string.jpeg_sample_warning_highlight_250_largest_shado_d2e18), Modifier.padding(horizontal = 20.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (info) AlertDialog(onDismissRequest = { info = false }, title = { Text(tr(R.string.photo_information_8aa32)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(photo.path, style = MaterialTheme.typography.bodySmall)
            Text(tr(R.string.photo_details, photo.source.label, photo.width, photo.height, formatBytes(photo.bytes), if (photo.starred) tr(R.string.yes) else tr(R.string.no)), style = MaterialTheme.typography.bodySmall)
            if (loaded != null && loaded!!.bitmap.width != photo.width) Text(tr(R.string.the_displayed_image_was_reduced_to_limit_memory_d149a), style = MaterialTheme.typography.bodySmall)
            if (photo.sha256.isNotEmpty()) Text(tr(R.string.preview_sha_256_n_1_s_46583 , photo.sha256), style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
            photo.metadata.forEach { (key, value) -> Text(tr(R.string.metadata_value, if (key == "camera_directory_date" || key == "目录日期") tr(R.string.directory_date_99665) else key, value), style = MaterialTheme.typography.bodySmall) }
            if (photo.localNote.isNotEmpty()) Text(tr(R.string.local_note_1_s_d7494 , photo.localNote))
            Text(tr(R.string.preview_attachments_and_mos_originals_are_separ_3b71f), style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(onClick = { info = false }) { Text(tr(R.string.close_6c14b)) } }, dismissButton = {
        TextButton(onClick = { repo.requestMetadata(photo.path) }, enabled = connected && !photo.isDemo) { Text(tr(R.string.read_back_information_80058)) }
    })
    if (notes) NotesDialog(photo, connected, repo) { notes = false }
}

@Composable private fun Histogram(channels: List<IntArray>) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
        Text(tr(R.string.histogram_source_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Canvas(Modifier.fillMaxWidth().height(44.dp).padding(top = 4.dp)) {
            drawLine(Color.Gray.copy(alpha = .5f), Offset(0f, size.height), Offset(size.width, size.height))
            val peak = channels.maxOf { it.maxOrNull() ?: 1 }.coerceAtLeast(1)
            val colors = listOf(Color(0xFFE48073), Color(0xFFADDA86), Color(0xFF81B8EE))
            channels.forEachIndexed { c, values ->
                val path = Path().apply {
                    moveTo(0f, size.height)
                    values.forEachIndexed { i, n -> lineTo(i / 255f * size.width, size.height * (1f - n.toFloat() / peak)) }
                    lineTo(size.width, size.height); close()
                }
                drawPath(path, colors[c].copy(alpha = .45f))
            }
        }
    }
}

@Composable private fun NotesDialog(photo: LeafPhoto, connected: Boolean, repo: LeafRepository, close: () -> Unit) {
    var note by rememberSaveable(photo.path) { mutableStateOf(photo.localNote) }
    var copyright by rememberSaveable(photo.path) { mutableStateOf(photo.metadata["copyright"] ?: "") }
    AlertDialog(onDismissRequest = close, title = { Text(tr(R.string.notes_and_flags_53c7a)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(tr(R.string.stars_and_local_notes_are_saved_on_the_phone_th_97eb9), style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(note, { note = it.take(4000) }, label = { Text(tr(R.string.note_e0361)) }, minLines = 3)
            OutlinedTextField(copyright, { copyright = it.take(1000) }, label = { Text(tr(R.string.back_copyright_field_optional_02e2d)) })
            Text(tr(R.string.writing_to_the_back_uses_the_original_wiview_se_49c33), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { repo.writeCameraNotes(photo.path, copyright, note); close() }, enabled = connected && !photo.isDemo) { Text(tr(R.string.writing_notes_to_back_18e71)) }
        }
    }, confirmButton = { TextButton(onClick = { repo.localNote(photo.path, note); close() }) { Text(tr(R.string.save_local_note_3fc9a)) } }, dismissButton = { TextButton(onClick = close) { Text(tr(R.string.cancel_4d0b4)) } })
}
