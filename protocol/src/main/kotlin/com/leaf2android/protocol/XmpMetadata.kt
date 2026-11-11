package com.leaf2android.protocol

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.Attr
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.SAXParseException
import org.xml.sax.helpers.DefaultHandler
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.io.StringReader
import java.nio.charset.CodingErrorAction
import java.util.Base64
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.SAXParserFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/** A bounded, parsed copy of the vendor's Base64 XMP header attribute.
 * DOM serialization preserves XML metadata meaning, namespace URIs, elements and other flag bits;
 * whitespace, attribute order and namespace prefixes need not remain byte-identical.
 * No empty replacement packet is synthesized when a back has not supplied XMP.
 */
class XmpMetadata private constructor(private val document: Document, private val originalBase64: String) {
    val flags: String? get() = flagNode(document)?.let(::flagValue)
    val isGood: Boolean? get() = when (flags?.getOrNull(2)) { '+' -> true; '-' -> false; else -> null }

    /** Change only the third character of Creo leaf:Flags; all other metadata is retained. */
    fun withGoodFlag(good: Boolean): String {
        // Document.cloneNode support varies across Android DOM implementations. Reparse the already
        // bounded original instead, so this immutable instance and all unrelated metadata stay intact.
        val updated = decode(originalBase64).document
        val flag = flagNode(updated)
        val previous = flag?.let(::flagValue)
        if (previous != null && previous.any { it.code !in 0x20..0x7e }) fail(
            "XMP 标记含有尚未确认的字符，未改写元数据", "XMP Flags contains unverified characters; metadata was not changed.")
        val replacement = if (previous == null || previous.length < 3) {
            if (good) "--+" else "---"
        } else previous.substring(0, 2) + (if (good) '+' else '-') + previous.substring(3)
        when (flag) {
            is Attr -> flag.value = replacement
            is Element -> {
                val textNodes = (0 until flag.childNodes.length).map(flag.childNodes::item)
                    .filter { it.nodeType == Node.TEXT_NODE || it.nodeType == Node.CDATA_SECTION_NODE }
                if (textNodes.isEmpty()) flag.appendChild(updated.createTextNode(replacement))
                else {
                    textNodes.first().nodeValue = replacement
                    textNodes.drop(1).forEach { it.nodeValue = "" }
                }
            }
            null -> {
                val descriptions = descriptions(updated)
                val owner = descriptions.singleOrNull() ?: descriptions.filter {
                    it.getAttributeNS(RDF_NAMESPACE, "about").isEmpty()
                }.singleOrNull() ?: fail("XMP 含有多个不明确的描述节点", "XMP contains ambiguous description nodes.")
                var prefix = owner.lookupPrefix(LEAF_NAMESPACE) ?: "leaf"
                var suffix = 1
                while (owner.lookupNamespaceURI(prefix)?.let { it != LEAF_NAMESPACE } == true) prefix = "leaf${suffix++}"
                owner.setAttributeNS(XMLNS_NAMESPACE, "xmlns:$prefix", LEAF_NAMESPACE)
                owner.appendChild(updated.createElementNS(LEAF_NAMESPACE, "$prefix:Flags").apply { textContent = replacement })
            }
            else -> fail("无法安全改写 XMP 标记格式", "The XMP Flags representation cannot be changed safely.")
        }
        return try {
            val factory = TransformerFactory.newInstance()
            runCatching { factory.setFeature(SECURE_PROCESSING, true) }
            runCatching { factory.setAttribute(ACCESS_EXTERNAL_DTD, "") }
            runCatching { factory.setAttribute(ACCESS_EXTERNAL_STYLESHEET, "") }
            val transformer = factory.newTransformer().apply {
                setOutputProperty(OutputKeys.ENCODING, "UTF-8")
                setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")
                setOutputProperty(OutputKeys.INDENT, "no")
            }
            val bytes = ByteArrayOutputStream()
            val output = object : OutputStream() {
                override fun write(value: Int) {
                    if (bytes.size() >= MAX_DECODED_BYTES) throw java.io.IOException("XMP output exceeds host limit")
                    bytes.write(value)
                }
                override fun write(buffer: ByteArray, offset: Int, length: Int) {
                    if (length > MAX_DECODED_BYTES - bytes.size()) throw java.io.IOException("XMP output exceeds host limit")
                    bytes.write(buffer, offset, length)
                }
            }
            transformer.transform(DOMSource(updated), StreamResult(output))
            Base64.getEncoder().encodeToString(bytes.toByteArray())
        } catch (e: ProtocolException) { throw e }
        catch (_: Exception) { fail("XMP 序列化失败或超过手机端大小限制，未发送改写数据", "XMP serialization failed or exceeded the host size limit; no updated data was sent.") }
    }

