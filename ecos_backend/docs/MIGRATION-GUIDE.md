# DataBridge v0.2 → v2 迁移执行手册

> 目标：按依赖顺序逐步迁移，每阶段产出独立可交付，不影响老工程运行。

## 前置准备

- [x] v2 工程骨架（根 POM + 模块目录） — **已完成**
- [x] common 模块（ApiResponse/异常/事件/DTO） — **已完成**
- [x] 前端 pnpm monorepo 骨架 — **已完成**
- [ ] WSL 内安装 Maven 3.9 + JDK 17（待处理）
- [ ] 从老工程复制 `database/` 初始化脚本到 v2

## 迁移总览

```
阶段 0: 脚手架 ✅ (已完成)
阶段 1: Runtime 公共底座迁移 ← 下一步
阶段 2: Sys-Man + Datanet-Ge 迁移
阶段 3: Bus-Zhi + Dc-Cheng + AIMod-Ming 迁移
阶段 4: api-gateway 迁移
阶段 5: 前端页面迁移（与后端并行）
```

## 阶段 1: Runtime 迁移（预计 5-7 天）

### 1.0 准备
```bash
# 安装 WSL 原生 Maven
sudo apt install maven -y

# 验证
mvn --version
java --version
```

### 1.1 迁移 runtime-api

**目标**：把老工程 `Runtime/runtime-api/src/main/java` 搬到新工程对应位置。

**步骤**：
1. 创建 POM：`runtime/runtime-core/pom.xml`（依赖 common-api）
2. Copy Java 文件：`Runtime/runtime-api/src/main/java` → `runtime/runtime-core/src/main/java`
3. 包名替换：
   - `com.chinacreator.gzcm.runtime` → 保持不变（这个包名已经是对的）
   - 检查是否有其他模块的 import，如有则调整为引用 common
4. Copy MyBatis XML：`Runtime/runtime-api/src/main/resources/mapper` → 对应位置
   - 修改 namespace 指向新包名（如包名没变则不改）
5. 编译验证：`mvn compile -pl runtime/runtime-core -am`

### 1.2 迁移 runtime-impl

**步骤**：
1. 创建 `runtime/runtime-core/pom.xml` 中的 impl 部分
2. Copy Java 文件
3. 所有 DAO 实现中 try-catch SQLException → throw DataAccessException
4. 编译验证

### 1.3 清理 throws Exception

在 Runtime 模块中搜索 `throws Exception`：
```bash
grep -rn "throws Exception" runtime/
```
逐个替换为具体异常。

### 1.4 Runtime 拆分（可选，建议先整体迁移再拆）

```
runtime/
├── runtime-core/       ← 数据访问 + 配置
├── runtime-task/       ← 任务调度
├── runtime-monitor/    ← 监控告警  
├── runtime-crypto/     ← 加密服务
└── runtime-datanet/    ← EDC 运行时
```

**拆分原则**：只拆接口/类边界清晰的，不强拆。如果某个类被多个子模块引用，放在 runtime-core。

### 1.5 验证

```bash
# 编译
mvn clean install -pl runtime -DskipTests

# 测试
mvn test -pl runtime/runtime-core

# 如需全项目测试
mvn test
```

---

## 阶段 2: Sys-Man 迁移（预计 3-4 天）

Sys-Man 只依赖 Runtime，迁移较简单。

### 步骤
1. Copy `Sys-Man/Sys-Man-api/` → `sysman/sysman-api/`
2. Copy `Sys-Man/Sys-Man-impl/` → `sysman/sysman-impl/`
3. 包名保持 `com.chinacreator.gzcm.sysman`（已符合规范）
4. 替换 `com.chinacreator.gzcm.sys-man` → `com.chinacreator.gzcm.sysman`（groupId 中有连字符的）
5. 清理 `throws Exception`
6. DAO 添加 `DataAccessException` 转换
7. 添加 ArchitectureTest
8. 编译 + 测试

---

