# 02-1 — Wave-2B ge (D→I) 数据→信息转换收口记录

> **PMO 域**: 3-data (ge D→I) | **波次**: Wave-2B | **日期**: 2026-09-02
> **关联指令**: PMO-21 data-engine 后端补齐（数据治理 Phase 1-2 已交付）
> **关联设计**: `docs/5-cognitive/03-跨引擎编排层设计.md` §三/四（OAG 8 步节点）
> **范围**: ge 是"格"——数据→信息转化服务，本次收口交付 data-engine 内 6 步 D→I 转换能力的 HTTP 暴露层（G2 文档 §3.5 `catalog GET /data/transform` 等价端点）
> **上游铁律引用**: [架构铁律](../../ARCHITECTURE-RULES.md) §1.2 / §2.4 / §3.3

---

## §1 执行概要

| 项 | 值 |
|:--|:--|
| **5 端点实现状态** | 见 §2 |
| **T4 实现端点** | `GET /api/v1/engine/data/transform/meta` + `POST /api/v1/engine/data/transform/execute` |
| **单测数量 / 通过** | 5 / 5（`Tests run: 5, Failures: 0, Errors: 0, Skipped: 0` 3.318s） |
| **V3 编译** | `mvn install ... -pl data-engine-impl -am` exit=0 (122s) + `mvn test ... -Dtest=TransformControllerTest` exit=0 |
| **三滤波器** | 零改动 — 路径在 `/api/v1/engine/**` permitAll + ClearanceInterceptor 豁免 |
| **新增文件** | 3（Controller + Service 注解 + 测试）+ 1 (集成 smoke .mjs) |
| **P0 风险** | 0 |
| **判定** | ✅ 收口达成 ge 域的"转换能力 API 化"基线 |

---

## §2 5 端点实现状态盘点

> G2 文档 §12 看齐 5 端点（任务指令语义）。"5 端点"中 4 个**不在 data-engine**（属 cognitive-engine 域认知/因果/域检索），本次执行范围为"1 个"——`catalog GET /data/transform`（在 data-engine 域，ge 的本职）。

| # | 端点 | 域 | 实现状态 | 责任模块 |
|:--:|------|:--:|:--:|:--|
| 1 | `catalog GET /data/transform` → `/api/v1/engine/data/transform/meta` + `/execute` | data-engine (ge) | 🆕 **本次实现** | `engine/data-engine/data-engine-impl/.../transform/controller/TransformController.java` |
| 2 | `cognition/domain/search` — 实现在 cognitive-engine（cognition c2） | cognitive-engine | ✅ 已由 PMO-32/33 落地 | 不在本次范围 |
| 3 | `cognition/domains/{id}/infer` — 推理 API | cognitive-engine | ✅ 已由 PMO-33 落地 | 不在本次范围 |
| 4 | `cognition/causality/parse` — 因果解析 | cognitive-engine | ✅ 已由 PMO-20 落地 | 不在本次范围 |
| 5 | Hologres → MinIO OSS 改造 (G1) | data-engine | ✅ 已收敛到 runtime-access（PMO-R3 数据工程迁移返工） | 不在本次范围 |

**结论**: Wave-2B 实际工作 = 端点 #1 的 D→I 转换 HTTP 化。

---

## §3 跨引擎引用骨架（节选自 §0.2 架构铁律）

ge (D→I) 是服务层"格"，**不**在 data-engine 引擎内独立 Open 启动。骨架：

```
原始行 (data-engine 数据源) 
    ↓ QueryController + QueryExecutionService（已交付，PMO-21）
DataFrame { columns: List<String>, rows: List<Map<String,Object>> }
    ↓
[TransformController 暴露的 /execute]
    ↓
TransformChain 6 步串联（DataCleansing | FieldMapping | TypeConversion | DataValidation | DataAggregation | Calculator）
    ↓
TransformResult { output: DataFrame, statistics, errors, warnings }
    ↓
ontology-engine 本体对象建模（zhi 致，I 信息层 / 金）
```

**严格**: 跨引擎只调 API（铁律 2.1），ge 不新增 DB 表，数据读取仅是 sidecar。

