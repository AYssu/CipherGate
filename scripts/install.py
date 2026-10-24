#!/usr/bin/env python3
"""Install an extracted CipherGate release without overwriting private configuration."""
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path, PurePosixPath
import re
import subprocess
import sys
from urllib.parse import urlsplit


def load_tool(name, filename):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).resolve().parent / filename)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def verify_directory(directory):
    """Check all shipped files; installation-local .env/logs may also exist."""
    directory = Path(directory).resolve()
    listed = set()
    for line in (directory / "SHA256SUMS").read_text(encoding="utf-8").splitlines():
        digest, name = line.split("  ", 1)
        path = PurePosixPath(name)
        if (not re.fullmatch(r"[0-9a-f]{64}", digest) or not name or name in listed
                or path.is_absolute() or ".." in path.parts or "\\" in name
                or name == "SHA256SUMS"):
            raise ValueError("Invalid checksum entry")
        listed.add(name)
        target = directory / name
        if target.is_symlink() or not target.resolve().is_relative_to(directory):
            raise ValueError("Unsafe release path: " + name)
        actual = hashlib.sha256()
        with target.open("rb") as stream:
            for block in iter(lambda: stream.read(1024 * 1024), b""):
                actual.update(block)
        if actual.hexdigest() != digest:
            raise ValueError("Release checksum mismatch: " + name)
    required = {".env.example", "app/app.jar", "docker-compose.server.yml", "build-info.json",
                "frontend/dist/index.html", "frontend/nginx.conf", "setup-env.py", "install.py",
                "release_tools.py", "LICENSE", "NOTICE"}
    if not required <= listed:
        raise ValueError("Incomplete release manifest: " + ", ".join(sorted(required - listed)))


def normalize_url(value):
    parsed = urlsplit(value)
    if (parsed.scheme not in {"http", "https"} or not parsed.hostname
            or parsed.username or parsed.password or parsed.query or parsed.fragment
            or parsed.path not in {"", "/"} or any(c.isspace() for c in value)):
        raise ValueError("Public URL must be an HTTP(S) origin, for example https://example.com")
    try:
        parsed.port
    except ValueError as error:
        raise ValueError("Invalid public URL port") from error
    return value.rstrip("/")


def prepare_environment(directory, public_url=None):
    setup = load_tool("ciphergate_setup", "setup-env.py")
    destination = directory / ".env"
    if destination.exists():
        values = setup.read_env(destination)
        setup.validate(values)
        if public_url and any(values.get(key) != public_url for key in
                              ["APP_BACKEND_PUBLIC_BASE_URL", "THIRD_PARTY_PUBLIC_BASE_URL"]):
            raise ValueError("Existing .env preserved. Edit its public URLs explicitly before retrying")
        return values
    template = setup.read_env(directory / ".env.example")
    public_url = normalize_url(public_url or "http://127.0.0.1:" + template["FRONTEND_PORT"])
    # Let setup-env generate secrets into a temporary exclusive file; publish once using O_EXCL.
    import tempfile
    with tempfile.TemporaryDirectory(prefix="ciphergate-config-") as folder:
        generated = Path(folder) / ".env"
        setup.generate(directory / ".env.example", generated)
        values = setup.read_env(generated)
    values.update(APP_BACKEND_PUBLIC_BASE_URL=public_url, THIRD_PARTY_PUBLIC_BASE_URL=public_url,
                  APP_SESSION_COOKIE_SECURE=str(public_url.startswith("https://")).lower())
    descriptor = os.open(destination, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, "w", encoding="utf-8", newline="\n") as output:
        output.write("# Private installation settings. Preserve this file on upgrade.\n")
        for key, value in values.items():
            output.write(f"{key}={value}\n")
    return values


def smoke_url(values):
    address = values.get("FRONTEND_BIND_ADDRESS", "127.0.0.1")
    address = {"0.0.0.0": "127.0.0.1", "::": "::1"}.get(address, address)
    if ":" in address and not address.startswith("["):
        address = "[" + address + "]"
    return "http://" + address + ":" + values["FRONTEND_PORT"]


