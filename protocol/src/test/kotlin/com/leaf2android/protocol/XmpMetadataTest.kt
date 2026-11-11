package com.leaf2android.protocol

import org.junit.Test
import org.w3c.dom.Document
import java.io.ByteArrayInputStream
import java.util.Base64
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.*

class XmpMetadataTest {
    private fun encode(xml: String) = Base64.getEncoder().encodeToString(xml.toByteArray(Charsets.UTF_8))
    private fun packet(properties: String, attributes: String = "") = """<?xml version="1.0" encoding="UTF-8"?>
        <x:xmpmeta xmlns:x="adobe:ns:meta/"><rdf:RDF xmlns:rdf="${XmpMetadata.RDF_NAMESPACE}">
        <rdf:Description rdf:about="" xmlns:leaf="${XmpMetadata.LEAF_NAMESPACE}" xmlns:dc="http://purl.org/dc/elements/1.1/" $attributes>
        $properties</rdf:Description></rdf:RDF></x:xmpmeta>""".trimIndent()
    private fun read(encoded: String): Document = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        .newDocumentBuilder().parse(ByteArrayInputStream(Base64.getDecoder().decode(encoded)))

    @Test fun thirdFlagCharacterChangesAndOtherNamespacesMetadataAndBitsSurvive() {
        val input = packet("""<leaf:Flags>+-+suffix</leaf:Flags><!-- keep comment --><dc:title><rdf:Alt><rdf:li xml:lang="zh-CN">用户 &amp; café</rdf:li></rdf:Alt></dc:title><other:Flags xmlns:other="urn:other">keep me</other:Flags>""")
        val original = XmpMetadata.decode(encode(input))
        assertEquals("+-+suffix", original.flags)
        assertEquals(true, original.isGood)
        val changed = original.withGoodFlag(false)
        assertEquals("+--suffix", XmpMetadata.decode(changed).flags)
        assertEquals(false, XmpMetadata.decode(changed).isGood)
        assertEquals("+-+suffix", original.flags, "Mutation must not change the previously parsed original packet")
        val doc = read(changed)
        assertEquals("用户 & café", doc.getElementsByTagNameNS(XmpMetadata.RDF_NAMESPACE, "li").item(0).textContent)
        assertEquals("keep me", doc.getElementsByTagNameNS("urn:other", "Flags").item(0).textContent)
        assertTrue(String(Base64.getDecoder().decode(changed), Charsets.UTF_8).contains("keep comment"))
    }

    @Test fun attributeRepresentationAndOtherFlagBitsArePreserved() {
        val encoded = encode(packet("<dc:creator>A photographer</dc:creator>", "leaf:Flags=\"++-keep\""))
        val changed = XmpMetadata.decode(encoded).withGoodFlag(true)
        val doc = read(changed)
        val description = doc.getElementsByTagNameNS(XmpMetadata.RDF_NAMESPACE, "Description").item(0) as org.w3c.dom.Element
        assertEquals("+++keep", description.getAttributeNS(XmpMetadata.LEAF_NAMESPACE, "Flags"))
        assertEquals(0, doc.getElementsByTagNameNS(XmpMetadata.LEAF_NAMESPACE, "Flags").length)
        assertEquals("A photographer", doc.getElementsByTagNameNS("http://purl.org/dc/elements/1.1/", "creator").item(0).textContent)
    }

    @Test fun missingAndShortFlagsUseVendorDefaultsWithoutReplacingThePacket() {
        listOf("", "<leaf:Flags />", "<leaf:Flags>+</leaf:Flags>").forEach { properties ->
            val parsed = XmpMetadata.decode(encode(packet(properties + "<dc:format>image/mos</dc:format>")))
            assertEquals("--+", XmpMetadata.decode(parsed.withGoodFlag(true)).flags)
            assertEquals("---", XmpMetadata.decode(parsed.withGoodFlag(false)).flags)
            assertEquals("image/mos", read(parsed.withGoodFlag(true)).getElementsByTagNameNS("http://purl.org/dc/elements/1.1/", "format").item(0).textContent)
        }
    }

