import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("install_tooling", ROOT / "scripts/install.py")
install = importlib.util.module_from_spec(spec)
spec.loader.exec_module(install)


class InstallTest(unittest.TestCase):
    def bundle(self, directory):
        required = [".env.example", "app/app.jar", "docker-compose.server.yml", "build-info.json",
                    "frontend/dist/index.html", "frontend/nginx.conf", "setup-env.py", "install.py",
                    "release_tools.py", "LICENSE", "NOTICE"]
        for name in required:
            path = directory / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes((ROOT / ".env.server.example").read_bytes() if name == ".env.example" else b"release file")
        self.write_manifest(directory, required)

    def write_manifest(self, directory, names):
        (directory / "SHA256SUMS").write_text("".join(
            hashlib.sha256((directory / name).read_bytes()).hexdigest() + "  " + name + "\n" for name in names))

    def test_first_install_creates_private_http_configuration_without_default_credentials(self):
        with tempfile.TemporaryDirectory() as folder:
            directory = Path(folder)
            self.bundle(directory)
            values = install.prepare_environment(directory)
            self.assertEqual(values["THIRD_PARTY_PUBLIC_BASE_URL"], "http://127.0.0.1:5173")
            self.assertEqual(values["APP_SESSION_COOKIE_SECURE"], "false")
            self.assertTrue(values["PORTAL_JWT_SECRET"])
            self.assertEqual((directory / ".env").stat().st_mode & 0o777, 0o600)
            before = (directory / ".env").read_bytes()
            install.prepare_environment(directory)
            self.assertEqual((directory / ".env").read_bytes(), before)
            with self.assertRaisesRegex(ValueError, "preserved"):
                install.prepare_environment(directory, "https://example.com")
            self.assertEqual((directory / ".env").read_bytes(), before)

    def test_https_sets_both_download_and_backend_origins_and_secure_cookie(self):
        with tempfile.TemporaryDirectory() as folder:
            directory = Path(folder)
            self.bundle(directory)
            values = install.prepare_environment(directory, "https://example.com/")
            self.assertEqual(values["APP_BACKEND_PUBLIC_BASE_URL"], "https://example.com")
            self.assertEqual(values["THIRD_PARTY_PUBLIC_BASE_URL"], "https://example.com")
            self.assertEqual(values["APP_SESSION_COOKIE_SECURE"], "true")

    def test_rejects_ambiguous_or_credential_urls(self):
        for value in ["ftp://example.com", "https://me:secret@example.com", "https://example.com/path",
                      "https://example.com?token=abc", "https://example.com/#fragment", "https://example.com:abc"]:
            with self.subTest(value=value), self.assertRaises(ValueError):
                install.normalize_url(value)

    def test_checksum_detects_corruption_before_docker_or_config_generation(self):
        with tempfile.TemporaryDirectory() as folder:
            directory = Path(folder)
            self.bundle(directory)
            install.verify_directory(directory)
            (directory / "app/app.jar").write_bytes(b"corrupted")
            with patch.object(install, "run") as run, self.assertRaisesRegex(ValueError, "checksum mismatch"):
                install.install(directory)
            run.assert_not_called()
            self.assertFalse((directory / ".env").exists())

    def test_manifest_rejects_traversal_duplicates_and_omitted_required_files(self):
        for contents in ["0" * 64 + "  ../private\n", "0" * 64 + "  .env.example\n" + "0" * 64 + "  .env.example\n", ""]:
            with tempfile.TemporaryDirectory() as folder:
                directory = Path(folder)
                self.bundle(directory)
                (directory / "SHA256SUMS").write_text(contents)
                with self.assertRaises(ValueError):
                    install.verify_directory(directory)

    def test_refuses_container_from_another_directory_even_with_same_project_name(self):
        config = {"name": "ciphergate", "services": {"backend": {"container_name": "ciphergate-backend"}}}
        foreign = [{"Config": {"Labels": {"com.docker.compose.project": "ciphergate",
                                            "com.docker.compose.project.working_dir": "/production"}}}]
        with patch.object(install, "run", side_effect=["abc\n", json.dumps(foreign)]):
            with self.assertRaisesRegex(ValueError, "another installation"):
                install.ensure_container_ownership(config, Path("/test"))
        foreign[0]["Config"]["Labels"]["com.docker.compose.project.working_dir"] = "/test"
        with patch.object(install, "run", side_effect=["abc\n", json.dumps(foreign)]):
            install.ensure_container_ownership(config, Path("/test"))

    def test_success_checks_versions_after_compose_health_and_supports_overrides(self):
        from types import SimpleNamespace
        with tempfile.TemporaryDirectory() as folder:
            directory = Path(folder)
            self.bundle(directory)
            (directory / "build-info.json").write_text(json.dumps({"version": "1.2.3"}))
            names = [line.split("  ", 1)[1] for line in (directory / "SHA256SUMS").read_text().splitlines()]
            self.write_manifest(directory, names)
            real_load = install.load_tool
            smoke_calls = []
            def load(name, filename):
                if name == "ciphergate_release":
                    return SimpleNamespace(smoke=lambda *args, **kwargs: smoke_calls.append((args, kwargs)))
                return real_load(name, filename)
            with patch.object(install, "load_tool", side_effect=load), patch.object(
                    install, "run", side_effect=[None, None, json.dumps({"name": "isolated", "services": {}}), None]) as run:
                install.install(directory, "https://example.com", ["docker-compose.server.yml", "isolated.json"], "isolated")
            command = run.call_args_list[-1].args[0]
            self.assertIn("isolated.json", command)
            self.assertIn("--wait", command)
            self.assertEqual(command[command.index("--project-name") + 1], "isolated")
            self.assertEqual(smoke_calls[0][0], ("http://127.0.0.1:5173", "1.2.3"))

    def test_compose_failure_retains_configuration_and_does_not_cleanup_data(self):
        import subprocess
        with tempfile.TemporaryDirectory() as folder:
            directory = Path(folder)
            self.bundle(directory)
            with patch.object(install, "run", side_effect=[None, None, subprocess.CalledProcessError(1, ["docker", "compose", "config"])]), self.assertRaises(subprocess.CalledProcessError):
                install.install(directory)
            self.assertTrue((directory / ".env").exists())


if __name__ == "__main__":
    unittest.main()
