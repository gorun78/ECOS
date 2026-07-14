# PMO指令：数据工作台 Phase 1 — Git版本管理基础设施

> **来源**: `/home/guorongxiao/ECOS/03-设计文档/data-workbench-design.md` §六 Phase 1
> **日期**: 2026-07-02
> **铁律**: 单文件粒度，每个Task改一个文件。Git提交=唯一绩效。DONE=commit hash+curl 200。禁止产出解释性文档。

---

## 背景

runtime-core已有Git模块（`GitService`/`GitRepositoryService`），但**两个关键问题**：

1. **不是Spring Bean** — `GitServiceImpl`和`GitRepositoryServiceImpl`没有`@Service`注解，无法`@Autowired`注入
2. **是占位实现** — 使用`ConcurrentHashMap`模拟commit（非真实JGit），commitId用`UUID.randomUUID()`

好消息：Gateway的`@ComponentScan`已覆盖`com.chinacreator.gzcm.runtime`，加个`@Service`就能注入。

Phase 1 策略：**先用占位实现跑通API契约，Phase 2再替换为JGit真实实现**。

---

## 禁止清单

1. 禁止新建Maven模块
2. 禁止修改runtime-core中已有的GitService接口签名（只加注解不改API）
3. 禁止产出分析文档——唯一产出是git commit
4. 禁止前端新增npm依赖（GitPanel用纯React+现有组件库）

---

## T0: 激活GitService为Spring Bean（0.5h，BE）

**目标**: 让`GitServiceImpl`和`GitRepositoryServiceImpl`可以通过`@Autowired`注入到Gateway的Controller中。

**改文件1**: `/home/guorongxiao/databridge-v2/databridge-runtime/runtime-core/src/main/java/com/chinacreator/gzcm/runtime/core/git/GitServiceImpl.java`

在类声明上加 `@Service`:
```java
@Service  // ← 加这一行
public class GitServiceImpl implements GitService {
```

**改文件2**: `/home/guorongxiao/databridge-v2/databridge-runtime/runtime-core/src/main/java/com/chinacreator/gzcm/runtime/core/git/GitRepositoryServiceImpl.java`

在类声明上加 `@Service`:
```java
@Service  // ← 加这一行
public class GitRepositoryServiceImpl implements GitRepositoryService {
```

**验收**:
```bash
cd /home/guorongxiao/databridge-v2
mvn compile -pl databridge-gateway -am -DskipTests 2>&1 | tail -5
# 期望: BUILD SUCCESS
# 如果失败 → 检查 @Service import 是否正确 (org.springframework.stereotype.Service)
```

---

## T1.1: GitController — 暴露8个Git REST端点（2天，BE）

**目标**: 将runtime-core的GitService封装为REST API，数据工作台前端可通过HTTP操作Git。

**改文件**: `databridge-gateway/src/main/java/com/chinacreator/gzcm/gateway/controller/GitController.java`（新建）

**复用**: 注入 `com.chinacreator.gzcm.runtime.core.git.GitService` 和 `GitRepositoryService`

**8个端点**:

```java
@RestController
@RequestMapping("/api/v1/ecos/git")
public class GitController {

    @Autowired
    private GitService gitService;
    @Autowired
    private GitRepositoryService gitRepositoryService;

    // 1. 当前仓库状态
    @GetMapping("/status")
    ApiResponse<GitStatus> status(@RequestParam String repoId);
    // → { branch, uncommittedFiles: [{path, status}], ahead, behind }

    // 2. 提交历史
    @GetMapping("/commits")
    ApiResponse<List<GitCommit>> commits(
        @RequestParam String repoId,
        @RequestParam(required = false) String path,
        @RequestParam(defaultValue = "20") int limit
    );

    // 3. 版本差异
    @GetMapping("/diff")
    ApiResponse<String> diff(
        @RequestParam String repoId,
        @RequestParam String commit1,
        @RequestParam String commit2,
        @RequestParam(required = false) String path
    );

    // 4. 提交变更
    @PostMapping("/commit")
    ApiResponse<GitCommit> commit(
        @RequestParam String repoId,
        @RequestBody CommitRequest body  // { message, paths, author }
    );

    // 5. 打标签
    @PostMapping("/tag")
    ApiResponse<Void> tag(
        @RequestParam String repoId,
        @RequestBody TagRequest body  // { name, message }
    );

    // 6. 回滚文件
    @PostMapping("/rollback")
    ApiResponse<Void> rollback(
        @RequestParam String repoId,
        @RequestBody RollbackRequest body  // { commitId, paths[] }
    );

    // 7. 分支列表
    @GetMapping("/branches")
    ApiResponse<List<String>> branches(@RequestParam String repoId);

    // 8. 创建/切换分支
    @PostMapping("/branch")
    ApiResponse<Void> createBranch(
        @RequestParam String repoId,
        @RequestBody BranchRequest body  // { name, fromBranch }
    );
}
```

