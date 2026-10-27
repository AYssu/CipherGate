"""Recovery evidence and fail-before-write guards; no Docker or production access."""
import hashlib
import importlib.util
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import Mock, patch


ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("recovery_smoke", ROOT / "scripts/release-smoke.py")
smoke = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(smoke)


class ObjectRecoveryTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.directory = Path(temporary.name)
        self.objects = {"update.bin": b"binary\x00\xff", "nested/update package.bin": bytes(range(256)),
                        "empty.txt": b""}
        self.manifest = []
        for key, body in self.objects.items():
            path = self.directory / key
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(body)
            self.manifest.append({"key": key, "bytes": len(body), "sha256": hashlib.sha256(body).hexdigest()})

    def restore(self, existing=()):
        self.created, self.uploaded = Mock(), Mock()
        smoke.restore_object_backup(self.directory, self.manifest, "recovery-target", existing,
                                    self.created, self.uploaded)

    def test_binary_nested_and_empty_objects_restore_exact_bytes(self):
        self.restore()
        self.created.assert_called_once_with("recovery-target")
        self.assertEqual(self.uploaded.call_count, 3)
        self.assertEqual({call.args[1]: call.args[2] for call in self.uploaded.call_args_list}, self.objects)

    def test_changed_backup_is_rejected_before_bucket_creation(self):
        # The size still matches, so the digest itself must catch corruption.
        (self.directory / "update.bin").write_bytes(b"tamper\x00\xff")
        with self.assertRaisesRegex(ValueError, "checksum mismatch"):
            self.restore()
        self.created.assert_not_called()
        self.uploaded.assert_not_called()

    def test_missing_later_object_prevents_even_first_object_upload(self):
        (self.directory / "nested/update package.bin").unlink()
        with self.assertRaisesRegex(ValueError, "missing"):
            self.restore()
        self.created.assert_not_called()
        self.uploaded.assert_not_called()

    def test_occupied_target_is_rejected_without_merge_or_overwrite(self):
        with self.assertRaisesRegex(ValueError, "already exists"):
            self.restore({"recovery-target"})
        self.created.assert_not_called()
        self.uploaded.assert_not_called()

    def test_duplicate_path_traversal_and_invalid_digests_are_rejected(self):
        original = self.manifest[0]
        invalid = [{**original, "key": "../outside"}, {**original, "key": "/absolute"},
                   {**original, "key": "nested/../update.bin"}, {**original, "sha256": "not-a-hash"},
                   {**original, "bytes": True}, {**original, "bytes": -1}]
        for item in invalid:
            with self.subTest(item=item), self.assertRaises(ValueError):
                smoke.validate_object_backup(self.directory, [item])
        with self.assertRaises(ValueError):
            smoke.validate_object_backup(self.directory, [original, original])

    def test_target_creation_failure_prevents_upload(self):
        create, upload = Mock(side_effect=RuntimeError("target became occupied")), Mock()
        with self.assertRaisesRegex(RuntimeError, "occupied"):
            smoke.restore_object_backup(self.directory, self.manifest, "recovery-target", (), create, upload)
        upload.assert_not_called()

    def test_downloaded_binary_and_empty_objects_match_recovered_database(self):
        for item in self.manifest:
            smoke.verify_recovered_object(item, self.objects[item["key"]], f"{item['sha256']}:{item['bytes']}")

    def test_downloaded_object_with_changed_bytes_is_rejected(self):
        item = self.manifest[0]
        with self.assertRaisesRegex(RuntimeError, "Downloaded recovery object differs"):
            smoke.verify_recovered_object(item, b"tamper\x00\xff", f"{item['sha256']}:{item['bytes']}")

    def test_correct_download_with_missing_or_changed_database_reference_is_rejected(self):
        item = self.manifest[0]
        for reference in ["", "0" * 64 + ":8", item["sha256"] + ":9"]:
            with self.subTest(reference=reference), self.assertRaisesRegex(RuntimeError, "SQL object reference differs"):
                smoke.verify_recovered_object(item, self.objects[item["key"]], reference)


class AcceptanceEvidenceTest(unittest.TestCase):
    def test_success_writes_structured_build_and_recovery_evidence(self):
        def exercise(archive, report):
            report["build"] = {"version": "fixture", "commit": "abc"}
            report["checks"]["objectRecovery"] = {"status": "passed", "downloadedBytesVerified": True}

        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "artifacts/recovery.json"
            with patch.object(smoke, "exercise", side_effect=exercise):
                report = smoke.run_acceptance(Path("fixture.zip"), path)
            self.assertEqual(json.loads(path.read_text()), report)
            self.assertEqual(report["status"], "passed")
            self.assertEqual(report["stage"], "complete")
            self.assertEqual(report["build"]["commit"], "abc")
            self.assertTrue(report["checks"]["objectRecovery"]["downloadedBytesVerified"])
            self.assertGreaterEqual(report["durationSeconds"], 0)

    def test_failure_records_exact_stage_and_preserves_nonzero_failure_without_secret_output(self):
        def exercise(archive, report):
            report["stage"] = "object-recovery/download-and-reconcile"
            raise subprocess.CalledProcessError(1, ["fixture"], output=b"private output", stderr=b"secret")

        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "failure.json"
            with patch.object(smoke, "exercise", side_effect=exercise), self.assertRaises(subprocess.CalledProcessError):
                smoke.run_acceptance(Path("fixture.zip"), path)
            report = json.loads(path.read_text())
            self.assertEqual(report["status"], "failed")
            self.assertEqual(report["stage"], "object-recovery/download-and-reconcile")
            self.assertEqual(report["errorType"], "CalledProcessError")
            self.assertNotIn("private output", path.read_text())
            self.assertNotIn("secret", path.read_text())

    def test_no_report_path_has_no_report_filesystem_side_effect(self):
        with patch.object(smoke, "exercise"), patch.object(smoke.Path, "write_text") as write:
            report = smoke.run_acceptance(Path("fixture.zip"))
        write.assert_not_called()
        self.assertEqual(report["status"], "passed")


if __name__ == "__main__":
    unittest.main()
