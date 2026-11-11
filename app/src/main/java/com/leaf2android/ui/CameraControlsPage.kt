package com.leaf2android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.leaf2android.R
import com.leaf2android.data.*
import com.leaf2android.localization.tr
import com.leaf2android.protocol.*
import java.io.File

private fun fieldTitle(field: ShootField): String = tr(when (field) {
    ShootField.NAME -> R.string.shooting_setup
    ShootField.FOLDER -> R.string.shooting_folder
    ShootField.ISO -> R.string.setting_iso
    ShootField.QUALITY -> R.string.setting_quality
    ShootField.FRAMING -> R.string.setting_framing
    ShootField.TONING -> R.string.setting_toning
    ShootField.LIGHT -> R.string.setting_white_balance
    ShootField.SHARPNESS -> R.string.setting_sharpness
    ShootField.INPUT_PROFILE -> R.string.setting_input_profile
    ShootField.OUTPUT_PROFILE -> R.string.setting_output_profile
    ShootField.CAMERA_TYPE -> R.string.setting_camera_type
    ShootField.ICC -> R.string.setting_icc
    ShootField.PREFIX -> R.string.setting_prefix
    ShootField.COMPRESSION -> R.string.setting_compression
    ShootField.NL_COMPRESSION -> R.string.setting_nl_compression
    ShootField.ORIENTATION -> R.string.setting_orientation
    ShootField.COLOR -> R.string.setting_color
    ShootField.SENSOR_CROP -> R.string.setting_sensor_crop
    ShootField.GRAIN -> R.string.setting_grain
    ShootField.MOIRE -> R.string.setting_moire
    ShootField.SATURATION -> R.string.setting_saturation
})

