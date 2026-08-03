# PMO指令：AI引擎运行时增强——P1生产就绪

> **引擎契约**: `engine/ai-engine/AGENTS.md` | **工期**: 3周（13天） | **铁律见§禁止清单**
> **基线**: P0四块已完成（ToolSchema+ToolRegistry / Provider抽象 / Token管理 / 结构化解析）

---

## §零 背景

P0补上了Agent Loop的"执行肌肉"——工具调用结构化、模型可切换、Token不爆、参数有校验。但离生产就绪还差四块：

1. **Skill存了但Agent不知道** — Skill CRUD有，Controller有，但`AgentLoopService.callLLM()`不加载Skill到System Prompt
2. **CronJob存了但没人执行** — CronJob CRUD+ExecutionHistory有，但没有调度线程去触发
3. **对话聊完了就忘** — AgentSession存了所有消息，但不提取持久记忆注入下轮对话
4. **Agent没法操作文件** — 没有read_file/write_file/search_files/patch工具

本指令补全这四块。P1-5（Agent评估框架）压低优先级，留P2。

---

## §禁止清单

1. ❌ 不新增Maven模块
2. ❌ 不引入外部调度框架（Quartz/xxl-job等）——Cron调度统一走runtime-task全局任务中心
3. ❌ ai-engine不自建调度线程（`ScheduledExecutorService`/`@Scheduled`/`ThreadPoolTaskScheduler`）
4. ❌ 不改LLMGatewayService接口
5. ❌ 不改AgentLoopService公开方法签名
6. ❌ Skill注入总量不超过2000字符（避免挤占Token预算）
7. ❌ 记忆提取只针对内容>50字的用户消息
8. ❌ 文件工具不写Windows宿主机路径——操作根目录锁定在 `/home/guorongxiao/ECOS/ecos-kb/` 下的 `agent-workspace/`

---

## §P1 Tasks

### T1: Skill运行时加载（3天）

**当前**: Skill CRUD完整，但Agent不知道。

**目标**: `AgentLoopService`在构造System Prompt时，从DB加载已启用的Skill，编译YAML frontmatter → 注入System Prompt。

**改文件**:
- 改造 `service/AgentLoopService.java` — `buildSystemPrompt()`方法增加Skill注入
- 改造 `service/PromptCompilerServiceImpl.java` — 增加`compileSkill()`方法

**注入格式**:
```
## Available Skills
- **skill_name** (v1.0.0): 简短描述。触发条件：xxx。
```

**关键逻辑**:
```java
private String buildSystemPrompt(String agentId, String userId) {
    StringBuilder sb = new StringBuilder(basePrompt);
    
    // 1. 加载已启用Skill
    List<SkillEntity> skills = skillService.listSkills(null, true);
    if (!skills.isEmpty()) {
        sb.append("\n\n## Available Skills\n");
        for (SkillEntity s : skills) {
            // 每个skill描述不超过120字符，总注入量不超过2000字符
            String desc = s.getDescription();
            if (desc != null && desc.length() > 120) desc = desc.substring(0, 117) + "...";
            sb.append("- **").append(s.getName()).append("**");
            if (s.getVersion() != null) sb.append(" (v").append(s.getVersion()).append(")");
            sb.append(": ").append(desc != null ? desc : "无描述").append("\n");
        }
    }
    return sb.toString();
}
```

**验收**:
```bash
# V1: 编译通过
cd ~/ECOS/ecos_backend && mvn install -pl engine/ai-engine/ai-engine-boot -am -DskipTests -q && echo "BUILD PASS"

# V2: 创建Skill后，Agent对话可引用
# 1) 创建skill: POST /api/v1/agent-config/skills {"name":"data_query","description":"查询数据库表结构和数据","enabled":true}
# 2) 创建Agent对话，检查System Prompt是否包含 "## Available Skills" 和 "data_query"
# 3) 发消息"你能查询数据库吗？"，Agent应回答包含data_query skill的描述

# V3: System Prompt不因Skill过多而超长（token估算验证）
```

---

### T2: Cron执行引擎——集成runtime-task全局任务中心（4天，含1天基础设施）

**当前**: CronJob CRUD完整，ExecutionHistory表有，但没有调度器。

**架构决策**: AI引擎不自己搞调度器。ECOS已有全局异步任务中心 `runtime/runtime-task`（`TaskSchedulerService` + 10线程池 + 持久化 + 回调 + 依赖检查 + 统计），Cron执行统一委托给它。