## 阶段 3: Datanet-Ge 迁移（预计 2-3 天）

Datanet-Ge 依赖 Runtime + EDC 0.15，结构复杂但代码量少（113 文件）。

### 步骤
1. 保持 EDC 相关子模块不变（它们是 EDC 标准实现）
2. 包名从 `com.chinacreator.gzcm.runtime.datanet` → `com.chinacreator.gzcm.datanet`
3. 与 Sys-Man 并行迁移（无依赖关系）

---

## 阶段 4: Bus-Zhi + Dc-Cheng + AIMod-Ming（预计 7-10 天）

三个模块可并行迁移。

### Bus-Zhi 解耦要点
- 移除对 `Dc-Cheng-api` 的 import
- 原本调用 Dc-Cheng 的地方改为发布 `PipelineEvent`
- 引入 `DataSourceDescriptor` 替代直接引用 Dc-Cheng 的 DTO

### Dc-Cheng 解耦要点  
- 移除对 `Bus-Zhi-api` 和 `Bus-Zhi-impl` 的 import
- 订阅 `PipelineEvent` 替代直接调用 Bus-Zhi 的 Service
- 引入 `DataSourceDescriptor`

### AIMod-Ming
- 357 个文件，仅依赖 Runtime + Sys-Man，无耦合问题
- 按 NL2SQL/精调/ML/RAG 四个子包分批迁移

---

## 阶段 5: api-gateway 迁移（预计 2-3 天）

### 降级方案
从"聚合所有 Controller"改为"路由层"：

```java
// 老工程: gateway 包含所有 Controller
@RestController  
public class BusZhiController { ... }

// 新工程: gateway 只有路由 + 过滤器
@Configuration
public class GatewayConfig {
    @Bean
    public RouteLocator routes(RouteLocatorBuilder builder) {
        return builder.routes()
            .route("buszhi", r -> r.path("/api/v1/bus-zhi/**")
                .uri("http://localhost:8082"))
            .route("dccheng", r -> r.path("/api/v1/dc-cheng/**")
                .uri("http://localhost:8083"))
            .build();
    }
}
```

如果团队不接受 Spring Cloud Gateway，备选方案：每个模块自带 Controller + 一个薄的 `@RestController` 转发。

---

## 阶段 6: 前端迁移（与后端并行）

### 每个模块的前端迁移
1. 从老工程 `cdrc-frontend/src/pages/<module>/` copy 页面文件
2. 放到对应 `packages/<module>/src/pages/`
3. 修正 import：老工程的全局组件 → `@databridge/core`
4. API 调用适配 `ApiResponse<T>` 新格式
5. 验证懒加载：`shell/src/App.tsx` 中的 `lazy()` 正常工作

### 模块激活
每迁移完一个模块，在 `App.tsx` 中取消对应的 `lazy()` 注释，模块上线。

---

## 数据库兼容策略

迁移期间新老工程共用 5 个 MySQL 数据库。约束：

1. **Schema 变更只加不删** — 新列、新表可以加，旧列、旧表不删除
2. **类型变更只放宽不收紧** — VARCHAR(50) → VARCHAR(100) OK，反过来不行
3. **新表加 `v2_` 前缀** — 避免与老工程表名冲突，迁移完成后再去掉前缀
4. **API 响应格式兼容** — v2 的 ApiResponse 和老工程的 `{code,message,data,timestamp}` 结构一致，前端无需适配

---

## 回滚策略

每个阶段都有独立回滚点：

```
Nginx 路由切回老 gateway → 回滚到上一阶段

紧急回滚（全量）:
Nginx 将所有 /api/* 切回老 api-gateway (8080)
```

---

## 完成标志

- [ ] 所有 7 个模块在新工程编译通过
- [ ] 146+ 测试全部移植并通过
- [ ] 前端 5 个模块页面可独立懒加载
- [ ] Nginx 路由切换到新 gateway
- [ ] 老工程进入只读维护模式
