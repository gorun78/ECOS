# ECOS 架构铁律 — 所有PMO指令强制执行

> 版本: 1.0 | 2026-08-07 | 基于 Phase 1-2 实战教训 + 后端AGENTS.md + 前端GEMINI.md + 六引擎契约
> 此文件是ECOS开发的**宪法**。每条PMO指令必须在开头引用，违反任一铁律=验收不通过。

---

## 〇、总则

### 0.1 单体架构

ECOS是**单体应用**，不是微服务。`gateway`是唯一Spring Boot入口(`GatewayApplication`)，通过Maven依赖导入所有业务模块。

### 0.2 DIKW层级依赖方向

```
D(数据) → I(信息) → K(知识) → W(智能)
```

下层**禁止**import上层。跨模块调用走`PipelineEvent`(common-api)或REST。

### 0.3 六引擎体系

| 引擎 | 端口 | 职责 | 禁止 |
|------|:--:|------|------|
| **security-engine** | 18081 | 认证/授权/审计/脱敏/ABAC | 不执行业务规则判定 |
| **data-engine** | 18082 | 数据源/管道/血缘/DQ/查询 | 管道不执行>30min任务；血缘不追踪Neo4j |
| **ontology-engine** | 18083 | 本体建模/实体/关系/版本 | — |
| **cognitive-engine** | 18089 | 因果推理/情景推演/混合推理 | 不新增DB表(推理实时计算)；不引入规则引擎(SpEL即可) |
| **kb-engine** | 18086 | KG存储/检索/RAG/规则CRUD/知识抽取 | **不执行规则判定**(那是cognitive的)；不直接调LLM(那是ai的) |
| **ai-engine** | 18084 | Agent/Loop/Memory/LLM调用 | — |

**引擎=api/impl/boot三模块**。boot独立启动仅开发用，生产走gateway。

### 0.4 一套代码三套发布

```
standard    → PG only
enterprise  → PG + Neo4j (因果链>3层启用图谱)
ultimate    → PG + Neo4j + Doris (单表>100万行启用列存)
```

**不加新Maven模块(基线13)、不加新Docker容器(基线已定)**

---

## 一、后端服务层铁律

### 1.1 Controller规范

```java
@RestController
@RequestMapping("/api/v1/xxx")  // 或 /api/xxx（一致性二选一）
public class XxxController {
    private final XxxService xxxService;  // 构造器注入，不用@Autowired字段注入
    
    // 返回值模式：
    return ApiResponse.success(data);
    return ApiResponse.badRequest("原因");
    return ApiResponse.notFound("原因");
    // 禁止 throws Exception — 抛DataBridgeException子类
}
```

**API路径铁律**：
- 不改已有路径或参数签名 — 只增不改
- 新增端点优先用`/api/v1/`前缀，与现有Controller保持一致
- 所有端点必须在`auth.whitelist.paths`(application.yml)注册

### 1.2 新增Controller的三滤波器（🔴最高频踩坑）

**缺任何一层→403或404**。每新增Controller必须逐项验证：

| 层 | 文件 | 操作 |
|:--|------|------|
| 1 | `gateway/.../VersionPrefixRewriteFilter.java` | 确认V1_REWRITE_MAP中路径映射正确。Controller用`/api/v1/XXX`→REMOVE对应重写规则；用`/api/XXX`→KEEP |
| 2 | `sysman/.../security/SecurityConfig.java` | permitAll加`/api/v1/XXX/**` 和 `/api/XXX/**` 两种形式 |
| 3 | `sysman/.../security/ClearanceInterceptor.java` | 豁免列表加`/api/v1/XXX` 和 `/api/XXX` 两种形式 |

**Ant路径陷阱**：`/api/v1/agent/**` 不一定匹配 `/api/v1/agent-loop/chat`（含连字符的路径）。必须显式写出完整前缀，不依赖父级`/**`通配。

### 1.3 依赖注入铁律

- **不绕过@Autowired走new** — JdbcTemplate始终构造器注入
- **不implements已有Service接口** — 会产生多Bean冲突(`expected single but found 2`)
- **Adapter/新增类不实现业务接口** — 通过`@Qualifier`注入，不做Spring Bean代理
- **新Bean加`ecos`前缀避免冲突**