**全局任务中心现状**（`runtime/runtime-task/`）:
| 组件 | 状态 | 说明 |
|------|------|------|
| `TaskSchedulerService` 接口 | ✅ | scheduleTask(立即/延迟/周期/cron)、cancelSchedule、checkDependencies |
| `TaskSchedulerServiceImpl` | ⚠️ 非Spring Bean | `new`构造，需加`@Component` |
| `TaskDescription` | ✅ | taskId/name/type/params/config/dependencies/extensions 完整 |
| `ITaskPersistenceService` | ✅ | PG持久化 |
| `ITaskStatusCallback` | ✅ | 状态回调接口 |
| `DefaultTaskExecutor` | ✅ | 默认执行器 |

**T2分两步**:

**T2a: 任务中心Spring化（1天，全局基础设施）**

改文件：`runtime/runtime-task/` 下
- 改造 `TaskSchedulerServiceImpl.java` — 加 `@Component`，构造器注入改为 `@Autowired`
- 改造 `TaskManagementServiceImpl.java` — 加 `@Service`
- 改造 `TaskPersistenceServiceImpl.java` — 加 `@Repository`
- 新增 `AgentCronTaskExecutor.java` — 实现 `ITaskStatusCallback`，负责执行完成后回调ai-engine记录ExecutionHistory

**T2b: ai-engine集成（3天）**

改文件：`engine/ai-engine/ai-engine-impl/` 下
- 改造 `service/CronJobServiceImpl.java`:
  - 注入 `TaskSchedulerService`
  - `createCronJob()` → 同时调 `taskScheduler.scheduleTask(taskDesc, cronExpression)` 注册到全局任务中心
  - `toggleCronJob(id, false)` → 同时调 `taskScheduler.cancelSchedule(scheduleId)`
  - `toggleCronJob(id, true)` → 重新 `scheduleTask()`
  - `deleteCronJob(id)` → 先 `cancelSchedule` 再删
  - 新增 `executeNow(Long id)` → 调 `taskScheduler.scheduleTask(taskDesc, 0)`
- 新增 `service/AgentCronJobBridge.java` — CronJobEntity → TaskDescription 转换器

**AgentCronJobBridge 关键逻辑**:
```java
@Component
public class AgentCronJobBridge {
    /**
     * 将 CronJobEntity 转为 TaskDescription，提交到全局任务中心
     * taskType = "AI_AGENT_CRON"
     * parameters 携带 agentId + prompt + userId
     * extensions 携带 cronJobId（回调时回写ExecutionHistory）
     */
    public String register(CronJobEntity cronJob, TaskSchedulerService scheduler) {
        TaskDescription task = new TaskDescription();
        task.setTaskId("agent-cron-" + cronJob.getId());
        task.setTaskName(cronJob.getName());
        task.setTaskType("AI_AGENT_CRON");
        task.setParameters(Map.of(
            "agentId", cronJob.getAgentId(),
            "prompt", cronJob.getPrompt(),
            "userId", cronJob.getCreatedBy()
        ));
        task.setExtensions(Map.of("cronJobId", cronJob.getId()));
        task.setAsync(true);
        task.setTimeout(300000L); // Agent执行上限5分钟
        task.setTags(List.of("ai-engine", "cron"));

        return scheduler.scheduleTask(task, cronJob.getCronExpression());
    }
}
```

**AgentCronTaskExecutor（回调）**:
```java
@Component
public class AgentCronTaskExecutor implements ITaskStatusCallback {
    // 实现 ITaskStatusCallback.onComplete(TaskDescription, TaskStatus)
    // 从 extensions 中取 cronJobId
    // 写 ExecutionHistory: status=status.state, output=status.message, duration=计算
    // 更新 CronJobEntity.nextRunAt
}
```

**数据流**:
```
CronJobController.createCronJob()
  → CronJobServiceImpl (写DB)
  → AgentCronJobBridge.register() (转TaskDescription)
  → TaskSchedulerService.scheduleTask(cronExpression)
  → TaskSchedulerServiceImpl (10线程池定时触发)
  → TaskManagementService.submitAndExecute()
  → AgentCronTaskExecutor.onComplete() (回调记录ExecutionHistory)
```

