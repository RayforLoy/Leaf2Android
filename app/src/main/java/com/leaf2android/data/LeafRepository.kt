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
import java.time.LocalDateTime
import java.time.Duration
import java.time.Instant
import java.util.UUID

class LeafRepository(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val actionScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val preferences = context.getSharedPreferences("leaf", Context.MODE_PRIVATE)
    private val store = PhotoStore(context)
    private val storageLock = Mutex()
    // Keep a whole read / write / readback operation together, including against automatic previews.
    // Notification acknowledgements bypass this lock so a back can continue its current operation.
    private val operationLock = Mutex()
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
    private var syncQueued = false
    private var labTouchedConnection = false
    private val labDirectory = File(context.cacheDir, "laboratory").apply { mkdirs() }
    var lastAddress: String? = preferences.getString("lastAddress", null); private set
    var lastName: String = preferences.getString("lastName", BACK_FAMILY) ?: BACK_FAMILY; private set

    init {
        scope.launch {
            val photos = withContext(Dispatchers.IO) { store.load() }
            mutable.update { s ->
                val live = s.photos.map { it.path }.toSet()
                val combined = photos.filterNot { it.path in live } + s.photos
                s.copy(photos = combined, selectedPath = s.selectedPath ?: preferences.getString("selected", null)
                    ?.takeIf { path -> combined.any { p -> p.path == path } })
            }
        }
    }
    private fun readSettings(): LeafSettings {
        // Earlier builds persisted the old 256 MiB default whenever any setting changed.
        // Migrate that value once; keep a smaller 64 MiB choice and all later choices.
        if (!preferences.getBoolean("maxCacheDefaultMigrated", false)) {
            val edit = preferences.edit().putBoolean("maxCacheDefaultMigrated", true)
            if (preferences.getInt("cacheMiB", 256) == 256) edit.putInt("cacheMiB", 1024)
            edit.apply()
        }
        return LeafSettings(
        style = runCatching { UiStyle.valueOf(preferences.getString("style", "MODERN")!!) }.getOrDefault(UiStyle.MODERN),
        encoding = runCatching { WireEncoding.valueOf(preferences.getString("encoding", "WESTERN")!!) }.getOrDefault(WireEncoding.WESTERN),
        insecure = preferences.getBoolean("insecure", false), autoFollow = preferences.getBoolean("autoFollow", true),
        keepAwake = preferences.getBoolean("keepAwake", true), cacheMiB = preferences.getInt("cacheMiB", 1024).coerceIn(64, 1024),
        showHistogram = preferences.getBoolean("showHistogram", false), showHighlights = preferences.getBoolean("showHighlights", false),
        showShadows = preferences.getBoolean("showShadows", false),
        attachmentLimitMiB = preferences.getInt("attachmentLimitMiB", 16).let { if (it == 64) 64 else 16 },
        shootingCompatibility = runCatching { ShootingCompatibility.valueOf(preferences.getString("shootingCompatibility", "AUTO")!!) }.getOrDefault(ShootingCompatibility.AUTO),
        )
    }
    fun updateSettings(update: (LeafSettings) -> LeafSettings) {
        val next = update(settings.value)
        mutableSettings.value = next
        preferences.edit().putString("style", next.style.name).putString("encoding", next.encoding.name)
            .putBoolean("insecure", next.insecure).putBoolean("autoFollow", next.autoFollow)
            .putBoolean("keepAwake", next.keepAwake).putInt("cacheMiB", next.cacheMiB).apply()
        preferences.edit().putBoolean("showHistogram", next.showHistogram).putBoolean("showHighlights", next.showHighlights)
            .putBoolean("showShadows", next.showShadows).apply()
        preferences.edit().putInt("attachmentLimitMiB", next.attachmentLimitMiB).apply()
        preferences.edit().putString("shootingCompatibility", next.shootingCompatibility.name).apply()
    }
    fun viewport(path: String): Viewport = viewports[path] ?: Viewport()
    val shootingDialect: ShootSetupDialect get() = settings.value.shootingCompatibility.dialect(state.value.deviceName, state.value.camera)
    fun shootingConfiguration(camera: Map<String, String> = state.value.camera): ShootingConfiguration = ShootingConfiguration.from(camera, shootingDialect)
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
            error = null, notice = null, camera = emptyMap(), entries = emptyList(), operation = msg(R.string.waiting_for_system_bluetooth_connection_and_pai_8f144), previewCount = 0,
            backPreview = null, pixelProbe = null, backHistograms = null, pixelProbePath = null, histogramPath = null, connectionGeneration = current,
            labMode = false, labBusy = false, labCurrentId = null) }
        val queue = Channel<String>(64)
        imageQueue = queue
        connectionJob = scope.launch {
            try {
                coroutineScope {
                    val p = LeafProtocol(transport, this, configuration.encoding,
                        log = { line -> scope.launch { log(line) } },
                        progress = { done, total -> mutable.update { if (generation == current) it.copy(received = done, expected = total) else it } },
                        failed = { e -> scope.launch { if (generation == current) fail(e) } },
                        maxAttachmentBytes = configuration.attachmentLimitMiB.coerceIn(16,64) * 1024 * 1024)
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
                            if (state.value.labMode) { queued.remove(path); continue }
                            try { operationLock.withLock {
                                if (generation == current && !state.value.labMode) receiveImage(p, path, current)
                            } }
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
        syncQueued = false
        actionScope.coroutineContext.cancelChildren()
        protocol?.close(); protocol = null
        connectionJob?.cancel(); connectionJob = null
        imageQueue?.close(); imageQueue = null; queued.clear()
        mutable.update { it.copy(phase = SessionPhase.DISCONNECTED, operation = UiText.Empty, received = 0, expected = 0, controlsBusy = false, connectionGeneration = null, labBusy = false, labCurrentId = null) }
    }
    private fun fail(e: Throwable) {
        val message = when (e) {
            is SecurityException -> msg(R.string.nearby_devices_permission_was_revoked_grant_per_a7f2d)
            is TimeoutCancellationException -> msg(R.string.bluetooth_connection_timed_out_enable_back_wire_06205)
            else -> errorText(e, msg(R.string.the_bluetooth_session_was_interrupted_29fb2))
        }
        log("SESSION STOP $message")
        protocol?.close()
        actionScope.coroutineContext.cancelChildren()
        mutable.update { it.copy(phase = SessionPhase.FAILED, error = message, operation = UiText.Empty, received = 0, expected = 0, controlsBusy = false) }
        connectionJob?.cancel()
    }
    private fun mergeCamera(frame: WireFrame, encoding: WireEncoding) {
        val values = frame.values(encoding.charset).mapKeys { it.key.lowercase(Locale.ROOT) }
        mutable.update {
            val camera = (if (frame.tag.equals("Sync", true)) emptyMap() else it.camera) + values
            it.copy(camera = camera, shootingDialect = settings.value.shootingCompatibility.dialect(it.deviceName, camera))
        }
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
    private fun controls(title: UiText, clearMessages: Boolean = true, block: suspend (LeafProtocol) -> Unit) = action(title, clearMessages) { p ->
        val current = generation
        mutable.update { it.copy(controlsBusy = true) }
        try { block(p) } finally { if (generation == current) mutable.update { it.copy(controlsBusy = false) } }
    }
    private suspend fun syncControls(p: LeafProtocol): ShootingConfiguration {
        val frame = p.execute(Commands.sync)
        mergeCamera(frame, p.encoding)
        return shootingConfiguration(frame.values(p.encoding.charset))
    }
    private fun refreshAfterConfigurationNotification() {
        if (syncQueued || !state.value.connected || state.value.labMode) return
        val current = generation
        syncQueued = true
        controls(msg(R.string.reading_back_controls), clearMessages = false) { p ->
            try { syncControls(p) } finally { if (generation == current) syncQueued = false }
        }
    }
    fun refreshControls() = controls(msg(R.string.reading_back_controls)) { p ->
        syncControls(p)
        val queries = if (shootingDialect == ShootSetupDialect.DP67) listOf(Commands.currentTime) else
            listOf(Commands.battery, Commands.exposures, Commands.totalFrames, Commands.histogramScale, Commands.currentTime, CameraSystemControls.defaultNotes)
        queries.forEach {
            mergeCamera(p.execute(it), p.encoding)
        }
    }
    fun applyDirectIso(expectedIso: String, value: String) = controls(msg(R.string.writing_shooting_settings)) { p ->
        val before = p.execute(Commands.sync).also { mergeCamera(it, p.encoding) }
        val settings = DirectCameraSettings.from(before.values(p.encoding.charset))
        if (settings == null || settings.iso != expectedIso) throw LocalizedIOException(msg(R.string.shooting_settings_changed))
        p.execute(CameraSystemControls.directIso(settings, value))
        val after = p.execute(Commands.sync).also { mergeCamera(it, p.encoding) }
        if (DirectCameraSettings.from(after.values(p.encoding.charset))?.iso != value) throw LocalizedIOException(msg(R.string.shooting_write_not_confirmed))
        mutable.update { it.copy(notice = msg(R.string.shooting_settings_confirmed)) }
    }
    fun applyDirectSettings(base: DirectCameraSettings, changes: Map<ShootField, String>) = controls(msg(R.string.writing_shooting_settings)) { p ->
        val before = p.execute(Commands.sync).also { mergeCamera(it, p.encoding) }
        val fresh = DirectCameraSettings.from(before.values(p.encoding.charset))
        if (fresh == null || fresh.values != base.values) throw LocalizedIOException(msg(R.string.shooting_settings_changed))
        val requests = changes.map { (field, value) -> CameraSystemControls.directSetting(fresh, field, value) }
        changes[ShootField.FOLDER]?.let { listed(p, it) }
        try { requests.forEach { p.execute(it) } }
        finally { if (state.value.connected) syncControls(p) }
        val actual = DirectCameraSettings.from(state.value.camera)
        val expected = ShootSetup(fresh.values).changed(changes)
        if (actual == null || expected.values.any { (key, value) -> actual.values.entries.firstOrNull { it.key.equals(key, true) }?.value != value }) throw LocalizedIOException(msg(R.string.shooting_write_not_confirmed))
        mutable.update { it.copy(notice = msg(R.string.shooting_settings_confirmed)) }
    }
    fun syncClock() = controls(msg(R.string.sync_back_clock)) { p ->
        val requested = LocalDateTime.now()
        p.execute(CameraSystemControls.clock(requested))
        val frame = p.execute(Commands.currentTime).also { mergeCamera(it, p.encoding) }
        val actual = frame.value("CurrentTime", p.encoding.charset)?.let { runCatching { LocalDateTime.parse(it, CameraSystemControls.clockFormat) }.getOrNull() }
        if (actual == null || Duration.between(requested, actual).abs().seconds > 30) throw LocalizedIOException(msg(R.string.clock_write_not_confirmed))
        mutable.update { it.copy(notice = msg(R.string.clock_confirmed)) }
    }
    fun writeDefaultNotes(annotation: String, copyright: String) = controls(msg(R.string.default_capture_notes)) { p ->
        p.execute(CameraSystemControls.notes(annotation, copyright))
        val frame = p.execute(CameraSystemControls.defaultNotes).also { mergeCamera(it, p.encoding) }
        if (frame.value("Annotation", p.encoding.charset) != annotation || frame.value("Copyright", p.encoding.charset) != copyright)
            throw LocalizedIOException(msg(R.string.default_notes_not_confirmed))
        mutable.update { it.copy(notice = msg(R.string.default_notes_confirmed)) }
    }
    fun setCaptureNotifications(enabled: Boolean) = controls(msg(R.string.back_session_requests)) { p ->
        p.execute(CameraSystemControls.captureNotifications(enabled))
        mutable.update { it.copy(notice = msg(R.string.back_request_ack_only)) }
    }
    fun requestPowerSource(source: BackPowerSource) = controls(msg(R.string.back_session_requests)) { p ->
        if (!state.value.camera["powersourceselection"].equals("support", true)) throw LocalizedIOException(msg(R.string.power_selection_unavailable))
        p.execute(CameraSystemControls.powerSource(source))
        mutable.update { it.copy(notice = msg(R.string.back_request_ack_only)) }
    }
    fun applyShootSetup(base: ShootSetup, changes: Map<ShootField, String>, sampledLight: String? = null) = controls(msg(R.string.writing_shooting_settings)) { p ->
        if (sampledLight != null) requireLiveLightSample(sampledLight)
        log("CONTROL ShootSetup PRE_SYNC dialect=$shootingDialect")
        val fresh = syncControls(p)
        if (fresh.active != base) throw LocalizedIOException(msg(R.string.shooting_settings_changed))
        val requested = fresh.validateChanges(base, changes, sampledLight)
        changes[ShootField.FOLDER]?.let { listed(p, it) }
        log("CONTROL ShootSetup WRITE_ACK dialect=${fresh.dialect}")
        p.execute(ControlCommands.shootSetup(requested, fresh.globals, fresh.dialect))
        log("CONTROL ShootSetup READBACK_SYNC")
        val result = syncControls(p)
        val actual = result.setups.singleOrNull { it.name == requested.name }
        val checked = ShootField.legacy.filter { requested[it] != null && (it != ShootField.NL_COMPRESSION || requested[ShootField.COMPRESSION] == "Nearly Lossless") }
        val original = result.setups.singleOrNull { it.name == base.name }
        if (result.activeName != requested.name || actual == null || checked.any { actual[it] != requested[it] } ||
            (fresh.dialect == ShootSetupDialect.DP67 && requested.name != base.name && (original == null || original.values != base.values)) ||
            result.globals.orientation != fresh.globals.orientation || !result.globals.color.equals(fresh.globals.color, true))
            throw LocalizedIOException(msg(R.string.shooting_write_not_confirmed))
        mutable.update { it.copy(notice = msg(R.string.shooting_settings_confirmed)) }
    }
    fun selectShootSetup(name: String) = controls(msg(R.string.selecting_shooting_setup)) { p ->
        val fresh = syncControls(p)
        val selected = fresh.setups.singleOrNull { it.name == name } ?: throw LocalizedIOException(msg(R.string.setup_not_available))
        p.execute(ControlCommands.selectSetup(selected, fresh.globals, fresh.dialect))
        if (syncControls(p).activeName != name) throw LocalizedIOException(msg(R.string.shooting_write_not_confirmed))
        mutable.update { it.copy(notice = msg(R.string.shooting_settings_confirmed)) }
    }
    fun applyImageSettings(base: ShootSetup, changes: Map<ShootField, String>, sampledLight: String? = null) = controls(msg(R.string.writing_shooting_settings)) { p ->
        if (sampledLight != null) requireLiveLightSample(sampledLight)
        syncControls(p)
        val fresh = ImageSettingsConfiguration.from(state.value.camera)
        if (fresh?.active != base) throw LocalizedIOException(msg(R.string.shooting_settings_changed))
        p.execute(CameraSystemControls.imageSettings(fresh, base, changes, sampledLight))
        syncControls(p)
        val result = ImageSettingsConfiguration.from(state.value.camera)
        val expected = base.changed(changes)
        if (result?.activeName != base.name || result.active == null || expected.values.any { (key, value) -> result.active!!.values.entries.firstOrNull { it.key.equals(key, true) }?.value != value })
            throw LocalizedIOException(msg(R.string.shooting_write_not_confirmed))
        mutable.update { it.copy(notice = msg(R.string.shooting_settings_confirmed)) }
    }
    fun selectImageSettings(name: String) = controls(msg(R.string.selecting_shooting_setup)) { p ->
        syncControls(p)
        val fresh = ImageSettingsConfiguration.from(state.value.camera)
        if (fresh?.setups?.none { it.name == name } != false) throw LocalizedIOException(msg(R.string.setup_not_available))
        p.execute(CameraSystemControls.selectImageSettings(name))
        syncControls(p)
        if (ImageSettingsConfiguration.from(state.value.camera)?.activeName != name) throw LocalizedIOException(msg(R.string.shooting_write_not_confirmed))
        mutable.update { it.copy(notice = msg(R.string.shooting_settings_confirmed)) }
    }
    fun writeBackMark(path: String, mark: BackMark) = controls(msg(R.string.writing_back_mark)) { p ->
        requireCurrentPhoto(path)
        val before = p.execute(Commands.pictureParams(path))
        val xmp = XmpMetadata.fromPictureParams(before)
        if (xmp == null && state.value.camera["camerasettings"] == null && state.value.camera["shootsetup"] == null) throw LocalizedIOException(msg(R.string.mark_requires_original_xmp))
        p.execute(ControlCommands.marking(path, mark, xmp?.withGoodFlag(mark == BackMark.GOOD)))
        val frame = p.execute(Commands.pictureParams(path))
        val actual = frame.value("marking", p.encoding.charset)
        if (actual == null || !actual.equals(mark.wireValue, true)) throw LocalizedIOException(msg(R.string.mark_write_not_confirmed))
        mergePhotoMetadata(path, frame, p.encoding)
        persist()
        mutable.update { it.copy(notice = msg(R.string.back_mark_confirmed)) }
    }
    fun requestBackPreview(path: String, zoom: ImageZoom, point: ImagePoint?, alarm: ExposureEdge?, grayPoint: ImagePoint?) = controls(msg(R.string.reading_processed_preview)) { p ->
        val frame = p.execute(ImageOperations.preview(path, zoom, point, alarm, grayPoint))
        if (frame.value("size") != null) throw LocalizedIOException(msg(R.string.the_back_enabled_an_unverified_packetized_size_9dd88))
        val photo = withContext(Dispatchers.IO) { store.putProcessed(path, frame.attachment) }
        mutable.update { it.copy(backPreview = ProcessedBackPreview(photo.copy(deviceAddress = it.deviceAddress, connectionGeneration = it.connectionGeneration), frame.value("light", p.encoding.charset))) }
    }
    fun requestPixelProbe(path: String, point: ImagePoint) = controls(msg(R.string.reading_back_pixel_probe)) { p ->
        val result = ImageOperations.parsePixelProbe(p.execute(ImageOperations.pixelProbe(path, point)), p.encoding)
        mutable.update { it.copy(pixelProbe = result, pixelProbePath = path) }
    }
    private fun requireLiveLightSample(light: String) {
        val sample = state.value.backPreview ?: throw LocalizedIOException(msg(R.string.gray_balance_sample_missing))
        requireCurrentPhoto(sample.photo.path)
        if (sample.sampledLight != light || sample.photo.connectionGeneration != state.value.connectionGeneration)
            throw LocalizedIOException(msg(R.string.gray_balance_sample_missing))
    }
    fun applySampledLightToFuture(light: String) {
        try {
            requireLiveLightSample(light)
            val legacy = shootingConfiguration().active
            val modern = ImageSettingsConfiguration.from(state.value.camera)?.active
            when {
                legacy != null -> applyShootSetup(legacy, mapOf(ShootField.LIGHT to light), light)
                modern != null -> applyImageSettings(modern, mapOf(ShootField.LIGHT to light), light)
                else -> showError(msg(R.string.complete_setup_unavailable))
            }
        } catch (e: Exception) { showError(errorText(e, msg(R.string.gray_balance_sample_missing))) }
    }
    fun commitGrayBalance(path: String, light: String) = controls(msg(R.string.apply_photo_gray_balance)) { p ->
        requireCurrentPhoto(path)
        if (state.value.backPreview?.let { it.photo.path != path || it.sampledLight != light } != false) throw LocalizedIOException(msg(R.string.gray_balance_sample_missing))
        p.execute(ImageOperations.commitGrayBalance(path, light))
        readAndStoreMetadata(p, path)
        enqueueImage(path)
        mutable.update { it.copy(notice = msg(R.string.gray_balance_request_acknowledged)) }
    }
    fun restoreGrayBalance(path: String) = controls(msg(R.string.restore_gray_balance_preview)) { p ->
        requireCurrentPhoto(path)
        p.execute(ImageOperations.restoreGrayBalance(path))
        mutable.update { it.copy(backPreview = null, notice = msg(R.string.gray_balance_request_acknowledged)) }
        enqueueImage(path)
    }
    private suspend fun listed(p: LeafProtocol, folder: String) = DirectoryParser.parse(p.execute(Commands.directory(folder)), CameraPaths.folder(folder), p.encoding)
    private suspend fun requireUnused(p: LeafProtocol, parent: String, name: String) {
        if (listed(p, parent).any { it.name.equals(name, true) }) throw LocalizedIOException(msg(R.string.back_name_already_exists))
    }
    fun makeDirectory(parent: String, name: String) = fileAction(parent, { entries -> entries.any { it.isDirectory && it.name == name } }) { p ->
        val command = ControlCommands.makeDirectory(parent, name)
        requireUnused(p, parent, name); command
    }
    fun removeDirectory(path: String) = fileAction(parentOf(path), { entries -> entries.none { it.path.trimEnd('/') == path.trimEnd('/') } }) { p ->
        val command = ControlCommands.removeDirectory(path)
        if (listed(p, path).isNotEmpty()) throw LocalizedIOException(msg(R.string.back_folder_not_empty))
        command
    }
    fun renameDirectory(path: String, name: String) = fileAction(parentOf(path), { entries -> entries.any { it.isDirectory && it.name == name } && entries.none { it.path == path } }) { p ->
        val command = ControlCommands.renameDirectory(path, name)
        requireUnused(p, parentOf(path), name); command
    }
    fun copyOrMovePhoto(path: String, targetFolder: String, move: Boolean) = fileAction(targetFolder, { entries -> entries.any { !it.isDirectory && it.name == path.substringAfterLast('/') } }, afterVerified = { p ->
        if (move && listed(p, parentOf(path)).any { it.path == path }) throw LocalizedIOException(msg(R.string.move_source_still_present))
    }) { p ->
        requireCurrentPhoto(path)
        val command = ControlCommands.transferImage(path, targetFolder, move)
        requireUnused(p, targetFolder, path.substringAfterLast('/')); command
    }
    fun deletePhoto(path: String) = fileAction(parentOf(path), { entries -> entries.none { it.path == path } }) { requireCurrentPhoto(path); ControlCommands.deleteImage(path) }
    private fun requireCurrentPhoto(path: String) {
        val snapshot = state.value
        val photo = snapshot.photos.firstOrNull { it.path == path }
        if (photo == null || photo.isDemo || photo.cacheFile == null || photo.deviceAddress == null || photo.deviceAddress != snapshot.deviceAddress || photo.connectionGeneration != snapshot.connectionGeneration)
            throw LocalizedIOException(msg(R.string.refresh_photo_before_back_write))
    }
    private fun parentOf(path: String) = path.trimEnd('/').substringBeforeLast('/', ".") + "/"
    private fun fileAction(refreshFolder: String, verified: (List<DirectoryEntry>) -> Boolean, afterVerified: suspend (LeafProtocol) -> Unit = {}, command: suspend (LeafProtocol) -> LeafCommand) = controls(msg(R.string.updating_back_files)) { p ->
        p.execute(command(p))
        readFolder(p, refreshFolder)
        if (!verified(state.value.entries)) throw LocalizedIOException(msg(R.string.back_file_action_not_confirmed))
        afterVerified(p)
        mutable.update { it.copy(notice = msg(R.string.back_file_action_acknowledged)) }
    }
    fun refreshSelected() { state.value.selectedPath?.let { enqueueImage(it) } }
    private fun action(title: UiText, clearMessages: Boolean = true, block: suspend (LeafProtocol) -> Unit) {
        actionScope.launch {
            val current = generation
            val p = protocol
            if (p == null || !state.value.connected) { showError(msg(R.string.connect_to_the_back_and_complete_synchronizatio_fa018)); return@launch }
            operationLock.withLock {
                if (generation != current || state.value.labMode) return@withLock
                try {
                    mutable.update { it.copy(operation = title, error = if (clearMessages) null else it.error, notice = if (clearMessages) null else it.notice) }
                    log("OP BEGIN ${title.resolve()}")
                    block(p)
                    log("OP END ${title.resolve()}")
                } catch (e: Exception) {
                    if (e !is CancellationException && generation == current) showError(errorText(e, msg(R.string.operation_failed , title)))
                } finally { if (generation == current) mutable.update { it.copy(operation = UiText.Empty) } }
            }
        }
    }

    private suspend fun onEvent(p: LeafProtocol, frame: WireFrame, current: Int) {
        if (current != generation) return
        if (state.value.labMode) {
            if (frame.tag.lowercase(Locale.ROOT) in setOf("picturetaken", "imagechange", "cameraparamchange", "camerashootsetupchangenotification", "storagenotification", "powermanagement", "pictureparamschange", "filesystemchange", "readytoshoot", "asynccommand", "beepnotification", "ifguiisactive")) p.acknowledge()
            val text = "${Instant.now()} ${LabRedaction.redact(frame.header.toString(p.encoding.charset))} [${frame.attachment.size} bytes]"
            mutable.update { it.copy(labEvents = (it.labEvents + text).takeLast(40)) }
            return
        }
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
            "cameraparamchange", "camerashootsetupchangenotification" -> {
                p.acknowledge()
                val keys = (ShootField.entries.mapNotNull { it.choiceKey } + listOf("shootsetup", "defaultshootsetup", "imagesettings", "activeimagesettings", "camerasettings", "defaultcolor", "defaultorientation")).toSet()
                val values = frame.values(p.encoding.charset).mapKeys { it.key.lowercase(Locale.ROOT) }
                mutable.update { it.copy(camera = it.camera + values.filterKeys { key -> key !in keys }) }
                if (values.keys.any { it in keys } || frame.tag.equals("CameraShootSetupChangeNotification", true)) refreshAfterConfigurationNotification()
            }
            "storagenotification", "powermanagement" -> {
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
        if (!state.value.active || state.value.labMode) return
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
            val updated = photo.copy(starred = old?.starred ?: false, localNote = old?.localNote ?: "", metadata = if (old != null && old.deviceAddress == s.deviceAddress) old.metadata else emptyMap(), deviceAddress = s.deviceAddress, connectionGeneration = current)
            s.copy(photos = s.photos.filterNot { it.path == path } + updated,
                selectedPath = if (settings.value.autoFollow || s.selectedPath == null) path else s.selectedPath,
                operation = UiText.Empty, received = 0, expected = 0, previewCount = s.previewCount + 1)
        }
        persist()
    }
    fun select(path: String) {
        mutable.update { it.copy(selectedPath = path) }
        preferences.edit().putString("selected", path).apply()
        if (state.value.connected && (state.value.selected?.cacheFile == null || state.value.selected?.deviceAddress != state.value.deviceAddress || state.value.selected?.connectionGeneration != state.value.connectionGeneration)) enqueueImage(path)
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
        readAndStoreMetadata(p, path)
    }
    private suspend fun readAndStoreMetadata(p: LeafProtocol, path: String) {
        val frame = p.execute(Commands.pictureParams(path))
        mergePhotoMetadata(path, frame, p.encoding)
        val histograms = ImageOperations.parseHistograms(frame)
        val xmp = XmpMetadata.fromPictureParams(frame)
        if (xmp != null) {
            mutable.update { s -> s.copy(photos = s.photos.map { photo -> if (photo.path == path)
                photo.copy(metadata = photo.metadata + ("decoded_xmp_flags" to xmp.flags.orEmpty())) else photo }) }
        }
        mutable.update { it.copy(backHistograms = histograms, histogramPath = path) }
        persist()
    }
    private fun mergePhotoMetadata(path: String, frame: WireFrame, encoding: WireEncoding) {
        val metadata = frame.values(encoding.charset).filterKeys { it.lowercase(Locale.ROOT) !in setOf("attachment", "file_name") }
        mutable.update { s -> s.copy(photos = s.photos.map { photo -> if (photo.path == path)
            photo.copy(metadata = photo.metadata.filterKeys { old -> metadata.keys.none { it.equals(old, true) } } + metadata) else photo }) }
    }
    fun writeCameraNotes(path: String, copyright: String, note: String) = action(msg(R.string.writing_notes_to_back_18e71)) { p ->
        requireCurrentPhoto(path)
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
                appendLine("Shooting compatibility: ${settings.value.shootingCompatibility}; effective dialect: $shootingDialect; cache: ${settings.value.cacheMiB} MiB")
                appendLine("Aptus / Valeo hardware/firmware: not verified by this build; manual mechanical shutter")
                appendLine("Counts: ${snapshot.previewCount} received previews; folder ${snapshot.folder}")
                appendLine("Camera: ${snapshot.camera.mapValues { (key, value) -> if (key.lowercase(Locale.ROOT) in setOf("pin", "pincode", "newpincode", "annotation", "copyright", "xmp_tag")) "[redacted]" else value }}")
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
        val line = LabRedaction.redact(raw).take(8192)
        val stamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT).format(Date())
        mutableLogs.update { (it + "$stamp $line").takeLast(300) }
    }
    fun enterLaboratory() {
        if (!state.value.labMode) labTouchedConnection = false
        mutable.update { it.copy(labMode = true) }
    }
    fun leaveLaboratory() {
        if (labTouchedConnection || state.value.labBusy) disconnect()
        mutable.update { it.copy(labMode = false) }
    }
    fun cancelLabExperiment() { disconnect() }

    fun runLabExperiment(header: String, expectedTag: String, expectedFile: String?, requireAttachment: Boolean, timeoutMs: Long, templateId: String? = null) {
        val snapshot = state.value
        val p = protocol
        if (p == null || !snapshot.connected) { showError(msg(R.string.connect_for_back_controls)); return }
        if (snapshot.labBusy || snapshot.controlsBusy || snapshot.operation.isNotEmpty() || snapshot.expected > 0) { showError(msg(R.string.lab_wait_for_idle)); return }
        val command = try { LabExperiments.command(header, p.encoding, expectedTag, expectedFile, requireAttachment, timeoutMs) }
        catch (e: Exception) { showError(errorText(e, msg(R.string.lab_invalid_request))); return }
        enterLaboratory()
        val id = UUID.randomUUID().toString()
        val redacted = LabRedaction.redact(header)
        val initial = LabRecord(id = id, time = Instant.now().toString(), device = snapshot.deviceName + " " + snapshot.deviceAddress?.takeLast(5), encoding = p.encoding.name,
            request = redacted, templateId = templateId, requestHex = if (redacted == header) hex(command.bytes(p.encoding)) else null,
            expectedTag = expectedTag, expectedFile = expectedFile, timeoutMs = timeoutMs)
        mutable.update { it.copy(labBusy = true, labCurrentId = id, labRecords = (listOf(initial) + it.labRecords).take(30), error = null, notice = null) }
        actionScope.launch {
            var result = initial
            try {
                labTouchedConnection = true
                if (LabExperiments.isSendOnly(command)) {
                    p.sendOnly(command)
                    result = initial.copy(outcome = LabOutcome.SENT_ONLY)
                } else {
                    val frame = p.execute(command, validateReply = false)
                    val rawResponse = frame.header.toString(p.encoding.charset)
                    val response = LabRedaction.redact(rawResponse)
                    val attachment = frame.attachment
                    val file = if (attachment.isNotEmpty()) withContext(Dispatchers.IO) {
                        File(labDirectory, "$id.bin").also { it.writeBytes(attachment) }
                    } else null
                    result = initial.copy(outcome = LabOutcome.RECEIVED, response = response,
                        responseHex = if (response == rawResponse) hex(frame.header) else null, responseTag = frame.tag, responseErrorCode = frame.value("error_code"),
                        attachmentFile = file?.absolutePath, attachmentBytes = attachment.size,
                        attachmentSha256 = if (attachment.isNotEmpty()) withContext(Dispatchers.Default) { PhotoStore.hash(attachment) } else null,
                        attachmentHex = if (attachment.isNotEmpty()) hex(attachment.take(256).toByteArray()) else null)
                }
            } catch (e: CancellationException) {
                val failure = state.value.takeIf { it.phase == SessionPhase.FAILED }?.error
                result = initial.copy(outcome = if (failure == null) LabOutcome.CANCELLED else LabOutcome.ERROR, error = failure?.resolve())
            }
            catch (e: Exception) { result = initial.copy(outcome = LabOutcome.ERROR, error = LabRedaction.redact(e.message ?: e.javaClass.simpleName)) }
            finally {
                val finished = result
                mutable.update { s -> s.copy(labRecords = s.labRecords.map { if (it.id == id) finished else it },
                    labBusy = if (s.labCurrentId == id) false else s.labBusy,
                    labCurrentId = if (s.labCurrentId == id) null else s.labCurrentId,
                    received = if (s.labCurrentId == id) 0 else s.received,
                    expected = if (s.labCurrentId == id) 0 else s.expected) }
                scope.launch(Dispatchers.IO) {
                    var total = labDirectory.listFiles()?.sumOf { it.length() } ?: 0
                    labDirectory.listFiles()?.sortedBy { it.lastModified() }?.forEach { file ->
                        if (total > 256L * 1024 * 1024 && file.name != "$id.bin") { total -= file.length(); file.delete() }
                    }
                }
            }
        }
    }
    fun exportLaboratory(onReady: (File) -> Unit) = scope.launch {
        try {
            val snapshot = state.value
            val file = withContext(Dispatchers.IO) {
                File(context.cacheDir, "exports/Leaf2Android-laboratory.zip").also { LabExport.write(it, snapshot.labRecords, snapshot.labEvents, logs.value) }
            }
            onReady(file)
        } catch (e: Exception) { showError(errorText(e, msg(R.string.lab_export_failed))) }
    }
    private fun hex(bytes: ByteArray): String {
        val digits = "0123456789abcdef"
        val chars = CharArray(bytes.size * 2)
        bytes.forEachIndexed { i, byte -> val n = byte.toInt() and 255; chars[i * 2] = digits[n ushr 4]; chars[i * 2 + 1] = digits[n and 15] }
        return String(chars)
    }
}
