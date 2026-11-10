import hashlib
import importlib.util
import json
from pathlib import Path
import struct
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
from acquire_sources import local_path, verify


class SourceTests(unittest.TestCase):
    def test_paths_cannot_escape_workspace(self):
        for path in ('../escape.cab', str(ROOT.parent / 'escape.cab')):
            with self.assertRaises(ValueError):
                local_path(path)

    def test_integrity_detects_corruption_and_truncation(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'input.cab'
            expected = {'size': 3, 'sha256': hashlib.sha256(b'abc').hexdigest()}
            path.write_bytes(b'abc')
            verify(path, expected)
            for data in (b'ab', b'abd'):
                path.write_bytes(data)
                with self.assertRaises(ValueError):
                    verify(path, expected)


@unittest.skipUnless(importlib.util.find_spec('pefile'), 'Install requirements-research.txt for CAB analysis tests')
class CabinetTests(unittest.TestCase):
    def test_truncated_cab_is_rejected(self):
        from analyze_wiview import cab_members
        with self.assertRaises(ValueError):
            cab_members(b'MSCF')

    def test_directory_path_is_rejected_before_extraction(self):
        from analyze_wiview import cab_members
        payload = bytearray(36)
        payload[:4] = b'MSCF'
        struct.pack_into('<I', payload, 16, 36)
        struct.pack_into('<H', payload, 28, 1)
        payload += struct.pack('<IIHHHH', 1, 0, 0, 0, 0, 0)
        payload += b'../escape.exe\0'
        struct.pack_into('<I', payload, 8, len(payload))
        with self.assertRaises(ValueError):
            cab_members(bytes(payload))

    def test_acquired_cabinets_match_pinned_inputs(self):
        from analyze_wiview import cab_members
        lock = json.loads((ROOT / 'reference/sources.lock.json').read_text('utf-8'))
        found = 0
        for source in lock['sources']:
            if not source['path'].lower().endswith('.cab'):
                continue
            path = ROOT / source['path']
            if not path.exists():
                self.skipTest('Download the research inputs and supply the user CAB locally')
            verify(path, source)
            self.assertEqual(len(cab_members(path.read_bytes())), 7)
            found += 1
        self.assertEqual(found, 2)


if __name__ == '__main__':
    unittest.main()
