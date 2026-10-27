#!/usr/bin/env python3
"""Exercise the packaged install/update/rollback lifecycle on an isolated CI Docker host.

Never run this on a shared production host: the release's default ports/container names
are used. All data created here is disposable and removed in finally.
"""
import argparse
import hashlib
import json
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


def exercise(archive):
    with tempfile.TemporaryDirectory(prefix="ciphergate-release-smoke-") as temporary:
        directory = Path(temporary)
        with zipfile.ZipFile(archive) as content:
            content.extractall(directory)
        compose = ["docker", "compose", "--project-directory", str(directory),
                   "--project-name", "ciphergate-release-smoke", "-f", str(directory / "docker-compose.server.yml")]
        def sql(statement):
            return run(compose + ["exec", "-T", "mysql", "sh", "-c",
                'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql -uroot --batch --skip-column-names "$MYSQL_DATABASE" -e "$1"',
                "sh", statement], directory, True).strip()
        def app(command, *arguments):
            run([sys.executable, command, *map(str, arguments)], directory)
        try:
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
            exercise_schema_restore(directory, compose)
            print("Release lifecycle passed: fresh database, initialization, retry, backup, update, rollback, migration guard.")
        finally:
            try:
                run(compose + ["logs", "--no-color", "--tail", "50", "backend", "frontend"], directory)
            finally:
                # This project and every volume were created by this test in its temporary directory.
                run(compose + ["down", "--volumes", "--remove-orphans"], directory)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archive", type=Path)
    parser.add_argument("--isolated-ci", action="store_true", required=True,
                        help="Confirm this is a disposable Docker test host")
    args = parser.parse_args()
    exercise(args.archive.resolve())


if __name__ == "__main__":
    main()
