"""The non-packetized control/attachment framing seen in WiView 1.0.2 and 1.0.3.

Static evidence: reference/analysis/protocol-evidence.json. This module does not
implement a camera session, authentication, JPEG decoding, or MOS file transfer.
Host-side size limits are defensive choices, not measured camera limits.
"""

from __future__ import annotations

from dataclasses import dataclass
import re


class ProtocolError(ValueError):
    """Malformed input or a host-side size limit was encountered."""


class TruncatedStreamError(ProtocolError):
    """The stream ended inside a control header or binary attachment."""


@dataclass(frozen=True)
class WireFrame:
    header: bytes
    tag: str
    attributes: tuple[tuple[str, bytes], ...]
    attachment: bytes = b""

    def get(self, name: str) -> bytes | None:
        return next((value for key, value in self.attributes if key == name), None)


_NAME = re.compile(rb"[A-Za-z_][A-Za-z0-9_.:-]*")
_SPACE = b" \t\r\n"


def parse_header(header: bytes) -> WireFrame:
    """Read a flat, attribute-only control header; retain attribute bytes verbatim.

    Non-ASCII values and entity sequences are preserved without guessing their
    encoding or unescaping rules. This is deliberately a bounded subset of the
    original parser, pending captures; it is not a general XML parser.
    """
    if not header.startswith(b"^<") or not header.endswith(b">"):
        raise ProtocolError("Expected a caret-prefixed control header")
    match = _NAME.match(header, 2)
    if match is None:
        raise ProtocolError("Missing control tag name")
    tag = match.group().decode("ascii")
    position = match.end()
    end = len(header) - 1
    attributes = []
    names = set()
    while position < end:
        start = position
        while position < end and header[position] in _SPACE:
            position += 1
        if position == end:
            break
        if header[position:end] == b"/":
            break
        if position == start:
            raise ProtocolError("Expected whitespace before an attribute")
        match = _NAME.match(header, position)
        if match is None:
            raise ProtocolError("Invalid attribute name")
        name = match.group().decode("ascii")
        if name in names:
            raise ProtocolError(f"Duplicate attribute: {name}")
        names.add(name)
        position = match.end()
        while position < end and header[position] in _SPACE:
            position += 1
        if position >= end or header[position] != ord("="):
            raise ProtocolError("Expected an attribute assignment")
        position += 1
        while position < end and header[position] in _SPACE:
            position += 1
        if position >= end or header[position] not in (ord('"'), ord("'")):
            raise ProtocolError("Expected a quoted attribute value")
        quote = header[position]
        position += 1
        stop = header.find(bytes((quote,)), position, end)
        if stop < 0:
            raise ProtocolError("Unterminated attribute value")
        value = header[position:stop]
        if any(character in value for character in (b"^", b"<", b">")):
            raise ProtocolError("Unescaped framing character in a control value")
        attributes.append((name, value))
        position = stop + 1
    return WireFrame(header, tag, tuple(attributes))


class StreamFramer:
    """Incrementally frame controls and their immediately following attachments.

    The original reader resynchronizes at the most recent '^' outside an
    attachment and terminates at the first '>'. Binary attachments must bypass
    that delimiter scan. reset() is required after a disconnect or timeout.
    """

    def __init__(self, *, max_header_bytes: int = 64 * 1024,
                 max_attachment_bytes: int = 64 * 1024 * 1024) -> None:
        if max_header_bytes < 4 or max_attachment_bytes < 0:
            raise ValueError("Invalid host-side framing limits")
        self.max_header_bytes = max_header_bytes
        self.max_attachment_bytes = max_attachment_bytes
        self.discarded_bytes = 0
        self.resync_count = 0
        self.reset()

    def reset(self) -> None:
        self._header = bytearray()
        self._pending: WireFrame | None = None
        self._attachment = bytearray()
        self._remaining = 0

    def feed(self, data: bytes) -> list[WireFrame]:
        frames = []
        position = 0
        while position < len(data):
            if self._pending is not None:
                count = min(self._remaining, len(data) - position)
                self._attachment.extend(data[position : position + count])
                self._remaining -= count
                position += count
                if self._remaining == 0:
                    pending = self._pending
                    frames.append(WireFrame(pending.header, pending.tag, pending.attributes, bytes(self._attachment)))
                    self.reset()
                continue
            character = data[position]
            position += 1
            if character == ord("^"):
                if self._header:
                    self.resync_count += 1
                    self.discarded_bytes += len(self._header)
                self._header = bytearray(b"^")
                continue
            if not self._header:
                self.discarded_bytes += 1
                continue
            self._header.append(character)
            if len(self._header) > self.max_header_bytes:
                self.reset()
                raise ProtocolError("Control header exceeds host-side size limit")
            if character != ord(">"):
                continue
            try:
                frame = parse_header(bytes(self._header))
                length = frame.get("attachment")
                if length is None:
                    attachment_bytes = 0
                elif not re.fullmatch(rb"[0-9]+", length):
                    raise ProtocolError("attachment must be a non-negative decimal byte count")
                elif len(length) > 10:
                    raise ProtocolError("Attachment exceeds host-side size limit")
                else:
                    attachment_bytes = int(length)
                if attachment_bytes > self.max_attachment_bytes:
                    raise ProtocolError("Attachment exceeds host-side size limit")
            except ProtocolError:
                self.reset()
                raise
            self._header.clear()
            if attachment_bytes:
                self._pending = frame
                self._remaining = attachment_bytes
            else:
                frames.append(frame)
        return frames

    def finish(self) -> None:
        if self._pending is not None:
            raise TruncatedStreamError(f"Attachment is missing {self._remaining} bytes")
        if self._header:
            raise TruncatedStreamError("Stream ended inside a control header")

