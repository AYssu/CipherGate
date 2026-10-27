import importlib.util
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
import zipfile
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("update_tool", Path(__file__).resolve().parents[1] / "update.py")
update = importlib.util.module_from_spec(spec)
spec.loader.exec_module(update)


class UpdateTest(unittest.TestCase):
    def test_legacy_plaintext_readiness_fails_before_stop_backup_or_replacement(self):
        import io
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            (directory / ".env").write_text("FRONTEND_PORT=5173")
            with patch.object(update.tooling, "verify_artifact"), patch.object(update, "call") as call, \
                 patch.object(update.urllib.request, "urlopen", return_value=io.BytesIO(b"healthy\n")), \
                 patch.object(update.operations, "backup") as backup:
                with self.assertRaisesRegex(RuntimeError, "no application was stopped"):
                    update.update(directory / "release.zip", directory)
                self.assertEqual(call.call_count, 1)
                self.assertEqual(call.call_args.args[0][-2:], ["config", "--quiet"])
                backup.assert_not_called()
                self.assertFalse((directory / "backups").exists())
                self.assertEqual((directory / ".env").read_text(), "FRONTEND_PORT=5173")

    def test_custom_configuration_and_custom_plugins_are_not_managed(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            for name in [".env", "docker-compose.server.yml", "frontend/nginx.conf", "app/app.jar",
                         "plugins/custom.jar", "plugins/rsa-old.jar"]:
                path = directory / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text("old")
            (directory / "SHA256SUMS").write_text(update.tooling.sha256(directory / "plugins/rsa-old.jar") + "  plugins/rsa-old.jar\n")
            stage = directory / "stage"
            (stage / "plugins").mkdir(parents=True)
            (stage / "plugins/rsa-new.jar").write_text("new")
            names = update.managed_paths(stage, directory)
            self.assertIn("plugins/rsa-old.jar", names)
            self.assertIn("plugins/rsa-new.jar", names)
            for name in [".env", "docker-compose.server.yml", "frontend/nginx.conf", "plugins/custom.jar"]:
                self.assertNotIn(name, names)

    def test_modified_official_plugin_is_preserved(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            (directory / "plugins").mkdir()
            (directory / "plugins/old.jar").write_text("custom content")
            (directory / "SHA256SUMS").write_text("0" * 64 + "  plugins/old.jar\n")
            self.assertNotIn("plugins/old.jar", update.managed_paths(directory / "empty", directory))

    def test_managed_symlinks_rejected_before_writes(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            (directory / "app").symlink_to(directory / "elsewhere")
            with self.assertRaises(ValueError):
                update.safe_target(directory, "app/app.jar")

    def test_rollback_refuses_changed_or_unknown_schema_without_starting_old_app(self):
        for value in ["changed", subprocess.CalledProcessError(1, ["mysql"])]:
            with self.subTest(value=value), tempfile.TemporaryDirectory() as temporary:
                backup = Path(temporary)
                update.private_json(backup / "update.json", {
                    "directory": str(backup), "command": ["docker", "compose"],
                    "schemaBefore": "original", "paths": ["app/app.jar"], "url": "http://local"})
                with patch.object(update, "call") as call, patch.object(update, "fingerprint") as fingerprint, \
                     patch.object(update, "restore_files") as restore, patch.object(update, "start") as start:
                    if isinstance(value, Exception):
                        fingerprint.side_effect = value
                    else:
                        fingerprint.return_value = value
                    with self.assertRaisesRegex(RuntimeError, "remain stopped"):
                        update.rollback(backup)
                    restore.assert_not_called()
                    start.assert_not_called()
                    self.assertEqual(call.call_args.args[0][-3:], ["stop", "frontend", "backend"])

    def test_rollback_restores_files_only_after_schema_check(self):
        with tempfile.TemporaryDirectory() as temporary:
            backup = Path(temporary)
            update.private_json(backup / "update.json", {
                "directory": str(backup), "command": ["docker", "compose"], "schemaBefore": "same",
                "paths": [], "url": "http://local", "previousVersion": "1.0.0"})
            with patch.object(update, "call"), patch.object(update, "fingerprint", return_value="same"), \
                 patch.object(update, "restore_files") as restore, patch.object(update, "start") as start, \
                 patch.object(update.tooling, "smoke") as smoke:
                update.rollback(backup)
                restore.assert_called_once()
                start.assert_called_once()
                smoke.assert_called_once_with("http://local", "1.0.0")

    def test_restore_removes_new_files_and_restores_old_content(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary) / "install"
            backup = Path(temporary) / "backup"
            (directory / "app").mkdir(parents=True)
            (directory / "app/app.jar").write_text("new")
            (directory / "new.py").write_text("new")
            (backup / "files/app").mkdir(parents=True)
            (backup / "files/app/app.jar").write_text("old")
            update.restore_files(directory, backup, ["app/app.jar", "new.py"])
            self.assertEqual((directory / "app/app.jar").read_text(), "old")
            self.assertFalse((directory / "new.py").exists())

    def test_compose_uses_explicit_project_directory_and_override(self):
        command = update.compose_command(Path("/srv/app"), "myapp", ["compose.yml", "/srv/runtime.json"])
        self.assertEqual(command, ["docker", "compose", "--project-directory", "/srv/app", "--project-name", "myapp",
                                   "-f", "/srv/app/compose.yml", "-f", "/srv/runtime.json"])

    def test_recreation_never_pulls_or_restarts_infrastructure(self):
        with patch.object(update, "call") as call:
            update.start(["docker", "compose"], Path("/srv/app"))
            command = call.call_args.args[0]
            self.assertEqual(command[-2:], ["backend", "frontend"])
            self.assertIn("--no-deps", command)
            self.assertEqual(command[command.index("--pull") + 1], "never")

    def test_successful_update_preserves_configuration_and_saves_database(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary) / "installation"
            directory.mkdir()
            for name, body in {".env": "FRONTEND_PORT=5173", "build-info.json": '{"version":"1.0.0"}',
                               "docker-compose.server.yml": "private compose", "frontend/nginx.conf": "private nginx",
                               "app/app.jar": "old jar"}.items():
                path = directory / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(body)
            archive = Path(temporary) / "new.zip"
            with zipfile.ZipFile(archive, "w") as output:
                output.writestr("app/app.jar", "new jar")
                output.writestr("build-info.json", '{"version":"2.0.0"}')
                output.writestr("frontend/dist/index.html", "new frontend")
            events = []
            def fake_call(command, folder, capture=False):
                events.append(command)
                if "inspect" in command:
                    service = command[-1]
                    mounts = ({"/app/app.jar": directory / "app/app.jar"} if service == "backend" else
                              {"/usr/share/nginx/html": directory / "frontend/dist", "/usr/share/nginx/docs": directory / "docs"})
                    return json.dumps([{"State": {"Running": True}, "Image": "sha256:pinned",
                        "Config": {"Env": ["KEEP=private", "SECRET=abc$HOME${USER}"]},
                        "Mounts": [{"Destination": key, "Source": str(value)} for key, value in mounts.items()]}])
                if "ps" in command:
                    return command[-1]
                return ""
            def fake_backup(path):
                path.write_text("database snapshot")
            with patch.object(update, "verify_readiness_route"), patch.object(update.tooling, "verify_artifact"), patch.object(update.tooling, "smoke") as smoke, \
                 patch.object(update, "fingerprint", side_effect=["before-stop", "after-stop"]) as fingerprint, patch.object(update, "call", side_effect=fake_call), \
                 patch.object(update.operations, "backup", side_effect=fake_backup):
                backup = update.update(archive, directory)
                smoke.assert_called_once_with("http://127.0.0.1:5173", "2.0.0")
            self.assertEqual((directory / "app/app.jar").read_text(), "new jar")
            self.assertEqual((directory / ".env").read_text(), "FRONTEND_PORT=5173")
            self.assertEqual((directory / "docker-compose.server.yml").read_text(), "private compose")
            self.assertEqual((directory / "frontend/nginx.conf").read_text(), "private nginx")
            self.assertEqual((backup / "files/app/app.jar").read_text(), "old jar")
            self.assertTrue((backup / "database.sql.gz").exists())
            self.assertEqual(json.loads((backup / "update.json").read_text())["schemaBefore"], "after-stop")
            self.assertEqual(fingerprint.call_count, 2)
            self.assertEqual((backup / "config/0-.env").read_text(), "FRONTEND_PORT=5173")
            self.assertEqual((backup / "config/0-.env").stat().st_mode & 0o777, 0o600)
            runtime = json.loads((backup / "runtime.json").read_text())
            self.assertEqual(runtime["services"]["backend"]["environment"]["SECRET"], "abc$$HOME$${USER}")
            self.assertFalse((directory / ".ciphergate-update.lock").exists())
            self.assertEqual(json.loads((backup / "runtime.json").read_text())["services"]["backend"]["image"], "sha256:pinned")

    def test_failed_update_automatically_recovers_only_when_database_schema_is_unchanged(self):
        for schema_changed in [False, True]:
            with self.subTest(schema_changed=schema_changed), tempfile.TemporaryDirectory() as temporary:
                directory = Path(temporary) / "installation"
                directory.mkdir()
                config = {".env": "FRONTEND_PORT=5173", "docker-compose.server.yml": "operator compose",
                          "frontend/nginx.conf": "operator nginx"}
                for name, body in {**config, "build-info.json": '{"version":"1.0.0"}',
                                   "app/app.jar": "old jar", "frontend/dist/index.html": "old frontend"}.items():
                    path = directory / name
                    path.parent.mkdir(parents=True, exist_ok=True)
                    path.write_text(body)
                archive = Path(temporary) / "new.zip"
                with zipfile.ZipFile(archive, "w") as output:
                    output.writestr("app/app.jar", "new jar")
                    output.writestr("build-info.json", '{"version":"2.0.0"}')
                    output.writestr("frontend/dist/index.html", "new frontend")
                events = []
                def fake_call(command, folder, capture=False):
                    events.append(command)
                    if "inspect" in command:
                        service = command[-1]
                        mounts = ({"/app/app.jar": directory / "app/app.jar"} if service == "backend" else
                                  {"/usr/share/nginx/html": directory / "frontend/dist", "/usr/share/nginx/docs": directory / "docs"})
                        return json.dumps([{"State": {"Running": True}, "Image": "sha256:pinned",
                            "Config": {"Env": ["KEEP=private"]},
                            "Mounts": [{"Destination": key, "Source": str(value)} for key, value in mounts.items()]}])
                    if "ps" in command:
                        return command[-1]
                    return ""
                def fake_backup(path):
                    path.write_text("database snapshot")
                schemas = ["before-stop", "schema-at-backup", "migrated" if schema_changed else "schema-at-backup"]
                message = "remain stopped" if schema_changed else "previous application restored"
                with patch.object(update, "verify_readiness_route"), patch.object(update.tooling, "verify_artifact"), \
                     patch.object(update.tooling, "smoke", side_effect=[RuntimeError("new version readiness failed"), None]) as smoke, \
                     patch.object(update, "fingerprint", side_effect=schemas), \
                     patch.object(update, "call", side_effect=fake_call), \
                     patch.object(update.operations, "backup", side_effect=fake_backup), \
                     patch.object(update, "restore_files", wraps=update.restore_files) as restore:
                    with self.assertRaisesRegex(RuntimeError, message):
                        update.update(archive, directory)
                backups = list((directory / "backups").iterdir())
                self.assertEqual(len(backups), 1)
                backup = backups[0]
                self.assertTrue((backup / "database.sql.gz").exists())
                self.assertEqual(json.loads((backup / "update.json").read_text())["schemaBefore"], "schema-at-backup")
                self.assertEqual((backup / "files/app/app.jar").read_text(), "old jar")
                self.assertFalse((directory / ".ciphergate-update.lock").exists())
                for name, body in config.items():
                    self.assertEqual((directory / name).read_text(), body)
                self.assertEqual(sum("stop" in event for event in events), 2)
                if schema_changed:
                    restore.assert_not_called()
                    self.assertEqual(sum("up" in event for event in events), 1)
                    self.assertEqual(events[-1][-3:], ["stop", "frontend", "backend"])
                    self.assertEqual((directory / "app/app.jar").read_text(), "new jar")
                    self.assertEqual((directory / "frontend/dist/index.html").read_text(), "new frontend")
                    self.assertEqual(smoke.call_count, 1)
                else:
                    restore.assert_called_once()
                    self.assertEqual(sum("up" in event for event in events), 2)
                    self.assertEqual((directory / "app/app.jar").read_text(), "old jar")
                    self.assertEqual((directory / "frontend/dist/index.html").read_text(), "old frontend")
                    self.assertEqual(smoke.call_count, 2)
                    self.assertEqual(smoke.call_args.args, ("http://127.0.0.1:5173", "1.0.0"))

    def test_lock_refuses_concurrent_operations_and_releases_after_failure(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            with self.assertRaisesRegex(ValueError, "test failure"):
                with update.installation_lock(directory):
                    with self.assertRaisesRegex(RuntimeError, "no update/rollback"):
                        with update.installation_lock(directory):
                            self.fail("second operation acquired lock")
                    raise ValueError("test failure")
            self.assertFalse((directory / ".ciphergate-update.lock").exists())

    def test_same_named_modified_plugin_requires_manual_migration(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            (directory / "plugins").mkdir()
            (directory / "plugins/official.jar").write_text("operator custom version")
            (directory / "SHA256SUMS").write_text("0" * 64 + "  plugins/official.jar\n")
            stage = directory / "stage"
            (stage / "plugins").mkdir(parents=True)
            (stage / "plugins/official.jar").write_text("new shipped version")
            with self.assertRaisesRegex(ValueError, "migrate it manually"):
                update.managed_paths(stage, directory)
            self.assertEqual((directory / "plugins/official.jar").read_text(), "operator custom version")

    def test_invalid_archive_fails_before_any_docker_action(self):
        with patch.object(update.tooling, "verify_artifact", side_effect=ValueError("bad archive")), \
             patch.object(update, "call") as call:
            with self.assertRaises(ValueError):
                update._update("invalid.zip", "/missing")
            call.assert_not_called()


if __name__ == "__main__":
    unittest.main()
