#!/usr/bin/env python3
"""Exercise the packaged install/update/rollback lifecycle on an isolated CI Docker host.

Never run this on a shared production host: the release's default ports/container names
are used. All data created here is disposable and removed in finally.
"""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
import re
import time
from pathlib import Path
import subprocess
import sys
import tempfile
import urllib.request
import zipfile


def run(command, directory, capture=False):
    return subprocess.run(command, cwd=directory, check=True, text=True,
                          stdout=subprocess.PIPE if capture else None).stdout


def validate_object_backup(directory, manifest):
    """Check every recovery fixture before touching a destination bucket."""
    directory = Path(directory).resolve()
    if not isinstance(manifest, list) or not manifest:
        raise ValueError("Object recovery manifest must contain objects")
    keys = set()
    for item in manifest:
        if not isinstance(item, dict):
            raise ValueError("Invalid object recovery manifest entry")
        key, size, digest = item.get("key"), item.get("bytes"), item.get("sha256")
        if (not isinstance(key, str) or not key or "\\" in key
                or any(part in ("", ".", "..") for part in key.split("/"))
                or not isinstance(size, int) or isinstance(size, bool) or size < 0
                or not isinstance(digest, str) or not re.fullmatch(r"[0-9a-f]{64}", digest)
                or key in keys):
            raise ValueError("Invalid object recovery manifest entry")
        keys.add(key)
        path = directory / key
        if not path.resolve().is_relative_to(directory) or not path.is_file() or path.is_symlink():
            raise ValueError("Object backup file missing or unsafe: " + key)
        body = path.read_bytes()  # Deliberately small deterministic CI fixtures.
        if len(body) != size or hashlib.sha256(body).hexdigest() != digest:
            raise ValueError("Object backup checksum mismatch: " + key)
    return manifest


def restore_object_backup(directory, manifest, target, existing_targets, create_target, upload):
    """A drill restores only to a new bucket; no merge or overwrite is permitted."""
    validate_object_backup(directory, manifest)
    if target in existing_targets:
        raise ValueError("Object restore target already exists: " + target)
    create_target(target)  # The actual S3 create must also reject an intervening creation.
    for item in manifest:
        upload(target, item["key"], (Path(directory) / item["key"]).read_bytes())


def verify_recovered_object(item, body, reference):
    if len(body) != item["bytes"] or hashlib.sha256(body).hexdigest() != item["sha256"]:
        raise RuntimeError("Downloaded recovery object differs from backup: " + item["key"])
    if reference != f"{item['sha256']}:{item['bytes']}":
        raise RuntimeError("Recovered SQL object reference differs from downloaded object")