**验收**:
```bash
# V1: runtime-task模块编译 + Spring化
cd ~/ECOS/ecos_backend && mvn install -pl runtime/runtime-task -am -DskipTests -q && echo "RUNTIME-TASK BUILD PASS"

# V2: ai-engine集成编译 + Gateway全量
cd ~/ECOS/ecos_backend && mvn install -pl gateway -am -DskipTests -q && echo "GATEWAY BUILD PASS"

# V3: CronJob注册到任务中心
TOKEN=$(curl -s http://localhost:8080/api/v1/auth/login -X POST -H 'Content-Type: application/json' -d '{"username":"admin","password":"admin123"}' | python3 -c "import json,sys; print(json.load(sys.stdin)['data']['token'])")
curl -s -X POST http://localhost:8080/api/v1/agent-config/cronjobs \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"test","cronExpression":"0 * * * * *","agentId":"diagnostic-agent","prompt":"当前时间是多少"}'
# 验证返回的 scheduleId 非空

# V4: 定时执行 + ExecutionHistory
# 按cron等待触发后
curl -s http://localhost:8080/api/v1/agent-config/cronjobs/{id}/executions \
  -H "Authorization: Bearer $TOKEN" | python3 -c "
import json,sys; data=json.load(sys.stdin)['data']
assert len(data) > 0, '没有execution记录'
e = data[0]
assert e['status'] in ('SUCCESS','RUNNING'), f'状态异常: {e[\"status\"]}'
print(f'Execution: status={e[\"status\"]} output_len={len(e.get(\"output\",\"\"))}')
"

# V5: toggle暂停
curl -s -X POST "http://localhost:8080/api/v1/agent-config/cronjobs/{id}/toggle?enabled=false" \
  -H "Authorization: Bearer $TOKEN"
# 验证任务中心已cancel: 查 TaskSchedulerService.getScheduledTasks() 不含该scheduleId
```

---

### T3: 记忆注入（2天）

**目标**: 从历史会话中提取"持久事实"——如用户偏好、关键决策、反复出现的话题——注入System Prompt。

**改文件**:
- 新增 `service/MemoryExtractor.java` — 记忆提取逻辑
- 改造 `service/AgentSessionService.java` — 增加`getFacts(userId)`查询
- 改造 `service/AgentLoopService.java` — `buildSystemPrompt()`增加记忆注入

**MemoryExtractor逻辑**:
```java
@Component
public class MemoryExtractor {
    /**
     * 从用户消息中提取事实（规则引擎，非LLM）
     * 提取条件:
     *   - 内容>50字
     *   - 包含"偏好/习惯/总是/不要/记住"等关键词
     *   - 或被标记为"重要"（role=user且有tool_calls包含mark_important）
     */
    public List<String> extractFacts(List<AgentMessage> messages) {
        // 规则匹配提取
        // 最多返回5条最近事实
    }
}
```

**DB设计**: 在 `sys_agent_message` 中复用现有字段。不新建记忆表——在System Prompt注入内存记忆。

**注入格式**:
```
## Memory (from previous conversations)
- 用户偏好：使用表格展示数据
- 用户关注指标：毛利率、现金流
```

**验收**:
```bash
# V1: 编译通过
cd ~/ECOS/ecos_backend && mvn install -pl engine/ai-engine/ai-engine-boot -am -DskipTests -q && echo "BUILD PASS"

# V2: 记忆提取
# 同一session内:
#   用户: "我喜欢用表格形式展示数据"（>50字，含"喜欢"关键词）
#   Agent: "好的，我会记住"
#   用户: "查询销售额"
# 验证: 第二次对话的System Prompt中是否包含 "用户偏好：使用表格展示数据"

# V3: 短消息不提取
# 发送"你好" → System Prompt中不出现Memory段
```

---

### T4: 文件系统工具（2天）

**目标**: 为Agent提供4个文件操作工具：read_file、write_file、search_files、patch。操作范围锁定在 `agent-workspace/` 下。

**改文件**:
- 新增 `service/FileToolService.java` — 文件操作 + 路径安全校验
- 改造 `service/ToolRegistry.java` — 注册4个文件工具

**FileToolService.java 关键设计**:
```java
@Service
public class FileToolService {
    private static final Path WORKSPACE_ROOT = Path.of("/home/guorongxiao/ECOS/ecos-kb/agent-workspace");

    /** 安全校验：拒绝任何试图跳出workspace的路径 */
    private Path resolveSafe(String relativePath) {
        Path resolved = WORKSPACE_ROOT.resolve(relativePath).normalize();
        if (!resolved.startsWith(WORKSPACE_ROOT)) {
            throw new SecurityException("路径越界: " + relativePath);
        }
        return resolved;
    }

    public String readFile(String path, int offset, int limit) { ... }
    public String writeFile(String path, String content) { ... }
    public String searchFiles(String pattern, String fileGlob) { ... }
    public String patch(String path, String oldStr, String newStr) { ... }
}
```

