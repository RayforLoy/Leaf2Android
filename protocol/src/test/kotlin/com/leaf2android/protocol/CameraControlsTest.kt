package com.leaf2android.protocol

import org.junit.Test
import kotlin.test.*

class CameraControlsTest {
    private fun setup(iso: String = "100") = "SetupName=Default,ShootingFolder=./Shots/,ISO=$iso,ImageQuality=RAW,Framing=Full,Toning=Normal,Light=Daylight,SharpTable=Normal,InputProfile=Back,OutputProfile=RGB,CameraType=Technical,ICCFlag=1,Prefix=IMG,FileCompression=None,Orientation=Landscape,Color=color"
    private fun configuration() = ShootingConfiguration.from(mapOf(
        "shootsetup" to setup(), "defaultshootsetup" to "Default", "iso" to "50;100;200;",
        "light" to "Daylight;Tungsten", "framing" to "Full,0,0,100,100;Square,10,0,80,100;", "defaultorientation" to "Landscape", "defaultcolor" to "color"))

    @Test fun isoEditPreservesEveryOtherSettingAndUsesNestedShootSetup() {
        val c = configuration()
        val before = c.active!!
        val updated = c.validateChanges(before, mapOf(ShootField.ISO to "200"))
        assertEquals(before.values.filterKeys { it != "ISO" }, updated.values.filterKeys { it != "ISO" })
        assertEquals("^<SetCameraParam ShootSetup=\"${setup("200")}\" Orientation=\"Landscape\" Color=\"Color\" />", ControlCommands.shootSetup(updated, c.globals).text)
        assertEquals(listOf("Full", "Square"), c.choices[ShootField.FRAMING])
    }
    @Test fun globalDirectionAndColorArePreservedExceptForTheVendorsDotOneBranch() {
        assertEquals("", SetupGlobals(null, null).attributesFor("Default.1"))
        assertFailsWith<ProtocolException> { SetupGlobals(null, null).attributesFor("Default") }
        assertEquals("^<SetCameraParam DefaultShootSetup=\"Default\" Orientation=\"Auto\" Color=\"BW\" />", ControlCommands.selectSetup("Default", SetupGlobals("Auto", "BW")).text)
    }
    @Test fun unsupportedIsoAndUnadvertisedWhiteBalanceCannotBeWritten() {
        val c = configuration()
        assertFailsWith<ProtocolException> { c.validateChanges(c.active!!, mapOf(ShootField.ISO to "6400")) }
        assertFailsWith<ProtocolException> { c.validateChanges(c.active!!, mapOf(ShootField.LIGHT to "Unknown")) }
        assertEquals("Tungsten", c.validateChanges(c.active!!, mapOf(ShootField.LIGHT to "Tungsten"))[ShootField.LIGHT])
    }
    @Test fun incompleteAndAmbiguousSetupsCannotBecomeWritable() {
        assertNull(ShootingConfiguration.from(mapOf("shootsetup" to setup(), "defaultshootsetup" to "Missing")).active)
        assertFailsWith<ProtocolException> { ShootSetup.parse("SetupName=X,ISO=100").serialize() }
        assertFailsWith<ProtocolException> { ShootSetup.parse("SetupName=X,ISO=100,iso=200") }
        assertFailsWith<ProtocolException> { ShootingConfiguration.from(mapOf("shootsetup" to setup()+";"+setup())) }
        assertFailsWith<ProtocolException> { ShootingConfiguration.from(mapOf("framing" to "bad,1,2")) }
    }
    @Test fun nestedSetupDelimiterInjectionIsRejectedBeforeWrite() {
        val c = configuration()
        listOf("x,ISO=800", "x;other", "bad\"value", "x=y", "x\n").forEach { value ->
            assertFailsWith<ProtocolException> { c.validateChanges(c.active!!, mapOf(ShootField.PREFIX to value)) }
        }
    }
    @Test fun onlyAnExplicitBackProvidedLightMayBypassThePresetList() {
        val c = configuration()
        assertFailsWith<ProtocolException> { c.validateChanges(c.active!!, mapOf(ShootField.LIGHT to "SampledBalance")) }
        assertEquals("SampledBalance", c.validateChanges(c.active!!, mapOf(ShootField.LIGHT to "SampledBalance"), "SampledBalance")[ShootField.LIGHT])
        assertFailsWith<ProtocolException> { c.validateChanges(c.active!!, mapOf(ShootField.LIGHT to "bad,ISO=800"), "bad,ISO=800") }
    }
    @Test fun nearlyLosslessRequiresAndPreservesItsOwnParameter() {
        val raw = setup().replace("FileCompression=None", "FileCompression=Nearly Lossless,NLCompression=Normal")
        assertEquals(raw, ShootSetup.parse(raw).serialize())
        assertFailsWith<ProtocolException> { ShootSetup.parse(raw.replace(",NLCompression=Normal", "")).serialize() }
    }
    @Test fun fileOperationsUseCompleteCameraPathsAndRefuseTraversalOrRootRemoval() {
        assertEquals("^<FileCommand FileCommand=\"mkdir\" Subject=\"./Shots/New/\" />", ControlCommands.makeDirectory("./Shots/", "New").text)
        assertEquals("^<FileCommand FileCommand=\"copy\" Subject=\"./Shots/a.mos\" Target=\"./Other/a.mos\" />", ControlCommands.transferImage("./Shots/a.mos", "./Other/", false).text)
        assertEquals("^<FileCommand FileCommand=\"rename\" Subject=\"./Shots/Old/\" Target=\"./Shots/New/\" />", ControlCommands.renameDirectory("./Shots/Old/", "New").text)
        listOf("../outside/", "./Shots/../", "C:\\host", "/root").forEach { value -> assertFailsWith<ProtocolException> { CameraPaths.path(value) } }
        assertFailsWith<ProtocolException> { ControlCommands.removeDirectory("./") }
        assertFailsWith<ProtocolException> { ControlCommands.renameDirectory("./", "Other") }
        assertFailsWith<ProtocolException> { ControlCommands.makeDirectory("./", "../other") }
    }
}
