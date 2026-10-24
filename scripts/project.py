#!/usr/bin/env python3
"""Cross-platform package and artifact verification, using only Python's stdlib."""
import argparse
from datetime import datetime, timezone
import hashlib
import importlib.util
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import subprocess
import sys
import time
import urllib.error
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def run(command, cwd=ROOT, env=None):
    print("+", " ".join(map(str, command)), flush=True)
    subprocess.run(command, cwd=cwd, env=env, check=True)


def git(*args):
    return subprocess.check_output(["git", *args], cwd=ROOT, text=True).strip()


def build_version():
    version = os.environ.get("RELEASE_VERSION", "").removeprefix("v")
    if version and not re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+(?:-[A-Za-z0-9.-]+)?", version):
        raise ValueError("RELEASE_VERSION must be a semantic version")
    return version or "0.0.0-dev." + git("rev-parse", "--short=12", "HEAD")


def sha256(path):
    digest = hashlib.sha256()
    with Path(path).open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def package():
    version = build_version()
    environment = dict(os.environ, RELEASE_VERSION=version)
    gradle = ["cmd", "/c", "gradlew.bat"] if os.name == "nt" else ["./gradlew"]
    npm = "npm.cmd" if os.name == "nt" else "npm"
    run([*gradle, "clean", "bootJar", ":plugins:rsa-crypto-plugin:clean", ":plugins:rsa-crypto-plugin:jar", ":plugins:example-function-plugin:clean", ":plugins:example-function-plugin:jar", "--no-daemon"], env=environment)
    for folder in ["frontend", "docs"]:
        run([npm, "ci", "--no-audit", "--no-fund"], cwd=ROOT / folder)
        run([npm, "run", "build"], cwd=ROOT / folder)
    bundle = ROOT / "deploy-bundle"
    if bundle.exists():
        shutil.rmtree(bundle)
    bundle.mkdir()
    (bundle / "app").mkdir()
    jars = [p for p in (ROOT / "build/libs").glob("*.jar") if not p.name.endswith("-plain.jar")]
    if len(jars) != 1:
        raise ValueError("Expected exactly one runnable backend JAR")
    shutil.copy2(jars[0], bundle / "app/app.jar")
    shutil.copytree(ROOT / "frontend/dist", bundle / "frontend/dist")
    shutil.copy2(ROOT / "frontend/nginx.conf", bundle / "frontend/nginx.conf")
    shutil.copytree(ROOT / "docs/.vitepress/dist", bundle / "docs")
    for source, target in [
        ("docker-compose.server.yml", "docker-compose.server.yml"),
        (".env.server.example", ".env.example"),
        ("scripts/setup-env.py", "setup-env.py"),
        ("scripts/operations.py", "operations.py"),
        ("docs/database/README.md", "DATABASE-UPGRADE.md"),
        ("docs/operations.md", "OPERATIONS.md"),
    ]:
        shutil.copy2(ROOT / source, bundle / target)
    for license_name in ["LICENSE", "NOTICE"]:
        if (ROOT / license_name).exists():
            shutil.copy2(ROOT / license_name, bundle / license_name)
    (bundle / "plugins").mkdir()
    for plugin in ["rsa-crypto-plugin", "example-function-plugin"]:
        outputs = list((ROOT / "plugins" / plugin / "build/libs").glob("*.jar"))
        if len(outputs) != 1:
            raise ValueError("Expected exactly one JAR for " + plugin)
        shutil.copy2(outputs[0], bundle / "plugins" / outputs[0].name)
    info = {
        "version": version, "commit": git("rev-parse", "HEAD"),
        "dirty": bool(git("status", "--porcelain")),
        "builtAt": datetime.now(timezone.utc).isoformat(),
    }
    (bundle / "build-info.json").write_text(json.dumps(info, indent=2) + "\n", encoding="utf-8")
    # Public metadata lets operators verify which frontend/JAR are paired without admin credentials.
    shutil.copy2(bundle / "build-info.json", bundle / "frontend/dist/build-info.json")
    files = sorted(p for p in bundle.rglob("*") if p.is_file())
    (bundle / "SHA256SUMS").write_text("".join(f"{sha256(p)}  {p.relative_to(bundle).as_posix()}\n" for p in files), encoding="utf-8")
    archive = ROOT / "ciphergate-deploy.zip"
    with zipfile.ZipFile(archive, "w", zipfile.ZIP_DEFLATED) as output:
        for path in sorted(bundle.rglob("*")):
            if path.is_file():
                output.write(path, path.relative_to(bundle).as_posix())
    verify_artifact(archive)
    (ROOT / "ciphergate-deploy.zip.sha256").write_text(f"{sha256(archive)}  {archive.name}\n", encoding="utf-8")
    print("Built", archive, "version", version)
    print("On installation: python3 setup-env.py ; read OPERATIONS.md before starting or upgrading.")