### 1.4 异常处理

```java
DataBridgeException(RuntimeException)
├── BusinessException
├── ForbiddenException
├── UnauthorizedException
├── ValidationException
├── NotFoundException
└── DataAccessException
```

禁止`throws Exception`、禁止裸500。GlobalExceptionHandler在sysman-boot。

### 1.5 Maven/POM铁律

- **编译=mvn install(非compile)** — `.m2`旧JAR不会被`compile`覆盖，Gateway加载`.m2`中旧JAR
- **重命名模块→删除`.m2`旧artifact目录** — `rm -rf ~/.m2/repository/com/chinacreator/gzcm/<old-name>/`
- **全量编译命令**：
  ```bash
  env -i HOME=/home/guorongxiao \
    PATH=/usr/bin:/usr/local/bin:/home/guorongxiao/.local/bin:/home/guorongxiao/.local/apache-maven-3.9.11/bin \
    JAVA_HOME=/home/guorongxiao/.local/jdk/jdk-17.0.19+10 \
    bash -c 'cd /home/guorongxiao/ECOS/ecos_backend && mvn install -DskipTests -Dmaven.test.skip=true -q'
  ```

---

## 二、后端引擎层铁律

### 2.1 引擎间通信

- 引擎间**只调API，不调Impl**。cognitive调kb走`GET :18086/api/v1/kb/rules`，不直接import kb-engine-impl
- **跨模块共享Service提升到common-api**（参考ICopilotService→common-api模式）

### 2.2 引擎内部

- 每个引擎实现`IEngine`接口（healthCheck/config/status/lifecycle）
- **新增Controller到引擎时，必须在`GatewayApplication`的`excludeFilters`中排除旧位置副本**（60+项已有）
- 引擎boot数据库配置均指向`sys_man/postgres`

### 2.3 新增引擎端点

- 统一路径：`/api/v1/engine/{type}/...`
- 新增端点后追加到对应引擎的`AGENTS.md`端点清单
- 如涉及定时/周期执行→委托`runtime-task`全局调度，不自建`ScheduledExecutorService`

---

## 三、数据层铁律

### 3.1 PostgreSQL

- DB: `sys_man`，本地凭据`postgres/postgres`
- **MyBatis**（非JPA — Hibernate已排除），Mapper XML: `classpath*:mapper/*.xml`
- **Schema只加不删** — 不加列/表可以，不删不改已有
- Flyway已禁用，不启用

### 3.2 Neo4j (enterprise/flagship)

- cognitive-engine的因果链>3层场景使用
- **Cypher只读+超时10s+结果上限1000节点**
- Neo4j健康检查+连接池由kb-engine管理

### 3.3 跨引擎数据访问

- data-engine不操作其他引擎的表
- cognitive-engine不新增DB表（推理实时计算）
- kb-engine的compliance_rules表被cognitive复用（只读）

---

## 四、前端铁律

### 4.1 主题系统（🔴绝对禁止硬编码颜色）

```tsx
import { useTheme } from "../components/ThemeContext";
const { styles } = useTheme();
// ✅ 正确
<div className={`border ${styles.cardBorder} ${styles.cardBg}`}>
// ❌ 禁止
<div className="border bg-white border-gray-200">
```

**4主题**：`slate-light` / `deep-space` / `cyber-terminal` / `royal-purple`

语义色（Success/Warning/Danger/Info）同样需主题感知——light用`bg-xx-50`，dark用`bg-xx-500/10`。

### 4.2 图标系统

- **仅用`lucide-react`**，禁止自定义SVG
- 表格/按钮：`w-3.5 h-3.5`；卡片：`w-4 h-4`；Hero：`w-5 h-5`

### 4.3 i18n国际化（🔴绝对禁止硬编码中文）

```tsx
import { useLanguage } from "../components/LanguageContext";
const { t, locale } = useLanguage();
// ✅ 正确
<span>{t("databench.datasource.add")}</span>
// ❌ 禁止
<span>添加数据源</span>
```

**新增namespace流程**：
1. 在`src/i18n/locales/{domain}/zh-CN.json`和`en.json`加keys
2. `LanguageContext.tsx`中注册namespace（如Phase 2已有的`chatbot`模式）
3. 端到端验证中英文切换

### 4.4 排版规范

