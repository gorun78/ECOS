# runtime-access (器·基础设施统一访问) 接口与验收 flows

> 横切底座·器 | cloudless (随 gateway fat-JAR) | 秋收 Driver/Client 收敛点
> 源码: Connector (Jdbc/RestApi/Csv) / MinioStorageService / GitRepositoryService / DuckDBQueryService / Neo4jConfig

## 接入 flows
client (任引擎/服务) → 注入 Bean (ConnectorFactory / MinioStorageService / Neo4jClient / GitRepositoryService) → 访问 PG/MinIO/Git/Neo4j/DuckDB → 回结果。
runtime-access 是 Java-in-Java 库 (无 own REST), 全仓每引擎 new Driver 统一收敛到这里 — 新增调用方只注入, 不 new。

## 主 API (curl)
runtime-access 无 own REST; 经 gateway `GitController` (`/api/v1/ecos/git/*`, 唯一 REST 曝光点) 入口:
```bash
curl -s "http://localhost:8080/api/v1/ecos/git/status?repoId=ontology" -H "Authorization: Bearer $TOKEN"
curl -s -X POST "http://localhost:8080/api/v1/ecos/git/commit?repoId=ontology" -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"message":"chore(d04): 物化同步"}'
curl -s "http://localhost:8080/api/v1/ecos/git/branches?repoId=ontology" -H "Authorization: Bearer $TOKEN"
```

## 接 DB 表
`ecos_git_repo` (Git 仓库元数据); PG 经 Connector 统一连接 (仓库根路径读 `sys_config.ecos_git_repo_root`); MinIO/Doris 凭据全走配置注入, 不在代码字面量。

## 别接 (调谁, 已核)
- 各引擎禁止 `new org.neo4j.driver.Driver` / `new MinioClient` / 自建 JdbcTemplate — 一律注入本模块 Bean (铁律 2.5 #1)
- 不新增 driver 依赖进各引擎 pom (只进 runtime-access), 不新建 Docker 容器
- LLM/调度/监控不是我们 (llm-gateway / runtime-task / runtime-monitor 各归位)

## 验收 flows
`GitController` 8 端点 (status/commits/diff/commit/tag/rollback/branches/branch) 全 200 且 `status.repoId` 回显;
新建连接器调用方 (如 XX-ACCESS-MIGRATION 迁移后) grep 全仓 `new GraphDatabaseDriver|new MinioClient` 仅命中 runtime-access 内部 (豁免清单) — 目标: 除豁免外 0 命中。