@Composable
internal fun CameraControlsPage(state: SessionState, repo: LeafRepository, shareFile: (File) -> Unit) {
    val parsed = remember(state.camera) { runCatching { ShootingConfiguration.from(state.camera) } }
    val configuration = parsed.getOrNull()
    val active = configuration?.active
    val direct = remember(state.camera) { runCatching { DirectCameraSettings.from(state.camera) }.getOrNull() }
    val imageConfiguration = remember(state.camera) { runCatching { ImageSettingsConfiguration.from(state.camera) }.getOrNull() }
    val activeImage = imageConfiguration?.active
    val enabled = state.connected && !state.controlsBusy
    var changes by rememberSaveable(active?.values) { mutableStateOf(emptyMap<String, String>()) }
    var confirmSetup by rememberSaveable { mutableStateOf(false) }
    var selectedSetup by rememberSaveable { mutableStateOf<String?>(null) }
    var fileAction by rememberSaveable { mutableStateOf<String?>(null) }
    var subject by rememberSaveable { mutableStateOf("") }
    var argument by rememberSaveable { mutableStateOf("") }
    var directChanges by rememberSaveable(direct?.values) { mutableStateOf(emptyMap<String, String>()) }
    val directEdits = directChanges.mapKeys { ShootField.valueOf(it.key) }
    var confirmDirectIso by rememberSaveable { mutableStateOf(false) }
    var confirmClock by rememberSaveable { mutableStateOf(false) }
    var imageChanges by rememberSaveable(activeImage?.values) { mutableStateOf(emptyMap<String, String>()) }
    var confirmImage by rememberSaveable { mutableStateOf(false) }
    var selectedImageSetup by rememberSaveable { mutableStateOf<String?>(null) }
    var sessionRequest by rememberSaveable { mutableStateOf<String?>(null) }
    val imageEdits = imageChanges.mapKeys { ShootField.valueOf(it.key) }
    var globalNotes by rememberSaveable { mutableStateOf(false) }
    var imageTools by rememberSaveable { mutableStateOf(false) }
    var laboratory by rememberSaveable { mutableStateOf(false) }
    var annotation by rememberSaveable { mutableStateOf("") }
    var copyright by rememberSaveable { mutableStateOf("") }
    val edits = changes.mapKeys { ShootField.valueOf(it.key) }
    val valid = active != null && configuration != null && runCatching { configuration.validateChanges(active, edits) }.isSuccess
    fun openFileAction(kind: String, path: String) { subject = path; argument = ""; fileAction = kind }

    LazyColumn(Modifier.fillMaxSize().testTag("back-controls-list"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text(tr(R.string.remote_shooting_settings), style = MaterialTheme.typography.titleLarge)
            Text(state.deviceName, style = MaterialTheme.typography.labelLarge)
            Text(tr(R.string.back_controls_intro), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = repo::refreshControls, enabled = enabled, modifier = Modifier.testTag("refresh-back-controls")) { Text(tr(R.string.reading_back_controls)) }
            OutlinedButton(onClick = { laboratory = true }, modifier = Modifier.testTag("open-laboratory")) { Text(tr(R.string.open_laboratory)) }
            if (state.controlsBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (!state.connected) Text(tr(R.string.connect_for_back_controls), style = MaterialTheme.typography.bodySmall)
        }
        item {
            Text(tr(R.string.shooting_setup), style = MaterialTheme.typography.titleMedium)
            if (configuration != null && configuration.setups.isNotEmpty()) {
                ControlChoice(tr(R.string.active_shooting_setup), configuration.activeName.orEmpty(), configuration.setups.map { it.name }, enabled, "select-shooting-setup") { selectedSetup = it }
            }
            if (active == null && direct == null) Text(tr(R.string.complete_setup_unavailable), style = MaterialTheme.typography.bodySmall)
            parsed.exceptionOrNull()?.let { Text(if (it is ProtocolException) it.messageForLanguage(com.leaf2android.localization.L10n.isChinese) else it.message.orEmpty(), style = MaterialTheme.typography.bodySmall) }
            if (active != null && active.missingFields.isNotEmpty()) Text(tr(R.string.setup_missing_fields, active.missingFields.joinToString()), style = MaterialTheme.typography.bodySmall)
        }
        if (direct != null) item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(ShootField.ISO, ShootField.ORIENTATION, ShootField.CAMERA_TYPE, ShootField.SENSOR_CROP, ShootField.COMPRESSION, ShootField.FOLDER, ShootField.PREFIX).forEach { field ->
                    val value = directEdits[field] ?: direct[field].orEmpty()
                    if (field in setOf(ShootField.FOLDER, ShootField.PREFIX)) OutlinedTextField(value, { directChanges = directChanges + (field.name to it) }, label = { Text(fieldTitle(field)) }, enabled = enabled && direct[field] != null, singleLine = true, modifier = Modifier.fillMaxWidth())
                    else ControlChoice(fieldTitle(field), value, direct.choices[field].orEmpty(), enabled && direct[field] != null && direct.choices[field].orEmpty().isNotEmpty(), "direct-${field.wireName}") { directChanges = directChanges + (field.name to it) }
                }
                Text(tr(R.string.device_choices_only), style = MaterialTheme.typography.bodySmall)
                Text(tr(R.string.prefix_current_length_limit), style = MaterialTheme.typography.bodySmall)
                Button(onClick = { confirmDirectIso = true }, enabled = enabled && directEdits.isNotEmpty() && runCatching { directEdits.forEach { (field, value) -> direct.validate(field, value) } }.isSuccess) { Text(tr(R.string.apply_shooting_changes)) }
                direct.values.filterKeys { key -> ShootField.entries.none { it.wireName.equals(key, true) } }.forEach { (key, value) -> Text("$key: $value", style = MaterialTheme.typography.bodySmall) }
            }
        }
        if (active != null && configuration != null) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ShootField.legacy.filter { it != ShootField.NAME }.forEach { field ->
                        if (field == ShootField.NL_COMPRESSION && (edits[ShootField.COMPRESSION] ?: active[ShootField.COMPRESSION]) != "Nearly Lossless") return@forEach
                        val current = edits[field] ?: active[field].orEmpty()
                        val choices = if (field == ShootField.ICC) listOf("0", "1") else configuration.choices[field].orEmpty()
                        if (field in setOf(ShootField.PREFIX, ShootField.FOLDER)) {
                            OutlinedTextField(current, { changes = changes + (field.name to it) }, label = { Text(fieldTitle(field)) },
                                enabled = enabled, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("shooting-field-${field.wireName}"))
                        } else ControlChoice(fieldTitle(field), current, choices, enabled && choices.isNotEmpty(), "shooting-field-${field.wireName}") { changes = changes + (field.name to it) }
                    }
                    Text(tr(R.string.device_choices_only), style = MaterialTheme.typography.bodySmall)
                    active.values.entries.firstOrNull { it.key.equals("ShutterSpeed", true) }?.let {
                        Text(tr(R.string.shutter_metadata_read_only, it.value), style = MaterialTheme.typography.bodySmall)
                    }
                    Button(onClick = { confirmSetup = true }, enabled = enabled && edits.isNotEmpty() && valid, modifier = Modifier.testTag("apply-shoot-settings")) { Text(tr(R.string.apply_shooting_changes)) }
                    if (edits.isNotEmpty() && !valid) Text(tr(R.string.invalid_shooting_changes), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (imageConfiguration != null) item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HorizontalDivider()
                Text(tr(R.string.image_processing_setup), style = MaterialTheme.typography.titleMedium)
                ControlChoice(tr(R.string.active_shooting_setup), imageConfiguration.activeName.orEmpty(), imageConfiguration.setups.map { it.name }, enabled, "select-image-settings") { selectedImageSetup = it }
                if (activeImage != null) {
                    ImageSettingsConfiguration.fields.filter { it != ShootField.NAME }.forEach { field ->
                        val choices = if (field == ShootField.ICC) listOf("0", "1") else imageConfiguration.choices[field].orEmpty()
                        ControlChoice(fieldTitle(field), imageEdits[field] ?: activeImage[field].orEmpty(), choices, enabled && choices.isNotEmpty(), "image-setting-${field.wireName}") { imageChanges = imageChanges + (field.name to it) }
                    }
                    Text(tr(R.string.device_choices_only), style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { confirmImage = true }, enabled = enabled && imageEdits.isNotEmpty() && runCatching { imageConfiguration.updatedCsv(activeImage, imageEdits) }.isSuccess) { Text(tr(R.string.apply_shooting_changes)) }
                }
            }
        }
        item {
            HorizontalDivider()
            Text(tr(R.string.default_capture_notes), style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = { annotation = state.camera["annotation"].orEmpty(); copyright = state.camera["copyright"].orEmpty(); globalNotes = true }, enabled = enabled) { Text(tr(R.string.edit_default_capture_notes)) }
            Text(tr(R.string.default_capture_notes_effect), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { confirmClock = true }, enabled = enabled) { Text(tr(R.string.sync_back_clock)) }
            Text(tr(R.string.back_clock_current, state.camera["currenttime"] ?: tr(R.string.not_read_yet)), style = MaterialTheme.typography.bodySmall)
        }
        item {
            HorizontalDivider()
            Text(tr(R.string.back_photo_actions), style = MaterialTheme.typography.titleMedium)
            val photo = state.selected
            Text(photo?.path ?: tr(R.string.select_photo_for_back_actions), style = MaterialTheme.typography.bodySmall)
            if (photo != null) {
                val currentPhoto = !photo.isDemo && photo.deviceAddress != null && photo.deviceAddress == state.deviceAddress && photo.cacheFile != null && photo.connectionGeneration == state.connectionGeneration
                if (!currentPhoto && !photo.isDemo) {
                    Text(tr(R.string.refresh_photo_before_back_write), style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = repo::refreshSelected, enabled = enabled) { Text(tr(R.string.read_preview_again_e63d3)) }
                }
                OutlinedButton(onClick = { imageTools = true }, enabled = enabled && currentPhoto) { Text(tr(R.string.back_image_tools)) }
                Text(tr(R.string.back_mark_value, photo.backMark ?: tr(R.string.not_read_yet)), style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { repo.requestMetadata(photo.path) }, enabled = enabled && currentPhoto, modifier = Modifier.weight(1f)) { Text(tr(R.string.read_photo_metadata)) }
                    OutlinedButton(onClick = { openFileAction("mark", photo.path) }, enabled = enabled && currentPhoto, modifier = Modifier.weight(1f)) { Text(tr(R.string.set_back_good_mark)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { openFileAction("unmark", photo.path) }, enabled = enabled && currentPhoto, modifier = Modifier.weight(1f)) { Text(tr(R.string.clear_back_good_mark)) }
                    OutlinedButton(onClick = { openFileAction("delete", photo.path) }, enabled = enabled && currentPhoto, modifier = Modifier.weight(1f)) { Text(tr(R.string.delete_back_photo)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { openFileAction("copy", photo.path) }, enabled = enabled && currentPhoto, modifier = Modifier.weight(1f)) { Text(tr(R.string.copy_back_photo)) }
                    OutlinedButton(onClick = { openFileAction("move", photo.path) }, enabled = enabled && currentPhoto, modifier = Modifier.weight(1f)) { Text(tr(R.string.move_back_photo)) }
                }
            }
            Text(tr(R.string.local_and_back_marks_separate), style = MaterialTheme.typography.bodySmall)
        }
        item {
            HorizontalDivider()
            Text(tr(R.string.back_folder_actions), style = MaterialTheme.typography.titleMedium)
            Text(state.folder, style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { openFileAction("mkdir", state.folder) }, enabled = enabled) { Text(tr(R.string.create_back_folder)) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { openFileAction("rename", state.folder) }, enabled = enabled && state.folder != "./") { Text(tr(R.string.rename_back_folder)) }
                OutlinedButton(onClick = { openFileAction("rmdir", state.folder) }, enabled = enabled && state.folder != "./") { Text(tr(R.string.remove_empty_back_folder)) }
            }
            Text(tr(R.string.back_files_effect), style = MaterialTheme.typography.bodySmall)
        }
        item {
            HorizontalDivider()
            Text(tr(R.string.back_status_values), style = MaterialTheme.typography.titleMedium)
            val hidden = setOf("shootsetup", "defaultshootsetup") + ShootField.entries.mapNotNull { it.choiceKey }
            state.camera.filterKeys { it !in hidden }.forEach { (key, value) -> Text("$key: $value", style = MaterialTheme.typography.bodySmall) }
        }
        item {
            HorizontalDivider()
            Text(tr(R.string.back_session_requests), style = MaterialTheme.typography.titleMedium)
            Text(tr(R.string.back_session_requests_effect), style = MaterialTheme.typography.bodySmall)
            val hasPowerSelection = state.camera["powersourceselection"].equals("support", true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { sessionRequest = "firewire" }, enabled = enabled && hasPowerSelection, modifier = Modifier.weight(1f)) { Text("FireWire") }
                OutlinedButton(onClick = { sessionRequest = "battery" }, enabled = enabled && hasPowerSelection, modifier = Modifier.weight(1f)) { Text(tr(R.string.camera_battery_power)) }
            }
            if (!hasPowerSelection) Text(tr(R.string.power_selection_unavailable), style = MaterialTheme.typography.bodySmall)
        }
    }
    if (imageTools && state.selected != null) BackImageToolsDialog(state.selected!!, state, repo) { imageTools = false }
    if (laboratory) LaboratoryDialog(repo, shareFile) { laboratory = false }
    sessionRequest?.let { request -> AlertDialog(onDismissRequest = { sessionRequest = null }, title = { Text(tr(R.string.back_session_requests)) }, text = {
        Text(tr(when (request) { "notify-on" -> R.string.enable_capture_notifications; "notify-off" -> R.string.pause_capture_notifications; else -> R.string.power_source_change_effect }) + "\n" + tr(R.string.back_session_requests_effect))
    }, confirmButton = { TextButton(onClick = {
        when (request) { "notify-on" -> repo.setCaptureNotifications(true); "notify-off" -> repo.setCaptureNotifications(false)
            "firewire" -> repo.requestPowerSource(BackPowerSource.FIREWIRE); "battery" -> repo.requestPowerSource(BackPowerSource.CAMERA_BATTERY) }
        sessionRequest = null
    }, enabled = enabled) { Text(tr(R.string.confirm_back_action)) } }, dismissButton = { TextButton(onClick = { sessionRequest = null }) { Text(tr(R.string.cancel_4d0b4)) } }) }
    if (confirmImage && activeImage != null) AlertDialog(onDismissRequest = { confirmImage = false }, title = { Text(tr(R.string.confirm_shooting_changes)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            imageEdits.forEach { (field, value) -> Text("${fieldTitle(field)}: ${activeImage[field]} → $value") }
            Text(tr(R.string.shooting_apply_effect))
        }
    }, confirmButton = { TextButton(onClick = { repo.applyImageSettings(activeImage, imageEdits); confirmImage = false }, enabled = enabled) { Text(tr(R.string.apply_and_read_back)) } }, dismissButton = { TextButton(onClick = { confirmImage = false }) { Text(tr(R.string.cancel_4d0b4)) } })
    selectedImageSetup?.let { name -> AlertDialog(onDismissRequest = { selectedImageSetup = null }, title = { Text(tr(R.string.image_processing_setup)) }, text = { Text(name) },
        confirmButton = { TextButton(onClick = { repo.selectImageSettings(name); selectedImageSetup = null }, enabled = enabled) { Text(tr(R.string.apply_and_read_back)) } },
        dismissButton = { TextButton(onClick = { selectedImageSetup = null }) { Text(tr(R.string.cancel_4d0b4)) } }) }
    if (confirmDirectIso && direct != null) AlertDialog(onDismissRequest = { confirmDirectIso = false }, title = { Text(tr(R.string.confirm_shooting_changes)) }, text = { Column(Modifier.verticalScroll(rememberScrollState())) {
        directEdits.forEach { (field, value) -> Text("${fieldTitle(field)}: ${direct[field]} → $value") }
        Text(tr(R.string.shooting_apply_effect))
    } },
        confirmButton = { TextButton(onClick = { repo.applyDirectSettings(direct, directEdits); confirmDirectIso = false }, enabled = enabled) { Text(tr(R.string.apply_and_read_back)) } },
        dismissButton = { TextButton(onClick = { confirmDirectIso = false }) { Text(tr(R.string.cancel_4d0b4)) } })
    if (confirmClock) AlertDialog(onDismissRequest = { confirmClock = false }, title = { Text(tr(R.string.sync_back_clock)) }, text = { Text(tr(R.string.sync_back_clock_effect)) },
        confirmButton = { TextButton(onClick = { repo.syncClock(); confirmClock = false }, enabled = enabled) { Text(tr(R.string.apply_and_read_back)) } },
        dismissButton = { TextButton(onClick = { confirmClock = false }) { Text(tr(R.string.cancel_4d0b4)) } })
    if (globalNotes) AlertDialog(onDismissRequest = { globalNotes = false }, title = { Text(tr(R.string.default_capture_notes)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(tr(R.string.default_capture_notes_effect), style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(annotation, { annotation = it.take(4000) }, label = { Text(tr(R.string.note_e0361)) })
            OutlinedTextField(copyright, { copyright = it.take(1000) }, label = { Text(tr(R.string.back_copyright_field_optional_02e2d)) })
        }
    }, confirmButton = { TextButton(onClick = { repo.writeDefaultNotes(annotation, copyright); globalNotes = false }, enabled = enabled) { Text(tr(R.string.apply_and_read_back)) } },
        dismissButton = { TextButton(onClick = { globalNotes = false }) { Text(tr(R.string.cancel_4d0b4)) } })
    if (confirmSetup && active != null) AlertDialog(onDismissRequest = { confirmSetup = false }, title = { Text(tr(R.string.confirm_shooting_changes)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            edits.forEach { (field, value) -> Text("${fieldTitle(field)}: ${active[field].orEmpty()} → $value") }
            Text(tr(R.string.shooting_apply_effect), Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(onClick = { repo.applyShootSetup(active, edits); confirmSetup = false }, enabled = enabled && valid) { Text(tr(R.string.apply_and_read_back)) } }, dismissButton = { TextButton(onClick = { confirmSetup = false }) { Text(tr(R.string.cancel_4d0b4)) } })
    selectedSetup?.let { name -> AlertDialog(onDismissRequest = { selectedSetup = null }, title = { Text(tr(R.string.selecting_shooting_setup)) }, text = { Text(name) },
        confirmButton = { TextButton(onClick = { repo.selectShootSetup(name); selectedSetup = null }, enabled = enabled) { Text(tr(R.string.apply_and_read_back)) } },
        dismissButton = { TextButton(onClick = { selectedSetup = null }) { Text(tr(R.string.cancel_4d0b4)) } }) }
    fileAction?.let { kind ->
        val needsArgument = kind in setOf("mkdir", "rename", "copy", "move")
        AlertDialog(onDismissRequest = { fileAction = null }, title = { Text(tr(when (kind) {
            "mkdir" -> R.string.create_back_folder; "rename" -> R.string.rename_back_folder; "rmdir" -> R.string.remove_empty_back_folder
            "copy" -> R.string.copy_back_photo; "move" -> R.string.move_back_photo; "delete" -> R.string.delete_back_photo
            "mark" -> R.string.set_back_good_mark; else -> R.string.clear_back_good_mark
        })) }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(state.deviceName, style = MaterialTheme.typography.titleSmall)
                Text(subject)
                if (needsArgument) OutlinedTextField(argument, { argument = it }, label = { Text(tr(if (kind in setOf("copy", "move")) R.string.target_back_folder else R.string.new_back_name)) }, singleLine = true, modifier = Modifier.testTag("back-action-argument"))
                Text(tr(if (kind == "delete") R.string.delete_back_photo_warning else R.string.back_files_effect), style = MaterialTheme.typography.bodySmall)
            }
        }, confirmButton = { TextButton(onClick = {
            when (kind) {
                "mkdir" -> repo.makeDirectory(subject, argument); "rename" -> repo.renameDirectory(subject, argument)
                "rmdir" -> repo.removeDirectory(subject); "copy" -> repo.copyOrMovePhoto(subject, argument, false)
                "move" -> repo.copyOrMovePhoto(subject, argument, true); "delete" -> repo.deletePhoto(subject)
                "mark" -> repo.writeBackMark(subject, BackMark.GOOD); "unmark" -> repo.writeBackMark(subject, BackMark.UNMARKED)
            }
            fileAction = null
        }, enabled = enabled && (!needsArgument || argument.isNotBlank()), modifier = Modifier.testTag("confirm-back-action")) { Text(tr(R.string.confirm_back_action)) } },
            dismissButton = { TextButton(onClick = { fileAction = null }) { Text(tr(R.string.cancel_4d0b4)) } })
    }
}

@Composable
internal fun ControlChoice(title: String, value: String, choices: List<String>, enabled: Boolean, tag: String, selected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(title, style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth().testTag(tag)) { Text(value.ifEmpty { tr(R.string.not_read_yet) }) }
            DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                choices.forEach { candidate -> DropdownMenuItem(text = { Text(candidate) }, onClick = { selected(candidate); expanded = false }) }
            }
        }
    }
}
