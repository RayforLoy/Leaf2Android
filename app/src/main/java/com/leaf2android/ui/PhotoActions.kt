package com.leaf2android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.leaf2android.R
import com.leaf2android.data.LeafPhoto
import com.leaf2android.data.LeafRepository
import com.leaf2android.data.SessionState
import com.leaf2android.localization.tr
import com.leaf2android.protocol.BackMark
import com.leaf2android.protocol.CameraPaths

internal fun isCurrentBackPhoto(photo: LeafPhoto, state: SessionState): Boolean =
    state.connected && !photo.isDemo && photo.cacheFile != null && photo.deviceAddress != null &&
        photo.deviceAddress == state.deviceAddress && photo.connectionGeneration != null &&
        photo.connectionGeneration == state.connectionGeneration

@Composable
internal fun PhotoActionsDialog(
    photo: LeafPhoto,
    state: SessionState,
    repo: LeafRepository,
    editNotes: () -> Unit,
    close: () -> Unit,
) {
    var action by rememberSaveable(photo.path, photo.connectionGeneration) { mutableStateOf<String?>(null) }
    var target by rememberSaveable(photo.path, photo.connectionGeneration) { mutableStateOf("") }
    val enabled = isCurrentBackPhoto(photo, state) && !state.controlsBusy && !state.labMode
    AlertDialog(onDismissRequest = close, title = { Text(tr(R.string.photo_actions)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(state.deviceName, style = MaterialTheme.typography.labelLarge)
            Text(photo.path, style = MaterialTheme.typography.bodySmall)
            Text(tr(R.string.back_mark_value, photo.backMark ?: tr(R.string.not_read_yet)), style = MaterialTheme.typography.bodySmall)
            if (!isCurrentBackPhoto(photo, state)) Text(tr(R.string.refresh_photo_before_back_write), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = editNotes, modifier = Modifier.fillMaxWidth().testTag("photo-action-notes")) { Text(tr(R.string.local_and_back_notes_7d42c)) }
            OutlinedButton(onClick = { action = "mark" }, enabled = enabled, modifier = Modifier.fillMaxWidth().testTag("photo-action-mark")) { Text(tr(R.string.set_back_good_mark)) }
            OutlinedButton(onClick = { action = "unmark" }, enabled = enabled, modifier = Modifier.fillMaxWidth().testTag("photo-action-unmark")) { Text(tr(R.string.clear_back_good_mark)) }
            OutlinedButton(onClick = { target = ""; action = "copy" }, enabled = enabled, modifier = Modifier.fillMaxWidth().testTag("photo-action-copy")) { Text(tr(R.string.copy_back_photo)) }
            OutlinedButton(onClick = { target = ""; action = "move" }, enabled = enabled, modifier = Modifier.fillMaxWidth().testTag("photo-action-move")) { Text(tr(R.string.move_back_photo)) }
            OutlinedButton(onClick = { action = "delete" }, enabled = enabled, modifier = Modifier.fillMaxWidth().testTag("photo-action-delete")) { Text(tr(R.string.delete_back_photo)) }
            Text(tr(R.string.photo_copy_move_hint), style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(onClick = close) { Text(tr(R.string.close_6c14b)) } })
    action?.let { selected ->
        val transfer = selected == "copy" || selected == "move"
        val validTarget = !transfer || (target.isNotBlank() && runCatching { CameraPaths.folder(target) }.isSuccess)
        AlertDialog(onDismissRequest = { action = null }, title = { Text(tr(when (selected) {
            "delete" -> R.string.delete_back_photo
            "copy" -> R.string.copy_back_photo
            "move" -> R.string.move_back_photo
            "mark" -> R.string.set_back_good_mark
            else -> R.string.clear_back_good_mark
        })) }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(state.deviceName, style = MaterialTheme.typography.labelLarge)
                Text(photo.path, modifier = Modifier.testTag("photo-action-source"))
                if (selected == "delete") Text(tr(R.string.photo_delete_confirm))
                else if (transfer) {
                    OutlinedTextField(target, { target = it }, label = { Text(tr(R.string.target_back_folder)) }, singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("photo-action-target"))
                    Text(tr(R.string.photo_copy_move_hint), style = MaterialTheme.typography.bodySmall)
                } else Text(tr(R.string.local_and_back_marks_separate), style = MaterialTheme.typography.bodySmall)
            }
        }, confirmButton = {
            TextButton(onClick = {
                when (selected) {
                    "delete" -> repo.deletePhoto(photo.path)
                    "copy", "move" -> repo.copyOrMovePhoto(photo.path, target, selected == "move")
                    "mark" -> repo.writeBackMark(photo.path, BackMark.GOOD)
                    "unmark" -> repo.writeBackMark(photo.path, BackMark.UNMARKED)
                }
                action = null
                close()
            }, enabled = enabled && validTarget, modifier = Modifier.testTag(if (selected == "delete") "confirm-photo-delete" else "confirm-photo-action")) { Text(tr(R.string.confirm_back_action)) }
        }, dismissButton = { TextButton(onClick = { action = null }, modifier = Modifier.testTag("cancel-photo-action")) { Text(tr(R.string.cancel_4d0b4)) } })
    }
}
