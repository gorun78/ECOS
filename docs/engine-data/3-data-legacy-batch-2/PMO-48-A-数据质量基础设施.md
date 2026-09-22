# PMO-48-A: 数据质量基础设施（Phase 1 落地）

> **架构铁律**: 必须遵循 [ECOS架构铁律](../../.trae/rules/架构铁律.md)
> 来源: 肖国荣
> 日期: 2026-09-10
> 上游方案: [PMO-48 数据质量管理方案 v1.1-decided](./PMO-48-数据质量管理方案.md)
> 状态: **执行中**
> 铁律（本指令硬约束）:
>   1. 所有新表归属 PG schema `ecos_dq`，Flyway 迁移，schema 只加不删
>   2. 每新增 Controller 必经三滤波器（铁律 1.2 #1-#5）
>   3. 旧 `ecos_dq_rule_v2` / `ecos_quality_rule` 双轨完全合并到 `ecos_dq.dq_rule`，旧端点保留**只读兼容**，合并期间禁止向旧表写入
>   4. 安全集成：DQ 写操作走全局审计（铁律 2.4 #5），敏感值响应前 `mask` 脱敏（铁律 2.4 #3）
>   5. 不新增 Maven 模块、不新增 Docker 容器

---

## §背景

PMO-48 是 ECOS 数据质量管理完整蓝图（8 模块 / 5 Phase / 本 PMO 为 Phase 1「基础设施」）。Phase 1 只做三件事：**DB 迁移 + 三滤波器接入 + 双轨合并只读兼容层**。不做业务逻辑（评分/告警/工单/知识库存 Phase 3-4）。

### 现状双轨

| 旧实现 | 表 | 现状 |
|:--|:--|:--|
| `DqController` (/api/v1/ecos/dq) | `ecos_dq_rule_v2` + `ecos_dq_issue` | 5 类 ruleType，无分类/生命周期/版本 |
| `QualityController` (/api/v1/engine/data/quality) | `ecos_quality_rule` + `ecos_quality_evaluation` | 7 类规则评估器 |
| `ecos_quality_execution` (ecos_dq_execution_result via V26) | `ecos_dq_execution_result` | 评估执行历史 |

### 决策 #1 落地点：双轨合并

旧表 → 新 `ecos_dq.dq_rule` 一次性数据迁移（保留原 ID + category 默认 TECHNICAL + status 默认 ACTIVE + version=1）。旧 Controller 改为只读：

| 旧端点 | 行为变更 |
|:--|:--|
| `GET /api/v1/ecos/dq/rules` | 仍读旧表 `ecos_dq_rule_v2`（只读） |
| `POST/PUT/DELETE /api/v1/ecos/dq/rules*` | 返回 `410 Gone`，body 提示切换到 `/api/v1/dq/rules` |
| `GET /api/v1/engine/data/quality/rules` | 仍读 `ecos_quality_rule`（只读） |
| 写入端点 | 返回 410，提示切 `/api/v1/dq/rules` |

新读写端点（本指令范围）：
| Method | Path | 用途 |
|:--|:--|:--|
| `GET` | `/api/v1/dq/rules` | 列表（category/status/domain 过滤 + 分页） |
| `GET` | `/api/v1/dq/rules/{id}` | 详情 + 版本历史 |
| `GET` | `/api/v1/dq/rules/dimension-registry` | 6 维 rule_type→dimension 映射注册表 |
| `POST` | `/api/v1/dq/health/selfcheck` | DQ 模块自检（schema + 三滤波器 + Mask/Audit 调用） |

> 完整生命周期（submit/approve/reject/deprecate/supersede）→ Phase 2（PMO-48-B）；评分/告警/工单/知识库 → Phase 3-4。本指令只交付"基础骨架 + 数据迁移 + 只读兼容"。

---

## §禁止清单（铁律 5.1 + 本指令特有）