def exercise_object_restore(directory, compose, report, project_name="ciphergate-release-smoke"):
    """Recover SQL object references and actual S3 bytes in disposable CI resources."""
    def stage(value):
        report["stage"] = "object-recovery/" + value

    def sql(database, statement):
        return run(compose + ["exec", "-T", "mysql", "sh", "-c",
            'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql -uroot --default-character-set=utf8mb4 '
            '--batch --skip-column-names "$1" -e "$2"', "sh", database, statement], directory, True).strip()

    def mc(*arguments, body=None):
        # Credentials expand only inside the disposable container; never echo them or enable shell tracing.
        command = compose + ["exec", "-T", "minio", "sh", "-c",
            'set -eu; umask 077; test -x /usr/bin/mc; '
            'export MC_CONFIG_DIR=/tmp/ciphergate-recovery-mc; '
            '/usr/bin/mc alias set recovery http://127.0.0.1:9000 '
            '"$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null; exec /usr/bin/mc "$@"',
            "sh", *arguments]
        return subprocess.run(command, cwd=directory, input=body, stdout=subprocess.PIPE,
                              stderr=subprocess.PIPE, check=True, timeout=60).stdout

    def listing(*arguments):
        entries = [json.loads(line) for line in mc("ls", "--json", *arguments).decode().splitlines() if line.strip()]
        if any(item.get("status") != "success" or not isinstance(item.get("key"), str) for item in entries):
            raise RuntimeError("Unexpected S3 recovery listing")
        return entries

    source, target = "ciphergate-recovery-source", "ciphergate-recovery-target"
    source_db, target_db = "ciphergate_object_source", "ciphergate_object_target"
    payloads = {"update.bin": b"CipherGate recovery fixture\x00\xff\n" * 17,
                "releases/update package.bin": bytes(range(256)) * 13,
                "documents/empty.txt": b""}
    manifest = [{"key": key, "bytes": len(body), "sha256": hashlib.sha256(body).hexdigest()}
                for key, body in payloads.items()]
    stage("seed")
    mc("mb", "recovery/" + source)
    for key, body in payloads.items():
        mc("pipe", "recovery/" + source + "/" + key, body=body)
    for database in [source_db, target_db]:
        sql("mysql", f"CREATE DATABASE {database} CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
    sql(source_db, "CREATE TABLE recovery_objects (object_key VARCHAR(255) PRIMARY KEY, sha256 CHAR(64), size_bytes INT)")
    for item in manifest:
        # Only fixed fixture keys and generated hex digests enter this SQL.
        sql(source_db, "INSERT INTO recovery_objects VALUES "
            f"('{item['key']}', '{item['sha256']}', {item['bytes']})")
    stage("backup")
    backup = directory / "backups/object-recovery"
    backup.mkdir(parents=True)
    mc("cp", "--recursive", "recovery/" + source + "/", "/tmp/ciphergate-recovery-backup/files/")
    run(compose + ["cp", "minio:/tmp/ciphergate-recovery-backup/files/.", str(backup)], directory)
    (backup / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    manifest = json.loads((backup / "manifest.json").read_text(encoding="utf-8"))
    validate_object_backup(backup, manifest)
    sql_backup = directory / "backups/object-recovery.sql.gz"
    run([sys.executable, "operations.py", "--project-name", project_name,
         "backup", str(sql_backup), "--database", source_db], directory)
    stage("remove-source")
    mc("rm", "--recursive", "--force", "recovery/" + source)
    if listing("--recursive", "recovery/" + source):
        raise RuntimeError("Recovery source objects were not removed")
    sql(source_db, "DELETE FROM recovery_objects")
    stage("restore")
    run([sys.executable, "operations.py", "--project-name", project_name,
         "restore", str(sql_backup), "--target-database", target_db, "--confirm-database", target_db], directory)
    existing = {item["key"].rstrip("/") for item in listing("recovery")}
    restore_object_backup(backup, manifest, target, existing,
                          lambda bucket: mc("mb", "recovery/" + bucket),
                          lambda bucket, key, body: mc("pipe", "recovery/" + bucket + "/" + key, body=body))
    stage("download-and-reconcile")
    downloaded = {item["key"] for item in listing("--recursive", "recovery/" + target)}
    if downloaded != set(payloads):
        raise RuntimeError("Recovered S3 object inventory differs from backup")
    for item in manifest:
        body = mc("cat", "recovery/" + target + "/" + item["key"])
        reference = sql(target_db, "SELECT CONCAT(sha256, ':', size_bytes) FROM recovery_objects "
                        f"WHERE object_key='{item['key']}'")
        verify_recovered_object(item, body, reference)
    if sql(target_db, "SELECT COUNT(*) FROM recovery_objects") != str(len(manifest)):
        raise RuntimeError("Recovered SQL object inventory differs from backup")
    stage("occupied-target-guard")
    try:
        restore_object_backup(backup, manifest, target, {target},
                              lambda bucket: mc("mb", "recovery/" + bucket),
                              lambda bucket, key, body: mc("pipe", "recovery/" + bucket + "/" + key, body=body))
    except ValueError as error:
        if "already exists" not in str(error):
            raise
    else:
        raise RuntimeError("Object recovery allowed an occupied target")
    report["checks"]["objectRecovery"] = {"status": "passed", "objects": manifest,
        "sourceObjectsRemoved": True, "restoredDatabase": target_db,
        "downloadedBytesVerified": True, "occupiedTargetRejected": True}
    print("Object recovery passed: independent backup → source removal → isolated SQL/S3 restore → verified download.")


def exercise_schema_restore(directory, compose):
    """Use real packaged Flyway migrations and backups; all targets belong to disposable CI."""
    def sql(database, statement):
        return run(compose + ["exec", "-T", "mysql", "sh", "-c",
            'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql -uroot --batch --skip-column-names "$1" -e "$2"',
            "sh", database, statement], directory, True).strip()

    with zipfile.ZipFile(directory / "app/app.jar") as jar:
        versions = sorted({match.group(1) for name in jar.namelist()
                           if (match := re.fullmatch(r"BOOT-INF/classes/db/migration/V([0-9]+)__[^/]+\.sql", name))},
                          key=int)
    if not versions or versions[0] != "1":
        raise RuntimeError("Packaged JAR is missing the V1 migration")
    expected_history = ",".join(versions)

    def verify_history(database, expected):
        actual = sql(database, "SELECT GROUP_CONCAT(version ORDER BY installed_rank SEPARATOR ',') "
                     "FROM flyway_schema_history WHERE success=1 AND type='SQL'")
        if actual != expected:
            raise RuntimeError(f"Unexpected migration history in {database}: {actual}; expected {expected}")
        if sql(database, "SELECT COUNT(*) FROM flyway_schema_history WHERE success=0") != "0":
            raise RuntimeError("Failed migration remains in recovery database")

    operator = run(compose + ["exec", "-T", "mysql", "printenv", "MYSQL_USER"], directory, True).strip()
    if not re.fullmatch(r"[A-Za-z0-9_]+", operator):
        raise RuntimeError("Unexpected isolated database user")
    source, restored = "ciphergate_restore_source", "ciphergate_restore_target"
    for database in [source, restored]:
        sql("mysql", f"CREATE DATABASE {database} CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; "
            f"GRANT ALL ON {database}.* TO '{operator}'@'%'")

    def migrate(database, target=None):
        name = "ciphergate-schema-restore-smoke"
        command = compose + ["run", "--detach", "--no-deps", "--name", name,
            "-e", f"SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/{database}?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"]
        if target:
            command += ["-e", "SPRING_FLYWAY_TARGET=" + target]
        try:
            run(command + ["backend"], directory)
            deadline = time.monotonic() + 180
            while time.monotonic() < deadline:
                probe = subprocess.run(["docker", "exec", name, "wget", "-q", "-T", "5", "-O", "/dev/null",
                                        "http://localhost:8080/actuator/health/readiness"],
                                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=10)
                if probe.returncode == 0:
                    return
                state = run(["docker", "inspect", "--format", "{{.State.Running}}", name], directory, True).strip()
                if state != "true":
                    raise RuntimeError("Recovery migration application exited")
                time.sleep(2)
            raise RuntimeError("Recovery migration did not become ready")
        finally:
            subprocess.run(["docker", "logs", "--tail", "40", name])
            subprocess.run(["docker", "rm", "--force", name], check=True)

    # A real V1 application startup produces both its schema and Flyway checksums.
    migrate(source, "1")
    verify_history(source, "1")
    sql(source, "CREATE TABLE recovery_marker (id INT PRIMARY KEY, value VARCHAR(64)); "
                "INSERT INTO recovery_marker VALUES (1, 'v1-business-data')")
    backup = directory / "backups/restore-v1.sql.gz"
    run([sys.executable, "operations.py", "--project-name", "ciphergate-release-smoke",
         "backup", str(backup), "--database", source], directory)
    migrate(source)
    verify_history(source, expected_history)
    if sql(source, "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() "
                   "AND TABLE_NAME='app_user_license_recharge'") != "1":
        raise RuntimeError("V2 upgrade did not create its ledger")
    # In-place restore is unsafe: a V1 dump cannot remove V2-only tables.
    blocked = subprocess.run([sys.executable, "operations.py", "--project-name", "ciphergate-release-smoke",
        "restore", str(backup), "--target-database", source, "--confirm-database", source], cwd=directory)
    if blocked.returncode == 0:
        raise RuntimeError("Restore allowed a nonempty migrated target")
    run([sys.executable, "operations.py", "--project-name", "ciphergate-release-smoke",
         "restore", str(backup), "--target-database", restored, "--confirm-database", restored], directory)
    if sql(restored, "SELECT value FROM recovery_marker WHERE id=1") != "v1-business-data":
        raise RuntimeError("V1 backup restore lost business data")
    if sql(restored, "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() "
                     "AND TABLE_NAME='app_user_license_recharge'") != "0":
        raise RuntimeError("Isolated V1 restore contains residual V2 tables")
    verify_history(restored, "1")
    migrate(restored)
    verify_history(restored, expected_history)
    if sql(restored, "SELECT COUNT(*) FROM flyway_schema_history WHERE version='2' AND success=1") != "1":
        raise RuntimeError("Restored V1 could not upgrade to V2 exactly once")
    if sql(restored, "SELECT value FROM recovery_marker WHERE id=1") != "v1-business-data":
        raise RuntimeError("Re-upgrade lost restored business data")
    if "3" in versions:
        for column, width in [("purpose", 80), ("email", 254)]:
            actual = sql(restored, "SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS "
                         f"WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='portal_verify_code' AND COLUMN_NAME='{column}'")
            if actual != str(width):
                raise RuntimeError("Restored upgrade did not apply V3 challenge scope")
    print(f"Schema recovery passed: V1 → V{versions[-1]} → isolated V1 restore → V{versions[-1]}, business data preserved.")


def exercise(archive, report=None):
    report = report if report is not None else {"stage": "extract", "checks": {}}
    with tempfile.TemporaryDirectory(prefix="ciphergate-release-smoke-") as temporary:
        directory = Path(temporary)
        with zipfile.ZipFile(archive) as content:
            content.extractall(directory)
        report["build"] = json.loads((directory / "build-info.json").read_text(encoding="utf-8"))
        compose = ["docker", "compose", "--project-directory", str(directory),
                   "--project-name", "ciphergate-release-smoke", "-f", str(directory / "docker-compose.server.yml")]
        def sql(statement):
            return run(compose + ["exec", "-T", "mysql", "sh", "-c",
                'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql -uroot --batch --skip-column-names "$MYSQL_DATABASE" -e "$1"',
                "sh", statement], directory, True).strip()
        def app(command, *arguments):
            run([sys.executable, command, *map(str, arguments)], directory)
        try:
            report["stage"] = "installation-update-rollback"
            app("install.py", "--project-name", "ciphergate-release-smoke")
            configuration = (directory / ".env").read_bytes()
            with urllib.request.urlopen("http://127.0.0.1:5173/api/config/init/status", timeout=10) as response:
                status = json.load(response)
            if status.get("code") != 200 or status.get("data", {}).get("initialized") is not False:
                raise RuntimeError("Fresh install did not expose the initialization wizard")
            # Complete the actual configuration endpoint without depending on GitHub availability.
            body = json.dumps({"clientId": "release-smoke-client", "clientSecret": "release-smoke-secret",
                "redirectUri": "http://127.0.0.1:5173/api/login/oauth2/code/github",
                "frontendUrl": "http://127.0.0.1:5173"}).encode()
            import http.cookiejar
            client = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
            with client.open("http://127.0.0.1:5173/api/csrf", timeout=10) as response:
                csrf = json.load(response)
            request = urllib.request.Request("http://127.0.0.1:5173/api/config/init", data=body,
                                             headers={"Content-Type": "application/json", csrf["headerName"]: csrf["token"]})
            with client.open(request, timeout=15) as response:
                result = json.load(response)
            if result.get("code") != 200:
                raise RuntimeError("First-run configuration failed")
            sql("CREATE TABLE release_smoke_marker (id INT PRIMARY KEY, value VARCHAR(64)); "
                "INSERT INTO release_smoke_marker VALUES (1, 'preserve-on-update')")
            custom = directory / "plugins/operator-owned.txt"
            custom.write_text("operator plugin settings", encoding="utf-8")
            protected = {name: hashlib.sha256((directory / name).read_bytes()).hexdigest()
                         for name in [".env", "docker-compose.server.yml", "frontend/nginx.conf"]}
            app("install.py", "--project-name", "ciphergate-release-smoke")
            if (directory / ".env").read_bytes() != configuration:
                raise RuntimeError("Install retry changed private configuration")
            app("update.py", archive, "--project-name", "ciphergate-release-smoke")
            backup = sorted((directory / "backups").glob("*-update"))[-1]
            if not (backup / "database.sql.gz").is_file():
                raise RuntimeError("Update did not create a database backup")
            app("update.py", "rollback", "--backup", backup)
            for name, digest in protected.items():
                if hashlib.sha256((directory / name).read_bytes()).hexdigest() != digest:
                    raise RuntimeError("Lifecycle changed operator configuration: " + name)
            if custom.read_text() != "operator plugin settings" or sql("SELECT value FROM release_smoke_marker WHERE id=1") != "preserve-on-update":
                raise RuntimeError("Update/rollback lost existing data")
            # A migration after this backup must block application-only rollback.
            sql("INSERT INTO flyway_schema_history (installed_rank, version, description, type, script, installed_by, execution_time, success) "
                "SELECT MAX(installed_rank)+1, '999999', 'smoke-rollback-guard', 'SQL', 'smoke_guard', 'smoke', 0, 1 FROM flyway_schema_history")
            result = subprocess.run([sys.executable, "update.py", "rollback", "--backup", str(backup)], cwd=directory)
            if result.returncode == 0:
                raise RuntimeError("Rollback allowed changed migration history")
            for service in ["backend", "frontend"]:
                if run(compose + ["ps", "--status", "running", "-q", service], directory, True).strip():
                    raise RuntimeError("Unsafe rollback left application running")
            sql("DELETE FROM flyway_schema_history WHERE version='999999' AND description='smoke-rollback-guard'")
            app("update.py", "rollback", "--backup", backup)
            report["checks"]["lifecycle"] = {"status": "passed", "configurationPreserved": True,
                                            "migrationRollbackGuard": True}
            report["stage"] = "schema-recovery"
            exercise_schema_restore(directory, compose)
            report["checks"]["schemaRecovery"] = {"status": "passed", "businessDataPreserved": True}
            exercise_object_restore(directory, compose, report)
            report["stage"] = "cleanup"
            print("Release lifecycle passed: install, initialization, retry, update, rollback, SQL and object recovery.")
        finally:
            try:
                run(compose + ["logs", "--no-color", "--tail", "50", "backend", "frontend"], directory)
            finally:
                # This project and every volume were created by this test in its temporary directory.
                run(compose + ["down", "--volumes", "--remove-orphans"], directory)


def run_acceptance(archive, report_path=None):
    started = time.monotonic()
    report = {"schemaVersion": 1, "startedAt": datetime.now(timezone.utc).isoformat(),
              "status": "running", "stage": "extract", "checks": {}}
    try:
        exercise(archive, report)
        report.update(status="passed", stage="complete")
    except BaseException as error:
        # A process failure can contain command output; retain only its type in public evidence.
        report.update(status="failed", errorType=type(error).__name__)
        raise
    finally:
        report["durationSeconds"] = round(time.monotonic() - started, 3)
        if report_path:
            path = Path(report_path)
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archive", type=Path)
    parser.add_argument("--isolated-ci", action="store_true", required=True,
                        help="Confirm this is a disposable Docker test host")
    args = parser.parse_args()
    run_acceptance(args.archive.resolve(), os.environ.get("CIPHERGATE_ACCEPTANCE_REPORT"))


if __name__ == "__main__":
    main()