**ToolSchema注册**:
```java
// read_file
new ToolSchema("read_file", "读取文件内容", Map.of(
    "path", new ParamDef("string", true, null, "相对于workspace的文件路径"),
    "offset", new ParamDef("number", false, 1, "起始行号"),
    "limit", new ParamDef("number", false, 500, "最大行数")
));

// write_file
new ToolSchema("write_file", "写入文件", Map.of(
    "path", new ParamDef("string", true, null, "相对于workspace的文件路径"),
    "content", new ParamDef("string", true, null, "文件内容")
));

// search_files
new ToolSchema("search_files", "搜索文件内容", Map.of(
    "pattern", new ParamDef("string", true, null, "搜索模式（正则表达式）"),
    "fileGlob", new ParamDef("string", false, "*", "文件名过滤（如*.md）")
));

// patch
new ToolSchema("patch", "替换文件中的文本", Map.of(
    "path", new ParamDef("string", true, null, "相对于workspace的文件路径"),
    "oldString", new ParamDef("string", true, null, "要替换的文本"),
    "newString", new ParamDef("string", true, null, "替换为的文本")
));
```

**验收**:
```bash
# V1: 编译通过
cd ~/ECOS/ecos_backend && mvn install -pl engine/ai-engine/ai-engine-boot -am -DskipTests -q && echo "BUILD PASS"

# V2: 工具注册验证
curl -s http://localhost:18084/api/v1/agent/tools | python3 -c "
import json,sys
tools = json.load(sys.stdin)['data']
names = [t['name'] for t in tools]
for n in ['read_file','write_file','search_files','patch']:
    assert n in names, f'{n} not registered'
print('ALL 4 FILE TOOLS REGISTERED')
"

# V3: 路径安全
# read /etc/passwd → 拒绝（路径越界）
# read ../../../etc/passwd → 拒绝
# read test.txt → 允许

# V4: Agent对话中使用文件工具
# 用户: "读取agent-workspace下的README.md"
# Agent调用read_file工具 → 返回文件内容
```

---

## §执行顺序

```
Week 1:
  Day 1-3: T1（Skill运行时加载）       —— 独立，先做
  Day 4-5: T4（文件系统工具）           —— 独立，并行

Week 2:
  Day 6:    T2a（runtime-task Spring化） —— 全局基础设施，T2b的前置
  Day 7-9:  T2b（ai-engine集成任务中心） —— 依赖T2a
  Day 10:   T3（记忆注入）               —— 依赖T1（复用buildSystemPrompt）

Week 3:
  Day 11-12: T1+T2+T3+T4联调
  Day 13:   全量编译 + Gateway集成测试 + pre-check
```

---

## §交付检查清单

```bash
# ─── 1. 全量编译 ───
cd ~/ECOS/ecos_backend
mvn install -pl gateway -am -DskipTests -q && echo "BUILD PASS"

# ─── 2. 新增文件检查 ───
# runtime-task (全局)
ls runtime/runtime-task/src/main/java/.../AgentCronTaskExecutor.java
# ai-engine
ls engine/ai-engine/ai-engine-impl/src/main/java/.../service/AgentCronJobBridge.java
ls engine/ai-engine/ai-engine-impl/src/main/java/.../service/MemoryExtractor.java
ls engine/ai-engine/ai-engine-impl/src/main/java/.../service/FileToolService.java

# ─── 3. 禁止清单验证 ───
# grep -r "ScheduledExecutorService\|ThreadPoolTaskScheduler" engine/ai-engine/ → 空（ai-engine不自建调度线程）
# grep -r "quartz\|xxl-job" engine/ai-engine/ → 空
# AgentLoopService.run() 签名未变
# Agent Loop上限5轮未变

# ─── 4. 功能验证 ───
# Skill注入: System Prompt含 "## Available Skills"
# Cron调度: 创建cron→TaskSchedulerService注册→1分钟后有execution记录
# 任务中心: TaskSchedulerService.getScheduledTasks() 返回ai-engine注册的任务
# 记忆提取: 含"偏好"关键词的长消息被提取
# 文件工具: 4个工具已注册，路径安全生效
```

---

## §一句话总结

**"Skill存了要能注入、Cron建了要进全局任务中心统一调度、聊完了要记得、文件要能读能写。P1四块补上后，AI引擎从'能对话'升级为'能干活'——定时执行走ECOS全局任务中心、记忆积累、文件操作，接近Hermes Agent的运行时能力。"**