| 用途 | 类 |
|------|-----|
| 页面大标题 | `font-bold text-xl tracking-tight` |
| 卡片/组标题 | `font-semibold text-sm` |
| 正文 | `text-xs leading-normal` |
| 注释/时间戳 | `font-mono text-[10px] tracking-wider uppercase` |

### 4.5 布局规范

```tsx
<div className="flex-grow overflow-y-auto p-6 font-sans">
  <div className="max-w-7xl mx-auto space-y-6">
    {/* 标题区 → KPI卡片网格 → 详情区 */}
  </div>
</div>
```

间距：主区间`space-y-6`，卡片内`space-y-4`，网格`gap-4`

### 4.6 组件规范

- **文件≤800行**。超限→拆分为独立子组件
- **每个Tab独立文件**，主Layout仅组合+状态管理，目标<300行
- HashRouter(`#/`路由)
- **Icon map用Record<string, ComponentType>**，不用switch-case

### 4.7 API调用

- 前端dev端口3000，代理`/api`→`:8080`
- API函数统一放`src/api.ts`或模块内`services/*Api.ts`
- SSE流式用`EventSource`或fetch readable stream

---

## 五、PMO执行铁律

### 5.1 禁止清单（🔴违反=验收失败）

| # | 禁止事项 | 后果 |
|:--|------|------|
| 1 | **跨Phase预创建文件** | 编译失败/Bean冲突 |
| 2 | **新增Adapter类时`implements`已有Service接口** | 多Bean冲突 |
| 3 | **修改既有文件注入未来Phase的依赖** | 编译失败/循环依赖 |
| 4 | **重命名模块后不删除`.m2`旧JAR** | ConflictingBeanDefinitionException |
| 5 | **新增Controller后不更新三滤波器** | 403/404 |
| 6 | **用`mvn compile`替代`mvn install`** | Gateway加载旧JAR |
| 7 | **硬编码Tailwind颜色(`bg-white`等)** | 主题切换失效 |
| 8 | **硬编码中文字符串** | i18n切换失效 |
| 9 | **自定义SVG图标** | 一致性断裂 |
| 10 | **新建Maven模块或Docker容器** | 架构基线破坏 |

### 5.2 原子任务格式

每个Task = **单文件 + curl验收 + 工期**：
```markdown
| Task | 文件/路径 | 操作 | 验收 |
|:--|------|------|------|
| T1 | `CausalReasonerServiceImpl.java` | 重写因果推理 | curl POST /api/v1/cognitive/diagnose 返回≥3层因果链 |
```

### 5.3 单指令≤5个Task

超5个Task的跨模块需求→拆为多条指令串行下发。

### 5.4 验证四步法

```
V1: 文件生存检查 (find -newer)
V2: 集成点grep (注入/注册/调用链)
V3: 编译 (mvn install -DskipTests)
V4: Gateway启动+curl验收
```

不要跳过V2直接编译——集成点grep比编译更快发现逻辑遗漏。

---

## 六、WSL环境铁律

| 规则 | 原因 |
|------|------|
| `env -i`清空环境后显式设置HOME/PATH/JAVA_HOME | Hermes重定向HOME致UNC路径双写bug |
| Maven用WSL原生路径`~/.m2`，不用`/mnt/d/` | Windows文件系统性能差+路径问题 |
| `~/start-gateway.sh`启动Gateway | 内置`unset HOME`绕bug |
| `lsof -ti:8080 | xargs kill -9`清端口 | 不用`fuser -k`(可能误杀Docker代理) |
| Git SSH过Clash代理: `nc -X 5 -x 127.0.0.1:7897` | WSL GitHub直连被墙 |

---

## 七、新PMO指令模板

每条PMO指令**开头必须引用此文**：

```markdown
# PMO-XX: <标题>

> **架构铁律**: 必须遵循 [ECOS架构铁律](../../ARCHITECTURE-RULES.md)
> 来源: 肖国荣 | 日期: YYYY-MM-DD
> 铁律: <3条以内本指令特有的硬约束>

## §背景
...

## §禁止清单（从铁律第5.1节继承 + 指令特有）
1. 不跨Phase预创建文件
2. ...

## §Task
| Task | 文件 | 操作 | 验收 |
|:--|------|------|------|
```
