# CipherGate

Enterprise authentication and security platform (Java 17 + Spring Boot 4 + React/TypeScript).

## Quick commands

```bash
# Start dependencies (MySQL, Redis, RabbitMQ, MinIO)
docker compose -f compose.yaml up -d

# Start backend
./gradlew bootRun

# Start frontend (proxies /api to localhost:8080)
cd frontend && npm ci && npm run dev

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
./gradlew unitTest
# Full gate requires isolated MySQL/Redis environment; see CONTRIBUTING.md
./gradlew unitTest integrationTest jacocoTestReport

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
- **Config**: environment variables override `application.yaml` defaults; see `compose.yaml` / `application-dev.yaml` for local-only credentials; `.env.server.example` is the public production template
- **CI**: fork-safe PR checks cover backend/frontend/plugins/docs; releases require tags, then package on Linux and Windows; both deploy scripts use `scripts/project.py`
- **Plugin system**: PF4J; plugins declare `Plugin-Class` in manifest, depend on `ciphergate-plugin-api`

## Development and approval workflow

- Start new features and fixes on a dedicated branch based on the current remote `master`; do not commit or push changes directly to `master`.
- Implement the complete change, add relevant regression tests, and update affected documentation. Use new Flyway migrations for schema changes; never edit an applied migration.
- Run the relevant local checks, push the branch, and open a PR with the behavior, validation results, and any migration or rollback implications.
- Wait for required CI checks and builds to pass. Resolve failures on the PR branch before requesting review.
- After checks pass, report the PR and wait for the user's explicit approval to merge that PR. A request to develop a feature or create a PR does not authorize merging, administrator bypass, release creation, or production deployment.
- Release publication and production deployment require their own explicit user authorization. Preserve existing production configuration and create verified backups before an authorized deployment.

## Watch out

- Root `package.json` is only for `caveman-installer` — not the frontend. Frontend is `frontend/package.json`.
- Both deploy scripts use the same Python packaging implementation; install Node 22.22.0 and Python 3.12+.
- Docker Compose services use non-standard host ports (e.g., MySQL 13306, Redis 16379) to avoid conflicts.
- Frontend build (`npm run build`) runs TypeScript compilation first (`tsc -b`) — type errors will fail the build.

<!-- code-review-graph MCP tools -->
## MCP Tools: code-review-graph

**This project has a knowledge graph. Start with the code-review-graph
MCP tools to narrow scope, then read the source.** The graph is cheaper than scanning files and
gives you structural context (callers, dependents, test coverage) that file search cannot.

### When to use graph tools FIRST

- **Exploring code**: `semantic_search_nodes_tool` or `query_graph_tool` instead of Grep
- **Understanding impact**: `get_impact_radius_tool` instead of manually tracing imports
- **Code review**: `detect_changes_tool` + `get_review_context_tool` instead of reading entire files
- **Finding relationships**: `query_graph_tool` with callers_of/callees_of/imports_of/tests_for
- **Architecture questions**: `get_architecture_overview_tool` + `list_communities_tool`

### Verify in the source

- Narrow scope with the graph, then read the source. Do not change code from graph output alone.
- For any non-trivial change, read the implementation and the relevant tests before concluding.
- Verify the exact source when touching behavior, database logic, migrations, retries, fallbacks,
  recovery, or compatibility code.
- When the graph and the source disagree, the source wins. The graph may be stale or may not
  model that relationship.
- An empty graph result can mean "not indexed" or "not statically visible", not "does not exist".

### Key Tools

| Tool | Use when |
| ------ | ---------- |
| `detect_changes_tool` | Reviewing code changes — gives risk-scored analysis |
| `get_review_context_tool` | Need source snippets for review — token-efficient |
| `get_impact_radius_tool` | Understanding blast radius of a change |
| `get_affected_flows_tool` | Finding which execution paths are impacted |
| `query_graph_tool` | Tracing callers, callees, imports, tests, dependencies |
| `semantic_search_nodes_tool` | Finding functions/classes by name or keyword |
| `get_architecture_overview_tool` | Understanding high-level codebase structure |
| `refactor_tool` | Planning renames, finding dead code |

### Workflow

1. The graph auto-updates on file changes (via hooks).
2. Use `detect_changes_tool` for code review.
3. Use `get_affected_flows_tool` to understand impact.
4. Use `query_graph_tool` pattern="tests_for" to check coverage.
<!-- /code-review-graph MCP tools -->
