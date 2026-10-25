#!/usr/bin/env python3
"""Database backup/restore against the explicitly selected Compose installation."""
import argparse
from datetime import datetime, timezone
import gzip
import hashlib
import json
import os
import re
from pathlib import Path
import shutil
import subprocess
import sys

COMPOSE = ["docker", "compose", "-f", "docker-compose.server.yml"]


def file_hash(path):
    digest = hashlib.sha256()
    with Path(path).open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def database_name():
    # Read the running container, never interpolate user input into a shell command.
    return subprocess.check_output(COMPOSE + ["exec", "-T", "mysql", "printenv", "MYSQL_DATABASE"], text=True).strip()


def backup(destination, database=None):
    if database is not None and not re.fullmatch(r"[A-Za-z0-9_]{1,64}", database):
        raise ValueError("Invalid backup database identifier")
    destination = Path(destination)
    destination.parent.mkdir(parents=True, exist_ok=True)
    # Fail before starting a process if this would overwrite an existing backup.
    descriptor = os.open(destination, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
    dump = None
    metadata_path = destination.with_suffix(destination.suffix + ".json")
    metadata_created = False
    try:
        with os.fdopen(descriptor, "wb") as output:
            dump = subprocess.Popen(COMPOSE + ["exec", "-T", "mysql", "sh", "-c",
                'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysqldump -uroot --single-transaction '
                '--routines --triggers --events --hex-blob --no-tablespaces --set-gtid-purged=OFF ' +
                ('"$1"' if database else '"$MYSQL_DATABASE"'), *(['sh', database] if database else [])],
                stdout=subprocess.PIPE)
            with gzip.GzipFile(fileobj=output, mode="wb", mtime=0) as compressed:
                shutil.copyfileobj(dump.stdout, compressed)
            if dump.wait() != 0:
                raise RuntimeError("mysqldump failed; incomplete backup removed")
        digest = file_hash(destination)
        metadata = {"database": database if database is not None else database_name(), "sha256": digest,
                    "createdAt": datetime.now(timezone.utc).isoformat(), "file": destination.name}
        with metadata_path.open("x", encoding="utf-8") as sidecar:
            metadata_created = True
            sidecar.write(json.dumps(metadata, indent=2) + "\n")
        print("Backup created:", destination, "sha256:", digest)
    except BaseException:
        if dump is not None and dump.poll() is None:
            dump.terminate()
            dump.wait()
        destination.unlink(missing_ok=True)
        if metadata_created:
            metadata_path.unlink(missing_ok=True)
        raise


def require_empty_database(target):
    if not re.fullmatch(r"[A-Za-z0-9_]{1,64}", target):
        raise ValueError("Restore target must be a valid MySQL database identifier")
    result = subprocess.check_output(COMPOSE + ["exec", "-T", "mysql", "sh", "-c",
        'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql -uroot --batch --skip-column-names -e "$1"',
        "sh", "SELECT (SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='" + target
        + "'), (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='" + target + "')"], text=True).strip()
    if result != "1\t0":
        raise ValueError("Restore requires an existing empty database; create an isolated target and use --target-database")


def restore(source, confirmation, target_database=None):
    source = Path(source)
    metadata = json.loads(source.with_suffix(source.suffix + ".json").read_text(encoding="utf-8"))
    if (not isinstance(metadata, dict)
            or not isinstance(metadata.get("database"), str) or not metadata["database"].strip()
            or not isinstance(metadata.get("sha256"), str)
            or not re.fullmatch(r"[0-9a-f]{64}", metadata["sha256"])):
        raise ValueError("Invalid backup metadata: database and SHA256 checksum are required")
    if file_hash(source) != metadata["sha256"]:
        raise ValueError("Backup checksum mismatch")
    target = target_database if target_database is not None else database_name()
    if confirmation != target:
        raise ValueError("Restore changes the target database; provide --confirm-database " + target)
    # Check all gzip bytes before sending SQL, avoiding a partial restore from corrupt archives.
    with gzip.open(source, "rb") as content:
        while content.read(1024 * 1024):
            pass
    require_empty_database(target)
    process = subprocess.Popen(COMPOSE + ["exec", "-T", "mysql", "sh", "-c",
        'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql -uroot "$1"', "sh", target], stdin=subprocess.PIPE)
    try:
        with gzip.open(source, "rb") as content:
            shutil.copyfileobj(content, process.stdin)
        process.stdin.close()
        if process.wait() != 0:
            raise RuntimeError("Restore failed; inspect the isolated target before restarting applications")
    finally:
        if process.poll() is None:
            process.terminate()
            process.wait()
    print("Restore completed into", target, "(source database:", metadata["database"] + ")")


def compose_command(directory, project_name=None, compose_files=None):
    directory = Path(directory).resolve()
    command = ["docker", "compose", "--project-directory", str(directory),
               "--env-file", str(directory / ".env")]
    if project_name:
        command += ["--project-name", project_name]
    for filename in compose_files or ["docker-compose.server.yml"]:
        path = Path(filename)
        command += ["-f", str(path if path.is_absolute() else directory / path)]
    return command


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--directory", type=Path, default=Path.cwd(),
                        help="Existing installation directory (default: current directory)")
    parser.add_argument("--project-name", help="Existing Compose project name")
    parser.add_argument("--compose-file", action="append", help="Compose file; repeat for installation overrides")
    commands = parser.add_subparsers(dest="command", required=True)
    create = commands.add_parser("backup")
    create.add_argument("destination", type=Path)
    create.add_argument("--database", help="Explicit database for isolated recovery rehearsals")
    load = commands.add_parser("restore")
    load.add_argument("source", type=Path)
    load.add_argument("--confirm-database", required=True)
    load.add_argument("--target-database", help="Existing empty isolated database; never drops existing tables")
    args = parser.parse_args()
    global COMPOSE
    original_directory, original_command = Path.cwd(), COMPOSE
    try:
        directory = args.directory.resolve()
        COMPOSE = compose_command(directory, args.project_name, args.compose_file)
        os.chdir(directory)
        if args.command == "backup":
            if args.database:
                backup(args.destination, args.database)
            else:
                backup(args.destination)
        elif args.target_database:
            restore(args.source, args.confirm_database, args.target_database)
        else:
            restore(args.source, args.confirm_database)
    except (OSError, EOFError, ValueError, RuntimeError, subprocess.CalledProcessError) as error:
        parser.exit(1, f"Operation failed: {error}\n")
    finally:
        os.chdir(original_directory)
        COMPOSE = original_command


if __name__ == "__main__":
    main()
