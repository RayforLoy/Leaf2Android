package com.leaf2android.protocol

import org.junit.Test
import kotlin.test.*

/** Format boundaries reconstructed from the supplied DP-67 client, not camera captures. */
class Dp67ControlsTest {
    private fun setup(name: String = "Default", prefix: Boolean = true) =
        "SetupName=$name,ShootingFolder=./Shots/,ISO=100,ImageQuality=RAW,Framing=Full,Toning=Normal,Light=Daylight,SharpTable=Normal,InputProfile=Back,OutputProfile=RGB,CameraType=Technical,ICCFlag=1" +
            if (prefix) ",Prefix=IMG" else ""

    private fun configuration(raw: String = setup()) = ShootingConfiguration.from(mapOf(
        "shootsetup" to raw, "defaultshootsetup" to ShootSetup.parse(raw).name,
        "iso" to "50;100;200", "light" to "Daylight;Tungsten"), ShootSetupDialect.DP67)

    @Test fun editingAPresetCreatesItsModifiedSlotAndSendsOnlyDp67Fields() {
        val raw = setup() + ",FileCompression=None,Orientation=Landscape,Color=color"
        val c = configuration(raw)
        val changed = c.validateChanges(c.active!!, mapOf(ShootField.ISO to "200"))
        assertEquals("Default.1", changed.name)
        val command = ControlCommands.shootSetup(changed, c.globals, c.dialect)
        assertEquals("^<SetCameraParam ShootSetup=\"${setup("Default.1").replace("ISO=100", "ISO=200")}\" />", command.text)
        val header = HeaderParser.parse(command.bytes(WireEncoding.WESTERN))
        assertEquals(setOf("ShootSetup"), header.attributes.keys)
        val sent = ShootSetup.parse(header.value("ShootSetup")!!)
        assertEquals(13, sent.values.size)
        assertNull(sent[ShootField.COMPRESSION]); assertNull(sent[ShootField.ORIENTATION]); assertNull(sent[ShootField.COLOR])
        assertEquals("None", changed[ShootField.COMPRESSION]) // Retained for readback checks, never sent in this dialect.
        assertEquals("100", c.active!![ShootField.ISO])
    }

    @Test fun aTwelveFieldSetupNeedsNoInventedPrefixColorOrCompression() {
        val c = configuration(setup(prefix = false))
        assertTrue(c.active!!.missingFields(c.dialect).isEmpty())
        assertTrue(c.active!!.missingFields.isNotEmpty())
        val changed = c.validateChanges(c.active!!, mapOf(ShootField.LIGHT to "Tungsten"))
        val header = HeaderParser.parse(ControlCommands.shootSetup(changed, c.globals, c.dialect).bytes(WireEncoding.WESTERN))
        assertEquals(12, ShootSetup.parse(header.value("ShootSetup")!!).values.size)
        assertFalse(header.value("ShootSetup")!!.contains("Prefix="))
        assertFailsWith<ProtocolException> { c.validateChanges(c.active!!, mapOf(ShootField.PREFIX to "NEW")) }
    }

    @Test fun existingModifiedNamesFollowTheOriginalContainsDotOneRule() {
        for (name in listOf("Default.1", "Default.1.extra")) {
            val c = configuration(setup(name))
            assertEquals(name, c.validateChanges(c.active!!, mapOf(ShootField.ISO to "200")).name)
        }
    }

    @Test fun selectingAPresetAndSelectingAModifiedSetupUseDifferentCommands() {
        val c = configuration()
        assertEquals("^<SetCameraParam DefaultShootSetup=\"Default\" />", ControlCommands.selectSetup(c.active!!, c.globals, c.dialect).text)
        val modified = ShootSetup.parse(setup("Default.1"))
        assertEquals("^<SetCameraParam ShootSetup=\"${setup("Default.1")}\" />", ControlCommands.selectSetup(modified, c.globals, c.dialect).text)
        assertFailsWith<ProtocolException> { ControlCommands.shootSetup(c.active!!, c.globals, c.dialect) }
    }

    @Test fun unsupportedFieldsAndIncompleteOldSetupsFailBeforeWriting() {
        val c = configuration()
        for (field in listOf(ShootField.COMPRESSION, ShootField.ORIENTATION, ShootField.COLOR)) {
            assertFailsWith<ProtocolException> { c.validateChanges(c.active!!, mapOf(field to "unknown")) }
        }
        assertFailsWith<ProtocolException> { c.validateChanges(c.active!!, mapOf(ShootField.ISO to "6400")) }
        val incomplete = configuration(setup().replace(",Light=Daylight", ""))
        assertFailsWith<ProtocolException> { incomplete.validateChanges(incomplete.active!!, mapOf(ShootField.ISO to "200")) }
    }

    @Test fun modifiedNamesAndWhiteBalanceCannotInjectAnotherField() {
        val c = configuration()
        for (value in listOf("x,ISO=6400", "x;Other", "x=y")) {
            assertFailsWith<ProtocolException> { c.validateChanges(c.active!!, mapOf(ShootField.LIGHT to value), value) }
        }
        val changed = c.validateChanges(c.active!!, mapOf(ShootField.LIGHT to "SampledBalance"), "SampledBalance")
        assertEquals("SampledBalance", changed[ShootField.LIGHT])
        assertEquals("Default.1", changed.name)
    }
}
