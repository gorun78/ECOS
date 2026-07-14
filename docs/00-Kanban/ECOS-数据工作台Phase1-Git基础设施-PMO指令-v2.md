# PMO指令：数据工作台 Phase 1 — Git版本管理基础设施（v2）

> **来源**: `/home/guorongxiao/ECOS/03-设计文档/data-workbench-design.md` §六 Phase 1
> **日期**: 2026-07-03（修订版）
> **v2变更**: ①Git仓库根路径改为系统配置参数 ②本体工作台纳入Git管理范围
> **铁律**: 单文件粒度，每个Task改一个文件。Git提交=唯一绩效。DONE=commit hash+curl 200。禁止产出解释性文档。

---

## 背景

runtime-core已有Git模块（`GitService`/`GitRepositoryService`），但**两个关键问题**：

1. **不是Spring Bean** — `GitServiceImpl`和`GitRepositoryServiceImpl`没有`@Service`注解，无法`@Autowired`注入
2. **是占位实现** — 使用`ConcurrentHashMap`模拟commit（非真实JGit），commitId用`UUID.randomUUID()`

好消息：Gateway的`@ComponentScan`已覆盖`com.chinacreator.gzcm.runtime`，加个`@Service`就能注入。

Phase 1 策略：**先用占位实现跑通API契约，Phase 2再替换为JGit真实实现**。

**v2关键变化**：Git仓库根路径不再硬编码，改为系统配置 `ecos_git_repo_root`，各工作台（数据工作台、本体工作台）统一从该配置读取自己的仓库目录。

---

## 禁止清单

1. 禁止新建Maven模块
2. 禁止修改runtime-core中已有的GitService接口签名（只加注解不改API）
3. 禁止产出分析文档——唯一产出是git commit
4. 禁止前端新增npm依赖（GitPanel用纯React+现有组件库）
5. 禁止在前端/后端代码中硬编码Git仓库路径——必须从系统配置API读取

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

## T0.1: 系统配置 — 增加Git仓库根路径配置项（0.5h，BE）

**目标**: 在sys_config表中增加 `ecos_git_repo_root` 配置项，GitController和前端从该配置读取仓库根路径。

**改文件**: `/home/guorongxiao/databridge-v2/databridge-gateway/src/main/resources/db/migration/V13__ecos_sys_config.sql`

在文件末尾追加（`INSERT ... ON CONFLICT DO NOTHING` 块内）:

```sql
-- ============================================================
-- Seed data: g5-git 1 config
-- ============================================================
INSERT INTO sys_config (id, config_group, config_key, config_value, config_type, config_label, config_label_en, description, sort_order) VALUES
('cfg-g5-git-repo-root', 'g5-git', 'ecos_git_repo_root', '/home/guorongxiao/ecos-git-repos', 'string', 'Git仓库根路径', 'Git Repo Root Path', '数据工作台和本体工作台的Git仓库根目录', 1)
ON CONFLICT (config_key) DO NOTHING;
```

**验收**:
```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Admin@123"}' | jq -r '.data.token')
H="Authorization: Bearer $TOKEN"

# 验证配置项存在
curl -s -H "$H" "http://localhost:8080/api/v1/system/config?group=g5-git" | jq '.data.data[] | select(.config_key=="ecos_git_repo_root") | .config_value'
# 期望: "/home/guorongxiao/ecos-git-repos"
```

---

## T1.1: GitController — 暴露8个Git REST端点（2天，BE）

**目标**: 将runtime-core的GitService封装为REST API，数据工作台和本体工作台前端可通过HTTP操作Git。

**改文件**: `databridge-gateway/src/main/java/com/chinacreator/gzcm/gateway/controller/GitController.java`（新建）

**复用**: 注入 `com.chinacreator.gzcm.runtime.core.git.GitService` 和 `GitRepositoryService`

**关键变更（v2）**: GitController启动时从sys_config读取 `ecos_git_repo_root`，repoId映射为 `${repoRoot}/${repoId}` 物理路径。

**8个端点**:

