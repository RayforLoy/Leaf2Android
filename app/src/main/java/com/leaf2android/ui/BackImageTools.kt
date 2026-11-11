package com.leaf2android.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.leaf2android.R
import com.leaf2android.data.*
import com.leaf2android.localization.tr
import com.leaf2android.protocol.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun BackImageToolsDialog(photo: LeafPhoto, state: SessionState, repo: LeafRepository, close: () -> Unit) {
    var zoomName by rememberSaveable(photo.path) { mutableStateOf(ImageZoom.FIT.name) }
    var alarmName by rememberSaveable(photo.path) { mutableStateOf("off") }
    var x by rememberSaveable(photo.path) { mutableStateOf("") }
    var y by rememberSaveable(photo.path) { mutableStateOf("") }
    var graySample by rememberSaveable(photo.path) { mutableStateOf(false) }
    var confirmGray by rememberSaveable(photo.path) { mutableStateOf(false) }
    var confirmFutureLight by rememberSaveable(photo.path) { mutableStateOf(false) }
    val point = runCatching { ImagePoint(x.toInt(), y.toInt()) }.getOrNull()
    val zoom = ImageZoom.valueOf(zoomName)
    val preview = state.backPreview?.takeIf { it.photo.path == photo.path }
    val enabled = state.connected && !state.controlsBusy
    val zoomLabels = ImageZoom.entries.associateWith { if (it == ImageZoom.FIT) tr(R.string.back_overview) else it.factor.toString() }
    val alarmLabels = mapOf("off" to tr(R.string.preview_alarm_off), ExposureEdge.WHITE.name to tr(R.string.preview_alarm_white), ExposureEdge.DARK.name to tr(R.string.preview_alarm_dark), ExposureEdge.BOTH.name to tr(R.string.preview_alarm_both))
    var bitmap by remember(preview?.photo?.sha256) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(preview?.photo?.sha256) {
        bitmap = preview?.photo?.cacheFile?.let { file -> withContext(Dispatchers.Default) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file, bounds)
            var sample = 1
            while (bounds.outWidth.toLong() * bounds.outHeight / (sample.toLong() * sample) > 4_000_000L) sample *= 2
            BitmapFactory.decodeFile(file, BitmapFactory.Options().apply { inSampleSize = sample })
        } }
    }
    AlertDialog(onDismissRequest = close, title = { Text(tr(R.string.back_image_tools)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(photo.path, style = MaterialTheme.typography.bodySmall)
            Text(tr(R.string.back_preview_separate), style = MaterialTheme.typography.bodySmall)
            ControlChoice(tr(R.string.back_zoom_level), zoomLabels.getValue(zoom), zoomLabels.values.toList(), enabled, "back-preview-zoom") { label -> zoomName = zoomLabels.entries.first { it.value == label }.key.name }
            ControlChoice(tr(R.string.back_preview_alarm), alarmLabels.getValue(alarmName), alarmLabels.values.toList(), enabled, "back-preview-alarm") { label -> alarmName = alarmLabels.entries.first { it.value == label }.key }
            Text(tr(R.string.back_image_coordinates_hint), style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(x, { x = it.take(8) }, label = { Text("X") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(y, { y = it.take(8) }, label = { Text("Y") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            Row {
                Checkbox(graySample, { graySample = it }, enabled = enabled)
                Text(tr(R.string.sample_gray_balance_at_point), style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = { repo.requestBackPreview(photo.path, zoom, if (zoom == ImageZoom.FIT) null else point,
                if (alarmName == "off") null else ExposureEdge.valueOf(alarmName), if (graySample) point else null) },
                enabled = enabled && (zoom == ImageZoom.FIT || point != null) && (!graySample || point != null)) { Text(tr(R.string.read_processed_preview)) }
            if (state.controlsBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
            bitmap?.let { Image(it.asImageBitmap(), tr(R.string.back_processed_preview), Modifier.fillMaxWidth().height(220.dp), contentScale = ContentScale.Fit) }
            preview?.let {
                Text(tr(R.string.back_processed_dimensions, it.photo.width, it.photo.height), style = MaterialTheme.typography.bodySmall)
                if (it.sampledLight != null) {
                    Text(tr(R.string.sampled_light_value, it.sampledLight), style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { confirmGray = true }, enabled = enabled) { Text(tr(R.string.apply_photo_gray_balance)) }
                    OutlinedButton(onClick = { confirmFutureLight = true }, enabled = enabled) { Text(tr(R.string.apply_gray_balance_to_future)) }
                }
            }
            OutlinedButton(onClick = { repo.restoreGrayBalance(photo.path) }, enabled = enabled) { Text(tr(R.string.restore_gray_balance_preview)) }
            HorizontalDivider()
            OutlinedButton(onClick = { repo.requestPixelProbe(photo.path, point!!) }, enabled = enabled && point != null) { Text(tr(R.string.reading_back_pixel_probe)) }
            if (state.pixelProbePath == photo.path) state.pixelProbe?.let {
                Text(tr(R.string.back_pixel_probe_result, it.histogramLocation, it.histogramValue), style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(onClick = { repo.requestMetadata(photo.path) }, enabled = enabled) { Text(tr(R.string.read_back_histograms)) }
            if (state.histogramPath == photo.path) state.backHistograms?.let { histograms ->
                if (histograms.hasData) BackHistogramChart(histograms) else Text(tr(R.string.no_back_histogram), style = MaterialTheme.typography.bodySmall)
            }
            Text(tr(R.string.back_analysis_source), style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(onClick = close) { Text(tr(R.string.close_6c14b)) } })
    if (confirmGray && preview?.sampledLight != null) AlertDialog(onDismissRequest = { confirmGray = false }, title = { Text(tr(R.string.apply_photo_gray_balance)) }, text = { Text(tr(R.string.gray_balance_apply_effect)) },
        confirmButton = { TextButton(onClick = { repo.commitGrayBalance(photo.path, preview.sampledLight); confirmGray = false }, enabled = enabled) { Text(tr(R.string.confirm_back_action)) } },
        dismissButton = { TextButton(onClick = { confirmGray = false }) { Text(tr(R.string.cancel_4d0b4)) } })
    if (confirmFutureLight && preview?.sampledLight != null) AlertDialog(onDismissRequest = { confirmFutureLight = false }, title = { Text(tr(R.string.apply_gray_balance_to_future)) }, text = { Text(tr(R.string.gray_balance_future_effect)) },
        confirmButton = { TextButton(onClick = { repo.applySampledLightToFuture(preview.sampledLight); confirmFutureLight = false }, enabled = enabled) { Text(tr(R.string.apply_and_read_back)) } },
        dismissButton = { TextButton(onClick = { confirmFutureLight = false }) { Text(tr(R.string.cancel_4d0b4)) } })
}

@Composable
private fun BackHistogramChart(histograms: BackHistograms) {
    val series = listOf(histograms.luminance to Color.Gray, histograms.red to Color(0xFFE8877E), histograms.green to Color(0xFFB7DF8C), histograms.blue to Color(0xFF85BFF1)).filter { !it.first.isNullOrEmpty() }
    val peak = series.maxOfOrNull { it.first!!.maxOrNull() ?: 0L }?.coerceAtLeast(1L)?.toFloat() ?: 1f
    Text(tr(R.string.back_histogram_bins, series.joinToString(" / ") { it.first!!.size.toString() }), style = MaterialTheme.typography.labelSmall)
    Canvas(Modifier.fillMaxWidth().height(100.dp)) {
        series.forEach { (bins, color) ->
            val values = bins!!
            val line = Path()
            values.forEachIndexed { index, count ->
                val px = index.toFloat() / (values.size - 1).coerceAtLeast(1) * size.width
                val py = size.height * (1 - count.toFloat() / peak)
                if (index == 0) line.moveTo(px, py) else line.lineTo(px, py)
            }
            drawPath(line, color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx()))
        }
    }
}
