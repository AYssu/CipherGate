#!/usr/bin/env python3
"""Generate install-local credentials. Never read a maintainer's private config."""
import argparse
import os
from pathlib import Path
import re
import secrets

SECRET_KEYS = {
    "MYSQL_ROOT_PASSWORD", "MYSQL_PASSWORD", "REDIS_PASSWORD", "RABBITMQ_PASSWORD",
    "MINIO_ACCESS_KEY", "MINIO_SECRET_KEY", "PORTAL_JWT_SECRET",
}
PORT_KEYS = {
    "MYSQL_PORT", "REDIS_PORT_HOST", "RABBITMQ_PORT", "RABBITMQ_MGMT_PORT",
    "MINIO_API_PORT", "MINIO_CONSOLE_PORT", "BACKEND_PORT", "FRONTEND_PORT",
}


def read_env(path):
    result = {}
    for line in Path(path).read_text(encoding="utf-8-sig").splitlines():
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        if "=" not in line:
            raise ValueError("Invalid environment line (expected KEY=value)")
        key, value = line.split("=", 1)
        key = key.strip()
        if not re.fullmatch(r"[A-Z][A-Z0-9_]*", key) or key in result:
            raise ValueError("Invalid or duplicate environment key: " + key)
        result[key] = value.strip()
    return result


def validate(values):
    for key in SECRET_KEYS:
        value = values.get(key, "")
        if not value or "CHANGE_ME" in value or len(value.encode()) < (32 if key == "PORTAL_JWT_SECRET" else 16):
            raise ValueError("Missing or weak setting: " + key)
    ports = []
    for key in PORT_KEYS:
        value = values.get(key, "")
        if not value.isdigit() or not 1 <= int(value) <= 65535:
            raise ValueError("Port must be 1..65535: " + key)
        ports.append(int(value))
    if len(set(ports)) != len(ports):
        raise ValueError("Host ports must be distinct")
    for key in ["MYSQL_DATABASE", "MYSQL_USER", "RABBITMQ_USERNAME"]:
        if not re.fullmatch(r"[A-Za-z][A-Za-z0-9_]{0,31}", values.get(key, "")):
            raise ValueError("Invalid identifier: " + key)


def generate(template, destination):
    template, destination = Path(template), Path(destination)
    values = read_env(template)
    for key in SECRET_KEYS:
        values[key] = secrets.token_urlsafe(48 if key == "PORTAL_JWT_SECRET" else 24)
    validate(values)
    # O_EXCL makes concurrent installs safe and refuses to overwrite working credentials.
    descriptor = os.open(destination, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, "w", encoding="utf-8", newline="\n") as output:
        output.write("# Generated on this installation. Private: do not commit or overwrite on upgrade.\n")
        for key, value in values.items():
            output.write(f"{key}={value}\n")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--template", type=Path)
    parser.add_argument("--output", type=Path, default=Path(".env"))
    parser.add_argument("--check", action="store_true", help="Validate existing config without printing secrets")
    args = parser.parse_args()
    try:
        if args.check:
            validate(read_env(args.output))
            print("Environment settings valid (secrets withheld).")
        else:
            base = Path(__file__).resolve().parent
            template = args.template or next((p for p in [base / ".env.example", base.parent / ".env.server.example"] if p.exists()), None)
            if template is None:
                raise ValueError("Missing .env.example template")
            generate(template, args.output)
            print("Created private configuration:", args.output)
            print("Keep it on upgrade. Ports bind to loopback; configure your reverse proxy before public access.")
    except (OSError, ValueError) as error:
        parser.exit(1, f"Configuration error: {error}\n")


if __name__ == "__main__":
    main()
