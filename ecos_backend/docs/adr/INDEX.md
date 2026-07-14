# 架构决策记录 (ADR)

## ADR-001: 统一包名规范

**状态**: 已采纳
**日期**: 2026-06-03
**决策**: 所有模块使用全小写包名 `com.chinacreator.gzcm.{module}`

**背景**: 老工程存在 4 种命名风格：
- `com.chinacreator.gzcm.sysman` (全小写)
- `com.chinacreator.gzcm.bus` (缩写)
- `com.chinacreator.gzcm.Cheng` (大写C)
- `com.chinacreator.gzcm.Ming` (大写M)

**备选方案**:
- A: 保持各模块现有风格，只改新模块
- B: 全部改为首字母大写 `com.chinacreator.gzcm.SysMan`
- C: 全小写 `com.chinacreator.gzcm.sysman` → **选中**

**理由**: 全小写是 Java 社区主流约定（Google Java Style、Spring Framework），避免 IDE 自动导入时的大小写混淆。

---

## ADR-002: 异常体系重构

**状态**: 已采纳
**日期**: 2026-06-03
**决策**: 使用 `DataBridgeException(RuntimeException)` 作为根异常，定义 6 个子类

**背景**: 老工程大量 DAO 接口声明 `throws Exception`，导致异常扩散、调用方被迫 try-catch 或继续声明 throws。

**异常层次**:
```
DataBridgeException (RuntimeException)
├── DataAccessException      ← DAO 层异常
├── BusinessException        ← 业务逻辑异常
├── ValidationException      ← 参数校验异常
├── NotFoundException        ← 资源不存在
├── UnauthorizedException    ← 未认证
└── ForbiddenException       ← 无权限
```

**理由**: RuntimeException 避免异常扩散；子类提供语义区分；`GlobalExceptionHandler` 统一转换为 `ApiResponse`。

---

## ADR-003: Bus-Zhi 与 Dc-Cheng 解耦方式

**状态**: 已采纳
**日期**: 2026-06-03
**决策**: 提取共享 DTO 到 `common`，通过 `PipelineEvent` 事件解耦

**背景**: 老工程中 Bus-Zhi-impl 和 Dc-Cheng-impl 互相 import 对方的类，形成循环依赖。

**方案**:
1. `DataSourceDescriptor` — 数据源描述，由 Datanet 提供，Bus-Zhi/Dc-Cheng 消费
2. `PipelineEvent` — 管道事件，Bus-Zhi 发布，Dc-Cheng 订阅

**理由**: 事件驱动比直接调用更松耦合；共享 DTO 放在 common 层，两个模块只依赖 common。

---

## ADR-004: 前端 pnpm monorepo

**状态**: 已采纳
**日期**: 2026-06-03
**决策**: 使用 pnpm workspace monorepo 替代老工程的单 SPA 结构

**备选方案**:
- A: Module Federation (Webpack 5) — 太复杂，不适合当前团队
- B: 保持单 SPA，仅内部按目录分层 — 不改根本问题
- C: pnpm workspace + 懒加载路由 → **选中**

**理由**: 
- 包级别隔离，真正防止跨模块 import
- 懒加载路由，改动一个模块不影响其他模块构建时间
- pnpm 硬链接机制解决 WSL/NTFS 跨文件系统性能问题

---

## ADR-005: 开发环境从 WSL+PowerShell 到 WSL 原生

**状态**: 已采纳
**日期**: 2026-06-03
**决策**: 新工程全部在 WSL ext4 原生文件系统开发，使用 WSL 内安装的 JDK/Maven

**背景**: 老工程在 /mnt/d/ (NTFS)，WSL 内 mvn 不工作，必须通过 PowerShell 调用 Windows Maven，构建慢且有 UNC 路径问题。

**方案**: 
- 代码路径：`~/databridge-v2` (WSL ext4)
- 前端路径：`~/databridge-frontend` (WSL ext4)
- 前端 npm 绕过代理：`http_proxy= https_proxy= HTTP_PROXY= pnpm install`

---

## ADR-006: api-gateway 降级为路由层

**状态**: 已提议
**日期**: 2026-06-03
**决策**: 新 gateway 不再聚合所有 Controller，改为 Spring Cloud Gateway 路由层

**理由**:
- 各模块可独立部署和测试
- 前端按模块懒加载，配合网关按路径前缀路由
- 支持灰度发布

**待定**: 如团队不同意引入 Spring Cloud Gateway，备选是 Spring Boot 自带的路由 + 过滤器。
