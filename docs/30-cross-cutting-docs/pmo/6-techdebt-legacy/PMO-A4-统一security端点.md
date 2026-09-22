# PMO-A4: 统一 security 端点（删 sysman 侧副本）

> **架构铁律**: 必须遵循 [ECOS架构铁律](../../ARCHITECTURE-RULES.md)
> **来源**: 肖国荣 | **日期**: 2026-08-21
> **协同**: ECOS-BE
> **铁律**: ①security-engine（护）是安全端点唯一权威，sysman 侧副本已失效 ②只删不迁 ③每 Task 独立 commit

## §背景

sysman 侧 7 个安全 Controller 已被 security-engine 接管，`GatewayApplication` 的 `excludeFilters`（第 54-60 行）已排除它们（不生效，属死副本）。security-engine 侧有对应权威实现。

冲突实锤：`/api/v1/policy-engine` 被 `sysman/PolicyEngineController`（旧，对接 OPA localhost:8181）和 `security-engine/PolicyEngineController`（权威，`@RequestMapping({"/api/v1/policy-engine", "/api/v1/security/policy-engine"})`）双重映射。

## §Task

| Task | 内容 | 验收 |
|:--|------|------|
| T1 | `git rm` sysman 侧 7 个安全副本 Controller（清单见下） | 文件不存在 |
| T2 | `GatewayApplication.java` excludeFilters 里删除对应 7 处 sysman 排除（第 54-60 行） | gateway 编译通过 |
| T3 | 全量 `mvn install -DskipTests` | BUILD SUCCESS |

### T1 删除清单（7 个，`sysman/sysman-impl/.../sysman/controller/` 下）

| 文件 | security-engine 对应 |
|------|------|
| PolicyEngineController.java | engine/security/controller/PolicyEngineController |
| AbacController.java | engine/security/controller/AbacController |
| AuditController.java | engine/security/controller/AuditController |
| CryptoAuditController.java | engine/security/controller/CryptoAuditController |
| DataMaskingController.java | engine/security/controller/DataMaskingController |
| DataPermissionController.java | engine/security/controller/DataPermissionController |
| SecurityConfigController.java | engine/security/controller/SecurityConfigController |

**保留**：`SecurityProfileController.java`（不在 excludeFilters 排除列表，仍在生效，非副本，本次不删）。

## §禁止清单

1. ❌ 不碰 security-engine 的任何 Controller（权威保留）
2. ❌ 不删 SecurityProfileController（未确认副本，保留）
3. ❌ 不改 excludeFilters 里其他排除项（datanet/dccheng/buszhi/portal/market/worldmodel/engine 等保留）

## §验证门禁

```bash
# V1: sysman 无 7 个安全副本
for c in PolicyEngineController AbacController AuditController CryptoAuditController DataMaskingController DataPermissionController SecurityConfigController; do
  test ! -f "/home/guorongxiao/ECOS/ecos_backend/sysman/sysman-impl/src/main/java/com/chinacreator/gzcm/sysman/controller/${c}.java" || echo "残留: ${c}"
done
# 期望: 无输出

# V2: 全量编译
env -i HOME=/home/guorongxiao \
  PATH=/usr/bin:/usr/local/bin:/home/guorongxiao/.local/bin:/home/guorongxiao/.local/apache-maven-3.9.11/bin \
  JAVA_HOME=/home/guorongxiao/.local/jdk/jdk-17.0.19+10 \
  bash -c 'cd /home/guorongxiao/ECOS/ecos_backend && mvn install -DskipTests -Dmaven.test.skip=true -q'
# 期望: BUILD SUCCESS

# V3: security-engine 端点仍可达（需 gateway 启动 + 认证）
# curl -X POST /api/v1/policy-engine/evaluate -H "Authorization: Bearer <token>" -d '{"policy":"...","input":{}}'
# 期望: 200（走 security-engine 权威）
```

## §工时

0.5 天（删 7 文件 + 删 7 行 excludeFilter + 编译验证）。

## §风险

- **测试类连带删除**：`sysman-impl/src/test/` 下有 `CryptoAuditControllerTest.java` 等，若引用了被删 Controller，需一并删除对应测试类（否则 test 编译失败）。
- **SecurityProfileController 边界**：它不在排除列表，说明可能仍被前端/其他模块调用。本次保留，若后续确认 security-engine 有对应，再单独出指令处理。
- **OPA 依赖**：sysman 的 PolicyEngineController 对接 OPA（localhost:8181），删掉后若 security-engine 的 PolicyEngineController 未实现 OPA 对接，policy-engine 功能可能有差异——需在 V3 用 curl 验证 security-engine 版本功能完整。
