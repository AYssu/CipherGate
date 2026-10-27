#!/usr/bin/env python3
"""Update an existing release installation, preserving its private configuration."""
import argparse
from contextlib import contextmanager
from datetime import datetime, timezone
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import zipfile
import urllib.request

HERE = Path(__file__).resolve().parent


def module(name, filename):
    spec = importlib.util.spec_from_file_location(name, HERE / filename)
    value = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(value)
    return value


tooling = module("release_tools", "release_tools.py" if (HERE / "release_tools.py").exists() else "project.py")
operations = module("update_operations", "operations.py")


@contextmanager
def installation_lock(directory):
    path = directory / ".ciphergate-update.lock"
    try:
        descriptor = os.open(path, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
    except FileExistsError as error:
        raise RuntimeError("An update lock already exists: " + str(path) +
                           ". Check that no update/rollback is running; after a crash, remove it manually only after checking.") from error
    try:
        with os.fdopen(descriptor, "w") as output:
            output.write(str(os.getpid()) + "\n")
        yield
    finally:
        path.unlink(missing_ok=True)


def private_json(path, value):
    descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, "w", encoding="utf-8") as output:
        json.dump(value, output, indent=2)
        output.write("\n")


def call(command, directory, capture=False):
    if capture:
        return subprocess.check_output(command, cwd=directory, text=True).strip()
    subprocess.run(command, cwd=directory, check=True)


def compose_command(directory, project=None, files=None):
    command = ["docker", "compose", "--project-directory", str(directory)]
    if project:
        command += ["--project-name", project]
    for filename in files or ["docker-compose.server.yml"]:
        path = Path(filename)
        command += ["-f", str(path if path.is_absolute() else directory / path)]
    return command


def fingerprint(command, directory):
    # Hash rows (including failed migrations), rather than only the maximum version.
    sql = "SELECT * FROM flyway_schema_history ORDER BY installed_rank"
    value = call(command + ["exec", "-T", "mysql", "sh", "-c",
        'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql -uroot --batch --raw "$MYSQL_DATABASE" -e "$1"',
        "sh", sql], directory, True)
    return hashlib.sha256(value.encode()).hexdigest()


def smoke_url(directory):
    values = {}
    for line in (directory / ".env").read_text(encoding="utf-8-sig").splitlines():
        if "=" in line and not line.lstrip().startswith("#"):
            key, value = line.split("=", 1)
            values[key.strip()] = value.strip().strip("\"'")
    address = values.get("FRONTEND_BIND_ADDRESS", "127.0.0.1")
    if address in {"0.0.0.0", "::"}:
        address = "127.0.0.1"
    if ":" in address:
        address = "[" + address + "]"
    return "http://" + address + ":" + values.get("FRONTEND_PORT", "5173")


def verify_readiness_route(url):
    """Check preserved ingress BEFORE stopping an old installation or replacing files."""
    try:
        with urllib.request.urlopen(url.rstrip("/") + "/health/ready", timeout=10) as response:
            body = json.load(response)
        if not isinstance(body, dict) or body.get("status") != "UP":
            raise ValueError("Backend is not ready")
    except (OSError, ValueError) as error:
        raise RuntimeError("Existing /health/ready must return JSON status=UP before update. "
                           "Check backend health; legacy Nginx must proxy this exact route to "
                           "/actuator/health/readiness. Existing .env, Compose and real-IP settings "
                           "have NOT been replaced; no application was stopped.") from error


def managed_paths(stage, directory=None):
    paths = ["app/app.jar", "frontend/dist", "docs", "build-info.json", "LICENSE", "NOTICE",
             "setup-env.py", "install.py", "update.py", "operations.py", "release_tools.py",
             "OPERATIONS.md", "DATABASE-UPGRADE.md", ".env.example", "SHA256SUMS"]
    result = [name for name in paths if (stage / name).exists()] + [
        path.relative_to(stage).as_posix() for path in sorted((stage / "plugins").glob("*.jar"))]
    # Remove obsolete official plugin filenames, but retain user-installed plugins.
    manifest = directory / "SHA256SUMS" if directory else None
    if manifest and manifest.is_file():
        for line in manifest.read_text().splitlines():
            if "  " not in line:
                continue
            digest, name = line.split("  ", 1)
            path = Path(name)
            if len(path.parts) == 2 and path.parts[0] == "plugins" and path.suffix == ".jar":
                old = safe_target(directory, name)
                if old.is_file() and tooling.sha256(old) == digest and name not in result:
                    result.append(name)
    if directory:
        previous = {}
        if manifest and manifest.is_file():
            previous = dict(line.split("  ", 1)[::-1] for line in manifest.read_text().splitlines() if "  " in line)
        for plugin in (stage / "plugins").glob("*.jar"):
            name = plugin.relative_to(stage).as_posix()
            old = safe_target(directory, name)
            if old.exists() and (name not in previous or tooling.sha256(old) != previous[name]):
                raise ValueError("Plugin differs from its installed release checksum; preserve/migrate it manually before updating: " + name)
    return result