**关键实现细节**:
- `GitService.getStatus(repoId)` 返回未提交文件列表
- `GitService.commit(repoId, message, paths)` 执行 `git add + git commit`
- `GitService.getHistory(repoId, path, limit)` 返回提交列表
- 所有操作需try-catch，GitException统一转换为ApiResponse错误

**验收**:
```bash
TOKEN=*** -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Admin@123"}' | jq -r '.data.token')
H="Authorization: Bearer *** 初始化一个测试仓库（如果有init端点）或使用已有repo
REPO_ID="data-workbench"

# T1: 查状态
curl -s -H "$H" "http://localhost:8080/api/v1/ecos/git/status?repoId=$REPO_ID" | jq '.data.branch'
# 期望: 返回分支名（如 "main"），非null

# T2: 查提交历史
curl -s -H "$H" "http://localhost:8080/api/v1/ecos/git/commits?repoId=$REPO_ID&limit=5" | jq '.data | length'
# 期望: >=0

# T3: 查分支列表
curl -s -H "$H" "http://localhost:8080/api/v1/ecos/git/branches?repoId=$REPO_ID" | jq '.data'
# 期望: 返回分支数组

# T4: 提交（需要先有未提交的变更文件）
# 创建一个测试文件 →
curl -s -X POST -H "$H" "http://localhost:8080/api/v1/ecos/git/commit?repoId=$REPO_ID" \
  -H "Content-Type: application/json" \
  -d '{"message":"test: PMO验证提交","paths":["/tmp/test.txt"],"author":"pmo"}' | jq '.code'
# 期望: 200
```

---

## T1.2: GitPanel — 前端Git侧边栏组件（1天，FE）

**目标**: 数据工作台左侧常驻Git面板，显示分支/变更/历史，可执行commit操作。

**改文件**: `c2eos/src/components/data-workbench/GitPanel.tsx`（新建）

**组件结构**:
```tsx
// GitPanel.tsx
// 依赖: 新增 gitService.ts（API调用层）
// 状态: repoId（当前工作台绑定的Git仓库）、status、commits、branch

interface GitPanelProps {
  repoId: string;           // 当前数据工作台绑定的仓库ID
  onCommit?: () => void;    // 提交后回调（刷新关联分区）
}

// 三段式布局（从上到下）:
// 1. 分支信息栏 — 当前分支名 + 分支切换下拉
// 2. 未提交变更列表 — 文件路径 + 状态标记(M/A/D) + [提交]按钮
// 3. 提交历史列表 — 最近20条，每条: hash前7位 + message + 时间
```

**API调用层**: `c2eos/src/services/gitService.ts`（新建，~50行）
```typescript
// 封装8个Git端点调用
export const gitService = {
  fetchStatus: (repoId: string) => apiFetchData<GitStatus>(`/api/v1/ecos/git/status?repoId=${repoId}`),
  fetchCommits: (repoId: string, path?: string, limit?: number) => ...,
  commit: (repoId: string, data: CommitRequest) => ...,
  // ...
};
```

**验收**:
```bash
cd /home/guorongxiao/c2eos
npx tsc --noEmit 2>&1 | grep -i "git"
# 期望: 0行（GitPanel和gitService零TS错误）

# 样式检查: 组件在浅色/深色主题下均可读
```

---

## T1.3: Pipeline保存自动commit（1天，FE+BE联动）

**目标**: 数据工程师在PipelineBuilder中点击保存时，自动执行git commit。

**改文件**: `c2eos/src/components/data-workbench/PipelinesView.tsx`（如果是新工作台）或 `c2eos/src/pages/PipelineBuilder.tsx`（如果Phase 1独立运行）

