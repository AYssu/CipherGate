#!/usr/bin/env python3
"""Exercise the packaged install/update/rollback lifecycle on an isolated CI Docker host.

Never run this on a shared production host: the release's default ports/container names
are used. All data created here is disposable and removed in finally.
"""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import urllib.request
import zipfile


def run(command, directory, capture=False):
    return subprocess.run(command, cwd=directory, check=True, text=True,
                          stdout=subprocess.PIPE if capture else None).stdout


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
            request = urllib.request.Request("http://127.0.0.1:5173/api/config/init", data=body,
                                             headers={"Content-Type": "application/json"})
            with urllib.request.urlopen(request, timeout=15) as response:
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
