@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.leaf2android.ui

import com.leaf2android.R
import com.leaf2android.localization.*

import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.leaf2android.connection.DeviceDiscovery
import com.leaf2android.connection.DiscoveryState
import com.leaf2android.data.*
import com.leaf2android.protocol.WireEncoding
import java.io.File

private enum class Page(private val titleResource: Int, val icon: ImageVector) {
    SHOOT(R.string.page_shoot, Icons.Default.CameraAlt), LIBRARY(R.string.page_library, Icons.Default.PhotoLibrary),
    CONNECT(R.string.page_connect, Icons.Default.Bluetooth), SETTINGS(R.string.page_settings, Icons.Default.Tune);
    val title: String get() = tr(titleResource)
    val classic: String get() = title
}
private val ModernColors = darkColorScheme(
    primary = Color(0xFFB7DF8C), onPrimary = Color(0xFF223318), primaryContainer = Color(0xFF304329), onPrimaryContainer = Color(0xFFD4EBC1),
    secondaryContainer = Color(0xFF35462C), onSecondaryContainer = Color(0xFFD3EAC0),
    secondary = Color(0xFFC8D0BC), background = Color(0xFF10130F), surface = Color(0xFF171C15),
    surfaceContainer = Color(0xFF20261D), surfaceVariant = Color(0xFF30382C), onSurface = Color(0xFFE7EBDD),
    onSurfaceVariant = Color(0xFFABB4A3), outline = Color(0xFF66715F),
)
private val PdaColors = lightColorScheme(
    primary = Color(0xFF365D37), onPrimary = Color.White, primaryContainer = Color(0xFFC9D8BF), onPrimaryContainer = Color(0xFF203D1E),
    secondaryContainer = Color(0xFFC7D6B8), onSecondaryContainer = Color(0xFF243D20),
    background = Color(0xFFE2E2DA), surface = Color(0xFFF0F0E7), surfaceContainer = Color(0xFFD6D9CF),
    surfaceVariant = Color(0xFFC7CDBF), onSurface = Color(0xFF20281E), onSurfaceVariant = Color(0xFF4E5949),
)

