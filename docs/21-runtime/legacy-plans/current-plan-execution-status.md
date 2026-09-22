## §10 阶段执行进度（2026-09-11 更新）

> 实际执行（PMO-49 微服务改造）本轮及前轮完成项。**Phase 2 六部署单元骨架全部就绪 + 独立启动验证 5/6 UP + monolith gateway 双跑兼容**。

### Phase 1 — 全部完成 ✅
- P1-1 基础设施 PG/Neo4j/MinIO/OPA/Kafka healthy
- P1-2 结构验证（各 engine-boot 可独立入口）
- P1-5 全量 mvn install exit 0
- P1-6 dccheng 双引擎 PoC（cognitive 直依赖 kb-impl 架构债务已知）
- P1-7 架构铁律 5 处修订（§0.1/§0.3/§1.2/§2.4-5/§5.1#10）
- P4-pre 数据迁移前备（8 service schema 已隔离 + 184 public 遗留表清单）

### Phase 2 — 6 部署单元物化 ✅（5/6 独立 health UP）

| 部署单元 | 端口 | 独立编译 | 独立 fat-JAR | 独立启动 | /actuator/health | 验证方式 |
|:--|:--:|:--:|:--:|:--:|:--:|:--|
| sysman-service | 18081 | ✅ | ✅ 95MB | ✅ | **UP** | java -jar |
| datanet-service | 18082 | ✅ | ✅ 95MB | ✅ | **UP** | java -jar |
| buszhi-service | 18083 | ✅ | ✅ | 骨架（代码搬迁待 P2-3） | — | 编译产出 |
| aiming-service | 18084 | ✅ | ✅ 95MB | ✅ | **UP** | java -jar |
| dccheng-service | 18086 | ✅ | ✅ 92MB | ✅ | **UP** | java -jar |
| workspace-service | 18090 | ✅ | ✅ | ✅ | **UP** | java -jar -Dspring.profiles.active=standard |
| **gateway (monolith)** | 8080 | ✅ 37-module reactor | ✅ | ✅ | **UP** | start-gateway.ps1 (env JWT_PRIVATE_KEY) |

### 关键技术决策与修复
- **D1 MyBatis 别名冲突**：MyBatisConfig `typeAliasesPackage` 改为 `@Value("${ecos.mybatis.type-aliases-package:...}")` property 化，各 service 收窄
- **@MapperScan 补齐**：`com.chinacreator.gzcm.sysman.**.dao` + `runtime.llm.repository`（Phase 2 拆出时遗漏）
- **aiming 三重路由解冲突**：mirror gateway `DiagnosticAgentController`+`CognitiveController`+`CognitiveService` 排除
- **workspace 依赖隔离**：object-domain ⇢ data/engine.ontology common.service + buszhi WorkflowController mirror 排除
- **workspace Profile=standard**：`PgGraphService @Profile(standard)`（standard 档）
- **monolith 双跑**：gateway `@ComponentScan` 保留于 engine/workspace/buszhi/agent 包基，独立 service 与 gateway 双进程共存（ADR-7）

### Phase 3-7 — 待做
- Phase 3: gateway 瘦身（移除已迁 basePackages + 三滤波器重构）
- Phase 4: 数据迁移（Schema 切分 + 过渡期双写）
- Phase 5: 回滚 runbook
- Phase 6: enterprise/ultimate 三档位 E2E + 性能基准
- Phase 7: Docker compose 7 单元 + 启动脚本 + AGENTS.md 对齐 + ecos-kb 刷新

> 注意：Docker 容器基线仍白不加面白加(§5.1#10 通用措施)；Docker 本实施可 precedent-to。
