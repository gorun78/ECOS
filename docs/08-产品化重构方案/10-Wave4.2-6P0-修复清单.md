# Wave-4.2 启动前置 — 6 P0 修复清单

> 版本: 1.0 | 2026-09-02 | 状态: 未修 | 来源: Wave-4.1 联调 (v3/v5 两轮 24/34)
> 验收: 修完 6 项后重跑 [06-Wave4-1-7域联调报告.md §8.6](./06-Wave4-1-7域联调报告.md) 72h Soak 准入门槛

## 0. 漏到 Wave-4.2 才算完成

Wave-4.1 主线程 (R2) **P0-1 已修** (super-admin bypass 1 行), 重跑后:
- v3 → v5 整体从 46.7% → 41.4% (反降 4 项)
- 原因: v3 跑清 admin 视角的 boundary (403 = fail-closed 正确), v5 后 super-admin 通到深层 → 暴露 P0-5
- **不要看 % 倒退** — 这是测试深度加强, 真 P0 数从 3 → 7 (它变得更严格)

## 1. 6 P0 逐项修复 (按复杂度排序)

### P0-2 (5 分钟): TransformStatistics 4 字段全 0

- **文件**: [TransformServiceImpl.java](file:///wsl%24/Ubuntu/home/guorongxiao/ECOS/ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/transform/service/TransformServiceImpl.java)
- **问题**: Wave-2B 子代理 mock 了 service, 4 个 statistic 字段 (`inputCount` / `outputCount` / `filteredCount` / `errorCount`) 在真实调用时没 setter, 全 0
- **修复**: 在 main transformer 实处理 加 4 处统计调用
- **不要 加 新列** (已存在)

### P0-5 (5 分钟): ecos_domain 缺 tenant_id 列

- **文件**: `gateway/src/main/resources/db/migration/V4.5__ecos_domain_tenant.sql`
- **问题**: [V4.4__ecos_ontology_rls.sql](file:///wsl%24/Ubuntu/home/guorongxiao/ECOS/ecos_backend/gateway/src/main/resources/db/migration) 只 ECS 丰收了 `ecos_ontology_entity` + `ecos_ontology` 的 tenant_id, **没补 `ecos_domain`**
- [OntologyDomainRepository.java](file:///wsl%24/Ubuntu/home/guorongxiao/ECOS/ecos_backend/engine/ontology-engine/ontology-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ontology/repository/OntologyDomainRepository.java) RLS 重写加 `WHERE tenant_id = ?` → 查询全部 500 (列不存在)
- **修复**: 加 V4.5:
```sql
ALTER TABLE ecos_domain ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64);
CREATE INDEX IF NOT EXISTS idx_ecos_domain_tenant ON ecos_domain(tenant_id);
UPDATE ecos_domain SET tenant_id = 'tenant-a' WHERE tenant_id IS NULL;
```

### P0-6 (5 分钟): 探针打错路径 (不是 bug, 修测试)

- **文件**: [01-sysman.mjs T4 探针](file:///wsl%24/Ubuntu/home/guorongxiao/ECOS/ecos-tests/integration/wave4/01-sysman.mjs)
- **问题**: 01-sysman 测试 抵头打 `/api/v1/ecos/objects/customer` 想看是否 fail-closed, 实为 404 (该路径不存在, 不是 403)
- **修复**: 改测试指向 `/api/v1/ecos/customer` 或 `/api/v1/ecos/objects/1` (现存的 默认租户 customer 对象 查回 403)
- **不要改 Java**

### P0-4 (30 分钟): ComplianceRule long vs TIMESTAMP

- **文件**: [ComplianceRule.java](file:///wsl%24/Ubuntu/home/guorongxiao/ECOS/ecos_backup/engine/kb-engine/kb-engine-api/src/main/java/com/chinacreator/gzcm/engine/kb/model/ComplianceRule.java) (extends ExpertRule)
- **父类**: [ExpertRule.java](file:///wsl%24/Ubuntu/home/guorongxiao/ECOS/ecos_backup/engine/kb-engine/kb-engine-api/src/main/java/com/chinacreator/gzcm/engine/kb/model/ExpertRule.java) createdAt/updatedAt 用 `long`
- **问题**: PG `sys_compliance_rule.created_at TIMESTAMP` → PreparedStatement `setLong(1, "2026-08-20 08:58:34.5368")` → `PSQLException: Bad value for type long`
- **修复**: 改 ExpertRule 2 字段 `long` → `java.time.LocalDateTime` (迁移 PG TIMESTAMP 原生)
- **不要 ALTER 列** (schema 只加不删, 但 这种 8 字类的 "type 互换" 不破坏签名, 是 修正代码)
- **建议 顺便 压紧 (Wave-4.2 必)**: 加 `@JsonFormat` for Instant

### P0-3' (30 分钟): QuotaFilter body 不可重读 修 P0-3 旧

- **文件**: [QuotaFilter.java L137-L175](file:///wsl%24/Ubuntu/home/guorongxiao/ECOS/ecos_backend/gateway/src/main/java/com/chinacreator/gzcm/gateway/filter/QuotaFilter.java) — doFilterInternal 写 `INSERT ... RETURNING used_count WHERE used_count < ?` + `catch (Exception) chain.doFilter`
- **根因**: 我之前修的 `chain.doFilter` 强转之后 body 已 read → Spring `@RequestBody` EOF → 400 `Required request body is missing`
- **2 个修**:
  - 修 A (优选): 用 [CachedBodyHttpServletRequest](file:///wsl%24/Ubuntu/home/guorongxiao/ECOS/ecos_backend/gateway/src/main/java/com/chinacreator/gzcm/gateway/filter/CachedBodyHttpServletRequest.java) 包装, 读 body 后可重复
  - 修 B (备选): catch 时 不 retry, 直接 500 (QuotaFilter 不可用不应阻塞业务)
- **可能要**: 看 gateway 是否已有 cached body helper (可能在 VersionPrefixRewriteFilter 用的)

### P0-3 旧 (5 分钟): statistics 0 + 400 body 责任分离 (Wave-4.2 后 读取用 看 test log)

- 实际 P0-2 + P0-3' 合并解决, 这里仅是 戏剧
- **不是独立 6 项**, 实际 5 项需修

## 2. Wave-4.2 准入指标

| 准入 | 验收 |
|:--:|:--:|
| 编译 | `mvn install -P enterprise -Dmaven.test.skip=true -q` exit 0 |
| 单测 | `mvn test -pl ssysman,engine/data-engine,engine/kb-engine,engine/cognitive-engine,engine/ontology-engine` 0 failures |
| 7 mjs PASS | 24/34 ≥ 30/34 (不追 100%, 追 5 Contract) |
| 5 Contract 出现 | ReasoningPath Step RuleRef PrecedentRef Justification 全部在 05-cognitive-w3 真实产物 |
| 跨域 7 步 | 07-cross-domain PASS = 7/7 |
| 故障率 | 7 域全 0 500 (P0-4/P0-5 修后) |
| 72h Soak | 内存不回灌 + Neo4j 拷贝策略 + Doris 单表 >100 万行 列存 |

## 3. 不可 skip 在 Wave-4.2

- **v2.0 release** 必须 Wave-4.2 Soak 过之后 (08-产品化重构方案/06 §里程碑)
- P2 (3 项) 可以 Wave-4.2 后处理 (P2-2 Transform TODO D4 / P2-3 Runtime-core test compile / P2-4 Front 303 tsc / P2-5 5 处 null guard)
- P3 (3 项) 长期 / 优化

## 4. 留给 KV review (合并 MR 前)

| 项 | 评审点 |
|:--|:--|
| 09-REVIEW_REPORT.md | 6 域是否还有新 P1 (基于 Wave-4.1 复跑) |
| 06-Wave4-1 §8 Re-run | super-admin bypass 需 audit log (本次没加) |
| 11. Wave-4.2 后 需 commit 命令 | 见 05 §5 |

---
> 总说明: 本文件是 Wave-4.2 启动的"路线图", 你 review 后 直接做 Wave-4.2 (主线程 30-40 min 完成 5 P0), 然后子代理跑 Wave-4.2 (72h Soak)**. 本次会话到 Wave-4.1 收口完结.
