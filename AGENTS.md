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

<!-- code-review-graph MCP tools -->
## MCP Tools: code-review-graph

**IMPORTANT: This project has a knowledge graph. ALWAYS use the
code-review-graph MCP tools BEFORE using Grep/Glob/Read to explore
the codebase.** The graph is faster, cheaper (fewer tokens), and gives
you structural context (callers, dependents, test coverage) that file
scanning cannot.

### When to use graph tools FIRST

- **Exploring code**: `semantic_search_nodes` or `query_graph` instead of Grep
- **Understanding impact**: `get_impact_radius` instead of manually tracing imports
- **Code review**: `detect_changes` + `get_review_context` instead of reading entire files
- **Finding relationships**: `query_graph` with callers_of/callees_of/imports_of/tests_for
- **Architecture questions**: `get_architecture_overview` + `list_communities`

Fall back to Grep/Glob/Read **only** when the graph doesn't cover what you need.

### Key Tools

| Tool | Use when |
| ------ | ---------- |
| `detect_changes` | Reviewing code changes — gives risk-scored analysis |
| `get_review_context` | Need source snippets for review — token-efficient |
| `get_impact_radius` | Understanding blast radius of a change |
| `get_affected_flows` | Finding which execution paths are impacted |
| `query_graph` | Tracing callers, callees, imports, tests, dependencies |
| `semantic_search_nodes` | Finding functions/classes by name or keyword |
| `get_architecture_overview` | Understanding high-level codebase structure |
| `refactor_tool` | Planning renames, finding dead code |

### Workflow

1. The graph auto-updates on file changes (via hooks).
2. Use `detect_changes` for code review.
3. Use `get_affected_flows` to understand impact.
4. Use `query_graph` pattern="tests_for" to check coverage.
