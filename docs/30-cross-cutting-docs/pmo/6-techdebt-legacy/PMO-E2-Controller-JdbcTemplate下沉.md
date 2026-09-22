# PMO指令: E2 Controller JdbcTemplate 下沉（铁律5 生产债务）

> **来源**: 肖国荣 | **日期**: 2026-08-24
> **协同**: ECOS-BE（为主）+ ECOS-ARCH（T0 副本判定）
> **架构铁律**: 必须遵循 [ECOS架构铁律](../../ARCHITECTURE-RULES.md)
> **铁律**: ①下沉是"搬家"不是"改行为"——不改端点路径、不改响应结构、不改 SQL 语义 ②禁止引入新依赖/ORM 框架 ③禁止动六引擎 ArchitectureTest（D1）④gateway 副本删除前必须 diff 确认权威在 sysman-boot

## 零、现状摸底（已核实）

铁律5（`Controller 禁止直接使用 JdbcTemplate`）ArchUnit 统计 **158 次违规**，涉及 **33 个 Controller 文件**（11 个模块）。E1 已 `@Disabled` 挂起，本指令做下沉。

**分布**：

| 模块 | 数量 | Controller |
|------|:---:|-----------|
| sysman-impl | 8 | TenantController(457行)/SysConfigController/MonitoringController/CausalController/EntityTableMappingController/UserController/SecurityProfileController/WorldModelGraphController |
| gateway | 6 | DqDashboardController/GitController/SysConfigController/MonitorController/TelemetryController + **AuthController 副本**（sysman.boot 包，错放） |
| workspace-impl | 4 | ObjectController/ObjectStateMachineController/ObjectQLController/ObjectActionController |
| ontology-engine | 4 | OntologyMappingController/GlossaryController/OntologyConfigController/OntologyProposalController |
| ai-engine | 3 | NLQController/DiagnosticAgentController/AgentMetricsController |
| data-engine | 2 | SchemaChangeController/DataLayerController |
| identity-service | 2 | MfaController/PrivacyController |
| sysman-boot | 1 | AuthController（权威版） |
| security-engine | 1 | SecurityAuditController |
| kb-engine | 1 | ComplianceRuleController |
| buszhi-impl | 1 | DqController |

**关键前置结论（已核实）**：gateway 里的 `com.chinacreator.gzcm.sysman.boot.controller.AuthController` 是 **sysman-boot 权威版的旧副本**（缺修改密码/登录锁定/密码强度校验，diff 已确认），gateway 里另有 `HealthController` 同款副本。**这 2 个副本删，不沉**。

**下沉模式（每个 Controller 统一流程）**：
1. Controller 删除 `private final JdbcTemplate jdbc` 字段 + `import org.springframework.jdbc.core.JdbcTemplate`
2. 建/复用 Service（复用模块现有 service 包，没有则新建 `XxxService`），Service 构造注入 `JdbcTemplate`
3. Controller 里的 SQL 逻辑（queryForObject/queryForList/update 等）整体搬到 Service 方法，SQL 字符串一字不改
4. Controller 改构造注入 Service，调 `service.xxx()`，方法签名对外不变
5. curl 验证端点响应与下沉前一致

**JdbcTemplate 留在 Service 层是允许的**——铁律5 只禁 Controller，不禁 Service。不引入 MyBatis/JPA 重写。

## 一、目标状态

33 个 Controller 不再 import JdbcTemplate（其中 2 个副本删除）；铁律5 去 `@Disabled` 后 common-api 全绿（158 次违规清零）。

## 二、分阶段执行计划

### T0: 删 gateway sysman.boot 副本（0.5天，ECOS-ARCH）

| 文件 | 操作 |
|------|------|
| `gateway/src/main/java/com/chinacreator/gzcm/sysman/boot/controller/AuthController.java` | diff 确认权威在 sysman-boot 后 `git rm` |
| `gateway/src/main/java/com/chinacreator/gzcm/sysman/boot/controller/HealthController.java` | diff 确认权威在 sysman-boot 后 `git rm` |

**验收**：`grep -rn "sysman.boot.controller" gateway/src/main/java` 0 匹配；Gateway 编译通过；`curl /api/v1/auth/login` 仍 200（走 sysman-boot 版本）。

### T1-T9: 按模块下沉（可并行 ≤3 模块，模块内逐个 Controller 下沉逐个 curl）

