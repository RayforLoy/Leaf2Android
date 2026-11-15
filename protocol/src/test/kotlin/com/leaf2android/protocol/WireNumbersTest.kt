package com.leaf2android.protocol

import org.junit.Test
import java.io.IOException
import kotlin.test.*

class WireNumbersTest {
    @Test fun decimalCodesCoverUnsignedDwordWithoutWrapping() {
        assertEquals(0L, WireNumbers.errorCode("0"))
        assertEquals(1L, WireNumbers.errorCode("1"))
        assertEquals(2_147_483_648L, WireNumbers.errorCode("2147483648"))
        assertEquals(4_294_967_295L, WireNumbers.errorCode("4294967295"))
        assertEquals(1L, WireNumbers.errorCode("0".repeat(32) + "1"))
    }

    @Test fun hexadecimalCodesAcceptBothPrefixesAndLetterCases() {
        assertEquals(0L, WireNumbers.errorCode("0x00"))
        assertEquals(1L, WireNumbers.errorCode("0x01"))
        assertEquals(1L, WireNumbers.errorCode("0X01"))
        assertEquals(0xabcdefL, WireNumbers.errorCode("0XaBcDeF"))
        assertEquals(4_294_967_295L, WireNumbers.errorCode("0xffffffff"))
        assertEquals(4_294_967_295L, WireNumbers.errorCode("0XFFFFFFFF"))
    }

    @Test fun unknownSyntaxAndOverflowNeverBecomeZero() {
        listOf(
            null, "", " ", "0x", "0X", "-1", "-0", "+1", "0x-1", "0x+1",
            "4294967296", "0x100000000", "18446744073709551615", "0xffffffffffffffff",
            "1.0", "1_0", "1e1", "ff", "0xgg", "0b10", "0o10", "0x0.1",
            " 0", "0 ", "\n0", "0\n", "0x01 ", "0x0\u0000", "０", "١",
        ).forEach { raw -> assertNull(WireNumbers.errorCode(raw), "Unexpectedly parsed: $raw") }
    }

    @Test fun conservativeComparisonAcceptsOnlyParsedZero() {
        assertFalse(WireNumbers.errorCode("0x00") != 0L)
        assertTrue(WireNumbers.errorCode("0x01") != 0L)
        assertTrue(WireNumbers.errorCode("unknown") != 0L)
        assertTrue(WireNumbers.errorCode(null) != 0L)
    }

    @Test fun rejectionDescribesRequestNamesAndRedactsResponseSecretsInBothLanguages() {
        val request = LeafCommand("^<SetCameraParam NewPinCode='request-pin' Annotation='request-note' Copyright='request-rights' XMP_tag='request-xmp' />")
        val response = frame("^<Error error_code='0x01' NewPinCode='response-pin' Annotation='response-note' Copyright='response-rights' XMP_tag='response-xmp' />")
        val originalRequest = request.text
        val originalResponse = response.header.copyOf()
        val failure = CommandRejectedException(request, response, WireEncoding.WESTERN)
        listOf(failure.messageForLanguage(false), failure.messageForLanguage(true)).forEach { message ->
            assertTrue(message.startsWith("SetCameraParam [NewPinCode, Annotation, Copyright, XMP_tag]"))
            assertTrue(message.contains("^<Error error_code='0x01'"))
            assertTrue(message.contains("[redacted]"))
            listOf("request-pin", "request-note", "request-rights", "request-xmp",
                "response-pin", "response-note", "response-rights", "response-xmp").forEach { secret ->
                assertFalse(message.contains(secret), "Exception disclosed $secret")
            }
        }
        assertEquals(1L, failure.errorCode)
        assertSame(request, failure.request)
        assertSame(response, failure.response)
        assertEquals(originalRequest, request.text)
        assertContentEquals(originalResponse, response.header)
        assertEquals("response-pin", response.value("NewPinCode"))
    }

    @Test fun rejectionRemainsAnIoAndProtocolExceptionEvenWithoutAKnownErrorCode() {
        val failure = CommandRejectedException(Commands.sync, frame("^<Error />"), WireEncoding.WESTERN)
        assertIs<IOException>(failure)
        assertIs<ProtocolException>(failure)
        assertNull(failure.errorCode)
        assertTrue(failure.message.orEmpty().startsWith("RequestSync:"))
    }

    @Test fun rejectedAckAndNakRetainRawCodesAndChosenResponseEncoding() {
        val response = frame("^<Ack error_code='4294967295' Annotation='私人备注' />", WireEncoding.GBK)
        val failure = CommandRejectedException(Commands.currentTime, response, WireEncoding.GBK)
        assertEquals(4_294_967_295L, failure.errorCode)
        assertEquals("4294967295", response.value("error_code"))
        assertFalse(failure.messageForLanguage(true).contains("私人备注"))
        assertTrue(failure.message.orEmpty().startsWith("GetCameraParam [GetParamList]:"))
        val unknown = CommandRejectedException(Commands.sync, frame("^<Nak error_code='unknown' />"), WireEncoding.WESTERN)
        assertNull(unknown.errorCode)
        assertEquals("unknown", unknown.response.value("error_code"))
    }

    private fun frame(text: String, encoding: WireEncoding = WireEncoding.WESTERN) =
        HeaderParser.parse(text.toByteArray(encoding.charset))
}
