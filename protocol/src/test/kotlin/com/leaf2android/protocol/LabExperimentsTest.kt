package com.leaf2android.protocol

import org.junit.Test
import java.util.Base64
import kotlin.test.*

class LabExperimentsTest {
    private fun build(id: String, inputs: Map<String, String> = emptyMap(), encoding: WireEncoding = WireEncoding.WESTERN) =
        LabExperiments.build(id, inputs, encoding)
    private fun frame(text: String) = HeaderParser.parse(text.toByteArray(Charsets.US_ASCII))
    private fun packet(): String = Base64.getEncoder().encodeToString(
        """<rdf:RDF xmlns:rdf="${XmpMetadata.RDF_NAMESPACE}"><rdf:Description xmlns:leaf="${XmpMetadata.LEAF_NAMESPACE}" leaf:Flags="+-+keep" /></rdf:RDF>""".toByteArray())

    @Test fun catalogueContainsOnlyExplicitExperimentsWithEvidenceAndEffects() {
        assertEquals(16, LabExperiments.templates.size)
        assertEquals(16, LabExperiments.templates.map { it.id }.distinct().size)
        LabExperiments.templates.forEach {
            assertTrue(it.titleEn.isNotBlank() && it.titleZh.isNotBlank())
            assertTrue(it.provenance.isNotBlank() && it.notesEn.isNotBlank() && it.notesZh.isNotBlank())
            assertTrue(it.impacts.isNotEmpty())
            if (!it.id.startsWith("ce-version-")) assertTrue(it.knownValues.isEmpty(), "Unknown values must not acquire defaults: ${it.id}")
        }
        assertTrue(LabExperiments.template("quick-format").erasesStorage)
        assertFalse(LabExperiments.template("storage-recovery").erasesStorage)
        assertTrue(LabExperiments.template("new-pin-code").changesPairing)
        assertTrue(LabExperiments.template("new-pin-code").requiresInputs.single().secret)
        assertFalse(LabExperiments.template("get-camera-param").writesBack)
    }

    @Test fun brightnessAcceptsExplicitInt32WithoutInventingAHardwareRange() {
        assertEquals("^<SetCameraParam Brightness=\"-17\" />", build("brightness", mapOf("brightness" to "-17")).text)
        assertEquals("^<SetCameraParam Brightness=\"2147483647\" />", build("brightness", mapOf("brightness" to Int.MAX_VALUE.toString())).text)
        assertFailsWith<ProtocolException> { build("brightness") }
        assertFailsWith<ProtocolException> { build("brightness", mapOf("brightness" to "2147483648")) }
        assertFailsWith<ProtocolException> { build("brightness", mapOf("brightness" to "0.5")) }
        assertFailsWith<ProtocolException> { build("brightness", mapOf("brightness" to "1", "unintended" to "2")) }
    }

    @Test fun timeUsesTheOriginalColonSeparatedFormatAndDoesNotDefaultToNow() {
        assertEquals("^<SetCameraParam CurrentTime=\"2024:02:29:13:14:15\" />", build("current-time", mapOf("currentTime" to "2024:02:29:13:14:15")).text)
        assertFailsWith<ProtocolException> { build("current-time") }
        assertFailsWith<ProtocolException> { build("current-time", mapOf("currentTime" to "2023:02:29:13:14:15")) }
        assertFailsWith<ProtocolException> { build("current-time", mapOf("currentTime" to "2024-02-29T13:14:15")) }
    }

    @Test fun counterAndBothClientVersionConstantsAreExactAndDistinct() {
        assertEquals("^<SetCameraParam ResetCounter=\"yes\" />", build("reset-counter").text)
        assertEquals("17171968", 0x01060600.toString())
        assertEquals("17301504", 0x01080000.toString())
        assertEquals("^<SetCameraParam CEVersion=\"17171968\" />", build("ce-version-1-0-2").text)
        assertEquals("^<SetCameraParam CEVersion=\"17301504\" />", build("ce-version-1-0-3").text)
        assertEquals(mapOf("CEVersion" to "17301504"), LabExperiments.template("ce-version-1-0-3").knownValues)
        assertTrue(LabExperiments.template("reset-counter").notesEn.contains("not a proved reset of lifetime TotalFrames"))
    }