**改动逻辑**:
```
用户点击[保存管道]
  → 1. 原有保存逻辑（POST /api/v1/ecos/ontologies/... 或其他管道保存端点）
  → 2. 保存成功后，调用 gitService.commit(repoId, {
        message: "pipeline: {pipelineName} saved",
        paths: ["pipelines/{pipelineId}.json"],
        author: currentUser
      })
  → 3. GitPanel自动刷新状态
```

**验收**:
```bash
# 1. 浏览器打开PipelineBuilder
# 2. 编辑一个管道 → 点击保存
# 3. 切换到GitPanel → 确认出现一条新commit，message含"pipeline: xxx saved"
# 4. curl验证:
TOKEN=***"
curl -s -H "$H" "http://localhost:8080/api/v1/ecos/git/commits?repoId=data-workbench&limit=3" | jq '.data[0].message'
# 期望: 包含 "pipeline:"
```

---

## T1.4: DQ规则保存自动commit（0.5天，FE）

**目标**: 与T1.3相同模式，DQ规则保存时自动commit。

**改文件**: `c2eos/src/pages/DataQualityDashboard.tsx`（或新工作台的GovernanceView）

**改动逻辑**:
```
用户点击[保存规则]
  → 1. 原有保存逻辑
  → 2. 保存成功后，调用 gitService.commit({
        message: "dq-rule: {ruleName} saved",
        paths: ["dq-rules/{ruleId}.json"]
      })
```

**验收**:
```bash
# curl验证最近commit包含dq-rule:
curl -s -H "$H" "http://localhost:8080/api/v1/ecos/git/commits?repoId=data-workbench&limit=5" | jq '.data[].message'
# 期望: 其中至少一条包含 "dq-rule:"
```

---

## 环境准备（PMO自行完成，不计入Task）

### E1: 初始化Git仓库

在服务器上创建一个裸仓库或本地仓库，作为数据工作台的Git存储：

```bash
# 在WSL环境创建
mkdir -p /home/guorongxiao/ecos-git-repos/data-workbench
cd /home/guorongxiao/ecos-git-repos/data-workbench
git init
git config user.email "ecos@chinacreator.com"
git config user.name "ECOS Data Workbench"

# 创建初始目录结构
mkdir -p pipelines dq-rules scripts mappings agents kg-schemas
touch pipelines/.gitkeep dq-rules/.gitkeep scripts/.gitkeep
git add .
git commit -m "init: data workbench repository"
```

### E2: GitService初始化

确认 `GitService` 可以注入到Gateway模块：
```bash
cd /home/guorongxiao/databridge-v2
grep -r "GitService\|GitRepositoryService" databridge-gateway/src/ 2>/dev/null
# 如果没有import，在Gateway的@ComponentScan中增加runtime-core包路径
```

---

## 交付检查清单

| # | 检查项 | 验证方式 |
|---|--------|---------|
| 1 | GitController 8个端点全部返回200 | curl逐条验证 |
| 2 | GitStatus返回正确的分支名和未提交文件 | curl `/status` |
| 3 | GitCommit成功创建commit | curl POST `/commit` + 查 `/commits` |
| 4 | GitPanel在浏览器中显示分支/变更/历史 | 浏览器截图 |
| 5 | 保存Pipeline → GitPanel出现新commit | 端到端操作+截图 |
| 6 | 保存DQ规则 → GitPanel出现新commit | 端到端操作+截图 |
| 7 | 零TS错误 | `npx tsc --noEmit | grep -i git` = 0 |

---

## 执行顺序

```
E1 初始化Git仓库（30min）
E2 验证GitService可注入（30min）
  └→ T1.1 GitController（2天）—— 可与下方并行开始
T1.2 GitPanel + gitService.ts（1天）—— 依赖T1.1的API就绪
T1.3 Pipeline自动commit（1天）—— 依赖T1.2
T1.4 DQ规则自动commit（0.5天）—— 依赖T1.2
```

**总工时**: ~5天。T1.1是最关键路径。

---

**一句话给PMO**: runtime-core的Git模块是现成的，你的工作是把13,000行已有代码用8个REST端点包一层，然后在前端做一个GitPanel。先让commit能跑通，别纠结Git的merge/rebase高级操作——那些是Phase 2的事。