---

## §4 新增 / 修改文件清单

| 文件 | 操作 | 说明 |
|:--|:--:|:--|
| `ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/transform/controller/TransformController.java` | 🆕 | ge D→I 转换 API 暴露层（GET /meta + POST /execute） |
| `ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/transform/service/impl/TransformServiceImpl.java` | ✏️ | 加 `@Service` Spring Bean 注册（Spring 找不到 Bean 的启动性补缺） |
| `ecos_backend/engine/data-engine/data-engine-impl/src/test/java/com/chinacreator/gzcm/engine/data/transform/controller/TransformControllerTest.java` | 🆕 | 5 个纯 JUnit 单测 |
| `docs/7-integration/03-data/TransformSmoke.mjs` | 🆕 | 集成 smoke (Node 24+, fetch, 无 Playwright) |

**未修改**：`data-engine-api` / 三滤波器 (`VersionPrefixRewriteFilter` `SecurityConfig` `ClearanceInterceptor`) / `GatewayApplication` excludeFilters / 其他引擎模块 — 全部零改动。

---

## §5 T4 端点规格

### 5.1 `GET /api/v1/engine/data/transform/meta`

| 项 | 值 |
|:--|:--|
| Method / Path | `GET /api/v1/engine/data/transform/meta` |
| 返回 | `ApiResponse<Map<String,Object>>` |
| 数据体 | `{ totalSteps: 6, availableSteps: [{type,name,description}×6] }` |
| 6 类 step | cleansing / mapping / typeConversion / validation / aggregation / calculator |
| 三滤波器 | `/api/v1/engine/**` permitAll (SecurityConfig line 72) + ClearanceInterceptor line 107 |

**curl 验收**：
```bash
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin).get('data',{}).get('accessToken',''))")

curl -s -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/v1/engine/data/transform/meta \
  | python3 -c "import sys,json; d=json.load(sys.stdin); steps=d.get('data',{}).get('availableSteps',[]); \
      print('PASS: 6 steps' if len(steps)==6 else f'FAIL: {len(steps)} steps')"
# 期望: PASS: 6 steps
```

### 5.2 `POST /api/v1/engine/data/transform/execute`

| 项 | 值 |
|:--|:--|
| Method / Path | `POST /api/v1/engine/data/transform/execute` |
| 入参 | `{ input: { columns: [...], rows: [...] }, chain: [{ type: "...", params: {...} }] }` |
| 合法 type | cleansing / mapping / typeConversion / validation / aggregation / calculator |
| 返回 | `ApiResponse<Map<String,Object>>`，data 含 `output/columns/rows`、`success`、`errors[]`、`warnings[]`、`statistics{inputCount/outputCount/filteredCount/errorCount}` |
| 错误 | 400：body null / input 非 Map / chain 非 List / 未知 step type / step transform 异常 |

**curl 验收**：
```bash
curl -s -H "Authorization: Bearer $TOKEN" \
  -X POST http://localhost:8080/api/v1/engine/data/transform/execute \
  -H 'Content-Type: application/json' \
  -d '{
    "input": {
      "columns": ["raw_name","raw_age"],
      "rows": [
        {"raw_name":"  张三  ","raw_age":30},
        {"raw_name":"李四","raw_age":25}
      ]
    },
    "chain": [
      {"type":"cleansing","params":{"trimWhitespace":true}},
      {"type":"mapping","params":{"mapping":{"raw_name":"name","raw_age":"age"},"keepUnmapped":true}}
    ]
  }' \
  | python3 -c "import sys,json; d=json.load(sys.stdin); \
        rows=d.get('data',{}).get('output',{}).get('rows',[]); r0=rows[0] if rows else {}; \
        print('PASS' if r0.get('name')=='张三' else f'FAIL: {r0}')"
# 期望: PASS
```

---

## §6 验证（四步法 §5.4）