```java
@RestController
@RequestMapping("/api/v1/ecos/git")
public class GitController {

    @Autowired
    private GitService gitService;
    @Autowired
    private GitRepositoryService gitRepositoryService;
    @Autowired
    private JdbcTemplate jdbc;   // ← 读取系统配置用

    private String repoRoot;     // ← 启动时从sys_config加载

    @PostConstruct
    public void init() {
        // 从sys_config读取ecos_git_repo_root，默认/home/guorongxiao/ecos-git-repos
        String sql = "SELECT config_value FROM sys_config WHERE config_key='ecos_git_repo_root'";
        List<String> rows = jdbc.queryForList(sql, String.class);
        this.repoRoot = rows.isEmpty() ? "/home/guorongxiao/ecos-git-repos" : rows.get(0);
    }

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

**repoId→物理路径映射规则**：`repoRoot + "/" + repoId`，如 repoId=`data-workbench` → `/home/guorongxiao/ecos-git-repos/data-workbench`

**验收**:
```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Admin@123"}' | jq -r '.data.token')
H="Authorization: Bearer $TOKEN"

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

# T4: 提交
curl -s -X POST -H "$H" "http://localhost:8080/api/v1/ecos/git/commit?repoId=$REPO_ID" \
  -H "Content-Type: application/json" \
  -d '{"message":"test: PMO验证提交","paths":["/tmp/test.txt"],"author":"pmo"}' | jq '.code'
# 期望: 200
```

---

## T1.2: GitPanel — 前端Git侧边栏组件（1天，FE）

**目标**: 数据工作台和本体工作台左侧常驻Git面板，显示分支/变更/历史，可执行commit操作。

**改文件**: `c2eos/src/components/data-workbench/GitPanel.tsx`（新建）

**组件结构**:
```tsx
// GitPanel.tsx
// 依赖: 新增 gitService.ts（API调用层）
// 状态: repoId（当前工作台绑定的Git仓库）、status、commits、branch