def verify_artifact(archive):
    with zipfile.ZipFile(archive) as content:
        names = content.namelist()
        if len(names) != len(set(names)):
            raise ValueError("Duplicate archive entry")
        required = {".env.example", "setup-env.py", "operations.py", "app/app.jar", "build-info.json", "SHA256SUMS", "OPERATIONS.md", "DATABASE-UPGRADE.md", "docker-compose.server.yml"}
        if not required.issubset(names):
            raise ValueError("Incomplete artifact: " + str(required - set(names)))
        for name in names:
            path = PurePosixPath(name)
            if path.is_absolute() or ".." in path.parts or "\\" in name:
                raise ValueError("Unsafe archive path")
            if path.name in {".env", ".env.server", ".env.server.local"}:
                raise ValueError("Private configuration packaged: " + name)
        for line in content.read(".env.example").decode().splitlines():
            if "=" not in line or line.lstrip().startswith("#"):
                continue
            key, value = line.split("=", 1)
            if ("PASSWORD" in key or "SECRET" in key or key == "MINIO_ACCESS_KEY") and value.strip():
                raise ValueError("Nonempty credential in public configuration: " + key)
        checksums = content.read("SHA256SUMS").decode().splitlines()
        listed = set()
        for line in checksums:
            digest, name = line.split("  ", 1)
            if name in listed or name == "SHA256SUMS":
                raise ValueError("Invalid checksum entry")
            listed.add(name)
            if hashlib.sha256(content.read(name)).hexdigest() != digest:
                raise ValueError("Checksum mismatch: " + name)
        if listed != {n for n in names if not n.endswith("/") and n != "SHA256SUMS"}:
            raise ValueError("Incomplete checksum manifest")
    print("Artifact verified: paths, required files, no private env, checksums.")


def smoke(base_url, version=None, attempts=30, delay=2):
    if not base_url.startswith(("http://", "https://")):
        raise ValueError("URL must use http:// or https://")
    base_url = base_url.rstrip("/")
    for attempt in range(attempts):
        try:
            with urllib.request.urlopen(base_url + "/health/ready", timeout=5) as response:
                if json.load(response).get("status") != "UP":
                    raise ValueError("Backend not ready")
            with urllib.request.urlopen(base_url + "/api/config/init/status", timeout=5) as response:
                if response.status != 200:
                    raise ValueError("Public API failed")
            with urllib.request.urlopen(base_url + "/build-info.json", timeout=5) as response:
                info = json.load(response)
            with urllib.request.urlopen(base_url + "/api/config/public/build-info", timeout=5) as response:
                backend = json.load(response)
            if not isinstance(info, dict) or not isinstance(info.get("version"), str) or not info["version"]:
                raise ValueError("Frontend build metadata is missing a version")
            if not isinstance(backend, dict) or backend.get("version") != info["version"]:
                raise ValueError("Frontend and backend deployed versions do not match")
            if version and info.get("version") != version.removeprefix("v"):
                raise ValueError("Deployed version mismatch")
            print("Smoke check passed:", json.dumps(info, ensure_ascii=False))
            return
        except (OSError, ValueError, urllib.error.URLError):
            if attempt + 1 == attempts:
                raise
            time.sleep(delay)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("package")
    check = commands.add_parser("verify-artifact")
    check.add_argument("archive", type=Path)
    probe = commands.add_parser("smoke")
    probe.add_argument("url")
    probe.add_argument("--version")
    args = parser.parse_args()
    try:
        if args.command == "package": package()
        elif args.command == "verify-artifact": verify_artifact(args.archive)
        elif args.command == "smoke": smoke(args.url, args.version)
    except (OSError, ValueError, subprocess.CalledProcessError, zipfile.BadZipFile) as error:
        parser.exit(1, f"Project command failed: {error}\n")


if __name__ == "__main__":
    main()