    @Test fun pinEncodingAndLoggingAreSeparateAndNoDefaultPinExists() {
        val command = build("new-pin-code", mapOf("newPinCode" to "8172"))
        assertEquals("^<SetCameraParam NewPinCode=\"8172\" PinEncryption=\"None\" />", command.text)
        assertTrue(command.bytes(WireEncoding.WESTERN).toString(Charsets.US_ASCII).contains("8172"), "Sent bytes must not be redacted")
        assertFalse(LabRedaction.redact(command.text).contains("8172"))
        assertFailsWith<ProtocolException> { build("new-pin-code") }
        assertFailsWith<ProtocolException> { build("new-pin-code", mapOf("newPinCode" to "1".repeat(17))) }
        assertFailsWith<ProtocolException> { build("new-pin-code", mapOf("newPinCode" to "中文")) }
        assertTrue(build("new-pin-code", mapOf("newPinCode" to "中文中文中文中文"), WireEncoding.GBK).bytes(WireEncoding.GBK).isNotEmpty())
    }

    @Test fun factoryRequestUsesExplicitInputsAndOriginalAttributeOrder() {
        assertEquals("^<SetCameraParam Orientation=\"Landscape\" Color=\"BW\" FactorySettings=\"TRUE\" />",
            build("factory-settings", mapOf("orientation" to "Landscape", "color" to "BW")).text)
        assertEquals("^<SetCameraParam DefaultShootSetup=\"A.1\" Orientation=\"Portrait CW\" Color=\"Color\" FactorySettings=\"TRUE\" />",
            build("factory-settings", mapOf("orientation" to "Portrait CW", "color" to "Color", "defaultShootSetup" to "A.1")).text)
        assertFailsWith<ProtocolException> { build("factory-settings", mapOf("color" to "BW")) }
        assertFailsWith<ProtocolException> { build("factory-settings", mapOf("orientation" to "Landscape", "color" to "color")) }
    }

    @Test fun destructiveStorageTargetsAreRequiredAndSlashesAreNotDuplicated() {
        assertEquals("^<FileCommand FileCommand=\"qformat\" Subject=\"./Card/\" />", build("quick-format", mapOf("storageRoot" to "./Card")).text)
        assertEquals("^<FileCommand FileCommand=\"recovery\" Subject=\"./Card/\" />", build("storage-recovery", mapOf("storageRoot" to "./Card/")).text)
        assertFailsWith<ProtocolException> { build("quick-format") }
        assertFailsWith<ProtocolException> { build("storage-recovery", mapOf("storageRoot" to "")) }
        assertTrue(LabImpact.STORAGE_ERASE in LabExperiments.template("quick-format").impacts)
        assertTrue(LabImpact.STORAGE_RECOVERY in LabExperiments.template("storage-recovery").impacts)
    }

    @Test fun xmpTemplatesKeepTheSuppliedPacketAndDoNotSilentlyRewriteFlags() {
        val xmp = packet()
        val only = build("xmp-only", mapOf("fileName" to "./a.mos", "xmp" to xmp))
        assertEquals("^<SetPictureParams file_name=\"./a.mos\" XMP_tag=\"$xmp\" />", only.text)
        val withAnnotation = build("annotation-xmp", mapOf("fileName" to "./a.mos", "annotation" to "", "xmp" to xmp))
        assertEquals("^<SetPictureParams file_name=\"./a.mos\" annotation=\"\" XMP_tag=\"$xmp\" />", withAnnotation.text)
        assertEquals("+-+keep", XmpMetadata.decode(frame(only.text).value("xmp_tag")!!).flags)
        assertFailsWith<ProtocolException> { build("annotation-xmp", mapOf("fileName" to "./a.mos", "xmp" to xmp)) }
        assertFailsWith<ProtocolException> { build("xmp-only", mapOf("fileName" to "./a.mos", "xmp" to "yes")) }
        assertFailsWith<ProtocolException> { build("annotation-xmp", mapOf("fileName" to "./a.mos", "annotation" to "bad\"data", "xmp" to xmp)) }
    }

