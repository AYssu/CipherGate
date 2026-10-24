"""Backup/restore process boundaries without Docker or access to any database."""
import gzip
import hashlib
import importlib.util
import io
import json
import os
from pathlib import Path
import tempfile
import sys
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("operations_under_test", ROOT / "scripts/operations.py")
operations = importlib.util.module_from_spec(spec)
spec.loader.exec_module(operations)


class CapturedInput(io.BytesIO):
    def close(self):
        self.sent = self.getvalue()
        super().close()


class FakeProcess:
    def __init__(self, output=b"", code=0):
        self.stdout = io.BytesIO(output)
        self.stdin = CapturedInput()
        self.code = code
        self.returncode = None
        self.terminated = False

    def wait(self):
        self.returncode = self.code
        return self.code

    def poll(self):
        return self.returncode

    def terminate(self):
        self.terminated = True


class BackupOperationTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.destination = Path(self.temporary.name) / "private" / "backup.sql.gz"

    def test_success_creates_private_compressed_dump_and_matching_metadata(self):
        sql = b"CREATE TABLE example (id INT);\nINSERT INTO example VALUES (42);\n"
        process = FakeProcess(sql)
        with patch.object(operations.subprocess, "Popen", return_value=process) as popen, \
                patch.object(operations, "database_name", return_value="ciphergate_test"):
            operations.backup(self.destination)
        self.assertEqual(gzip.decompress(self.destination.read_bytes()), sql)
        metadata = json.loads(self.destination.with_suffix(".gz.json").read_text())
        self.assertEqual(metadata["sha256"], hashlib.sha256(self.destination.read_bytes()).hexdigest())
        self.assertEqual(metadata["database"], "ciphergate_test")
        self.assertEqual(metadata["file"], self.destination.name)
        if os.name != "nt":
            self.assertEqual(self.destination.stat().st_mode & 0o777, 0o600)
        command = popen.call_args.args[0]
        self.assertIn("--single-transaction", command[-1])
        self.assertIn('"$MYSQL_DATABASE"', command[-1])
        self.assertNotIn("shell", popen.call_args.kwargs)

    def test_existing_dump_is_preserved_and_process_never_starts(self):
        self.destination.parent.mkdir()
        self.destination.write_bytes(b"existing backup")
        with patch.object(operations.subprocess, "Popen") as process:
            with self.assertRaises(FileExistsError):
                operations.backup(self.destination)
            process.assert_not_called()
        self.assertEqual(self.destination.read_bytes(), b"existing backup")

    def test_failed_dump_exit_removes_partial_file_and_does_not_write_metadata(self):
        process = FakeProcess(b"partial SQL", code=2)
        with patch.object(operations.subprocess, "Popen", return_value=process):
            with self.assertRaisesRegex(RuntimeError, "mysqldump failed"):
                operations.backup(self.destination)
        self.assertFalse(self.destination.exists())
        self.assertFalse(self.destination.with_suffix(".gz.json").exists())

    def test_process_start_failure_removes_empty_dump(self):
        with patch.object(operations.subprocess, "Popen", side_effect=FileNotFoundError("docker unavailable")):
            with self.assertRaises(FileNotFoundError):
                operations.backup(self.destination)
        self.assertFalse(self.destination.exists())

    def test_interrupted_stream_terminates_process_and_removes_partial_backup(self):
        process = FakeProcess(b"SQL")
        with patch.object(operations.subprocess, "Popen", return_value=process), \
                patch.object(operations.shutil, "copyfileobj", side_effect=OSError("disk full")):
            with self.assertRaises(OSError):
                operations.backup(self.destination)
        self.assertTrue(process.terminated)
        self.assertFalse(self.destination.exists())

    def test_database_lookup_failure_removes_backup_instead_of_reporting_success(self):
        with patch.object(operations.subprocess, "Popen", return_value=FakeProcess(b"SQL")), \
                patch.object(operations, "database_name", side_effect=RuntimeError("container stopped")):
            with self.assertRaises(RuntimeError):
                operations.backup(self.destination)
        self.assertFalse(self.destination.exists())

    def test_existing_metadata_is_preserved_and_new_dump_is_removed(self):
        self.destination.parent.mkdir()
        metadata = self.destination.with_suffix(".gz.json")
        metadata.write_text("preserve original metadata")
        with patch.object(operations.subprocess, "Popen", return_value=FakeProcess(b"SQL")), \
                patch.object(operations, "database_name", return_value="ciphergate_test"):
            with self.assertRaises(FileExistsError):
                operations.backup(self.destination)
        self.assertEqual(metadata.read_text(), "preserve original metadata")
        self.assertFalse(self.destination.exists())

    def test_backup_hashing_streams_instead_of_reading_whole_archive(self):
        with patch.object(operations.subprocess, "Popen", return_value=FakeProcess(b"SQL")), \
                patch.object(operations, "database_name", return_value="ciphergate_test"), \
                patch.object(Path, "read_bytes", side_effect=AssertionError("unbounded file read")):
            operations.backup(self.destination)


class RestoreOperationTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.source = Path(self.temporary.name) / "backup.sql.gz"
        self.sql = b"CREATE TABLE example (id INT);\nINSERT INTO example VALUES (42);\n"
        self.write_backup(gzip.compress(self.sql))

    def write_backup(self, content):
        self.source.write_bytes(content)
        self.source.with_suffix(".gz.json").write_text(json.dumps({
            "database": "original_database", "sha256": hashlib.sha256(content).hexdigest()}))

    def test_success_streams_exact_sql_only_after_target_confirmation(self):
        process = FakeProcess()
        with patch.object(operations, "database_name", return_value="isolated_restore"), \
                patch.object(operations.subprocess, "Popen", return_value=process) as popen:
            operations.restore(self.source, "isolated_restore")
        self.assertEqual(process.stdin.sent, self.sql)
        self.assertTrue(process.stdin.closed)
        self.assertIn('"$MYSQL_DATABASE"', popen.call_args.args[0][-1])
        self.assertNotIn("shell", popen.call_args.kwargs)
        self.assertFalse(process.terminated)

    def test_wrong_target_rejects_before_starting_mysql(self):
        with patch.object(operations, "database_name", return_value="production"), \
                patch.object(operations.subprocess, "Popen") as process:
            with self.assertRaisesRegex(ValueError, "confirm-database production"):
                operations.restore(self.source, "isolated_restore")
            process.assert_not_called()

    def test_checksum_mismatch_rejects_before_querying_target_or_starting_mysql(self):
        self.source.write_bytes(b"modified after backup")
        with patch.object(operations, "database_name") as database, \
                patch.object(operations.subprocess, "Popen") as process:
            with self.assertRaisesRegex(ValueError, "checksum"):
                operations.restore(self.source, "isolated_restore")
            database.assert_not_called()
            process.assert_not_called()

    def test_valid_checksum_but_corrupt_gzip_rejects_before_starting_mysql(self):
        self.write_backup(b"not gzip but checksum matches")
        with patch.object(operations, "database_name", return_value="isolated_restore"), \
                patch.object(operations.subprocess, "Popen") as process:
            with self.assertRaises(OSError):
                operations.restore(self.source, "isolated_restore")
            process.assert_not_called()

    def test_truncated_gzip_rejects_before_any_sql_is_sent(self):
        self.write_backup(gzip.compress(self.sql)[:-5])
        with patch.object(operations, "database_name", return_value="isolated_restore"), \
                patch.object(operations.subprocess, "Popen") as process:
            with self.assertRaises((EOFError, OSError)):
                operations.restore(self.source, "isolated_restore")
            process.assert_not_called()

    def test_mysql_error_exit_is_failure_not_success(self):
        with patch.object(operations, "database_name", return_value="isolated_restore"), \
                patch.object(operations.subprocess, "Popen", return_value=FakeProcess(code=1)):
            with self.assertRaisesRegex(RuntimeError, "Restore failed"):
                operations.restore(self.source, "isolated_restore")

    def test_broken_restore_pipe_terminates_active_mysql_process(self):
        process = FakeProcess()
        with patch.object(operations, "database_name", return_value="isolated_restore"), \
                patch.object(operations.subprocess, "Popen", return_value=process), \
                patch.object(operations.shutil, "copyfileobj", side_effect=BrokenPipeError("mysql exited")):
            with self.assertRaises(BrokenPipeError):
                operations.restore(self.source, "isolated_restore")
        self.assertTrue(process.terminated)

    def test_missing_metadata_never_starts_mysql(self):
        self.source.with_suffix(".gz.json").unlink()
        with patch.object(operations.subprocess, "Popen") as process:
            with self.assertRaises(FileNotFoundError):
                operations.restore(self.source, "isolated_restore")
            process.assert_not_called()

    def test_invalid_metadata_shape_is_rejected_before_accessing_database(self):
        for metadata in [None, [], "text", {}, {"database": 123, "sha256": "0" * 64},
                         {"database": "", "sha256": "0" * 64},
                         {"database": "source", "sha256": "not-a-hash"}]:
            self.source.with_suffix(".gz.json").write_text(json.dumps(metadata))
            with patch.object(operations, "database_name") as database, \
                    patch.object(operations.subprocess, "Popen") as process:
                with self.assertRaisesRegex(ValueError, "Invalid backup metadata"):
                    operations.restore(self.source, "isolated_restore")
                database.assert_not_called()
                process.assert_not_called()

    def test_cli_reports_truncated_gzip_as_clean_operation_failure(self):
        self.write_backup(gzip.compress(self.sql)[:-5])
        with patch.object(sys, "argv", ["operations.py", "restore", str(self.source),
                                       "--confirm-database", "isolated_restore"]), \
                patch.object(operations, "database_name", return_value="isolated_restore"), \
                patch.object(operations.subprocess, "Popen") as process, \
                patch.object(sys, "stderr", new_callable=io.StringIO) as stderr:
            with self.assertRaises(SystemExit) as raised:
                operations.main()
            self.assertEqual(raised.exception.code, 1)
            self.assertIn("Operation failed:", stderr.getvalue())
            self.assertNotIn("Traceback", stderr.getvalue())
            process.assert_not_called()


if __name__ == "__main__":
    unittest.main()