| Task | 模块 | 文件（`src/main/java/com/chinacreator/gzcm/` 下） | 工期 |
|:-----|------|------|:---:|
| T1 | sysman-impl | `sysman/controller/{Tenant,SysConfig,Monitoring,Causal,EntityTableMapping,User,SecurityProfile,WorldModelGraph}Controller.java` | 3天 |
| T2 | sysman-boot | `sysman/boot/controller/AuthController.java`（权威版下沉） | 0.5天 |
| T3 | gateway | `gateway/controller/{DqDashboard,Git,SysConfig,Monitor,Telemetry}Controller.java` | 2天 |
| T4 | workspace-impl | `workspace/controller/{Object,ObjectStateMachine,ObjectQL,ObjectAction}Controller.java` | 1.5天 |
| T5 | ontology-engine | `engine/ontology/controller/{OntologyMapping,Glossary,OntologyConfig,OntologyProposal}Controller.java` | 1.5天 |
| T6 | ai-engine | `engine/ai/controller/{NLQ,DiagnosticAgent,AgentMetrics}Controller.java` | 1天 |
| T7 | data-engine | `engine/data/controller/{SchemaChange,DataLayer}Controller.java` | 1天 |
| T8 | identity-service | `services/identity/controller/{Mfa,Privacy}Controller.java` | 1天 |
| T9 | 三小模块 | `engine/security/controller/SecurityAuditController.java` + `engine/kb/controller/ComplianceRuleController.java` + `buszhi/workflow/controller/DqController.java` | 1天 |

### T10: 铁律5 重新启用（0.5天）

去掉 E1 加的 `@Disabled`，全量验证。

## 三、禁止清单

- ❌ 改端点路径（`@RequestMapping` 值一字不改）、改响应结构（`ApiResponse` 包装不变）
- ❌ 改 SQL 语义（下沉前后 SQL 字符串必须一字不差，可用单测快照比对）
- ❌ 删除业务功能（T0 的 2 个 gateway 副本除外，那是已 diff 确认的过时副本）
- ❌ 引入新依赖（MyBatis/JPA/QueryDSL 一律不进，继续用 JdbcTemplate 放 Service 层）
- ❌ 下沉时顺手"顺手优化"——发现 Controller 里还有别的债（如硬编码、魔法数）只记录不动

## 四、风险与回滚

- **SQL 语义漂移**：下沉是高风险搬家。每个 Controller 下沉前先抓一次端点响应（curl + 入参），下沉后比对。高风险 SQL（如 ObjectQL、Causal、EntityTableMapping 的动态拼接）建议先写"下沉前 SQL 快照"。
- **循环依赖**：新建 Service 若互相调用，用构造注入 + `@Lazy` 打破。
- **gateway 副本误删**：T0 删副本前必须 diff 两份文件，且确认 gateway 的 `@ComponentScan`/`excludeFilters` 无对 sysman.boot 包的直接依赖。
- **回滚**：每个 Controller 单独 commit，`git revert` 精确回退。T0 删副本单独一个 commit。

## 五、验证门禁

```bash
# V1: 每模块下沉后，该模块 Controller 无 JdbcTemplate
grep -rn "JdbcTemplate" <模块>/src/main/java/**/*Controller.java
# 期望: 0 匹配

# V2: 全量编译
env -i HOME=/home/guorongxiao PATH=/usr/bin:/usr/local/bin:/home/guorongxiao/.local/bin:/home/guorongxiao/.local/apache-maven-3.9.11/bin JAVA_HOME=/home/guorongxiao/.local/jdk/jdk-17.0.19+10 \
  bash -c 'cd /home/guorongxiao/ECOS/ecos_backend && mvn install -DskipTests -Dmaven.test.skip=true -Pstandard'
# 期望: BUILD SUCCESS

# V3: 关键端点 curl 200（每模块选 1-2 个代表性端点，比对下沉前后响应）

# V4（最终）: 铁律5 去 @Disabled 后全绿
env -i HOME=/home/guorongxiao PATH=/usr/bin:/usr/local/bin:/home/guorongxiao/.local/bin:/home/guorongxiao/.local/apache-maven-3.9.11/bin JAVA_HOME=/home/guorongxiao/.local/jdk/jdk-17.0.19+10 \
  bash -c 'cd /home/guorongxiao/ECOS/ecos_backend && mvn test -pl common/common-api 2>&1 | grep -E "Tests run|BUILD SUCCESS|BUILD FAILURE"'
# 期望: Failures: 0，BUILD SUCCESS（铁律5 158 次违规清零）

# V5: 全仓 Controller 无 JdbcTemplate
grep -rl "JdbcTemplate" --include='*Controller.java' . | grep -v target
# 期望: 0 匹配（gateway 2 副本已删，31 个已下沉）
```

## 六、工时估算

T0(0.5) + T1(3) + T2(0.5) + T3(2) + T4(1.5) + T5(1.5) + T6(1) + T7(1) + T8(1) + T9(1) + T10(0.5) ≈ **13.5 天 ≈ 3 周**

## 七、一句话给 PMO

33 个 Controller 直连数据库是历史债，逐个把 SQL 搬到 Service 层，JdbcTemplate 留在 Service 里继续用；gateway 里 2 个 sysman.boot 旧副本直接删；搬完铁律5 重新启用，158 次违规清零。