interface GitPanelProps {
  repoId: string;           // 当前工作台绑定的仓库ID
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

## T1.3: 本体工作台保存自动commit（1天，FE+BE联动）

**目标**: 用户在OntologyDesigner中创建/修改/删除实体、属性、关系时，自动执行git commit。repoId=`data-workbench`，路径前缀 `ontologies/{ontologyId}/`。

> **注意**: 本体操作涉及多个Controller（OntologyController、OntologyPropertyController、OntologyRelationshipController等），Phase 1策略是**前端拦截**——OntologyDesigner保存成功后调用gitService.commit()。Phase 2再考虑后端AOP拦截方案。

**改文件1**: `c2eos/src/pages/OntologyDesigner.tsx`（或当前本体设计器的入口组件）

**改动逻辑**:
```
用户在本体设计器中执行任意保存操作（新增实体/修改属性/删除关系等）
  → 1. 原有保存逻辑（POST/PUT/DELETE 到 /api/v1/ecos/ontologies/...）
  → 2. 所有操作成功后，调用 gitService.commit("data-workbench", {
        message: "ontology: {ontologyName} — {操作摘要}",
        paths: ["ontologies/{ontologyId}/"],
        author: currentUser
      })
  → 3. GitPanel自动刷新状态
```

**操作摘要格式**:
- 创建实体: `新增实体「客户信息」`
- 修改属性: `修改属性「客户名称」`
- 删除关系: `删除关系「客户→订单」`
- 批量操作: `批量修改3个实体`

**commit message格式**: `ontology: {本体名称} — {操作摘要}`

**前端改动要点**:
- OntologyDesigner页面嵌入GitPanel组件: `<GitPanel repoId="data-workbench" />`
- 在save回调链末尾统一调用gitService.commit()
- 仅当有实际变更时才commit（避免空提交）

**验收**:
```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Admin@123"}' | jq -r '.data.token')
H="Authorization: Bearer $TOKEN"

# 1. 浏览器打开OntologyDesigner
# 2. 新增一个实体 → 保存
# 3. 切换到GitPanel → 确认出现一条新commit，message含"ontology:"
# 4. curl验证:
curl -s -H "$H" "http://localhost:8080/api/v1/ecos/git/commits?repoId=data-workbench&limit=3" | jq '.data[0].message'
# 期望: 包含 "ontology:"
```

---

## T1.4: Pipeline保存自动commit（1天，FE+BE联动）

**目标**: 数据工程师在PipelineBuilder中点击保存时，自动执行git commit。repoId=`data-workbench`，路径前缀 `pipelines/`。

**改文件**: `c2eos/src/components/data-workbench/PipelinesView.tsx`

**改动逻辑**:
```
用户点击[保存管道]
  → 1. 原有保存逻辑（POST /api/v1/ecos/... 或其他管道保存端点）
  → 2. 保存成功后，调用 gitService.commit("data-workbench", {
        message: "pipeline: {pipelineName} saved",
        paths: ["pipelines/{pipelineId}.json"],
        author: currentUser
      })
  → 3. GitPanel自动刷新状态
```

**验收**:
```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Admin@123"}' | jq -r '.data.token')
H="Authorization: Bearer $TOKEN"

curl -s -H "$H" "http://localhost:8080/api/v1/ecos/git/commits?repoId=data-workbench&limit=3" | jq '.data[0].message'
# 期望: 包含 "pipeline:"
```

---

## T1.5: DQ规则保存自动commit（0.5天，FE）

**目标**: 与T1.4相同模式，DQ规则保存时自动commit。repoId=`data-workbench`，路径前缀 `dq-rules/`。

**改文件**: `c2eos/src/pages/DataQualityDashboard.tsx`

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
curl -s -H "$H" "http://localhost:8080/api/v1/ecos/git/commits?repoId=data-workbench&limit=5" | jq '.data[].message'
# 期望: 其中至少一条包含 "dq-rule:"
```

---

## 环境准备（PMO自行完成，不计入Task）

### E1: 初始化Git仓库（从系统配置读取路径）

```bash
# 读取配置中的repo根路径
REPO_ROOT="/home/guorongxiao/ecos-git-repos"  # 默认值，与sys_config中ecos_git_repo_root一致

mkdir -p $REPO_ROOT/data-workbench
cd $REPO_ROOT/data-workbench
git init
git config user.email "ecos@chinacreator.com"
git config user.name "ECOS Data Workbench"

# 创建初始目录结构（含本体工作台的ontologies目录）
mkdir -p pipelines dq-rules scripts mappings agents kg-schemas ontologies
touch pipelines/.gitkeep dq-rules/.gitkeep scripts/.gitkeep ontologies/.gitkeep
git add .
git commit -m "init: data workbench + ontology repository"
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
| 1 | sys_config中存在 `ecos_git_repo_root` 配置项 | curl查 `/api/v1/system/config?group=g5-git` |
| 2 | GitController 8个端点全部返回200 | curl逐条验证 |
| 3 | GitStatus返回正确的分支名和未提交文件 | curl `/status` |
| 4 | GitCommit成功创建commit | curl POST `/commit` + 查 `/commits` |
| 5 | GitPanel在浏览器中显示分支/变更/历史 | 浏览器截图 |
| 6 | **本体设计器保存 → GitPanel出现ontology: commit** | 端到端操作+截图 |
| 7 | 保存Pipeline → GitPanel出现pipeline: commit | 端到端操作+截图 |
| 8 | 保存DQ规则 → GitPanel出现dq-rule: commit | 端到端操作+截图 |
| 9 | 零TS错误 | `npx tsc --noEmit \| grep -i git` = 0 |
| 10 | 前端/后端无硬编码Git仓库路径 | grep验证 |

---

## 执行顺序

```
E1 初始化Git仓库（30min）
  └→ 注意: ontologies/ 目录已包含在内
T0  激活GitService为Spring Bean（0.5h）
T0.1 增加系统配置项 ecos_git_repo_root（0.5h）
  └→ T1.1 GitController（2天）—— 依赖T0+T0.1
T1.2 GitPanel + gitService.ts（1天）—— 依赖T1.1的API就绪
T1.3 本体工作台自动commit（1天）—— 依赖T1.2
T1.4 Pipeline自动commit（1天）—— 依赖T1.2
T1.5 DQ规则自动commit（0.5天）—— 依赖T1.2
```

**总工时**: ~6.5天。T1.1是最关键路径。

---

## 一句话给PMO

runtime-core的Git模块是现成的。你的核心工作是：**①sys_config加一个配置项** `ecos_git_repo_root`→**②用8个REST端点把GitService包一层**（从配置读路径，不硬编码）→**③前端做GitPanel**→**④在三个保存入口挂上自动commit**（本体设计器、Pipeline、DQ规则）。先让commit能跑通，别纠结merge/rebase——那些是Phase 2的事。