    companion object {
        const val LEAF_NAMESPACE = "http://www.creo.com/global/products/digital_photography_leaf/default.htm/"
        const val RDF_NAMESPACE = "http://www.w3.org/1999/02/22-rdf-syntax-ns#"
        const val MAX_DECODED_BYTES = 32 * 1024 // Host bound, not a camera specification; keeps the Base64 command below the control-header cap.
        private const val MAX_ENCODED_CHARS = ((MAX_DECODED_BYTES + 2) / 3) * 4
        private const val MAX_DEPTH = 64
        private const val MAX_ELEMENTS = 4096
        private const val XMLNS_NAMESPACE = "http://www.w3.org/2000/xmlns/"
        private const val SECURE_PROCESSING = "http://javax.xml.XMLConstants/feature/secure-processing"
        private const val ACCESS_EXTERNAL_DTD = "http://javax.xml.XMLConstants/property/accessExternalDTD"
        private const val ACCESS_EXTERNAL_STYLESHEET = "http://javax.xml.XMLConstants/property/accessExternalStylesheet"
        private val SAFE_FEATURES = listOf(
            "http://apache.org/xml/features/disallow-doctype-decl" to true,
            "http://xml.org/sax/features/external-general-entities" to false,
            "http://xml.org/sax/features/external-parameter-entities" to false,
            "http://apache.org/xml/features/nonvalidating/load-external-dtd" to false,
            SECURE_PROCESSING to true,
        )

        /** WiView's GetPictureParams parser passes the xmp_tag HEADER VALUE to its Base64 decoder (0x2e3f8..0x2e44c).
         * Attachments have not been proved to carry XMP on this path and are rejected explicitly.
         */
        fun fromPictureParams(frame: WireFrame): XmpMetadata? {
            if (!frame.tag.equals("Ack", true)) fail("XMP 信息没有收到确认回复", "XMP metadata did not receive an Ack response.")
            if (frame.attachment.isNotEmpty() || frame.value("attachment")?.let { it != "0" } == true) fail(
                "尚未确认二进制附件形式的 XMP，已拒绝解析", "XMP in a binary attachment has not been verified and was rejected.")
            return frame.value("xmp_tag", Charsets.US_ASCII)?.let(::decode)
        }

        fun decode(base64: String): XmpMetadata {
            if (base64.isEmpty() || base64.length > MAX_ENCODED_CHARS || base64.length % 4 != 0 ||
                !base64.matches(Regex("[A-Za-z0-9+/]*={0,2}"))) fail(
                "XMP 不是受支持的 Base64 属性或超过手机端大小限制", "XMP is not a supported Base64 attribute or exceeds the host size limit.")
            val bytes = try { Base64.getDecoder().decode(base64) } catch (_: IllegalArgumentException) {
                fail("XMP Base64 数据无效", "XMP Base64 data is invalid.")
            }
            if (bytes.size > MAX_DECODED_BYTES || Base64.getEncoder().encodeToString(bytes) != base64) fail(
                "XMP Base64 数据无效或超过手机端大小限制", "XMP Base64 data is invalid or exceeds the host size limit.")
            val xml = try {
                Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF")
            } catch (_: Exception) { fail("XMP 不是受支持的 UTF-8 元数据", "XMP is not supported UTF-8 metadata.") }
            // Portable enforcement on Android and the JVM: DTD declarations are forbidden BEFORE either parser.
            // Optional JAXP hardening features differ by platform; neither DTD nor entity resolution depends on them.
            if (xml.contains("<!DOCTYPE", true) || xml.contains("<!ENTITY", true)) fail(
                "XMP 的 DTD 和外部实体被禁止", "DTD declarations and external entities are forbidden in XMP.")
            try {
                verifyStructure(xml)
                val factory = DocumentBuilderFactory.newInstance().apply {
                    isNamespaceAware = true
                    isExpandEntityReferences = false
                    runCatching { isXIncludeAware = false }
                    SAFE_FEATURES.forEach { (key, value) -> runCatching { setFeature(key, value) } }
                }
                val builder = factory.newDocumentBuilder().apply {
                    setEntityResolver { _, _ -> throw SAXException("External entities are forbidden") }
                    setErrorHandler(strictErrors())
                }
                val document = builder.parse(InputSource(StringReader(xml)))
                if (document.doctype != null || descriptions(document).isEmpty()) fail(
                    "XMP 缺少有效 RDF 描述或含有不允许的声明", "XMP has no valid RDF description or contains a forbidden declaration.")
                flagNode(document) // Validate ambiguous/structured Flags without changing the supplied metadata.
                return XmpMetadata(document, base64)
            } catch (e: ProtocolException) { throw e }
            catch (_: Exception) { fail("XMP XML 格式无效、过于复杂或含有禁止内容", "XMP XML is invalid, too complex or contains forbidden content.") }
        }

        /** SAX validates depth before the DOM parser or serializer can recurse through a deeply nested input. */
        private fun verifyStructure(xml: String) {
            val factory = SAXParserFactory.newInstance().apply {
                isNamespaceAware = true
                runCatching { isXIncludeAware = false }
                SAFE_FEATURES.forEach { (key, value) -> runCatching { setFeature(key, value) } }
            }
            val reader = factory.newSAXParser().xmlReader
            reader.entityResolver = org.xml.sax.EntityResolver { _, _ -> throw SAXException("External entities are forbidden") }
            reader.errorHandler = strictErrors()
            reader.contentHandler = object : DefaultHandler() {
                private var depth = 0
                private var elements = 0
                override fun startElement(uri: String?, localName: String?, qName: String?, attributes: org.xml.sax.Attributes?) {
                    depth++; elements++
                    if (depth > MAX_DEPTH || elements > MAX_ELEMENTS) throw SAXException("XMP exceeds host XML complexity limits")
                }
                override fun endElement(uri: String?, localName: String?, qName: String?) { depth-- }
            }
            reader.parse(InputSource(StringReader(xml)))
        }

        private fun descriptions(document: Document): List<Element> = document.getElementsByTagNameNS(RDF_NAMESPACE, "Description").let { nodes ->
            (0 until nodes.length).map { nodes.item(it) as Element }
        }

        private fun flagNode(document: Document): Node? {
            val elements = document.getElementsByTagNameNS(LEAF_NAMESPACE, "Flags").let { nodes ->
                (0 until nodes.length).map { nodes.item(it) as Element }
            }
            if (elements.any { element -> (0 until element.childNodes.length).any { element.childNodes.item(it).nodeType == Node.ELEMENT_NODE } }) fail(
                "XMP Flags 不是简单文本，未改写", "XMP Flags is not simple text and was not changed.")
            val attributes = descriptions(document).mapNotNull { it.getAttributeNodeNS(LEAF_NAMESPACE, "Flags") }
            if (elements.size + attributes.size > 1) fail("XMP 含有重复的 Flags 属性，未改写", "XMP contains duplicate Flags properties and was not changed.")
            return elements.singleOrNull() ?: attributes.singleOrNull()
        }

        private fun flagValue(node: Node): String = if (node is Attr) node.value else node.textContent
        private fun strictErrors() = object : DefaultHandler() {
            override fun warning(e: SAXParseException) { throw e }
            override fun error(e: SAXParseException) { throw e }
            override fun fatalError(e: SAXParseException) { throw e }
        }
        private fun fail(chinese: String, english: String): Nothing = throw ProtocolException(chinese, english)
    }
}