    @Test fun notificationParameterDoesNotClaimNoDisablesNotifications() {
        assertEquals("^<SetCameraParam EnableTakenPicture=\"yes\" />", build("enable-taken-picture", mapOf("value" to "yes")).text)
        assertEquals("^<SetCameraParam EnableTakenPicture=\"no\" />", build("enable-taken-picture", mapOf("value" to "no")).text)
        assertTrue(LabExperiments.template("enable-taken-picture").notesEn.contains("no has not been proved to stop notifications"))
        assertFailsWith<ProtocolException> { build("enable-taken-picture") }
        assertFailsWith<ProtocolException> { build("enable-taken-picture", mapOf("value" to "false")) }
    }

    @Test fun controlAndProbeTemplatesKeepUnknownSemanticsExplicit() {
        val ack = build("ack")
        assertEquals("^<Ack />", ack.text)
        assertTrue(LabExperiments.isSendOnly(ack))
        assertEquals("^<ReSyncRequest />", build("resync-request").text)
        assertEquals("^<GetCameraParam GetParamList=\"UnknownField;CurrentTime\" />", build("get-camera-param", mapOf("getParamList" to "UnknownField;CurrentTime")).text)
        assertEquals("^<SetCameraParam UnverifiedFixture=\"3\" />", build("set-camera-param-probe", mapOf("attribute" to "UnverifiedFixture", "value" to "3")).text)
        assertTrue(LabExperiments.template("set-camera-param-probe").provenance.contains("USER PROBES"))
        assertFailsWith<ProtocolException> { build("set-camera-param-probe", mapOf("attribute" to "bad name", "value" to "3")) }
    }

    @Test fun rawExperimentRejectsMultipleIncompleteOrMalformedControls() {
        listOf("", "<Ack />", "^<Ack", "^<Ack />^<RequestSync />", "^<Ack /> junk>", "^<Probe a='1' a='2' />", "^<Probe a='unterminated />", "^<Probe a='nul\u0000' />").forEach { bad ->
            assertFailsWith<ProtocolException> { LabExperiments.command(bad, WireEncoding.WESTERN) }
        }
        assertFailsWith<ProtocolException> { LabExperiments.command("^<Probe attachment='1' />", WireEncoding.WESTERN) }
        assertEquals("^<Probe attachment='0' />", LabExperiments.command("^<Probe attachment='0' />", WireEncoding.WESTERN).text)
    }

    @Test fun rawHeaderLimitIsOnEncodedBytesAndPreservesTheChosenEncoding() {
        val prefix = "^<Probe text=\""; val suffix = "\" />"
        val boundary = prefix + "x".repeat(LabExperiments.MAX_HEADER_BYTES - prefix.length - suffix.length) + suffix
        assertEquals(LabExperiments.MAX_HEADER_BYTES, LabExperiments.command(boundary, WireEncoding.WESTERN).bytes(WireEncoding.WESTERN).size)
        assertFailsWith<ProtocolException> { LabExperiments.command(boundary.replace("x\" />", "xx\" />"), WireEncoding.WESTERN) }
        val chinese = prefix + "中".repeat(30_000) + suffix
        val gbk = LabExperiments.command(chinese, WireEncoding.GBK)
        assertContentEquals(LeafCommand(chinese).bytes(WireEncoding.GBK), gbk.bytes(WireEncoding.GBK))
        assertFailsWith<ProtocolException> { LabExperiments.command(chinese, WireEncoding.UTF8) }
        assertFailsWith<ProtocolException> { LabExperiments.command("^<Probe text='中文' />", WireEncoding.WESTERN) }
    }

    @Test fun unknownExpectedTagFilenameAndAttachmentMatchingRemainConfigurable() {
        val command = LabExperiments.command("^<UnknownRequest />", WireEncoding.WESTERN,
            expectedTag = "FixtureReply", expectedFileName = "./a.mos", requiresAttachment = true)
        assertFalse(command.acceptAnyFrame)
        assertTrue(command.accepts(frame("^<FixtureReply file_name='./a.mos' attachment='2' />"), WireEncoding.WESTERN))
        assertFalse(command.accepts(frame("^<OtherReply file_name='./a.mos' attachment='2' />"), WireEncoding.WESTERN))
        assertFalse(command.accepts(frame("^<FixtureReply file_name='./b.mos' attachment='2' />"), WireEncoding.WESTERN))
        assertFalse(command.accepts(frame("^<FixtureReply file_name='./a.mos' />"), WireEncoding.WESTERN))
        assertEquals("Ack", LabExperiments.command("^<UnknownRequest />", WireEncoding.WESTERN).responseTag)
    }