def run(command, directory, capture=False):
    result = subprocess.run(command, cwd=directory, check=True, text=True,
                            stdout=subprocess.PIPE if capture else None)
    return result.stdout if capture else None


def ensure_container_ownership(configuration, directory):
    for service in configuration.get("services", {}).values():
        name = service.get("container_name")
        if not name:
            continue
        existing = run(["docker", "ps", "-aq", "--filter", "name=^/" + name + "$"], directory, True).strip()
        if not existing:
            continue
        containers = json.loads(run(["docker", "inspect", existing], directory, True))
        for container in containers:
            labels = container.get("Config", {}).get("Labels", {}) or {}
            if (labels.get("com.docker.compose.project.working_dir") != str(directory)
                    or labels.get("com.docker.compose.project") != configuration.get("name")):
                raise ValueError("Container " + name + " belongs to another installation; use its update script")


def install(directory, public_url=None, compose_files=(), project_name=None, wait_timeout=600):
    directory = Path(directory).resolve()
    if sys.version_info < (3, 12):
        raise ValueError("Python 3.12 or newer is required")
    public_url = normalize_url(public_url) if public_url else None
    verify_directory(directory)
    run(["docker", "compose", "version"], directory)
    run(["docker", "info", "--format", "{{.ServerVersion}}"], directory)
    values = prepare_environment(directory, public_url)
    command = ["docker", "compose", "--project-directory", str(directory), "--env-file", str(directory / ".env")]
    if project_name:
        command += ["--project-name", project_name]
    for file in compose_files or ["docker-compose.server.yml"]:
        command += ["-f", str(file)]
    configuration = json.loads(run(command + ["config", "--format", "json"], directory, True))
    ensure_container_ownership(configuration, directory)
    # Own the bind-mount directory before Docker creates it as root.
    (directory / "logs").mkdir(mode=0o700, exist_ok=True)
    run(command + ["up", "-d", "--wait", "--wait-timeout", str(wait_timeout)], directory)
    tools_file = "release_tools.py" if (Path(__file__).parent / "release_tools.py").exists() else "project.py"
    release = load_tool("ciphergate_release", tools_file)
    info = json.loads((directory / "build-info.json").read_text(encoding="utf-8"))
    local_url = smoke_url(values)
    release.smoke(local_url, info["version"], attempts=5, delay=2)
    print("Installation ready:", public_url or values.get("APP_BACKEND_PUBLIC_BASE_URL") or local_url)
    print("First use: create your own GitHub OAuth App, then complete the site initialization form.")
    origin = public_url or values.get("APP_BACKEND_PUBLIC_BASE_URL") or local_url
    print("OAuth callback:", origin + "/api/login/oauth2/code/github")
    print("Frontend redirect:", origin + "/dashboard")
    print("The first GitHub user to log in receives the super administrator role.")
    print("Ports bind to localhost by default. For a remote server, use an SSH tunnel or configure your HTTPS reverse proxy.")
    print("Private .env retained; no default administrator password is created.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    base = Path(__file__).resolve().parent
    parser.add_argument("--directory", type=Path, default=base if (base / ".env.example").exists() else base.parent / "deploy-bundle")
    parser.add_argument("--public-url", help="Public origin; HTTPS requires your existing reverse proxy and certificate")
    parser.add_argument("--compose-file", action="append", default=[], help="Compose files in order; include the base when adding overrides")
    parser.add_argument("--project-name")
    parser.add_argument("--wait-timeout", type=int, default=600)
    args = parser.parse_args()
    try:
        if args.wait_timeout <= 0:
            raise ValueError("Wait timeout must be positive")
        install(args.directory, args.public_url, args.compose_file, args.project_name, args.wait_timeout)
    except (OSError, ValueError, RuntimeError, subprocess.CalledProcessError) as error:
        parser.exit(1, f"Installation failed: {error}\nExisting containers/data are retained. Inspect docker compose logs before retrying.\n")


if __name__ == "__main__":
    main()