@Composable
fun LeafApp(
    repository: LeafRepository, discovery: DeviceDiscovery, requestPermissions: () -> Unit, enableBluetooth: () -> Unit,
    connect: (String, String) -> Unit, disconnect: () -> Unit, keepAwake: (Boolean) -> Unit, shareFile: (File) -> Unit,
) {
    val state by repository.state.collectAsStateWithLifecycle()
    val settings by repository.settings.collectAsStateWithLifecycle()
    val devices by discovery.state.collectAsStateWithLifecycle()
    val localeRevision by L10n.revisions.collectAsStateWithLifecycle()
    val selectedLanguage = remember(localeRevision) { L10n.languageTag() }
    var pageName by rememberSaveable { mutableStateOf(Page.SHOOT.name) }
    val page = Page.valueOf(pageName)
    val pda = settings.style == UiStyle.PDA
    val shortWindow = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() } < 500.dp
    LaunchedEffect(settings.keepAwake, state.active) { keepAwake(settings.keepAwake && state.active) }
    DisposableEffect(Unit) { onDispose { keepAwake(false) } }
    MaterialTheme(
        colorScheme = if (pda) PdaColors else ModernColors,
        shapes = Shapes(extraSmall = RoundedCornerShape(4.dp), small = RoundedCornerShape(if (pda) 2.dp else 12.dp),
            medium = RoundedCornerShape(if (pda) 2.dp else 18.dp), large = RoundedCornerShape(if (pda) 2.dp else 24.dp)),
    ) {
        Scaffold(
            modifier = Modifier.semantics { testTagsAsResourceId = true },
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                Column(Modifier.statusBarsPadding().padding(horizontal = 20.dp, vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (pda) "Leaf WiView" else "leaf", fontSize = MaterialTheme.typography.titleLarge.fontSize,
                            fontWeight = FontWeight.SemiBold, fontFamily = if (pda) FontFamily.Serif else FontFamily.Default)
                        if (!pda && !shortWindow) Text("  /  ANDROID", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        if (shortWindow) Page.entries.forEach { item ->
                            TextButton(onClick = { pageName = item.name }, modifier = Modifier.testTag("page-${item.name}")) {
                                Icon(item.icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(5.dp)); Text(if (pda) item.classic else item.title)
                            }
                        }
                        Surface(color = if (state.connected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(if (pda) 2.dp else 50.dp)) {
                            Row(Modifier.clickable { pageName = Page.CONNECT.name }.padding(horizontal = 11.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Bluetooth, null, Modifier.size(14.dp))
                                Spacer(Modifier.width(5.dp)); Text(state.phase.label, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    if (pda && !shortWindow) Text(tr(R.string.classic_back_caption), style = MaterialTheme.typography.labelSmall)
                }
            },
            bottomBar = {
                if (!shortWindow) {
                NavigationBar(tonalElevation = 0.dp) {
                    Page.entries.forEach { item ->
                        NavigationBarItem(selected = item == page, onClick = { pageName = item.name },
                            icon = { Icon(item.icon, null) }, label = { Text(if (pda) item.classic else item.title) },
                            modifier = Modifier.testTag("page-${item.name}"))
                    }
                }
                }
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                val message = state.error ?: state.notice?.takeUnless { page == Page.SHOOT && it.resourceId == R.string.offline_demo_no_back_is_connected_this_generate_3eb0f }
                if (message != null) {
                    Surface(color = if (state.error != null) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp), shape = MaterialTheme.shapes.small) {
                        Row(Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(message.resolve(), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            IconButton(onClick = repository::clearMessage, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.Close, tr(R.string.dismiss_message_c6208), Modifier.size(18.dp)) }
                        }
                    }
                }
                when (page) {
                    Page.SHOOT -> ShootPage(state, settings, repository, { pageName = Page.CONNECT.name })
                    Page.LIBRARY -> LibraryPage(state, repository) { pageName = Page.SHOOT.name }
                    Page.CONNECT -> ConnectionPage(state, devices, repository, discovery, requestPermissions, enableBluetooth, connect, disconnect)
                    Page.SETTINGS -> SettingsPage(settings, state, repository, shareFile, selectedLanguage)
                }
            }
        }
    }
}

@Composable private fun SectionTitle(title: String, subtitle: String, trailing: @Composable (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing?.invoke()
    }
}

@Composable private fun ShootPage(state: SessionState, settings: LeafSettings, repo: LeafRepository, openConnect: () -> Unit) {
    val shortWindow = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() } < 500.dp
    Column(Modifier.fillMaxSize()) {
        if (!shortWindow) {
        SectionTitle(if (settings.style == UiStyle.PDA) tr(R.string.shoot_image_check_f8464) else tr(R.string.capture_monitor_5cc3d), tr(R.string.aptus_valeo_release_mechanical_shutter_manually_71543)) {
            FilterChip(selected = settings.autoFollow, onClick = { repo.updateSettings { it.copy(autoFollow = !it.autoFollow) } },
                label = { Text(tr(R.string.follow_new_644b5)) }, leadingIcon = { Icon(Icons.Default.Sync, null, Modifier.size(16.dp)) })
        }
        }
        val cameraStatus = listOfNotNull(state.camera["battery_percent"]?.let { tr(R.string.back_battery_1_s_cbba0 , it) }, state.camera["iso"]?.let { "ISO $it" }, state.camera["exp_left"]?.let { tr(R.string.frames_left_afc47 , it) })
        if (cameraStatus.isNotEmpty()) Text(cameraStatus.joinToString("   ·   "), Modifier.padding(horizontal = 20.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall)
        if (state.operation.isNotBlank()) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text(state.operation.resolve(), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (state.expected > 0) LinearProgressIndicator(progress = { state.received.toFloat() / state.expected }, modifier = Modifier.fillMaxWidth())
                else LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val wide = maxWidth >= 740.dp
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    val photo = state.selected
                    if (photo == null) {
                        Box(Modifier.weight(1f).fillMaxWidth().padding(16.dp).background(Color(0xFF080C08), MaterialTheme.shapes.medium), contentAlignment = Alignment.Center) {
                            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.CameraAlt, null, Modifier.size(52.dp), tint = Color(0xFF8B9A80))
                                Spacer(Modifier.height(18.dp))
                                Text(tr(R.string.waiting_for_the_first_photo_77c8a), color = Color(0xFFDEE8D6), style = MaterialTheme.typography.titleLarge)
                                Text(tr(R.string.connect_to_the_back_and_release_the_shutter_man_e1c7c), Modifier.padding(vertical = 10.dp), color = Color(0xFFA0AD97), style = MaterialTheme.typography.bodySmall)
                                Button(onClick = openConnect) { Text(tr(R.string.connect_back_84200)) }
                                TextButton(onClick = repo::loadDemo, enabled = !state.active, modifier = Modifier.testTag("open-demo")) { Text(tr(R.string.open_offline_demo)) }
                            }
                        }
                    } else {
                        PhotoViewer(photo, repo, state.connected, Modifier.weight(1f).fillMaxWidth())
                    }
                    if (!wide && state.photos.isNotEmpty()) Filmstrip(state, repo)
                }
                if (wide) {
                    VerticalDivider()
                    Column(Modifier.width(248.dp).fillMaxHeight()) {
                        Text(tr(R.string.session_photos_1e61a), Modifier.padding(16.dp), style = MaterialTheme.typography.titleSmall)
                        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(state.photos.sortedByDescending { it.receivedAt }, key = { it.path }) { PhotoRow(it, it.path == state.selectedPath, { repo.select(it.path) }, { repo.star(it.path) }) }
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun Filmstrip(state: SessionState, repo: LeafRepository) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(state.photos.sortedByDescending { it.receivedAt }, key = { it.path }) { p ->
            Surface(shape = MaterialTheme.shapes.small, color = if (p.path == state.selectedPath) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                border = if (p.path == state.selectedPath) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                modifier = Modifier.width(132.dp).clickable { repo.select(p.path) }) {
                Column(Modifier.padding(9.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (p.cacheFile != null) Icons.Default.Image else Icons.Default.CloudDownload, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(if (p.isDemo) tr(R.string.offline_demo) else if (p.cacheFile != null) tr(R.string.preview_cached_988af) else tr(R.string.not_downloaded_a58e1), style = MaterialTheme.typography.labelSmall)
                    }
                    Text(p.name, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable private fun PhotoRow(photo: LeafPhoto, selected: Boolean, click: () -> Unit, star: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.small, color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().clickable(onClick = click)) {
        Row(Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (photo.cacheFile != null) Icons.Default.Image else Icons.Default.CloudDownload, null, Modifier.size(22.dp))
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(photo.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (photo.cacheFile != null) "${photo.width} × ${photo.height} · ${formatBytes(photo.bytes)}" else tr(R.string.tap_to_read_preview_from_the_back_92bbe),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = star) { Icon(if (photo.starred) Icons.Default.Star else Icons.Default.StarBorder, tr(R.string.local_flag_fdc55)) }
        }
    }
}

@Composable private fun LibraryPage(state: SessionState, repo: LeafRepository, viewPhoto: () -> Unit) {
    var starredOnly by rememberSaveable { mutableStateOf(false) }
    var sortName by rememberSaveable { mutableStateOf(false) }
    var folderOnly by rememberSaveable { mutableStateOf(false) }
    SectionTitle(tr(R.string.photos_and_folders_8ed09), tr(R.string.back_folders_and_phone_preview_cache_d3018)) {
        IconButton(onClick = { repo.browse(state.folder) }, enabled = state.connected) { Icon(Icons.Default.Refresh, tr(R.string.refresh_back_folder_0b9d3)) }
    }
    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { repo.browse(state.folder.trimEnd('/').substringBeforeLast('/', ".") + "/") }, enabled = state.connected && state.folder != "./") {
            Icon(Icons.Default.ArrowUpward, tr(R.string.parent_folder_3b1c7))
        }
        Text(state.folder, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
    }
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item { FilterChip(starredOnly, { starredOnly = !starredOnly }, label = { Text(tr(R.string.flagged_d1e3d)) }) }
        item { FilterChip(sortName, { sortName = !sortName }, label = { Text(if (sortName) tr(R.string.sort_by_name_390c0) else tr(R.string.recently_received_24dd1)) }) }
        item { FilterChip(folderOnly, { folderOnly = !folderOnly }, label = { Text(tr(R.string.current_folder_only_8d5bc)) }) }
    }
    val folderPaths = state.entries.map { it.path }.toSet()
    val photos = state.photos.filter { (!starredOnly || it.starred) && (!folderOnly || it.path in folderPaths) }
        .let { if (sortName) it.sortedBy { p -> p.name.lowercase() } else it.sortedByDescending { p -> p.receivedAt } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(state.entries.filter { it.isDirectory }, key = { it.path }) { folder ->
            OutlinedCard(onClick = { repo.browse(folder.path) }, enabled = state.connected, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Folder, null); Text(folder.name, Modifier.padding(start = 12.dp))
                }
            }
        }
        items(photos, key = { it.path }) { p -> PhotoRow(p, p.path == state.selectedPath, { repo.select(p.path); viewPhoto() }, { repo.star(p.path) }) }
        if (photos.isEmpty()) item { Text(tr(R.string.no_photos_yet_connect_and_refresh_back_folders_c17b2), Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable private fun ConnectionPage(
    state: SessionState, discovered: DiscoveryState, repo: LeafRepository, discovery: DeviceDiscovery,
    requestPermissions: () -> Unit, enableBluetooth: () -> Unit, connect: (String, String) -> Unit, disconnect: () -> Unit,
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { discovery.refresh() }
    DisposableEffect(Unit) { onDispose { discovery.stop() } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(tr(R.string.phone_to_back_connection_ed551), style = MaterialTheme.typography.titleLarge)
            Text(tr(R.string.classic_bluetooth_spp_rfcomm_a6a76), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(state.deviceName, style = MaterialTheme.typography.titleMedium)
                    Text(state.phase.label + if (state.operation.isNotBlank()) " · ${state.operation}" else "", style = MaterialTheme.typography.bodySmall)
                    Text(tr(R.string.enable_wireless_on_the_back_bring_it_near_the_p_ce46d), style = MaterialTheme.typography.bodyMedium)
                    if (state.active) Button(onClick = disconnect) { Text(tr(R.string.disconnect_eb724)) }
                    else if (repo.lastAddress != null) Button(onClick = { connect(repo.lastAddress!!, repo.lastName) }) { Text(tr(R.string.reconnect_1_s_86aa0 , repo.lastName)) }
                }
            }
        }
        if (!discovery.hasPermissions) item { Button(onClick = requestPermissions, Modifier.fillMaxWidth()) { Text(tr(R.string.grant_nearby_devices_permission_a9b4c)) } }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { if (discovery.hasPermissions) discovery.scan() else requestPermissions() }, enabled = !state.active && !discovered.scanning,
                    modifier = Modifier.weight(1f)) { Text(if (discovered.scanning) tr(R.string.searching_455d8) else tr(R.string.search_for_backs_739ce)) }
                OutlinedButton(onClick = enableBluetooth) { Text(tr(R.string.enable_bluetooth_52439)) }
            }
        }
        if (discovered.scanning) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        discovered.error?.let { error -> item { Text(error.resolve(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) } }
        items(discovered.devices, key = { it.address }) { device ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text(device.name, fontWeight = FontWeight.Medium)
                    Text("${device.address} · ${when (device.bondState) { BluetoothDevice.BOND_BONDED -> tr(R.string.paired); BluetoothDevice.BOND_BONDING -> tr(R.string.pairing); else -> tr(R.string.not_paired) }}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        if (device.bondState == BluetoothDevice.BOND_NONE) TextButton(onClick = { discovery.pair(device.address) }, enabled = !state.active) { Text(tr(R.string.system_pairing_e5242)) }
                        TextButton(onClick = { connect(device.address, device.name) }, enabled = !state.active && device.bondState != BluetoothDevice.BOND_BONDING) { Text(tr(R.string.page_connect)) }
                    }
                }
            }
        }
        item { TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }) { Text(tr(R.string.open_system_bluetooth_settings_e0cc0)) } }
        item {
            HorizontalDivider(); Text(tr(R.string.if_connection_fails_eebcf), Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleSmall)
            Text(tr(R.string.close_other_wiview_connections_if_pairing_has_e_2747e), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable private fun SettingsPage(settings: LeafSettings, state: SessionState, repo: LeafRepository, shareFile: (File) -> Unit, selectedLanguage: String) {
    var showLogs by rememberSaveable { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text(tr(R.string.display_and_shooting_session_d3714), style = MaterialTheme.typography.titleLarge) }
        item {
            Text(tr(R.string.language_heading), style = MaterialTheme.typography.titleSmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("" to R.string.language_follow_system, "zh" to R.string.language_zh, "en" to R.string.language_en).forEach { (tag, label) ->
                    FilterChip(selected = selectedLanguage.substringBefore('-') == tag,
                        onClick = { L10n.setLanguage(tag) }, label = { Text(tr(label)) },
                        modifier = Modifier.testTag("language-${tag.ifEmpty { "system" }}"))
                }
            }
            Text(tr(R.string.language_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Text(tr(R.string.interface_style_56870), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(settings.style == UiStyle.MODERN, { repo.updateSettings { it.copy(style = UiStyle.MODERN) } }, label = { Text(tr(R.string.style_modern)) }, modifier = Modifier.testTag("style-modern"))
                FilterChip(settings.style == UiStyle.PDA, { repo.updateSettings { it.copy(style = UiStyle.PDA) } }, label = { Text(tr(R.string.style_pda)) }, modifier = Modifier.testTag("style-pda"))
            }
            Text(tr(R.string.both_styles_share_the_connection_photos_zoom_po_9f3bc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { SettingSwitch(tr(R.string.follow_new_photos_e804a), tr(R.string.bring_the_latest_received_preview_to_the_foregr_63c00), settings.autoFollow) { value -> repo.updateSettings { it.copy(autoFollow = value) } } }
        item { SettingSwitch(tr(R.string.keep_screen_awake_while_shooting_0b063), tr(R.string.only_while_a_bluetooth_session_is_active_2b09a), settings.keepAwake) { value -> repo.updateSettings { it.copy(keepAwake = value) } } }
        item {
            Text(tr(R.string.local_preview_cache_1_s_mib_42ccb , settings.cacheMiB), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(64, 256, 1024).forEach { amount -> FilterChip(settings.cacheMiB == amount, { repo.updateSettings { it.copy(cacheMiB = amount) } }, label = { Text("$amount") }) }
            }
            Text(tr(R.string.older_cached_previews_are_removed_when_space_is_2ee27), style = MaterialTheme.typography.bodySmall)
        }
        item { HorizontalDivider(); Text(tr(R.string.bluetooth_and_encoding_a1bfc), style = MaterialTheme.typography.titleMedium) }
        item { SettingSwitch(tr(R.string.old_firmware_insecure_rfcomm_24cc1), tr(R.string.off_by_default_reconnect_after_changing_this_an_dc2fe), settings.insecure, !state.active) { value -> repo.updateSettings { it.copy(insecure = value) } } }
        item {
            Text(tr(R.string.protocol_encoding_a6b63), style = MaterialTheme.typography.titleSmall)
            WireEncoding.entries.forEach { encoding ->
                Row(Modifier.fillMaxWidth().clickable(enabled = !state.active) { repo.updateSettings { it.copy(encoding = encoding) } }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(settings.encoding == encoding, onClick = { repo.updateSettings { it.copy(encoding = encoding) } }, enabled = !state.active)
                    Text(L10n.encodingTitle(encoding), style = MaterialTheme.typography.bodySmall)
                }
            }
            Text(tr(R.string.the_original_client_uses_the_pda_system_code_pa_98e65), style = MaterialTheme.typography.bodySmall)
        }
        item { HorizontalDivider(); Text(tr(R.string.features_and_limitations_2d6c2), style = MaterialTheme.typography.titleMedium) }
        items(CameraCapabilities.items) { capability ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(capability.title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                        Text(capability.support.label, style = MaterialTheme.typography.labelSmall, color = if (capability.support == Support.SUPPORTED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(capability.detail, Modifier.padding(top = 5.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            HorizontalDivider(); Text(tr(R.string.offline_checks_and_diagnostics_f2b1e), Modifier.padding(top = 14.dp), style = MaterialTheme.typography.titleMedium)
            Text(tr(R.string.implemented_assuming_back_wireless_works_z_fold_448c1), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = repo::loadDemo, enabled = !state.active) { Text(tr(R.string.load_offline_demo_image_adda4)) }
            OutlinedButton(onClick = { repo.exportDiagnostics(shareFile) }) { Text(tr(R.string.export_diagnostics_5c37f)) }
            TextButton(onClick = { showLogs = true }) { Text(tr(R.string.view_recent_communication_log_e35a7)) }
            Text(tr(R.string.app_version_evidence, com.leaf2android.BuildConfig.VERSION_NAME), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (showLogs) {
        val logs by repo.logs.collectAsStateWithLifecycle()
        AlertDialog(onDismissRequest = { showLogs = false }, title = { Text(tr(R.string.session_log_21bdf)) }, text = {
            LazyColumn(Modifier.heightIn(max = 440.dp)) { items(logs) { Text(it, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(vertical = 4.dp)) } }
        }, confirmButton = { TextButton(onClick = { showLogs = false }) { Text(tr(R.string.close_6c14b)) } })
    }
}

@Composable private fun SettingSwitch(title: String, detail: String, checked: Boolean, enabled: Boolean = true, changed: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleSmall); Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Switch(checked, onCheckedChange = changed, enabled = enabled)
    }
}

internal fun formatBytes(bytes: Long): String = if (bytes < 1024 * 1024) "${bytes / 1024} KiB" else "%.1f MiB".format(bytes / 1048576.0)