    @Test fun firstFrameAndSendOnlyModesCannotSilentlyIgnoreMatchingFilters() {
        val any = LabExperiments.command("^<UnknownRequest />", WireEncoding.WESTERN, expectedTag = "*")
        assertTrue(any.acceptAnyFrame)
        assertEquals("*", any.responseTag)
        assertTrue(any.accepts(frame("^<UnrelatedNotification />"), WireEncoding.WESTERN), "First-frame mode intentionally does not prove a response")
        assertFalse(LabExperiments.isSendOnly(any))
        val sendOnly = LabExperiments.command("^<UnknownRequest />", WireEncoding.WESTERN, expectedTag = "")
        assertTrue(LabExperiments.isSendOnly(sendOnly))
        assertFalse(sendOnly.acceptAnyFrame)
        listOf("", "*").forEach { mode ->
            assertFailsWith<ProtocolException> { LabExperiments.command("^<UnknownRequest />", WireEncoding.WESTERN, expectedTag = mode, expectedFileName = "a.mos") }
            assertFailsWith<ProtocolException> { LabExperiments.command("^<UnknownRequest />", WireEncoding.WESTERN, expectedTag = mode, requiresAttachment = true) }
        }
    }

    @Test fun invalidObservationSettingsAndUnsafeSubstitutionsFailBeforeSending() {
        assertFailsWith<ProtocolException> { LabExperiments.command("^<Probe />", WireEncoding.WESTERN, expectedTag = "Ack /><Other") }
        assertFailsWith<ProtocolException> { LabExperiments.command("^<Probe />", WireEncoding.WESTERN, timeoutMs = LabExperiments.MIN_TIMEOUT_MS - 1) }
        assertFailsWith<ProtocolException> { LabExperiments.command("^<Probe />", WireEncoding.WESTERN, timeoutMs = LabExperiments.MAX_TIMEOUT_MS + 1) }
        listOf("bad\"value", "bad^value", "bad<value", "bad&value", "bad\nvalue").forEach { bad ->
            assertFailsWith<ProtocolException> { build("set-camera-param-probe", mapOf("attribute" to "SomeField", "value" to bad)) }
        }
    }

    @Test fun redactionCoversPinAliasesWhitespaceQuotesAndPrivacyFields() {
        val log = """TX ^<SetCameraParam NewPinCode = "8172" PinCode='9981' pin=6712 PinEncryption="None" />
            RX ^<Ack PINCODE="1138" annotation="Private note" copyright='Private owner' XMP_tag="base64Private" />""".trimIndent()
        val redacted = LabRedaction.redact(log)
        listOf("8172", "9981", "6712", "1138", "Private note", "Private owner", "base64Private").forEach { assertFalse(redacted.contains(it), it) }
        assertTrue(redacted.contains("PinEncryption=\"None\""))
        assertTrue(redacted.contains("NewPinCode = \"[redacted]\""))
        assertEquals(redacted, LabRedaction.redact(redacted))
        assertEquals("OtherPinCode=not-a-pin", LabRedaction.redact("OtherPinCode=not-a-pin"))
    }

    @Test fun truncatedSecretValuesAreRedactedBeforeAnExportCanRevealThem() {
        val truncated = "RX ^<Ack PinCode='secretAcross\nlines"
        val redacted = LabRedaction.redact(truncated)
        assertFalse(redacted.contains("secretAcross"))
        assertFalse(redacted.contains("lines"))
        assertTrue(redacted.contains("[redacted]"))
        val raw = build("new-pin-code", mapOf("newPinCode" to "reviewSecret"))
        assertTrue(raw.text.contains("reviewSecret"))
        assertFalse(LabRedaction.redact(raw.text).contains("reviewSecret"))
    }
}
