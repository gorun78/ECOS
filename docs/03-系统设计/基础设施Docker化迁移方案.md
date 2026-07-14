# ECOS 基础设施 Docker 化迁移方案

> **文档编号**: ECOS-INFRA-DOCKER-001  
> **版本**: V1.0  
> **日期**: 2025-06-25  
> **作者**: ECOS-ARCH  
> **状态**: 待评审

---

## 目录

1. [环境现状分析](#1-环境现状分析)
2. [组件迁移评估](#2-组件迁移评估)
3. [目标架构](#3-目标架构)
4. [Docker Compose 编排](#4-docker-compose-编排)
5. [数据迁移方案](#5-数据迁移方案)
6. [配置管理方案](#6-配置管理方案)
7. [迁移步骤](#7-迁移步骤)
8. [回滚方案](#8-回滚方案)
9. [附录](#9-附录)

---

## 1. 环境现状分析

### 1.1 当前运行环境

| 项目 | 详情 |
|------|------|
| 宿主机 | Windows 11 + WSL2 (Ubuntu) |
| WSL 内存 | 15 GiB 总量，7.2 GiB 已用，7.9 GiB 可用 |
| Docker Desktop | v29.5.2（已安装于 Windows，**WSL 集成未启用**） |
| Docker 容器 | 当前无镜像、无容器 |

### 1.2 当前运行服务清单

| 组件 | 版本 | 端口 | 运行方式 | PID | 内存占用(约) |
|------|------|------|---------|-----|------------|
| PostgreSQL | (Windows 原生) | :5432 | Windows 原生进程 (11 个 postgres.exe) | N/A | ~150 MB |
| Neo4j | 5.26.4 CE | :7474 (HTTP) / :7687 (Bolt) | WSL Java 进程 (`/home/guorongxiao/neo4j/`) | 426448 | ~980 MB |
| OPA | 0.63.0 | :8181 | WSL 二进制 (`/home/guorongxiao/opa`) | 440171 | ~36 MB |
| MinIO | RELEASE.2026-06-06 | :9000 / :9001 (Console) | WSL 二进制 (`/home/guorongxiao/minio`) | 543092 | ~232 MB |
| MQTT (Moquette) | 0.17 | :1883 | **嵌入式**在 Gateway 进程中 | — (随23287) | — |
| Apache Doris | 3.0.3 | FE:8030/9030, BE:8040 | Docker Compose (1FE+1BE) | — | ~1.5 GB |
| Gateway | Spring Boot 3.2 | :8080 | WSL Maven (`spring-boot:run`) | 23287 | ~435 MB |
| FE (Vite) | React 19 | :5173 | WSL Node (`npx vite`) | 457654 | ~232 MB |

### 1.3 数据目录

| 组件 | 数据路径 | 大小 |
|------|---------|------|
| PostgreSQL | Windows 主机 (`C:\Program Files\PostgreSQL\14\data\`) | 未知 |
| Neo4j | `/home/guorongxiao/neo4j/data/` | 517 MB |
| OPA 策略 | `/home/guorongxiao/opa-policies/` (5 个 .rego 文件) | < 10 KB |
| MinIO | `/home/guorongxiao/start/` (含 `.minio.sys/`) | 128 KB |

### 1.4 网络架构（当前）

```
WSL2 内部                          Windows 主机
┌─────────────────────┐           ┌──────────────────┐
│  Gateway :8080      │───localhost──▶ PostgreSQL :5432 │
│  ├─ MQTT :1883     │           └──────────────────┘
│  └─ Doris FE/BE (Docker)│
│                     │
│  Neo4j :7474/:7687 │
│  OPA :8181         │
│  MinIO :9000       │
│  FE :5173          │
└─────────────────────┘
```

> **关键发现**: PostgreSQL 运行在 Windows 主机上，Gateway 通过 WSL2 的 `localhost` 端口映射访问。  
> Neo4j、OPA、MinIO 均在 WSL 内原生运行。  
> 端口 :5432 未出现在 WSL 的 `ss -tlnp` 中，但 `nc -z localhost 5432` 确认可达。

---

## 2. 组件迁移评估

### 2.1 评估标准

| 等级 | 标准 |
|------|------|
| **必须迁移** | 资源消耗大、WSL 内存压力大、有官方镜像、运维收益高 |
| **建议迁移** | 有官方 Docker 镜像、运维方便、隔离性好 |
| **保持原生** | 嵌入在 Java 进程中的组件、开发时频繁重启的组件 |

### 2.2 逐组件分析

#### 2.2.1 PostgreSQL — **建议迁移**（高优先级）

| 维度 | 评估 |
|------|------|
| 当前状态 | Windows 原生运行，11 个 postgres.exe 进程，耗 ~150MB |
| 问题 | 跨 OS 边界访问、WSL 内无 psql 客户端、管理不便、不与 WSL 生命周期统一 |
| 推荐方案 | 迁移至 Docker 容器，统一在 WSL/Docker 内管理 |
| Docker 镜像 | `postgres:14-alpine` |
| 内存限制 | 512 MB (`mem_limit: 512m`) |
| 数据卷 | `./data/postgres:/var/lib/postgresql/data` |
| 环境变量 | `POSTGRES_USER=postgres`, `POSTGRES_PASSWORD=postgres`, `POSTGRES_DB=sys_man` |
| 风险 | 中等 — 需要 pg_dump/pg_restore 全量迁移数据，需停机窗口 |

#### 2.2.2 Neo4j — **必须迁移**（最高优先级）

| 维度 | 评估 |
|------|------|
| 当前状态 | WSL Java 进程，占用 ~980 MB 内存，是最大单一内存消耗者 |
| 问题 | 内存占用极高（WSL 总内存 15GB 中占用 ~1GB），无资源隔离，重启不便 |
| 推荐方案 | 迁移至 Docker 容器，释放 WSL 内存压力 |
| Docker 镜像 | `neo4j:5.26.4-community` |
| 内存限制 | 1 GB (`mem_limit: 1g`)，JVM 堆上限 512m |
| 数据卷 | `./data/neo4j/data:/data`, `./data/neo4j/logs:/logs`, `./data/neo4j/conf:/var/lib/neo4j/conf` |
| 端口 | 7474:7474, 7687:7687 |
| 风险 | 中等 — 数据目录可直接复制（同版本），亦可使用 `neo4j-admin dump/load` |
| 备注 | 需禁用认证 (当前配置 `dbms.security.auth_enabled=false`)，保持与当前行为一致 |

#### 2.2.3 OPA — **建议迁移**

| 维度 | 评估 |
|------|------|
| 当前状态 | WSL 单二进制运行，占用 ~36 MB，资源消耗低 |
| 问题 | 手动管理进程，无自动重启 |
| 推荐方案 | 迁移至 Docker 容器，策略文件通过 volume 挂载 |
| Docker 镜像 | `openpolicyagent/opa:0.63.0-static` |
| 内存限制 | 64 MB (`mem_limit: 64m`) |
| 数据卷 | `./data/opa-policies:/policies:ro` |
| 启动命令 | `run --server --addr 0.0.0.0:8181 /policies` |
| 风险 | 低 — 仅需复制 5 个 .rego 文件，无状态服务 |

#### 2.2.4 MinIO — **建议迁移**

| 维度 | 评估 |
|------|------|
| 当前状态 | WSL 二进制运行，占用 ~232 MB，数据量 128 KB |
| 问题 | 手动管理进程，API 返回 License 错误（AIStor License 未安装） |
| 推荐方案 | 迁移至 Docker 容器 |
| Docker 镜像 | `minio/minio:latest` |
| 内存限制 | 512 MB (`mem_limit: 512m`) |
| 数据卷 | `./data/minio:/data` |
| 启动命令 | `server /data --console-address :9001` |
| 环境变量 | `MINIO_ROOT_USER=minioadmin`, `MINIO_ROOT_PASSWORD=minioadmin` |
| 风险 | 低 — 数据量极小 (128 KB)，直接复制 `.minio.sys/` 目录即可 |

#### 2.2.5 MQTT (Moquette) — **保持原生**

| 维度 | 评估 |
|------|------|
| 当前状态 | 嵌入式在 Gateway Spring Boot 进程中（`moquette-broker-0.17` 依赖） |
| 原因 | 生命周期与 Gateway 绑定，随 Gateway 启动/停止 |
| 推荐方案 | 不迁移，保持嵌入在 Gateway JVM 中 |
| 备注 | 未来如独立扩展 MQTT 服务，可考虑迁移至 `eclipse-mosquitto` |

#### 2.2.6 Apache Doris — **Docker 部署（替换 DuckDB）**

| 维度 | 评估 |
|------|------|
| 当前状态 | 🆕 新增，替换原 DuckDB 嵌入式方案 |
| 部署方式 | Docker Compose — 1FE + 1BE（开发环境） |
| 镜像 | `apache/doris:3.0.3-fe-x86_64` + `apache/doris:3.0.3-be-x86_64` |
| 端口 | FE HTTP :8030, FE MySQL :9030, BE HTTP :8040 |
| JDBC | 标准 MySQL 协议 — `mysql-connector-j`（Spring Boot 3.2 已管理） |
| 连接串 | `jdbc:mysql://ecos-doris-fe:9030` |
| 备注 | 开发环境 1FE+1BE 即可；DuckDB 依赖从 `runtime-task/pom.xml` 移除 |
| docker-compose | 详见 `03-系统设计/ECOS-架构整改方案-20260626.md` §4.2 |

#### 2.2.7 Gateway (Spring Boot) — **保持原生（开发阶段）**

| 维度 | 评估 |
|------|------|
| 当前状态 | 通过 Maven `spring-boot:run` 运行，支持热加载 |
| 原因 | 开发阶段频繁重启/调试，Docker 化会增加构建-运行循环时间 |
| 推荐方案 | 开发阶段保持 Maven 运行，仅生产部署时才 Docker 化 |
| 配置调整 | 连接地址从 `localhost` 改为 Docker 容器名（见第 6 章） |

#### 2.2.8 FE (Vite Dev Server) — **保持原生（开发阶段）**

| 维度 | 评估 |
|------|------|
| 当前状态 | `npx vite --host 0.0.0.0`，支持 HMR 热更新 |
| 原因 | 开发阶段需要 HMR，Docker 化会增加复杂度 |
| 推荐方案 | 开发阶段保持原生运行 |

### 2.3 迁移决策汇总

| 组件 | 决策 | Docker 镜像 | 内存限制 | 数据卷 |
|------|------|------------|---------|--------|
| PostgreSQL | **建议迁移** | `postgres:14-alpine` | 512m | `./data/postgres:/var/lib/postgresql/data` |
| Neo4j | **必须迁移** | `neo4j:5.26.4-community` | 1g | `./data/neo4j/data:/data` |
| OPA | **建议迁移** | `openpolicyagent/opa:0.63.0-static` | 64m | `./data/opa-policies:/policies` |
| MinIO | **建议迁移** | `minio/minio:latest` | 512m | `./data/minio:/data` |
| MQTT | 保持原生 | — | — | — |
| Doris | **Docker 新增** | `apache/doris:3.0.3-fe/be` | 1.5g | 独立 docker-compose |
| Gateway | 保持原生(dev) | — | — | — |
| FE | 保持原生(dev) | — | — | — |

### 2.4 内存预计回收

| 组件 | 当前 WSL 占用 | Docker 后占用 | 回收量 |
|------|------------|------------|--------|
| Neo4j | ~980 MB | ~1 GB (在 Docker VM 中) | **~980 MB** |
| MinIO | ~232 MB | ~512 MB (在 Docker VM 中) | **~232 MB** |
| OPA | ~36 MB | ~64 MB (在 Docker VM 中) | **~36 MB** |
| **合计** | **~1,248 MB** | **从 WSL 内存中释放** | **~1.25 GB** |

> **预期效果**: WSL 可用内存从 7.9 GB 提升至约 9.1 GB，缓解当前 7.2 GB/15 GB 的使用压力。

---

## 3. 目标架构

### 3.1 网络拓扑

```
                        WSL2 (Ubuntu)
┌──────────────────────────────────────────────────────┐
│                                                      │
│  ┌──────────────────┐   localhost   ┌─────────────┐ │
│  │ Gateway :8080    │──────────────▶│ PostgreSQL  │ │
│  │ (Maven, WSL原生) │               │ (Windows)   │ │
│  │ ├─ MQTT :1883   │               │ :5432       │ │
│  │ └─ Doris FE/BE (Docker)│               └─────────────┘ │
│  └──────┬───────────┘                                │
│         │                                            │
│  ┌──────┼──────────────────────────────────────┐     │
│  │      │        Docker Network (bridge)       │     │
│  │      │    docker-compose 自定义网络          │     │
│  │      │    网段: 172.20.0.0/16               │     │
│  │      │                                      │     │
│  │  ┌───▼──────────┐  ┌───────────────────┐   │     │
│  │  │ Neo4j        │  │ MinIO             │   │     │
│  │  │ :7474 :7687  │  │ :9000 :9001       │   │     │
│  │  │ mem: 1g      │  │ mem: 512m         │   │     │
│  │  └──────────────┘  └───────────────────┘   │     │
│  │                                             │     │
│  │  ┌──────────────┐                           │     │
│  │  │ OPA :8181    │                           │     │
│  │  │ mem: 64m     │                           │     │
│  │  └──────────────┘                           │     │
│  │                                             │     │
│  │  ┌──────────────┐                           │     │
│  │  │ PostgreSQL   │  (可选: 未来从Windows迁入) │     │
│  │  │ :5432        │                           │     │
│  │  │ mem: 512m    │                           │     │
│  │  └──────────────┘                           │     │
│  └─────────────────────────────────────────────┘     │
│                                                      │
│  ┌──────────────────┐                                │
│  │ FE Vite :5173    │                                │
│  │ (WSL原生)        │                                │
│  └──────────────────┘                                │
│                                                      │
└──────────────────────────────────────────────────────┘
```

### 3.2 网络说明

| 项目 | 方案 |
|------|------|
| WSL → Docker 容器 | 通过 `localhost` + 端口映射访问（Docker Desktop WSL2 集成） |
| 容器间通信 | docker-compose 自定义 bridge 网络，使用服务名 DNS 解析 |
| Gateway → PostgreSQL | 保持 `localhost:5432`（PG 仍在 Windows）；如果 PG 也迁入 Docker，改为 `postgres:5432` |
| Gateway → Neo4j | `localhost:7687`（Docker 端口映射） |
| Gateway → OPA | `localhost:8181`（Docker 端口映射） |
| Gateway → MinIO | `localhost:9000`（Docker 端口映射） |
| 端口冲突 | 检查通过 — Docker 映射端口与当前 WSL 监听端口完全一致 |

### 3.3 Docker Desktop WSL 集成配置

**前置条件**: 必须在 Docker Desktop 中启用 WSL2 集成。

1. 打开 Docker Desktop → Settings → Resources → WSL Integration
2. 启用当前 WSL 发行版（Ubuntu）
3. 点击 "Apply & Restart"
4. 验证: `docker ps` 应可在 WSL 内执行

---

## 4. Docker Compose 编排

### 4.1 部署目录结构

```
~/ecos-docker/
├── docker-compose.yml          # 主编排文件
├── .env                         # 环境变量
├── data/                        # 持久化数据卷
│   ├── postgres/                # PostgreSQL 数据
│   ├── neo4j/                   # Neo4j 数据
│   │   ├── data/                #   数据库文件
│   │   ├── logs/                #   日志
│   │   └── conf/                #   配置文件
│   ├── minio/                   # MinIO 数据
│   └── opa-policies/            # OPA 策略文件（只读挂载）
├── logs/                        # 集中日志目录(可选)
└── scripts/                     # 运维脚本
    ├── migrate-pg.sh            # PostgreSQL 数据迁移
    ├── migrate-neo4j.sh         # Neo4j 数据迁移
    └── health-check.sh          # 健康检查
```

### 4.2 docker-compose.yml

```yaml
version: '3.8'

# ============================================================
# ECOS 基础设施 Docker Compose 编排
# 版本: V1.0
# 日期: 2025-06-25
# ============================================================

services:

  # ==========================================
  # PostgreSQL 14 — 业务数据库
  # ==========================================
  postgres:
    image: postgres:14-alpine
    container_name: ecos-postgres
    restart: unless-stopped
    ports:
      - "5432:5432"
    environment:
      POSTGRES_USER: ${PG_USER:-postgres}
      POSTGRES_PASSWORD: ${PG_PASSWORD:-postgres}
      POSTGRES_DB: sys_man
      PGDATA: /var/lib/postgresql/data/pgdata
    volumes:
      - ./data/postgres:/var/lib/postgresql/data
      - ./scripts/init-pg.sql:/docker-entrypoint-initdb.d/init.sql:ro
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres -d sys_man"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 30s
    mem_limit: 512m
    mem_reservation: 256m
    networks:
      - ecos-net
    logging:
      driver: "json-file"
      options:
        max-size: "10m"
        max-file: "3"

  # ==========================================
  # Neo4j 5.26.4 Community — 图数据库
  # ==========================================
  neo4j:
    image: neo4j:5.26.4-community
    container_name: ecos-neo4j
    restart: unless-stopped
    ports:
      - "7474:7474"   # HTTP
      - "7687:7687"   # Bolt
    environment:
      NEO4J_AUTH: "none"                          # 与当前 auth_enabled=false 保持一致
      NEO4J_dbms_memory_heap_initial__size: "256m"
      NEO4J_dbms_memory_heap_max__size: "512m"
      NEO4J_dbms_memory_pagecache_size: "256m"
      NEO4J_server_default__listen__address: "0.0.0.0"
      # 以下为可选优化项
      NEO4J_db_tx__log_rotation_retention__policy: "2 days 2G"
      NEO4J_dbms_security_procedures_unrestricted: "apoc.*"
    volumes:
      - ./data/neo4j/data:/data
      - ./data/neo4j/logs:/logs
      - ./data/neo4j/conf:/var/lib/neo4j/conf
      - ./data/neo4j/import:/var/lib/neo4j/import
    healthcheck:
      test: ["CMD-SHELL", "wget -qO- http://localhost:7474 || exit 1"]
      interval: 15s
      timeout: 10s
      retries: 5
      start_period: 60s
    mem_limit: 1g
    mem_reservation: 768m
    networks:
      - ecos-net
    logging:
      driver: "json-file"
      options:
        max-size: "10m"
        max-file: "3"

  # ==========================================
  # OPA 0.63.0 — 策略引擎
  # ==========================================
  opa:
    image: openpolicyagent/opa:0.63.0-static
    container_name: ecos-opa
    restart: unless-stopped
    ports:
      - "8181:8181"
    command:
      - "run"
      - "--server"
      - "--addr"
      - "0.0.0.0:8181"
      - "/policies"
    volumes:
      - ./data/opa-policies:/policies:ro   # 只读挂载策略文件
    healthcheck:
      test: ["CMD", "wget", "-qO-", "http://localhost:8181/v1/data"]
      interval: 10s
      timeout: 5s
      retries: 3
      start_period: 5s
    mem_limit: 64m
    mem_reservation: 32m
    networks:
      - ecos-net
    logging:
      driver: "json-file"
      options:
        max-size: "10m"
        max-file: "3"

  # ==========================================
  # MinIO — 对象存储
  # ==========================================
  minio:
    image: minio/minio:latest
    container_name: ecos-minio
    restart: unless-stopped
    ports:
      - "9000:9000"   # S3 API
      - "9001:9001"   # Web Console
    environment:
      MINIO_ROOT_USER: ${MINIO_USER:-minioadmin}
      MINIO_ROOT_PASSWORD: ${MINIO_PASSWORD:-minioadmin}
    command: server /data --console-address ":9001"
    volumes:
      - ./data/minio:/data
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:9000/minio/health/live"]
      interval: 15s
      timeout: 10s
      retries: 3
      start_period: 10s
    mem_limit: 512m
    mem_reservation: 256m
    networks:
      - ecos-net
    logging:
      driver: "json-file"
      options:
        max-size: "10m"
        max-file: "3"

# ============================================================
# 自定义网络
# ============================================================
networks:
  ecos-net:
    name: ecos-net
    driver: bridge
    ipam:
      config:
        - subnet: 172.20.0.0/16
          gateway: 172.20.0.1
```

### 4.3 .env 文件

```bash
# ECOS Docker 环境变量
# 生成日期: 2025-06-25

# PostgreSQL
PG_USER=postgres
PG_PASSWORD=postgres
PG_DATABASE=sys_man

# MinIO
MINIO_USER=minioadmin
MINIO_PASSWORD=minioadmin

# Neo4j (密码在 NEO4J_AUTH 环境变量中)
# NEO4J_AUTH=neo4j/neo4j123  # 如需启用认证，取消此行注释并修改 docker-compose.yml
```

### 4.4 常用操作命令

```bash
# 启动所有服务
cd ~/ecos-docker && docker compose up -d

# 查看服务状态
docker compose ps

# 查看日志
docker compose logs -f neo4j
docker compose logs -f --tail=100

# 停止所有服务
docker compose down

# 停止并删除数据卷(危险操作!)
docker compose down -v

# 重启单个服务
docker compose restart neo4j

# 进入容器
docker compose exec postgres psql -U postgres -d sys_man
docker compose exec neo4j cypher-shell
```

---

## 5. 数据迁移方案

### 5.1 PostgreSQL 数据迁移

> **⚠️ 注意**: 当前 PostgreSQL 运行在 Windows 主机上。迁移前需确保：
> 1. Windows 侧 PostgreSQL 的 `pg_dump` 可访问
> 2. WSL 内可通过 `localhost:5432` 连接到 Windows PostgreSQL

#### 步骤

```bash
# === 步骤 1: 从 Windows PostgreSQL 导出数据 ===
# 在 WSL 中执行 (需要 Windows 侧 psql 客户端)
/mnt/c/Program\ Files/PostgreSQL/14/bin/pg_dump.exe \
  -h localhost -p 5432 -U postgres \
  -d sys_man -F c -f /tmp/sys_man_backup.dump

# === 步骤 2: 启动 Docker PostgreSQL ===
cd ~/ecos-docker && docker compose up -d postgres

# === 步骤 3: 等待 PostgreSQL 就绪 ===
until docker compose exec -T postgres pg_isready -U postgres; do
  echo "等待 PostgreSQL 就绪..."
  sleep 2
done

# === 步骤 4: 恢复数据到 Docker PostgreSQL ===
docker compose exec -T postgres pg_restore \
  -U postgres -d sys_man -c --if-exists \
  < /tmp/sys_man_backup.dump

# === 步骤 5: 验证 ===
docker compose exec postgres psql -U postgres -d sys_man -c "\dt"
docker compose exec postgres psql -U postgres -d sys_man -c "SELECT count(*) FROM sys_config;"
```

#### 替代方案: pg_dumpall (迁移所有数据库)

```bash
# 如果存在多个数据库
/mnt/c/Program\ Files/PostgreSQL/14/bin/pg_dumpall.exe \
  -h localhost -p 5432 -U postgres \
  -f /tmp/all_databases.sql

# 恢复到 Docker
cat /tmp/all_databases.sql | docker compose exec -T postgres psql -U postgres
```

### 5.2 Neo4j 数据迁移

> **推荐方案**: 直接复制 data 目录（同版本 5.26.4，兼容性最佳）

#### 方案 A: 直接复制 data 目录（推荐）

```bash
# === 步骤 1: 停止 WSL 原生 Neo4j ===
kill $(pgrep -f Neo4jBoot) 2>/dev/null
sleep 5

# === 步骤 2: 复制数据目录 ===
mkdir -p ~/ecos-docker/data/neo4j
cp -r /home/guorongxiao/neo4j/data/* ~/ecos-docker/data/neo4j/data/
cp -r /home/guorongxiao/neo4j/logs/* ~/ecos-docker/data/neo4j/logs/
cp /home/guorongxiao/neo4j/conf/neo4j.conf ~/ecos-docker/data/neo4j/conf/

# === 步骤 3: 启动 Docker Neo4j ===
cd ~/ecos-docker && docker compose up -d neo4j

# === 步骤 4: 验证 ===
docker compose logs neo4j | grep -i "started"
curl -s http://localhost:7474 | head -5
```

#### 方案 B: neo4j-admin dump/load（适合跨版本迁移）

```bash
# === 导出 (在 WSL 原生 Neo4j 停止后) ===
~/neo4j/bin/neo4j-admin database dump neo4j --to-path=/tmp/neo4j-backup/

# === 导入 (在 Docker Neo4j 启动后) ===
docker compose exec neo4j neo4j-admin database load neo4j \
  --from-path=/var/lib/neo4j/import/backup/ --overwrite-destination=true

# 需要先将备份文件复制到容器的 import 目录
cp /tmp/neo4j-backup/* ~/ecos-docker/data/neo4j/import/backup/
```

### 5.3 OPA 策略迁移

```bash
# OPA 策略文件是无状态的，直接复制即可
mkdir -p ~/ecos-docker/data/opa-policies
cp /home/guorongxiao/opa-policies/*.rego ~/ecos-docker/data/opa-policies/

# 验证策略文件已复制
ls -la ~/ecos-docker/data/opa-policies/
# 预期输出: abac.rego  data_mask.rego  ip_restrict.rego  rbac.rego  time_window.rego
```

### 5.4 MinIO 数据迁移

```bash
# MinIO 数据量极小 (128 KB)，直接复制
mkdir -p ~/ecos-docker/data/minio
cp -r /home/guorongxiao/start/.minio.sys ~/ecos-docker/data/minio/

# 如果 MinIO 有业务数据 bucket 目录，一并复制
# cp -r /home/guorongxiao/start/*/ ~/ecos-docker/data/minio/

# 验证
docker compose up -d minio
curl -s http://localhost:9000/minio/health/live
```

---

## 6. 配置管理方案

### 6.1 Spring Boot 配置变更

当前 Gateway 通过 `application.yml` 连接各服务，所有地址均为 `localhost`。Docker 化后端口映射保持不变，但 **需要确保 WSL2 Docker 集成正常工作**。

#### 6.1.1 当前配置（application.yml 关键片段）

```yaml
# datasource → PostgreSQL
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/sys_man?currentSchema=public
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:postgres}

# Neo4j
neo4j:
  uri: bolt://localhost:7687
  username: neo4j
  password: neo4j123
  database: neo4j
```

#### 6.1.2 变更方案

**方案一: 保持 localhost（推荐，零代码变更）**

Docker 容器的端口映射将服务暴露在 WSL 的 `localhost`，与当前完全一致：

| 服务 | 当前地址 | Docker 化后地址 | 变更 |
|------|---------|---------------|------|
| PostgreSQL | `localhost:5432` | `localhost:5432` | **无变更** |
| Neo4j Bolt | `localhost:7687` | `localhost:7687` | **无变更** |
| OPA | `localhost:8181` | `localhost:8181` | **无变更** |
| MinIO | `localhost:9000` | `localhost:9000` | **无变更** |

> ✅ **结论**: 所有 Docker 化服务通过端口映射暴露在 `localhost`，Gateway 配置 **无需修改**。

**方案二: 使用环境变量覆盖（生产环境推荐）**

通过 Spring 外部化配置，无需修改 application.yml：

```bash
# 启动 Gateway 时传入环境变量
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/sys_man
NEO4J_URI=bolt://localhost:7687
```

**方案三: 容器内 DNS（仅当 Gateway 也 Docker 化时）**

如果 Gateway 也在 Docker 中运行，使用 Docker 内部 DNS：

```yaml
# Gateway Docker 化后的 application-docker.yml
spring:
  datasource:
    url: jdbc:postgresql://postgres:5432/sys_man
neo4j:
  uri: bolt://neo4j:7687
```

### 6.2 系统配置前端映射

ECOS 通过数据库表 `sys_config` 和 `sys_param` 管理运行时配置，前端分别由以下 Controller 提供 CRUD 接口：

| Controller | 路由 | 管理对象 | 数据库表 |
|-----------|------|---------|---------|
| `ConfigController` | `/api/system/config/items` | 系统配置项 | `sys_config` |
| `SystemParamController` | `/api/system/config/params` | 系统参数 | `sys_param` |
| `SysConfigController` | — | 系统配置 | `sys_config` |

#### 6.2.1 需要新增的配置项

Docker 化后，建议在 `sys_config` 表中新增以下配置项，供前端「系统配置」页面管理：

| config_key | config_value (示例) | config_type | 说明 |
|-----------|-------------------|------------|------|
| `infra.postgres.host` | `localhost` | `infrastructure` | PostgreSQL 主机地址 |
| `infra.postgres.port` | `5432` | `infrastructure` | PostgreSQL 端口 |
| `infra.neo4j.host` | `localhost` | `infrastructure` | Neo4j 主机地址 |
| `infra.neo4j.bolt_port` | `7687` | `infrastructure` | Neo4j Bolt 端口 |
| `infra.neo4j.http_port` | `7474` | `infrastructure` | Neo4j HTTP 端口 |
| `infra.opa.host` | `localhost` | `infrastructure` | OPA 主机地址 |
| `infra.opa.port` | `8181` | `infrastructure` | OPA 端口 |
| `infra.minio.host` | `localhost` | `infrastructure` | MinIO 主机地址 |
| `infra.minio.api_port` | `9000` | `infrastructure` | MinIO S3 API 端口 |
| `infra.minio.console_port` | `9001` | `infrastructure` | MinIO Web Console 端口 |
| `infra.mqtt.host` | `localhost` | `infrastructure` | MQTT Broker 地址 |
| `infra.mqtt.port` | `1883` | `infrastructure` | MQTT 端口 |

```sql
-- 在 sys_man 数据库中执行
INSERT INTO sys_config (config_key, config_value, config_type, description, environment, created_by)
VALUES
  ('infra.postgres.host', 'localhost', 'infrastructure', 'PostgreSQL 主机地址', 'DEV', 'system'),
  ('infra.postgres.port', '5432', 'infrastructure', 'PostgreSQL 端口', 'DEV', 'system'),
  ('infra.neo4j.host', 'localhost', 'infrastructure', 'Neo4j 主机地址', 'DEV', 'system'),
  ('infra.neo4j.bolt_port', '7687', 'infrastructure', 'Neo4j Bolt 端口', 'DEV', 'system'),
  ('infra.neo4j.http_port', '7474', 'infrastructure', 'Neo4j HTTP 端口', 'DEV', 'system'),
  ('infra.opa.host', 'localhost', 'infrastructure', 'OPA 主机地址', 'DEV', 'system'),
  ('infra.opa.port', '8181', 'infrastructure', 'OPA 策略引擎端口', 'DEV', 'system'),
  ('infra.minio.host', 'localhost', 'infrastructure', 'MinIO 对象存储地址', 'DEV', 'system'),
  ('infra.minio.api_port', '9000', 'infrastructure', 'MinIO S3 API 端口', 'DEV', 'system'),
  ('infra.minio.console_port', '9001', 'infrastructure', 'MinIO Web Console 端口', 'DEV', 'system'),
  ('infra.mqtt.host', 'localhost', 'infrastructure', 'MQTT Broker 地址', 'DEV', 'system'),
  ('infra.mqtt.port', '1883', 'infrastructure', 'MQTT Broker 端口', 'DEV', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_value = EXCLUDED.config_value;
```

#### 6.2.2 前端配置页面集成

现有「系统配置」前端页面（通过 `/api/system/config/items` 接口）已支持按 `configType=infrastructure` 筛选。只需：

1. 前端在配置列表页面增加「基础设施配置」Tab/分组
2. 筛选条件: `configType=infrastructure`
3. 支持编辑每个配置项的 `config_value`

> 无需新增后端接口，现有 `ConfigController` 已完全支持 CRUD 操作。

---

## 7. 迁移步骤

### 7.1 前置准备

- [ ] **启用 Docker Desktop WSL2 集成**
  - Docker Desktop → Settings → Resources → WSL Integration → 启用 Ubuntu → Apply & Restart
  - 验证: `docker ps` 在 WSL 中可执行

- [ ] **创建部署目录**
  ```bash
  mkdir -p ~/ecos-docker/{data/{postgres,neo4j/{data,logs,conf,import},minio,opa-policies},scripts,logs}
  ```

- [ ] **部署 docker-compose.yml 和 .env**
  - 将第 4 章的 docker-compose.yml 和 .env 写入 `~/ecos-docker/`

### 7.2 迁移执行（按推荐顺序）

#### Phase 1: OPA 迁移（影响最小，验证流程）

```bash
# 1. 复制策略文件
cp /home/guorongxiao/opa-policies/*.rego ~/ecos-docker/data/opa-policies/

# 2. 停止 WSL 原生 OPA
kill 440171  # PID from current session

# 3. 启动 Docker OPA
cd ~/ecos-docker && docker compose up -d opa

# 4. 验证
curl -s http://localhost:8181/v1/data/ecos/rbac
# 预期输出: {"result":{"allow":false}}

# 5. 测试 Gateway 连接 OPA (如有相关功能)
curl -s http://localhost:8080/api/health
```

#### Phase 2: MinIO 迁移

```bash
# 1. 复制数据
cp -r /home/guorongxiao/start/.minio.sys ~/ecos-docker/data/minio/

# 2. 停止 WSL 原生 MinIO
kill 543092

# 3. 启动 Docker MinIO
cd ~/ecos-docker && docker compose up -d minio

# 4. 验证
curl -s http://localhost:9000/minio/health/live
# 访问 Console: http://localhost:9001
```

#### Phase 3: Neo4j 迁移（内存回收最大收益）

```bash
# 1. 停止 WSL 原生 Neo4j
kill $(pgrep -f Neo4jBoot)

# 2. 复制数据
cp -r /home/guorongxiao/neo4j/data/* ~/ecos-docker/data/neo4j/data/
cp -r /home/guorongxiao/neo4j/logs/* ~/ecos-docker/data/neo4j/logs/
cp /home/guorongxiao/neo4j/conf/neo4j.conf ~/ecos-docker/data/neo4j/conf/

# 3. 启动 Docker Neo4j
cd ~/ecos-docker && docker compose up -d neo4j

# 4. 等待就绪 (约30-60秒)
docker compose logs -f neo4j | grep -i "started"

# 5. 验证
curl -s http://localhost:7474
# 访问 Neo4j Browser: http://localhost:7474

# 6. 验证 Gateway 连接
# 检查 Gateway 日志，确认 Neo4j 连接正常
```

#### Phase 4: PostgreSQL 迁移（需停机窗口）

```bash
# === 步骤 1: 通知停机 ===
# 通知团队，停止 Gateway，确保无写入

# === 步骤 2: 停止 Gateway ===
kill 23287  # 或常规关闭

# === 步骤 3: 导出 Windows PostgreSQL 数据 ===
/mnt/c/Program\ Files/PostgreSQL/14/bin/pg_dump.exe \
  -h localhost -p 5432 -U postgres \
  -d sys_man -F c -f /tmp/sys_man_backup.dump

# === 步骤 4: 停止 Windows PostgreSQL (可选) ===
# net stop postgresql-x64-14  (在 Windows 管理员终端)

# === 步骤 5: 启动 Docker PostgreSQL ===
cd ~/ecos-docker && docker compose up -d postgres

# === 步骤 6: 等待 PostgreSQL 就绪 ===
until docker compose exec -T postgres pg_isready -U postgres; do
  echo "等待 PostgreSQL 就绪..."
  sleep 2
done

# === 步骤 7: 创建数据库和恢复数据 ===
docker compose exec -T postgres psql -U postgres -c "CREATE DATABASE sys_man;"
docker compose exec -T postgres pg_restore \
  -U postgres -d sys_man -c --if-exists \
  < /tmp/sys_man_backup.dump

# === 步骤 8: 创建扩展（如有需要） ===
docker compose exec postgres psql -U postgres -d sys_man \
  -c "CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\";"

# === 步骤 9: 重启 Gateway ===
# mvn spring-boot:run 或通过你的启动脚本

# === 步骤 10: 验证 ===
curl -s http://localhost:8080/api/health
docker compose exec postgres psql -U postgres -d sys_man -c "\dt"
```

### 7.3 一键启动脚本

```bash
#!/bin/bash
# ~/ecos-docker/scripts/start-all.sh
# ECOS 基础设施全部启动

set -e

cd ~/ecos-docker

echo "=== 启动 ECOS Docker 基础设施 ==="

# 启动所有容器
docker compose up -d

echo ""
echo "等待服务就绪..."

# 等待各服务健康检查通过
declare -A SERVICES=( ["postgres"]="30" ["neo4j"]="60" ["opa"]="5" ["minio"]="15" )

for svc in "${!SERVICES[@]}"; do
    timeout=${SERVICES[$svc]}
    echo "  等待 $svc (超时: ${timeout}s)..."
    
    for i in $(seq 1 $timeout); do
        status=$(docker compose ps --format json $svc 2>/dev/null | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('Health',''))" 2>/dev/null || echo "")
        if [ "$status" = "healthy" ]; then
            echo "    ✓ $svc 就绪"
            break
        fi
        sleep 1
    done
done

echo ""
echo "=== 服务状态 ==="
docker compose ps

echo ""
echo "=== 端口监听 ==="
echo "  PostgreSQL:  http://localhost:5432"
echo "  Neo4j HTTP:  http://localhost:7474"
echo "  Neo4j Bolt:  bolt://localhost:7687"
echo "  OPA:         http://localhost:8181"
echo "  MinIO API:   http://localhost:9000"
echo "  MinIO Console: http://localhost:9001"
echo ""
echo "✓ 基础设施启动完成！"
```

### 7.4 迁移时间估算

| 阶段 | 组件 | 预计时间 | 停机时间 |
|------|------|---------|---------|
| Phase 1 | OPA | 5 分钟 | 无 |
| Phase 2 | MinIO | 5 分钟 | 无（如无 S3 写入） |
| Phase 3 | Neo4j | 10 分钟 | 短暂（复制数据期间） |
| Phase 4 | PostgreSQL | 30 分钟 | **需要**（导出+导入期间） |
| **总计** | | **~50 分钟** | **~30 分钟（仅 Phase 4）** |

---

## 8. 回滚方案

### 8.1 快速回滚步骤

```bash
# 1. 停止所有 Docker 容器
cd ~/ecos-docker && docker compose down

# 2. 恢复启动 WSL 原生服务

# PostgreSQL (如果已停止 Windows 服务)
# net start postgresql-x64-14

# Neo4j
~/neo4j/bin/neo4j start

# OPA
~/opa run --server --addr localhost:8181 ~/opa-policies &

# MinIO
~/minio server start &

# 3. 验证
curl -s http://localhost:7474
curl -s http://localhost:8181/v1/data
curl -s http://localhost:9000/minio/health/live
```

### 8.2 数据回滚（如需恢复 Docker 数据到原生）

```bash
# Neo4j 回滚
cp -r ~/ecos-docker/data/neo4j/data/* ~/neo4j/data/

# OPA 策略回滚
cp ~/ecos-docker/data/opa-policies/*.rego ~/opa-policies/

# MinIO 回滚
cp -r ~/ecos-docker/data/minio/.minio.sys ~/start/
```

---

## 9. 附录

### 9.1 当前端口映射速查表

| 服务 | 端口 | Docker 映射 | 外部访问 |
|------|------|------------|---------|
| PostgreSQL | 5432 | 5432:5432 | `localhost:5432` |
| Neo4j HTTP | 7474 | 7474:7474 | `http://localhost:7474` |
| Neo4j Bolt | 7687 | 7687:7687 | `bolt://localhost:7687` |
| OPA | 8181 | 8181:8181 | `http://localhost:8181` |
| MinIO API | 9000 | 9000:9000 | `http://localhost:9000` |
| MinIO Console | 9001 | 9001:9001 | `http://localhost:9001` |
| MQTT | 1883 | N/A (嵌入式) | `mqtt://localhost:1883` |
| Gateway | 8080 | N/A (WSL) | `http://localhost:8080` |
| FE Vite | 5173 | N/A (WSL) | `http://localhost:5173` |

### 9.2 Docker 资源限制汇总

| 容器 | 内存限制 | CPU 预留 | 说明 |
|------|---------|---------|------|
| postgres | 512 MB | — | Alpine 镜像，轻量 |
| neo4j | 1 GB | — | JVM 堆 512m + page cache 256m |
| opa | 64 MB | — | 无状态策略引擎 |
| minio | 512 MB | — | 对象存储 |
| **合计** | **~2.1 GB** | | Docker Desktop 默认内存 2GB 可能需调整到 4GB |

> **建议**: 在 Docker Desktop Settings → Resources → Advanced 中将 Memory 调整为 **4 GB** 以上。

### 9.3 Neo4j 配置对照

| 原配置 (neo4j.conf) | Docker 环境变量 | 值 |
|-------------------|----------------|-----|
| `dbms.security.auth_enabled=false` | `NEO4J_AUTH=none` | 禁用认证 |
| `server.bolt.enabled=true` | 默认 | 启用 Bolt |
| `server.http.enabled=true` | 默认 | 启用 HTTP |
| `server.bolt.listen_address=:7687` | 默认 | Bolt 端口 |
| `server.http.listen_address=:7474` | 默认 | HTTP 端口 |
| `db.tx_log.rotation.retention_policy=2 days 2G` | `NEO4J_db_tx__log_rotation_retention__policy` | 2 days 2G |

> Neo4j 5.x Docker 环境变量映射规则: `dbms.security.auth_enabled` → `NEO4J_dbms_security_auth__enabled`  
> 注意: 配置键中的 `.` 和 `_` 均映射为 `__`（双下划线）。

### 9.4 健康检查端点

| 服务 | 健康检查 |
|------|---------|
| PostgreSQL | `pg_isready -U postgres -d sys_man` |
| Neo4j | `wget -qO- http://localhost:7474` |
| OPA | `wget -qO- http://localhost:8181/v1/data` |
| MinIO | `curl -f http://localhost:9000/minio/health/live` |
| Gateway | `curl http://localhost:8080/api/health` |

### 9.5 常见问题排查

**Q1: Docker 命令在 WSL 中不可用**
```
The command 'docker' could not be found in this WSL 2 distro.
```
**解决**: 在 Docker Desktop → Settings → Resources → WSL Integration 中启用当前发行版。

**Q2: 端口冲突**
```bash
# 检查端口占用
ss -tlnp | grep -E '(5432|7474|7687|8181|9000|9001)'

# 停止冲突的原生服务
kill <PID>
```

**Q3: Neo4j 容器启动失败 (内存不足)**
```yaml
# 在 docker-compose.yml 中降低内存限制
environment:
  NEO4J_dbms_memory_heap_max__size: "256m"
  NEO4J_dbms_memory_pagecache_size: "128m"
mem_limit: 768m
```

**Q4: PostgreSQL 连接被拒绝**
```bash
# 检查 pg_hba.conf 中的认证配置
docker compose exec postgres cat /var/lib/postgresql/data/pgdata/pg_hba.conf
# 确保有: host all all 0.0.0.0/0 md5
```

### 9.6 后续规划

| 阶段 | 内容 | 优先级 |
|------|------|--------|
| 短期 | 完成 Phase 1-4 迁移，稳定运行 | P0 |
| 中期 | Gateway 和 FE 也 Docker 化（生产部署） | P1 |
| 中期 | 添加 Redis 容器（缓存）、Kafka 容器（事件驱动） | P1 |
| 长期 | 迁移至 Kubernetes（docker-compose → K8s manifests） | P2 |
| 长期 | 生产环境高可用部署（主从复制、集群） | P2 |

---

> **文档状态**: 待评审  
> **下一步**: 团队评审 → 修正 → 审批 → 执行 Phase 1 (OPA 迁移) 作为试点