| 步骤 | 命令 | 结果 |
|:--|------|:--|
| V1 文件生存 | `find data-engine-impl/src/main -name "TransformController.java"` | ✅ |
| V2 集成点 grep | `grep -rln "ITransformService" data-engine-impl/src/main` | ✅ 2 文件 (Controller + ServiceImpl) |
| V3 编译 | `mvn install -pl data-engine-impl -am -DskipTests -q` (env -i 绕 UNC bug) | ✅ exit=0, 122s |
| V3-test 单测 | `mvn test ... -Dtest=TransformControllerTest -q` | ✅ 5/5 PASS |
| V4 Gateway curl | 需 `~/start-gateway.sh` enterprise profile + PG 可达后跑 | ⏳ 待集成 smoke 执行 (见 §7) |

> **V3 实现**: 因 Hermes 重定 HOME 致 Maven UNC 双写 bug，绕法 `env -i HOME=/home/guorongxiao ... bash _wave2b-inner.sh`（架构铁律 §6 已沉淀）。

---

## §7 Wave-2B 责任边界（≠ 范围）

| 已交付 | 未交付 (待 Ge 域后续 PMO) |
|:--|:--|
| 6 步 Transform 链 API 化 (`clensing/mapping/typeConversion/validation/aggregation/calculator`) | ObjectTypeClassifier 4 级 (ENTITY/ATTRIBUTE/RELATION/EVENT/CONCEPT) 骨架 — 继承自 5-cognitive §三 的设计，需 `ge/ObjectTypeClassifier.java` 收敛（建议 Wave-2C） |
| `/api/v1/engine/data/transform/meta+execute` 暴露 + 三滤波器零改 | 5 端点中 #2 #3 #4 (cognition 域) — 不归 data-engine 管，由 cognitive-engine 域 PMO-33 链负责 |
| `TransformServiceImpl @Service` Spring 注册 | 前端数据工作台 transform 可视化 (V2 改前端，建议 Wave-2B 后续) |
| 5 单测 + 1 .mjs smoke 脚本 | 端到端 `.mjs` 在 Gateway 跑通 (仅 .mjs 脚本已落，运行需 WSL `~/start-gateway.sh` + Node 24) |
| 集成 smoke .mjs 脚本 | 不新建 Maven 模块 / 不提新 Docker 容器 / 不 put 新 DB 表 (铁律遵守) |

---

## §8 与下游衔接（C REQ）

- 本收口落 `docs/7-integration/03-data/05-验收记录.md`，是 03-data 集成验收门 **C REQ** 的前排依据。
- 1.数据治理 Phase 1-2 (PMO-21/23/25/37) 的 QueryController + CatalogService 已提供 D 侧（数据读）；本记录确认 D→I 转换链路 API 化。
- 4-onto 域 (zhi 致 I→K) 的 ontology-engine 待新增 `POST /api/v1/ontology/entities` 调用 `/transform/execute` 的 `I 信息` 输出做本体落地时，本端点是数据侧唯一 API 入口。

---

## §9 工件版本与追溯

| 工件 | 仓库路径 | 状态 |
|:--|:--|:--:|
| TransformController | `ecos_backend/engine/data-engine/data-engine-impl/.../transform/controller/TransformController.java` | 新建 |
| TransformService 注册 | `.../transform/service/impl/TransformServiceImpl.java` (line 11 `@Service`) | 改 +9 |
| TransformControllerTest | `.../src/test/.../transform/controller/TransformControllerTest.java` | 新建 (192 lines) |
| TransformSmoke.mjs | `docs/7-integration/03-data/TransformSmoke.mjs` | 新建 (~170 lines) |
| 验证脚本 (临时) | `ecos_backend/ecos-gen-scratch/wave2b-ge-verify2.sh` 等 (归档下可删) | 工具用 |
| 验收记录 (C REQ) | `docs/7-integration/03-data/05-验收记录.md` | 新增 |

**推断版本标签**：`wave2b-ge-DtoI-v1.0`（本次代码产物可合并到 `dev` 分支后，按 Git 提交规范打 commit `feat(数据·ge): 暴露 D→I 转换 Pipeline 端点 暴露 /api/v1/engine/data/transform`）。

<!-- ECOS_WAVE2B_GE_DTOI / 02-1 / 3-data / 2026-09-02 / v1.0 -->