1. 不跨 Phase 预创建 Phase 2+ 文件（评分/告警/工单 Controller/Service 禁止提前建）
2. 不新增 Maven 模块 / Docker 容器
3. 不 `implements` 已有 Service 接口（Bean 冲突）
4. 不改 `VersionPrefixRewriteFilter` 既有条目，只**追加** `/api/v1/dq/**` REMOVE 重写
5. 不物理删除旧表 `ecos_dq_rule_v2` / `ecos_quality_rule`（只加不删）
6. 不 `throws Exception`，抛 `DataBridgeException` 子类
7. 不接受 `Map<String,Object>` 入参出参（用强类型 DTO）
8. Entity 用 Lombok `@Getter+@Setter+@NoArgsConstructor`，DTO 用 `@Data`
9. 控制流 if/for/while 必须花括号（IDEA 红线）
10. 不绕过 runtime-access 直接 new Driver（铁律 2.5 #1）
11. 前端不硬编码颜色 / 不硬编码中文图标 / 大文件超 800 行（铁律 4.1/4.2/4.6）
12. 不跳过前端编译门（esbuild/tsc 必须跑，铁律 5.1 #11）
13. 不创建临时脚本（探测用 RunCommand 内联，构建用现有脚本，铁律 5.1 #14）
14. 旧端点写操作必须 410 拒绝（双轨合并硬约束，决策 #1）

---

## §Task

| Task | 文件/路径 | 操作 | curl / 浏览器 E2E 验收 |
|:--|:--|:--|:--|
| **T1 DB 迁移** | `gateway/src/main/resources/db/migration/V110__ecos_dq_governance_v1.sql` | 建 schema `ecos_dq` + 5 新表（`dq_rule`/`dq_rule_version`/`dq_rule_check`/`dq_alert_record`/`dq_work_order`）+ 旧表数据迁移到 `dq_rule`（保留原 ID，category 默认 TECHNICAL，status 默认 ACTIVE，version=1）+ 索引 + 不删旧表 | `docker exec -i ecos-postgres psql -U postgres -d sys_man -tAc "SELECT count(*) FROM ecos_dq.dq_rule"` ≥ 1；`SELECT category,status from ecos_dq.dq_rule limit 5` 有值 |
| **T2 三滤波器** | `gateway/.../VersionPrefixRewriteFilter.java` + `sysman/.../security/SecurityConfig.java` + `sysman/.../security/ClearanceInterceptor.java` + `application.yml` | 追加 `/api/v1/dq/**` REMOVE 重写；permitAll `/api/v1/dq/**` **双路径**；ClearanceInterceptor 豁免加双路径；auth.whitelist.paths 注册 | `curl http://localhost:8080/api/v1/dq/rules` 非 404/403 |
| **T3 新 DQ Controller** | `data-engine-.../quality/controller/DqGovernanceController.java` + `DqRuleMapper` + `DqRuleDTO/VO` | `/api/v1/dq/rules` 读 `ecos_dq.dq_rule`；`dimension-registry` 返回 6 维映射；写操作返回 405（Phase 2 再开）；DTO 强类型，禁 Map | `curl http://localhost:8080/api/v1/dq/rules` 返回 `ApiResponse.success(data)` 且 code=200 |
| **T4 旧端点只读改造** | `.../quality/controller/DqController.java` + `.../data/controller/QualityController.java` | 写端点（POST/PUT/DELETE）改返回 `ApiResponse.gone("DQ 规则已迁移到 /api/v1/dq/rules")`；GET 只读保持读旧表 | `curl -X POST http://localhost:8080/api/v1/ecos/dq/rules` 返回 code=410；`curl -X GET .../rules` 200 仍可查 |
| **T5 前端路由 + Tab** | `ecos_frontend/src/main.tsx` + 新建 `src/pages/DataQualityDashboard.tsx` + 改造 `HealthTab.tsx` | main.tsx 注册 `<Route path="dq_dashboard" element={DataQualityDashboard} />`（lazy）；DataQualityDashboard 顶部规则中心 Tab + 自检 Tab；i18n 扩展 `dw.dqRule.*`；主题/图标合规 | 浏览器 `http://localhost:3000/#/dq_dashboard` 渲染非 404（path=* fallback 不触发）；console 无 error；network 无 4xx/5xx（favicon 可忽略） |
| **T6 selfcheck** | `.../quality/controller/DqSelfCheckController.java` | `POST /api/v1/dq/health/selfcheck` 返回 schema 存在性（5 表）+ 三滤波器 403/404 自检 + `maskHandler`/`auditHandler` 构造器注入命中验证 | `curl -X POST http://localhost:8080/api/v1/dq/health/selfcheck` 返回 5 表 exists=true + security_engines_loaded=true |

