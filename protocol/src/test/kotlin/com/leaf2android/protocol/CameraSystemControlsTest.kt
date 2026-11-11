package com.leaf2android.protocol

import org.junit.Test
import java.time.LocalDateTime
import kotlin.test.*

class CameraSystemControlsTest {
    private fun direct() = DirectCameraSettings.from(mapOf(
        "CameraSettings" to "ShootingFolder=./Shots/,ISO=100,Orientation=Landscape,CameraType=Technical,Prefix=IMG,SensorCrop=Full,FileCompression=None",
        "ISO" to "25;100;200", "Orientation" to "Landscape;Landscape 180", "CameraType" to "Technical",
        "SensorCrop" to "Full;Square", "FileCompression" to "None;Lossless;Nearly Lossless"))!!
    private fun imageSetup() = "SetupName=Portrait,ImageQuality=RAW,Color=Color,Framing=Full,Toning=Normal,Light=Daylight,SharpTable=Normal,InputProfile=Back,OutputProfile=RGB,ICCFlag=1,Grain=0,MoireReduction=0,Saturation=Normal,FutureField=keep"
    private fun image() = ImageSettingsConfiguration.from(mapOf(
        "ImageSettings" to imageSetup(), "ActiveImageSettings" to "Portrait", "Light" to "Daylight;Tungsten", "Grain" to "0;1",
        "Framing" to "Full,0,0,100,100;Square,10,0,80,100", "Color" to "Color;BW"))!!

    @Test fun cameraSettingsIsoIsSeparateFromItsChoicesAndUsesTheFirmwareDialect() {
        val c = direct()
        assertEquals("100", c.iso)
        assertEquals(listOf("25", "100", "200"), c.isoChoices)
        assertEquals("^<SetCameraParam ISO=\"200\" />", CameraSystemControls.directIso(c, "200").text)
        assertEquals("^<SetCameraParam Orientation=\"Landscape 180\" />", CameraSystemControls.directSetting(c, ShootField.ORIENTATION, "Landscape 180").text)
    }
    @Test fun unadvertisedValuesUnprovenCompressionAndLongerPrefixesStayBlocked() {
        val c = direct()
        assertFailsWith<ProtocolException> { CameraSystemControls.directIso(c, "6400") }
        assertFailsWith<ProtocolException> { CameraSystemControls.directSetting(c, ShootField.COMPRESSION, "Nearly Lossless") }
        assertFailsWith<ProtocolException> { CameraSystemControls.directSetting(c, ShootField.PREFIX, "LONG") }
        assertFailsWith<ProtocolException> { CameraSystemControls.directSetting(c, ShootField.PREFIX, "../") }
        assertEquals("^<SetCameraParam Prefix=\"NEW\" />", CameraSystemControls.directSetting(c, ShootField.PREFIX, "NEW").text)
    }
    @Test fun completeImageSettingsKeepUnknownFieldsAndChangeOnlyAdvertisedProcessingValues() {
        val c = image()
        val csv = c.updatedCsv(c.active!!, mapOf(ShootField.LIGHT to "Tungsten", ShootField.GRAIN to "1"))
        val expected = ShootSetup.parse(imageSetup()).values.toMutableMap().apply { put("Light", "Tungsten"); put("Grain", "1") }
        assertEquals(expected, ShootSetup.parse(csv).values)
        assertTrue(csv.startsWith("SetupName=Portrait,"))
        assertTrue(csv.contains("FutureField=keep"))
        assertEquals(listOf("Full", "Square"), c.choices[ShootField.FRAMING])
        assertEquals("^<SetCameraParam ActiveImageSettings=\"Portrait\" />", CameraSystemControls.selectImageSettings("Portrait").text)
    }
    @Test fun incompleteOrUnadvertisedImageSettingsCannotBeWritten() {
        val c = image()
        assertFailsWith<ProtocolException> { c.updatedCsv(c.active!!, mapOf(ShootField.ISO to "200")) }
        assertFailsWith<ProtocolException> { c.updatedCsv(c.active!!, mapOf(ShootField.LIGHT to "Unknown")) }
        assertFailsWith<ProtocolException> { c.updatedCsv(ShootSetup.parse("SetupName=Portrait,Light=Daylight"), emptyMap()) }
    }
    @Test fun cameraClockAndDefaultNotesUseTheEvidencedSyntax() {
        assertEquals("^<SetCameraParam CurrentTime=\"2026:11:11:09:08:07\" />", CameraSystemControls.clock(LocalDateTime.of(2026,11,11,9,8,7)).text)
        assertEquals("^<SetCameraParam Annotation=\"note\" Copyright=\"owner\" />", CameraSystemControls.notes("note", "owner").text)
        assertEquals("^<GetCameraParam GetParamList=\"Copyright;Annotation\" />", CameraSystemControls.defaultNotes.text)
        assertFailsWith<ProtocolException> { CameraSystemControls.notes("bad\"data", "owner") }
    }
    @Test fun notificationRequestsDoNotRepresentShutterReleaseAndPowerValuesAreClosed() {
        assertEquals("^<SetCameraParam EnableTakenPicture=\"no\" />", CameraSystemControls.captureNotifications(false).text)
        assertEquals("^<SetCameraParam PowerSource=\"CameraBattery\" />", CameraSystemControls.powerSource(BackPowerSource.CAMERA_BATTERY).text)
    }
}