    @Test fun addingFlagsAvoidsAnExistingDifferentLeafNamespacePrefix() {
        val xml = """<rdf:RDF xmlns:rdf="${XmpMetadata.RDF_NAMESPACE}" xmlns:leaf="urn:unrelated"><rdf:Description rdf:about=""><leaf:property>unchanged</leaf:property></rdf:Description></rdf:RDF>"""
        val changed = XmpMetadata.decode(encode(xml)).withGoodFlag(true)
        assertEquals("--+", XmpMetadata.decode(changed).flags)
        assertEquals("unchanged", read(changed).getElementsByTagNameNS("urn:unrelated", "property").item(0).textContent)
    }

    @Test fun onlyTheVerifiedBase64HeaderAttributeIsAccepted() {
        val encoded = encode(packet("<leaf:Flags>---</leaf:Flags>"))
        val reply = HeaderParser.parse("^<Ack file_name='./a.mos' XMP_tag='$encoded' />".toByteArray())
        assertEquals("---", XmpMetadata.fromPictureParams(reply)?.flags)
        assertNull(XmpMetadata.fromPictureParams(HeaderParser.parse("^<Ack file_name='./a.mos' />".toByteArray())))
        assertFailsWith<ProtocolException> { XmpMetadata.fromPictureParams(reply.copy(attachment = "not a proved XMP format".toByteArray())) }
        assertFailsWith<ProtocolException> { XmpMetadata.fromPictureParams(HeaderParser.parse("^<Ack attachment='1' />".toByteArray())) }
        assertFailsWith<ProtocolException> { XmpMetadata.fromPictureParams(HeaderParser.parse("^<Ack xmp_tag='yes' />".toByteArray())) }
        assertFailsWith<ProtocolException> { XmpMetadata.fromPictureParams(reply.copy(tag = "Nak")) }
    }

    @Test fun noDtdOrExternalEntityCanBeResolved() {
        val dtd = """<!DOCTYPE rdf:RDF [<!ENTITY attack SYSTEM "file:///this-file-must-never-be-opened">]>"""
        val internal = """<!DOCTYPE rdf:RDF [<!ENTITY attack "expansion">]>"""
        listOf(dtd, internal, "<!DOCTYPE rdf:RDF SYSTEM \"https://example.invalid/never-fetch\">").forEach { declaration ->
            assertFailsWith<ProtocolException> { XmpMetadata.decode(encode(declaration + packet("<dc:title>&attack;</dc:title>").substringAfter("?>"))) }
        }
        assertFailsWith<ProtocolException> { XmpMetadata.decode(encode(packet("<dc:title>&undeclared;</dc:title>"))) }
    }

    @Test fun malformedBase64Utf8AndNonXmpXmlFailExplicitly() {
        listOf("", "yes", "====", "AAAA!", "YWJj\n", "Zg=", "Zh==").forEach { invalid ->
            assertFailsWith<ProtocolException> { XmpMetadata.decode(invalid) }
        }
        assertFailsWith<ProtocolException> { XmpMetadata.decode(Base64.getEncoder().encodeToString(byteArrayOf(0xff.toByte(), 0xfe.toByte()))) }
        assertFailsWith<ProtocolException> { XmpMetadata.decode(encode("<root/>")) }
        assertFailsWith<ProtocolException> { XmpMetadata.decode(encode(packet("<unclosed>"))) }
    }

    @Test fun ambiguousOrStructuredFlagsAreRejectedInsteadOfOverwritingMetadata() {
        listOf("<leaf:Flags>---</leaf:Flags><leaf:Flags>--+</leaf:Flags>",
            "<leaf:Flags><rdf:Seq><rdf:li>---</rdf:li></rdf:Seq></leaf:Flags>").forEach { properties ->
            assertFailsWith<ProtocolException> { XmpMetadata.decode(encode(packet(properties))) }
        }
        assertFailsWith<ProtocolException> { XmpMetadata.decode(encode(packet("<leaf:Flags>---</leaf:Flags>", "leaf:Flags=\"--+\""))) }
        assertFailsWith<ProtocolException> { XmpMetadata.decode(encode(packet("<leaf:Flags>标记</leaf:Flags>"))).withGoodFlag(true) }
    }

    @Test fun sizeAndDepthLimitsProtectParserAndOutput() {
        val oversized = encode(packet("<dc:title>" + "x".repeat(XmpMetadata.MAX_DECODED_BYTES) + "</dc:title>"))
        assertFailsWith<ProtocolException> { XmpMetadata.decode(oversized) }
        val nested = packet("<a>".repeat(80) + "text" + "</a>".repeat(80))
        assertFailsWith<ProtocolException> { XmpMetadata.decode(encode(nested)) }
    }
}