### 安全集成硬卡（铁律 2.4 #7 验收必过）

T3/T4/T6 的安全集成项（task card 硬项，verifier 用 grep 计数）：
- [ ] DqGovernanceController 写操作/响应前调 `securityEngine.mask(...)` 脱敏敏感字段（手机号/身份证/银行卡/金额）— 验收：本模块 `grep -E "mask|ISecretService|SecurityEngine"` ≥ 1
- [ ] 所有写操作（含 410 拒绝）异步调 `POST /api/v1/security/audit/log` — 验收：`grep -E "audit|AuditLog|auditLogService"` ≥ 1
- [ ] 三滤波器 4 文件均修改到位（V1_REWRITE_MAP + SecurityConfig 双路径 + ClearanceInterceptor 双路径 + application.yml）— 验收：4 文件 diff 均含 `/api/v1/dq`

### 验证四步法（V1-V4）

```
V1: 文件生存检查   — find -newer 确认 5 DDL + 3 滤波器 + Controller + 前端文件落盘
V2: 集成点 grep     — 注入/注册/调用链（DqGovernanceController 注入 DqRuleMapper + securityEngine；旧 Controller 410 改造）
V3: 编译           — 后端 mvn install -DskipTests（非 compile）；前端 npx tsc --noEmit + npx eslint
V4: Gateway启动+curl — start-gateway.ps1 起 8080；curl T2/T3/T4/T6；前端浏览器 E2E（T5）
```

### 工期

| Task | 工期 |
|:--|:--:|
| T1 DB 迁移 | 1 天 |
| T2 三滤波器 | 1 天 |
| T3 新 Controller | 1.5 天 |
| T4 旧端点只读 | 0.5 天 |
| T5 前端路由+Tab | 1.5 天 |
| T6 selfcheck | 0.5 天 |
| 合计 | **6 人日（跨 3 天）** |

### 风险与回退

- 双轨数据迁移旧表非结构化（`ecos_dq_rule_v2` 与 V6 `ecos_dq_rule` 字段不一致）→ 迁移脚本加防御性 COALESCE，迁移后跑 `count(*)` 对比旧表
- 三滤波器漏改 → T2 单独 curl 验收（404=rewrite 漏；403=SecurityConfig/Clearance 漏）
- 前端 `/dq_dashboard` 仍 404 → main.tsx Route 未注册，V4 浏览器 E2E 必查

### 产出物清单

- `gateway/.../db/migration/V110__ecos_dq_governance_v1.sql`
- `data-engine-.../quality/controller/DqGovernanceController.java` + `DqSelfCheckController.java`
- `data-engine-.../quality/mapper/DqRuleMapper.java` + DTO/VO
- `gateway/.../VersionPrefixRewriteFilter.java`（追加）
- `sysman/.../security/SecurityConfig.java` + `ClearanceInterceptor.java`（追加双路径）
- `ecos_frontend/src/main.tsx`（Route 注册）+ `src/pages/DataQualityDashboard.tsx`
- `data-engine/AGENTS.md` 段："DQ 治理 API 路径表 + 三滤波器 + 安全集成"
- `ecos-backend.zip` / `ecos-frontend.zip`（归档）
- `api-be.zip`（curl 报告）/ `api-fe.zip`（openapi 增量）
