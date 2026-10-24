import hashlib
import importlib.util
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
import zipfile

ROOT = Path(__file__).resolve().parents[2]


def load(name, file):
    spec = importlib.util.spec_from_file_location(name, ROOT / "scripts" / file)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


setup = load("setup_environment", "setup-env.py")
project = load("project_tooling", "project.py")
operations = load("operations_tooling", "operations.py")


class EnvironmentTest(unittest.TestCase):
    def test_independent_installations_have_unique_credentials_and_valid_ports(self):
        with tempfile.TemporaryDirectory() as folder:
            a, b = Path(folder) / "a", Path(folder) / "b"
            setup.generate(ROOT / ".env.server.example", a)
            setup.generate(ROOT / ".env.server.example", b)
            first, second = setup.read_env(a), setup.read_env(b)
            setup.validate(first)
            setup.validate(second)
            for key in setup.SECRET_KEYS:
                self.assertNotEqual(first[key], second[key])
            self.assertEqual(first["SERVICE_BIND_ADDRESS"], "127.0.0.1")

    def test_existing_credentials_are_never_overwritten(self):
        with tempfile.TemporaryDirectory() as folder:
            output = Path(folder) / ".env"
            output.write_text("keep private config")
            with self.assertRaises(FileExistsError):
                setup.generate(ROOT / ".env.server.example", output)
            self.assertEqual(output.read_text(), "keep private config")

    def test_invalid_ports_duplicate_keys_and_weak_jwt_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            output = Path(folder) / ".env"
            setup.generate(ROOT / ".env.server.example", output)
            valid = setup.read_env(output)
            for update in [{"MYSQL_PORT": "83306"}, {"MYSQL_PORT": "0"},
                           {"MYSQL_PORT": valid["BACKEND_PORT"]}, {"PORTAL_JWT_SECRET": "weak"}]:
                with self.assertRaises(ValueError):
                    setup.validate({**valid, **update})
            output.write_text("A=one\nA=two\n")
            with self.assertRaises(ValueError):
                setup.read_env(output)


class ArtifactTest(unittest.TestCase):
    def archive(self, folder, extra=None, tamper=False):
        files = {n: b"test" for n in ["setup-env.py", "operations.py", "app/app.jar", "build-info.json",
            "OPERATIONS.md", "DATABASE-UPGRADE.md", "docker-compose.server.yml"]}
        files[".env.example"] = (ROOT / ".env.server.example").read_bytes()
        files.update(extra or {})
        manifest = "".join(f"{hashlib.sha256(body).hexdigest()}  {name}\n" for name, body in files.items())
        if tamper: files["app/app.jar"] = b"changed after manifest"
        files["SHA256SUMS"] = manifest.encode()
        output = Path(folder) / "test.zip"
        with zipfile.ZipFile(output, "w") as archive:
            for name, body in files.items(): archive.writestr(name, body)
        return output

    def test_valid_complete_artifact(self):
        with tempfile.TemporaryDirectory() as folder:
            project.verify_artifact(self.archive(folder))

    def test_public_archive_rejects_private_env_and_traversal(self):
        with tempfile.TemporaryDirectory() as folder:
            for name in [".env", ".env.server", "nested/.env", "../escape", "/absolute"]:
                with self.assertRaises(ValueError):
                    project.verify_artifact(self.archive(folder, {name: b"private"}))

    def test_public_template_rejects_embedded_password(self):
        with tempfile.TemporaryDirectory() as folder:
            with self.assertRaises(ValueError):
                project.verify_artifact(self.archive(folder, {".env.example": b"MYSQL_PASSWORD=accidentally-published"}))

    def test_tampering_fails_checksums(self):
        with tempfile.TemporaryDirectory() as folder:
            with self.assertRaises(ValueError):
                project.verify_artifact(self.archive(folder, tamper=True))

    def test_smoke_requires_readiness_api_and_matching_version(self):
        payloads = [b'{"status":"UP"}', b'{"code":200}', b'{"version":"1.2.3"}', b'{"version":"1.2.3"}']
        with patch.object(project.urllib.request, "urlopen", side_effect=[io.BytesIO(p) for p in payloads]) as mock:
            # HTTPResponse supports .status; BytesIO below supplies it explicitly.
            responses = [io.BytesIO(p) for p in payloads]
            for response in responses: response.status = 200
            mock.side_effect = responses
            project.smoke("http://localhost:5173", "v1.2.3", attempts=1)
            self.assertEqual(mock.call_count, 4)
        responses = [io.BytesIO(p) for p in payloads]
        for response in responses: response.status = 200
        with patch.object(project.urllib.request, "urlopen", side_effect=responses):
            with self.assertRaises(ValueError): project.smoke("http://localhost:5173", "9.9.9", attempts=1)

    def test_smoke_rejects_mixed_frontend_backend_release_versions(self):
        payloads = [b'{"status":"UP"}', b'{"code":200}', b'{"version":"1.2.3"}', b'{"version":"1.2.2"}']
        responses = [io.BytesIO(p) for p in payloads]
        for response in responses: response.status = 200
        with patch.object(project.urllib.request, "urlopen", side_effect=responses):
            with self.assertRaisesRegex(ValueError, "Frontend and backend"):
                project.smoke("http://localhost:5173", "1.2.3", attempts=1)


class RestoreTest(unittest.TestCase):
    def test_wrong_target_confirmation_blocks_restore_process(self):
        import gzip
        with tempfile.TemporaryDirectory() as folder:
            source = Path(folder) / "backup.sql.gz"
            source.write_bytes(gzip.compress(b"SELECT 1;"))
            source.with_suffix(".gz.json").write_text(json.dumps({
                "database": "source", "sha256": hashlib.sha256(source.read_bytes()).hexdigest()}))
            with patch.object(operations, "database_name", return_value="isolated_restore"), patch.object(operations.subprocess, "Popen") as process:
                with self.assertRaises(ValueError): operations.restore(source, "wrong_database")
                process.assert_not_called()

    def test_corrupt_backup_blocks_restore_process(self):
        with tempfile.TemporaryDirectory() as folder:
            source = Path(folder) / "backup.sql.gz"
            source.write_bytes(b"corrupt")
            source.with_suffix(".gz.json").write_text(json.dumps({"database": "source", "sha256": "wrong"}))
            with patch.object(operations.subprocess, "Popen") as process:
                with self.assertRaises(ValueError): operations.restore(source, "target")
                process.assert_not_called()


if __name__ == "__main__":
    unittest.main()
