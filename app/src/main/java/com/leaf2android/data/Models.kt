package com.leaf2android.data

import com.leaf2android.R
import com.leaf2android.localization.*

import com.leaf2android.protocol.DirectoryEntry
import com.leaf2android.protocol.WireEncoding
import com.leaf2android.protocol.ShootingConfiguration
import com.leaf2android.protocol.ShootField
import com.leaf2android.protocol.DirectCameraSettings
import com.leaf2android.protocol.BackPixelProbe
import com.leaf2android.protocol.BackHistograms
import com.leaf2android.protocol.ShootSetupDialect

enum class UiStyle { MODERN, PDA }
enum class ShootingCompatibility {
    AUTO, WIVIEW, DP67;
    fun dialect(deviceName: String, camera: Map<String, String>): ShootSetupDialect = when {
        this == DP67 -> ShootSetupDialect.DP67
        this == AUTO && deviceName.contains("valeo", true) && camera.keys.any { it.equals("shootsetup", true) } -> ShootSetupDialect.DP67
        else -> ShootSetupDialect.WIVIEW
    }
}
const val BACK_FAMILY = "Aptus / Valeo"

enum class SessionPhase(private val labelResource: Int) {
    DISCONNECTED(R.string.phase_disconnected), CONNECTING(R.string.phase_connecting), SYNCING(R.string.phase_syncing), READY(R.string.phase_ready), FAILED(R.string.phase_failed);
    val label: String get() = tr(labelResource)
}

enum class Support(private val labelResource: Int) {
    SUPPORTED(R.string.supported_a9aab), UNVERIFIED(R.string.unverified_0b48e), UNSUPPORTED(R.string.unsupported_ef827);
    val label: String get() = tr(labelResource)
}
data class CameraCapability(val title: String, val support: Support, val detail: String)
object CameraCapabilities {
    val items get() = listOf(
        CameraCapability(tr(R.string.direct_bluetooth_and_post_capture_preview_a91ba), Support.SUPPORTED, tr(R.string.hardware_reported_connections)),
        CameraCapability(tr(R.string.remote_shooting_settings), Support.UNVERIFIED, tr(R.string.remote_shooting_support_detail)),
        CameraCapability(tr(R.string.folders_photo_information_and_notes_15ecb), Support.UNVERIFIED, tr(R.string.the_corresponding_original_commands_are_impleme_e0f90)),
        CameraCapability(tr(R.string.zoom_histograms_and_local_flags_18862), Support.SUPPORTED, tr(R.string.uses_actual_preview_pixels_histograms_come_from_26059)),
        CameraCapability(tr(R.string.back_flags_and_display_parameter_writes_ca2e9), Support.UNVERIFIED, tr(R.string.local_flags_and_exposure_warnings_are_available_0e7f6)),
        CameraCapability(tr(R.string.mos_original_transfer_3c09f), Support.UNVERIFIED, tr(R.string.getimage_returns_a_preview_a_complete_mos_file_7aa1c)),
        CameraCapability(tr(R.string.continuous_live_view_fcece), Support.UNVERIFIED, tr(R.string.no_verified_continuous_sensor_stream_interface_96e46)),
        CameraCapability(tr(R.string.remote_mechanical_shutter_release_bcd7f), Support.UNSUPPORTED, tr(R.string.the_technical_camera_mechanical_shutter_must_be_788b5)),
    )
}

data class LeafSettings(
    val style: UiStyle = UiStyle.MODERN,
    val encoding: WireEncoding = WireEncoding.WESTERN,
    val insecure: Boolean = false,
    val autoFollow: Boolean = true,
    val keepAwake: Boolean = true,
    val cacheMiB: Int = 1024,
    val showHistogram: Boolean = false,
    val showHighlights: Boolean = false,
    val showShadows: Boolean = false,
    val attachmentLimitMiB: Int = 16,
    val shootingCompatibility: ShootingCompatibility = ShootingCompatibility.AUTO,
)

enum class PhotoSource(private val labelResource: Int) {
    CAMERA(R.string.back_jpeg_preview_e11ea), OFFLINE_DEMO(R.string.offline_demo_generated_jpeg_4a985);
    val label: String get() = tr(labelResource)
    companion object {
        fun fromStored(value: String): PhotoSource = if (value == OFFLINE_DEMO.name || value.startsWith("离线") || value.contains("offline", true)) OFFLINE_DEMO else CAMERA
    }
}

data class LeafPhoto(
    val path: String,
    val cacheFile: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val bytes: Long = 0,
    val sha256: String = "",
    val receivedAt: Long = 0,
    val starred: Boolean = false,
    val localNote: String = "",
    val metadata: Map<String, String> = emptyMap(),
    val source: PhotoSource = PhotoSource.CAMERA,
    val deviceAddress: String? = null,
    val connectionGeneration: Int? = null,
) {
    val name: String get() = path.substringAfterLast('/')
    val isDemo: Boolean get() = source == PhotoSource.OFFLINE_DEMO
    val backMark: String? get() = metadata.entries.firstOrNull { it.key.equals("marking", true) }?.value
}

data class Viewport(val zoom: Float = 1f, val x: Float = 0f, val y: Float = 0f)
data class ProcessedBackPreview(val photo: LeafPhoto, val sampledLight: String? = null)
data class SessionState(
    val phase: SessionPhase = SessionPhase.DISCONNECTED,
    val deviceName: String = BACK_FAMILY,
    val deviceAddress: String? = null,
    val connectionGeneration: Int? = null,
    val operation: UiText = UiText.Empty,
    val received: Int = 0,
    val expected: Int = 0,
    val folder: String = "./",
    val entries: List<DirectoryEntry> = emptyList(),
    val photos: List<LeafPhoto> = emptyList(),
    val selectedPath: String? = null,
    val camera: Map<String, String> = emptyMap(),
    val shootingDialect: ShootSetupDialect = ShootSetupDialect.WIVIEW,
    val error: UiText? = null,
    val notice: UiText? = null,
    val previewCount: Int = 0,
    val controlsBusy: Boolean = false,
    val backPreview: ProcessedBackPreview? = null,
    val pixelProbe: BackPixelProbe? = null,
    val backHistograms: BackHistograms? = null,
    val pixelProbePath: String? = null,
    val histogramPath: String? = null,
    val labMode: Boolean = false,
    val labBusy: Boolean = false,
    val labCurrentId: String? = null,
    val labRecords: List<LabRecord> = emptyList(),
    val labEvents: List<String> = emptyList(),
) {
    val connected: Boolean get() = phase == SessionPhase.READY
    val active: Boolean get() = phase in setOf(SessionPhase.CONNECTING, SessionPhase.SYNCING, SessionPhase.READY)
    val selected: LeafPhoto? get() = photos.firstOrNull { it.path == selectedPath }
    val currentIso: String? get() = runCatching {
        if (shootingDialect == ShootSetupDialect.DP67) ShootingConfiguration.from(camera, shootingDialect).active?.get(ShootField.ISO)
        else DirectCameraSettings.from(camera)?.iso ?: ShootingConfiguration.from(camera).active?.get(ShootField.ISO)
    }.getOrNull()
}