def safe_target(directory, name):
    target = directory / name
    # Never follow operator-created symlinks while modifying or backing up files.
    for path in [target, *target.parents]:
        if path == directory.parent:
            break
        if path.is_symlink():
            raise ValueError("Managed path must not be a symlink: " + str(path))
    if target.is_dir() and any(path.is_symlink() for path in target.rglob("*")):
        raise ValueError("Managed directory contains symlinks: " + str(target))
    return target


def copy_path(source, destination):
    destination.parent.mkdir(parents=True, exist_ok=True)
    if source.is_dir():
        shutil.copytree(source, destination)
    else:
        shutil.copy2(source, destination)


def remove_path(path):
    if path.is_dir():
        shutil.rmtree(path)
    else:
        path.unlink(missing_ok=True)


def restore_files(directory, backup, names):
    for name in names:
        target = safe_target(directory, name)
        remove_path(target)
        old = backup / "files" / name
        if old.exists():
            copy_path(old, target)


def start(command, directory):
    call(command + ["up", "-d", "--no-deps", "--pull", "never", "--force-recreate",
                    "--wait", "--wait-timeout", "300", "backend", "frontend"], directory)


def _rollback(backup, url=None):
    backup = Path(backup).resolve()
    metadata = json.loads((backup / "update.json").read_text())
    directory = Path(metadata["directory"])
    command = metadata["command"]
    expected = metadata.get("fileHashes", {})
    actual = {path.relative_to(backup / "files").as_posix(): tooling.sha256(path)
              for path in (backup / "files").rglob("*") if path.is_file()}
    if actual != expected:
        raise ValueError("Application backup checksum mismatch; installation was not changed")
    call(command + ["stop", "frontend", "backend"], directory)
    try:
        unchanged = fingerprint(command, directory) == metadata["schemaBefore"]
    except (OSError, subprocess.CalledProcessError):
        unchanged = False
    if not unchanged:
        raise RuntimeError("Database migration history changed or cannot be checked. Applications remain stopped. "
                           "Restore the database backup explicitly before reverting application files: " + str(backup))
    restore_files(directory, backup, metadata["paths"])
    start(command, directory)
    tooling.smoke(url or metadata["url"], metadata.get("previousVersion"))
    print("Rollback completed:", backup)


