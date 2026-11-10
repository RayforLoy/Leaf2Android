import unittest

from leafresearch.framing import ProtocolError, StreamFramer, TruncatedStreamError, parse_header


class FramingTests(unittest.TestCase):
    def test_control_split_at_every_possible_byte_boundary(self):
        control = b'^<GetCameraParam GetParamList="battery_percent" />'
        for position in range(len(control) + 1):
            with self.subTest(position=position):
                framer = StreamFramer()
                frames = framer.feed(control[:position]) + framer.feed(control[position:])
                framer.finish()
                self.assertEqual([f.header for f in frames], [control])

    def test_binary_attachment_with_delimiters_and_next_control(self):
        binary = b'\xff\xd8^<Ack />\0>^\xff\xd9'
        header = f'^<Ack file_name="Shots/test.mos" attachment="{len(binary)}" />'.encode()
        data = header + binary + b'^<PictureTaken file_name="Shots/next.mos" />'
        for chunk_size in (1, 2, 3, 7, 4096):
            with self.subTest(chunk_size=chunk_size):
                framer = StreamFramer()
                frames = []
                for start in range(0, len(data), chunk_size):
                    frames.extend(framer.feed(data[start : start + chunk_size]))
                framer.finish()
                self.assertEqual([f.tag for f in frames], ["Ack", "PictureTaken"])
                self.assertEqual(frames[0].attachment, binary)
                self.assertEqual(frames[1].get("file_name"), b"Shots/next.mos")

    def test_multiple_controls_in_one_read(self):
        self.assertEqual([f.tag for f in StreamFramer().feed(b'^<Ack />^<Nak />^<Sync />')], ["Ack", "Nak", "Sync"])

    def test_repeated_caret_resynchronizes_outside_attachments(self):
        framer = StreamFramer()
        frames = framer.feed(b'garbage^broken^<Ack />')
        self.assertEqual([f.tag for f in frames], ["Ack"])
        self.assertEqual(framer.resync_count, 1)
        self.assertEqual(framer.discarded_bytes, len(b'garbage^broken'))

    def test_zero_length_attachment(self):
        frames = StreamFramer().feed(b'^<Ack attachment="0" />^<Ack />')
        self.assertEqual(len(frames), 2)
        self.assertEqual(frames[0].attachment, b'')

    def test_partial_control_at_end_of_stream(self):
        framer = StreamFramer()
        framer.feed(b'^<Ack')
        with self.assertRaises(TruncatedStreamError):
            framer.finish()

    def test_partial_attachment_at_end_of_stream(self):
        framer = StreamFramer()
        framer.feed(b'^<Ack attachment="4" />12')
        with self.assertRaisesRegex(TruncatedStreamError, "2 bytes"):
            framer.finish()

    def test_invalid_attachment_lengths(self):
        for value in (b'-1', b'+1', b'1e3', b'', b'1 2', b'9999999999999999999999999'):
            with self.subTest(value=value):
                with self.assertRaises(ProtocolError):
                    StreamFramer().feed(b'^<Ack attachment="' + value + b'" />')

    def test_attachment_size_limit_is_enforced_before_collecting_payload(self):
        with self.assertRaises(ProtocolError):
            StreamFramer(max_attachment_bytes=10).feed(b'^<Ack attachment="11" />')

    def test_attachment_at_limit(self):
        framer = StreamFramer(max_attachment_bytes=3)
        self.assertEqual(framer.feed(b'^<Ack attachment="3" />123')[0].attachment, b'123')

    def test_header_size_limit(self):
        with self.assertRaises(ProtocolError):
            StreamFramer(max_header_bytes=8).feed(b'^<Unbounded')

    def test_reset_after_timeout_does_not_mix_sessions(self):
        framer = StreamFramer()
        framer.feed(b'^<Ack attachment="20" />partial')
        framer.reset()
        self.assertEqual(framer.feed(b'^<Sync />')[0].tag, 'Sync')
        framer.finish()

    def test_error_does_not_poison_next_read(self):
        framer = StreamFramer()
        with self.assertRaises(ProtocolError):
            framer.feed(b'^<Ack attachment="bad" />')
        self.assertEqual(framer.feed(b'^<Ack />')[0].tag, 'Ack')

    def test_attribute_like_text_inside_value_is_not_a_length(self):
        frame = StreamFramer().feed(b'''^<Ack note="attachment='999'" />''')[0]
        self.assertEqual(frame.get('note'), b"attachment='999'")
        self.assertEqual(frame.attachment, b'')

    def test_attribute_bytes_are_not_guessed_or_unescaped(self):
        frame = parse_header(b'^<Ack file_name="\x81\xfe&amp;test" />')
        self.assertEqual(frame.get('file_name'), b'\x81\xfe&amp;test')

    def test_duplicate_length_is_rejected(self):
        with self.assertRaises(ProtocolError):
            StreamFramer().feed(b'^<Ack attachment="2" attachment="3" />')

    def test_invalid_headers(self):
        for header in (b'<Ack />', b'^< />', b'^<Ack x=1 />', b'^<Ack x="a>b" />', b'^<Ack x="1"y="2" />'):
            with self.subTest(header=header):
                with self.assertRaises(ProtocolError):
                    parse_header(header)

    def test_header_without_self_closing_slash(self):
        self.assertEqual(parse_header(b'^<Ack file_name="a">').get('file_name'), b'a')

    def test_empty_reads_and_noise_only(self):
        framer = StreamFramer()
        self.assertEqual(framer.feed(b''), [])
        self.assertEqual(framer.feed(b'noise'), [])
        framer.finish()

    def test_invalid_host_limits(self):
        for parameters in ({'max_header_bytes': 3}, {'max_attachment_bytes': -1}):
            with self.assertRaises(ValueError):
                StreamFramer(**parameters)


if __name__ == '__main__':
    unittest.main()

