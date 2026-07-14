# ECOS — Root Monorepo Guide

> **ECOS** = Enterprise Cognitive Operating System. Core pipeline: data governance → knowledge graph → LLM agent deployment.
> WSL path: `/home/guorongxiao/ECOS/` | Windows UNC: `\\wsl$\Ubuntu\home\guorongxiao\ECOS\`

## Repo Structure

| Directory | Stack | Entry | Notes |
|-----------|-------|-------|-------|
| `ecos_backend/` | Java 17 / Spring Boot 3.2.2 / MyBatis / PG | `gateway/GatewayApplication.java` | Single fat-JAR, not microservices. See `ecos_backend/AGENTS.md` |
| `ecos_frontend/` | React 19 / Vite 6 / Tailwind 4 / TypeScript | `src/main.tsx` → `App.tsx` | Express server (`server.ts`) for SSR. Proxies `/api` → `localhost:8080` |
| `ecos-docker/` | Docker Compose | `docker-compose.yml` | PG 16, Neo4j 5, MinIO, OPA |
| `ecos-kb/` | Python scripts + JSON | `scripts/scan_all.py` | Auto-generated API index & schema. See `ecos-kb/AGENTS.md` |
| `ecos-tests/` | Node.js / Playwright | `data-workbench-smoke.mjs` | Headless smoke tests against running backend+frontend |
| `docs/` | Markdown | — | Kanban, architecture, specs, handover docs |
| `ecos-git-repos/` | Git data dirs | — | `.gitkeep` placeholders for pipeline/ontology data |

## Backend Quick Commands (WSL)

```bash
source ~/ecos-env.sh                    # set JAVA_HOME, Maven, aliases
cd ~/ECOS/ecos_backend
mvn clean install -DskipTests           # build standard edition
bash build.sh standard|enterprise|ultimate  # edition-specific build
mvn compile -pl gateway -am             # compile gateway + deps only
mvn test -pl common/common-api          # single-module test
bash ~/start-gateway.sh                 # start gateway (includes unset HOME workaround)
```

**Pre-commit**: `bash ~/pre-check.sh` runs compile → ArchUnit → Enforcer → API contract tests.

## Frontend Quick Commands

```bash
cd ecos_frontend
npm install                             # or pnpm install
npm run dev                             # Vite dev server (port 3000), proxies /api→:8080
npm run build                           # vite build + esbuild server → dist/
npm run lint                            # tsc --noEmit
npm test                                # vitest run
npm run test:watch                      # vitest watch
```

**Env**: `GEMINI_API_KEY` in `.env.local` for AI features; `DISABLE_HMR=true` to disable hot reload.

## Three Editions (Maven Profiles)

| Edition | DB | Profile Flag |
|---------|----|-------------|
| standard (default) | PostgreSQL | `-Pstandard` |
| enterprise | PostgreSQL + Neo4j | `-Penterprise` |
| ultimate/flagship | PostgreSQL + Neo4j + Doris | `-Pultimate` |

## Database

- PostgreSQL 16, database `sys_man`, local creds `postgres/postgres`
- MyBatis (not JPA — Hibernate auto-config excluded)
- Flyway disabled (`spring.flyway.enabled: false`)
- Schema rule: only add columns/tables, never drop

## Infrastructure (ecos-docker)

```bash
cd ecos-docker && docker-compose up -d   # PG:5432, Neo4j:7474+7687, MinIO:9000, OPA:8181
```

## WSL Gotchas

- **UNC path bug**: Hermes redirects `$HOME` → jansi.dll error. Use `~/start-gateway.sh` (contains `unset HOME`)
- **Maven**: must use WSL-native paths (`~/.m2/repository`), never `/mnt/d/`
- **Git SSH through proxy**: `GIT_SSH_COMMAND="ssh -o ProxyCommand='nc -X 5 -x 127.0.0.1:7897 %h %p'"`

## Frontend Conventions

- Theme tokens via `useTheme()` — never hardcode Tailwind colors (`bg-white`, `bg-slate-900`) for structural components
- Icons from `lucide-react` only — no custom SVG
- i18n via `useLanguage()` → `t("namespace.key")` — no hardcoded strings
- Path alias: `@/` maps to project root (`vite.config.ts` + `tsconfig.json`)
- 4 themes: `slate-light`, `deep-space`, `cyber-terminal`, `royal-purple`
- Full design spec: `ecos_frontend/GEMINI.md`

## Key Cross-Cutting Facts

- Backend API base: `http://localhost:8080/api/v1/...`
- Frontend dev: `http://localhost:3000` (proxies `/api` and `/datanet` to backend)
- Smoke test: `node ~/ecos-tests/data-workbench-smoke.mjs` (requires both backend and frontend running)
- Knowledge base refresh: `cd ecos-kb && python3 scripts/scan_all.py`
- Sub-project AGENTS.md files: `ecos_backend/AGENTS.md` (backend arch & rules), `ecos-kb/AGENTS.md` (KB queries)

## What Not To Do

- Don't create new Maven modules (baseline is 13)
- Don't add new Docker containers (compose image count is baselined)
- Don't change existing API paths or signatures — only additive changes
- Don't bypass `@Autowired` with `new` — always use constructor injection
- Don't delete columns/tables from the database schema