def _update(archive, directory, project=None, files=None, url=None):
    archive, directory = Path(archive).resolve(), Path(directory).resolve()
    tooling.verify_artifact(archive)
    if not (directory / ".env").is_file():
        raise ValueError("No existing .env; use install.py for a new installation")
    command = compose_command(directory, project, files)
    call(command + ["config", "--quiet"], directory)
    verify_readiness_route(url or smoke_url(directory))
    # Pin the actual running images, not mutable tags or changed Compose defaults.
    runtime = {"services": {}}
    for service in ["backend", "frontend"]:
        container = call(command + ["ps", "-q", service], directory, True)
        if not container or "\n" in container:
            raise ValueError("Expected one running " + service + " container")
        info = json.loads(call(["docker", "inspect", container], directory, True))[0]
        if not info["State"]["Running"]:
            raise ValueError(service + " is not running")
        expected_mounts = ({"/app/app.jar": directory / "app/app.jar"} if service == "backend" else
                           {"/usr/share/nginx/html": directory / "frontend/dist",
                            "/usr/share/nginx/docs": directory / "docs"})
        actual_mounts = {item["Destination"]: item.get("Source") for item in info.get("Mounts", [])}
        for destination, expected in expected_mounts.items():
            if actual_mounts.get(destination) != str(expected):
                raise ValueError("Running " + service + " does not mount this installation at " + destination)
        environment = dict(item.split("=", 1) for item in info["Config"].get("Env", []) if "=" in item)
        environment = {key: value.replace("$", "$$") for key, value in environment.items()}
        runtime["services"][service] = {"image": info["Image"], "environment": environment}
    before = fingerprint(command, directory)
    with tempfile.TemporaryDirectory(prefix="ciphergate-update-") as temporary:
        stage = Path(temporary)
        with zipfile.ZipFile(archive) as bundle:
            bundle.extractall(stage)
        names = managed_paths(stage, directory)
        for name in names:
            safe_target(directory, name)
        backup = directory / "backups" / (datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ") + "-update")
        backup.mkdir(parents=True, mode=0o700)
        os.chmod(backup, 0o700)
        config = backup / "config"
        config.mkdir(mode=0o700)
        config_sources = [directory / ".env", directory / "frontend/nginx.conf"]
        config_sources += [Path(command[index + 1]) for index, argument in enumerate(command[:-1]) if argument == "-f"]
        for index, source in enumerate(config_sources):
            if source.is_file():
                target = config / (str(index) + "-" + source.name)
                descriptor = os.open(target, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
                with source.open("rb") as content, os.fdopen(descriptor, "wb") as output:
                    shutil.copyfileobj(content, output)
        private_json(backup / "runtime.json", runtime)
        pinned = command + ["-f", str(backup / "runtime.json")]
        previous = json.loads((directory / "build-info.json").read_text()).get("version")
        metadata = {"directory": str(directory), "command": pinned, "schemaBefore": before,
                    "paths": names, "previousVersion": previous, "url": url or smoke_url(directory)}
        for name in names:
            old = directory / name
            if old.exists():
                copy_path(old, backup / "files" / name)
        metadata["fileHashes"] = {
            path.relative_to(backup / "files").as_posix(): tooling.sha256(path)
            for path in (backup / "files").rglob("*") if path.is_file()}
        # Stop writers before the database snapshot. Infrastructure and volumes remain untouched.
        call(pinned + ["stop", "frontend", "backend"], directory)
        try:
            metadata["schemaBefore"] = fingerprint(pinned, directory)
            private_json(backup / "update.json", metadata)
            original = Path.cwd()
            try:
                os.chdir(directory)
                operations.COMPOSE = pinned
                operations.backup(backup / "database.sql.gz")
            finally:
                os.chdir(original)
        except BaseException:
            start(pinned, directory)
            raise
        try:
            for name in names:
                target = safe_target(directory, name)
                remove_path(target)
                if (stage / name).exists():
                    copy_path(stage / name, target)
            start(pinned, directory)
            tooling.smoke(metadata["url"], json.loads((stage / "build-info.json").read_text())["version"])
        except BaseException as error:
            print("Update failed; checking whether application rollback is safe.")
            try:
                _rollback(backup)
            except BaseException as recovery:
                raise RuntimeError(f"Update failed: {error}. Recovery requires attention: {recovery}") from error
            raise RuntimeError("Update failed; previous application restored. Backup: " + str(backup)) from error
        print("Update completed. Configuration preserved. Backup:", backup)
        return backup


def update(archive, directory, project=None, files=None, url=None):
    directory = Path(directory).resolve()
    with installation_lock(directory):
        return _update(archive, directory, project, files, url)


def rollback(backup, url=None):
    backup = Path(backup).resolve()
    directory = Path(json.loads((backup / "update.json").read_text())["directory"])
    with installation_lock(directory):
        return _rollback(backup, url)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archive", help="Release ZIP, or 'rollback'")
    default = HERE if (HERE / "docker-compose.server.yml").exists() else HERE.parent / "deploy-bundle"
    parser.add_argument("--directory", type=Path, default=default)
    parser.add_argument("--project-name")
    parser.add_argument("--compose-file", action="append")
    parser.add_argument("--url", help="Public or local frontend URL used for version/readiness verification")
    parser.add_argument("--backup", type=Path, help="Backup directory for rollback")
    args = parser.parse_args()
    try:
        if args.archive == "rollback":
            if not args.backup:
                parser.error("rollback requires --backup")
            rollback(args.backup, args.url)
        else:
            update(args.archive, args.directory, args.project_name, args.compose_file, args.url)
    except (OSError, ValueError, RuntimeError, subprocess.CalledProcessError, zipfile.BadZipFile) as error:
        parser.exit(1, f"Update failed: {error}\n")


if __name__ == "__main__":
    main()
