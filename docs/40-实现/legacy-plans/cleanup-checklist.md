# ECOS Phase 8 — 附录 A 目录对齐清理清单

> 目标：物理目录与 `docs/plans/current-plan.md` 附录 A 完全对齐
> 起点：Phase 1-7 微服务改造形态（7 独立 JAR + 顶层 `sysman/` `buszhi/` 仍遗留）
> 策略：**保 artifact 稳定 · 仅移动物理目录**，所有依赖方零 import/dependency 改动
> 参考：`.trae/rules/架构铁律.md` v1.1 · `ecos_backend/AGENTS.md` 模块树

## 已完成 Sprint

| Sprint | 动作 | 代价 | 风险 | 完成日 |
|:---:|---|---|:---:|:---:|
| **P8-A** | 顶层 `common/common-api` → `runtime/common-api` | 45 个 java 保包名 `com.chinacreator.gzcm.common.*`；根 reactor 主列表 + 3 profile 各删 1 处 `<module>common</module>`；`runtime/pom.xml` 补 1 处；原 `ArchitectureTest.java` 中 1 处字符串路径 `"common/common-api"` → `"runtime/common-api"` | 🔴 | 2026-09-11 |
| **P8-B** | 顶层 `buszhi/` (buszhi-impl library) → `services/buszhi/impl/` | 1 物理目录 mv；1 parent POM relativePath 改 (`../pom.xml` → `../../pom.xml`)；root reactor 主列表 1 处 `<module>buszhi</module>` → `<module>services/buszhi/impl</module>`；5 依赖方零改（保留 `buszhi-impl` artifact） | 🟠 | 2026-09-11 |
| **P8-C** | 顶层 `sysman/` (sysman-api/impl/boot 3 子模块, 263 java) 物理并入 `services/sysman/impl/` | 1 物理目录 mv；1 parent POM relativePath 改 (`../pom.xml` → `../../pom.xml`)；root reactor 主列表 1 处 `<module>sysman</module>` → `<module>services/sysman/impl</module>`；3 profile (standard/enterprise/ultimate) 各 1 处同步替换；15 处外部依赖零改（`sysman-{api,impl,boot}` artifact 稳定 + Java 包名 `com.chinacreator.gzcm.sysman.*` 不变, gateway 内 7 处 import 零改） | 🔴 | 2026-09-11 |

## 门禁（每个 Sprint 完成后必验）

| # | 门禁项 | 验证工具 |
|:---:|---|---|
| 1 | Full reactor `mvn clean install -DskipTests -Dmaven.test.skip=true` 全绿 | `mvn -f pom.xml install ...` |
| 2 | Gateway :8080 UP + 双跑共存 | `curl /actuator/health` + `/api/...` 端点 |
| 3 | `services/{sysman,datanet,buszhi,dccheng,aiming}` 各自独立 :18081/2/3/4/6 UP | `curl {port}/actuator/health` |
| 4 | Frontend :3000 BFF 用 `server.ts` 自定义 proxy → 各服务转发全绿 | `curl :3000/api/...` |

## 回滚策略

每个 Sprint **1 commit**：P8-A / P8-B 已完成 baseline；P8-C 单独 commit，遇编译问题 `git revert <c>` 一步回退。
