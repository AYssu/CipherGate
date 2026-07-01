# CipherGate

Enterprise authentication and security platform (Java 17 + Spring Boot 4 + React/TypeScript).

## Quick commands

```bash
# Start dependencies (MySQL, Redis, RabbitMQ, MinIO)
docker compose -f compose.yaml up -d

# Start backend
./gradlew bootRun

# Start frontend (proxies /api to localhost:8080)
cd frontend && npm install && npm run dev

# Build everything for deployment
./deploy-server.sh
```

## Build & verify

```bash
# Backend
./gradlew clean bootJar --no-daemon

# Frontend (runs tsc + vite build)
cd frontend && npm run build

# Plugin (each plugin is its own Gradle project)
./gradlew -p plugins/rsa-crypto-plugin clean jar --no-daemon

# Tests
./gradlew test

# Frontend lint
cd frontend && npm run lint
```

## Structure

- `src/` — Spring Boot backend (MyBatis-Plus, Security, WebSocket, RabbitMQ)
- `frontend/` — React + TypeScript (Vite, Ant Design, ECharts)
- `plugins/` — PF4J plugins (each subfolder is a standalone Gradle project)
- `ciphergate-plugin-api/` — shared plugin API (included in main build via `settings.gradle`)
- `deploy-bundle/` — packaging output (JAR + frontend dist + plugins + compose)

## Key conventions

- **Ports**: backend 8080 (prod via `BACKEND_PORT`), frontend dev 5173 (Vite default)
- **Frontend proxy**: Vite proxies `/api` to `http://localhost:8080` (see `frontend/vite.config.ts`)
- **MyBatis-Plus**: logical deletes enabled (`deleted` field, 1=deleted, 0=active)
- **Config**: environment variables override `application.yaml` defaults; see `compose.yaml` and `.env.server` for local credentials
- **CI**: GitHub Actions builds on Windows (`deploy-server.bat`); local dev uses `.sh` scripts
- **Plugin system**: PF4J; plugins declare `Plugin-Class` in manifest, depend on `ciphergate-plugin-api`

## Watch out

- Root `package.json` is only for `caveman-installer` — not the frontend. Frontend is `frontend/package.json`.
- CI runs `deploy-server.bat` (Windows); local dev uses `deploy-server.sh` — both do the same steps.
- Docker Compose services use non-standard host ports (e.g., MySQL 13306, Redis 16379) to avoid conflicts.
- Frontend build (`npm run build`) runs TypeScript compilation first (`tsc -b`) — type errors will fail the build.
