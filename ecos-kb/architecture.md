# ECOS 架构决策记录

> 手动维护。每次重大设计决策追加一条。不写废话，每条不超过5行。

## 1. 模块划分

| 模块 | 职责 | 为什么独立 |
|------|------|----------|
| `databridge-sysman` | 系统管理（用户/角色/权限/组织/字典/配置/IAM） | 租户管理+认证鉴权是产品入口，与业务逻辑隔离 |
| `databridge-aimod` | AI平台（Agent/Mesh/LLM/NLQ/AgentStudio） | AI能力迭代快，独立发版不影响其他模块 |
| `databridge-worldmodel` | 世界模型（目标/场景/因果链/帕累托优化/Case） | DIKW的K+W层，因果推理+模拟引擎核心 |
| `databridge-workspace` | 本体空间（Object/QL/状态机/关系/时间线/Workbook） | DIKW的I层，Ontology驱动架构根基 |
| `databridge-gateway` | API网关（路由/限流/监控/DQ/告警） | 统一入口，Spring Cloud Gateway |
| `databridge-portal` | 前端BFF（BizDashboard/前端数据聚合） | 为前端定制的数据聚合层，不污染业务模块 |
| `databridge-common` | 公共库（ApiResponse/异常/工具类） | 所有模块共享 |

## 2. URL路径规范

- 认证端点: `/sys-man/api/auth/**`
- 系统管理: `/api/v1/**` （通过Gateway）
- DIKW业务: `/api/v1/ecos/**`
- 世界模型: `/api/v1/worldmodel/**` （别名）
- 前端专用: `/api/v1/ecos/biz/**`
- 原则: 新业务端点放 `/api/v1/ecos/` 下，走Gateway统一鉴权

## 3. 数据库命名

- 业务表: `ecos_biz_*` （如 ecos_biz_project）
- 系统表: `ecos_*` （如 ecos_cognitive_rule, ecos_task）
- 迁移管理: Flyway，版本号 `V{序号}__{描述}.sql`
- 运行位置: PostgreSQL Docker，localhost:5432，库 sys_man
- 原则: 所有DDL通过Flyway，禁止手动改表

## 4. 认证鉴权

- 方案: JWT + Spring Security（无状态）
- 入口: `AuthController` — `/sys-man/api/auth/login`
- 白名单: `SecurityConfig.java` 的 `permitAll()` 列表
- 新增端点: 必须加到白名单，否则全403
- 权限注解: `@RequirePermission` + AOP切面（Sprint 6）
- 测试账号: admin / Admin@123

## 5. 前端架构

- 技术栈: React 19 + TypeScript + Vite
- BFF: `server.ts`（:3000 → localhost:8081/sys-man）
- 路径: `/home/guorongxiao/c2eos/`
- 路由: React Router，页面在 `src/pages/`
- API层: `src/api/` 下按模块拆分
- 构建: `pnpm build` → 输出到 `dist/`

## 6. Agent框架

- 引擎: Supervisor（编排） + Pipeline（流式）双模式
- 工具注册: AgentStudio → 工具绑定 → Agent配置 → 测试控制台
- 工具实现: `DiagnosticToolService` 类，Spring Bean
- LLM: HermesEngine（/mnt/d/workspace/AI工程研究/）
- 原则: Agent是通用AI底座，业务诊断靠工具链+Prompt注入

## 7. DIKW四层映射

| 层 | ECOS模块 | 核心能力 | 代表Controller |
|----|---------|---------|---------------|
| **D** (Data) | sysman + gateway | 数据目录/数据质量/数据接入 | DqDashboardController / DataLakeController |
| **I** (Information) | workspace | 本体定义/对象运行时/QL查询 | ObjectController / OntologyController |
| **K** (Knowledge) | worldmodel + gateway | 工作流/世界模型/因果链 | WorldModelController / CausalController |
| **W** (Wisdom) | aimod | Agent诊断/NSGA-II优化/报告 | AgentMeshController / ParetoController |

## 9. 架构债清单（2026-06-28复盘）

| 优先级 | 事项 | 状态 |
|:--:|------|:--:|
| P0 | ObjectRuntimeService下沉到common | 📤 PMO指令已发 |
| P0 | FrontendBridgeController拆分(903行→3个) | 📤 PMO指令已发 |
| P0 | 9个端点统一ApiResponse | 📤 PMO指令已发 |
| P1 | runtime-core引用分析 | 📤 PMO指令已发 |
| P1 | 131端点加/v1/前缀 | 📤 PMO指令已发 |
| P1 | ecos_tenant_usage加主键 | 📤 PMO指令已发 |

详见 `00-Kanban/ECOS-架构债清理-PMO指令.md`

## 8. 编译与运行

- JDK: Temurin 17.0.19+10 (`/home/guorongxiao/.local/jdk/`)
- Maven: `/home/guorongxiao/.local/apache-maven-3.9.11/bin/mvn`
- mvnd: `/home/guorongxiao/.local/mvnd/`（加速编译）
- 编译: `source ~/ecos-env.sh && mvn install -DskipTests`
- 启动: `mvn spring-boot:run -pl databridge-sysman/sysman-boot`
- 端口: 8081（sysman-boot），context-path `/sys-man`
- 关键: 禁止用Microsoft JDK wrapper，必须用绝对路径
