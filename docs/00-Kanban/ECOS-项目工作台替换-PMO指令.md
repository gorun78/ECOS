# PMO指令: 项目工作台替换 + 知识工作台改名

> **来源**: 肖国荣 | **日期**: 2026-07-08
> **铁律**: 本指令分四阶段顺序执行，每阶段完成后curl验证再进下一阶段。
> **技能**: `ecos-module-refinement`

---

## 前置环境

| 组件 | 地址 |
|------|------|
| ECOS前端 | `/home/guorongxiao/c2eos/` |
| ECOS后端 | `/home/guorongxiao/databridge-v2/` |
| ceos_new源 | `/home/guorongxiao/ceos_new/` |
| Gateway | `:8080` |

---

## 阶段一: 侧边栏改名

### 1.1 知识库工作台 → 知识工作台

**文件**: `/home/guorongxiao/c2eos/src/components/Sidebar.tsx`
- 第120行: `labelZh: "知识库工作台"` → `labelZh: "知识工作台"`
- 同行的 `descZh` 更新: `"向量库·文本切片·RAG检索"` → `"知识库管理·RAG检索·数据对象图谱导入"`

**文件**: `/home/guorongxiao/c2eos/src/components/AsyncTaskCenterView.tsx`
- 第592-593行: `'知识库工作台'` → `'知识工作台'`

**文件**: `/home/guorongxiao/c2eos/src/components/LanguageContext.tsx`
- 第491行附近注释: `知识库工作台` → `知识工作台`

### 1.2 应用工作台 → 项目工作台

**文件**: `/home/guorongxiao/c2eos/src/components/Sidebar.tsx`
- 第118行完整替换:
  ```
  旧: { id: "workflow_designer", label: "App Workbench", labelZh: "应用工作台", icon: GitPullRequest, desc: "Workflow & form designer", descZh: "流程设计·表单搭建·运营应用" }
  新: { id: "project_workbench", label: "Project Workbench", labelZh: "项目工作台", icon: Briefcase, desc: "Scenario management & project orchestration", descZh: "场景调度·项目管理·资源编排" }
  ```
- 需在文件顶部 `lucide-react` 导入中补充 `Briefcase`

### 验证

```bash
# 前端编译通过
cd /home/guorongxiao/c2eos && npx tsc --noEmit 2>&1 | head -20
```

---

## 阶段二: 迁移 ScenarioManagementView → ECOS

### 2.1 复制组件

```bash
cp /home/guorongxiao/ceos_new/src/components/ScenarioManagementView.tsx \
   /home/guorongxiao/c2eos/src/pages/ScenarioManagementView.tsx
```

### 2.2 适配修改

迁移后必须修改以下内容:

**a. LucideIcon 导入路径**
```
旧: import LucideIcon from './LucideIcon';
新: import LucideIcon from '../components/LucideIcon';
```
> 注: ECOS已有 `src/components/LucideIcon.tsx`，与ceos_new的 `LucideIcon` 接口兼容。

**b. showToast props 适配**
ECOS中 `showToast` 可能来自父级或全局。检查路由注册方式（阶段三），确认props传递路径。如果父级无showToast props，改为使用ECOS的全局toast机制。

**c. 检查 recharts 依赖**
ECOS已安装 `recharts`（WorkshopView已使用），无需额外安装。

**d. API路径检查**
组件内fetch调用的API:
- `GET /api/ontology/proposals` → 对应ECOS `GET /api/v1/ontology/proposals`（需网关路由，或改fetch路径）
- `POST /api/ontology/proposals/{id}/execute` → ECOS已有，路径一致
- `POST /api/ontology/proposals/{id}/reject` → ECOS已有，路径一致
- `POST /api/knowledge/query` → 需确认ECOS对应端点
- 注释中引用的 `/api/security/audit-logs` → 需确认ECOS对应端点

**决策**: 组件内fetch路径统一加 `/api/v1` 前缀（ECOS Gateway规范），或Gateway层做 `/api/` → `/api/v1/` 转发。

### 验证

```bash
cd /home/guorongxiao/c2eos && npx tsc --noEmit 2>&1 | grep -i scenario
```

---

## 阶段三: 路由注册

### 3.1 修改 main.tsx

**文件**: `/home/guorongxiao/c2eos/src/main.tsx`

```diff
- import WorkshopView from './pages/WorkshopView';
+ import ScenarioManagementView from './pages/ScenarioManagementView';

- <Route path="workflow_designer" element={<WorkshopView />} />
+ <Route path="project_workbench" element={<ScenarioManagementView />} />
```

### 3.2 旧 WorkshopView 处理

不删除，保留在代码库但取消路由注册。后续如需流程设计功能可复用。

### 验证

```bash
# 编译 + 前端启动验证
cd /home/guorongxiao/c2eos && npx tsc --noEmit
# 浏览器打开 http://localhost:5173/project_workbench 确认渲染
```

---

## 阶段四: 后端 workspace 模块

### 4.1 创建模块目录

```
databridge-v2/databridge-workspace/
├── pom.xml
└── src/main/java/com/chinacreator/gzcm/workspace/
    ├── controller/
    │   └── ScenarioController.java       ← 场景CRUD（如需要新端点）
    ├── service/
    │   └── ScenarioService.java
    └── model/
        └── BusinessScenario.java          ← 场景实体
```

### 4.2 分析: 哪些端点需要新增

**已有端点（无需再造）**:
| ceos_new端点 | ECOS对应 | 状态 |
|-------------|---------|:--:|
| `GET /api/ontology/proposals` | `OntologyProposalController.listProposals` | ✅ |
| `POST /api/ontology/proposals` | `OntologyProposalController.createProposal` | ✅ |
| `POST /api/ontology/proposals/:id/execute` | `OntologyProposalController.executeProposal` | ✅ |
| `POST /api/ontology/proposals/:id/reject` | `OntologyProposalController.rejectProposal` | ✅ |
| `POST /api/ontology/proposals/:id/verify` | `OntologyProposalController.verifyProposal` | ✅ |

**需确认/补齐**:
| ceos_new端点 | 用途 | 状态 |
|-------------|------|:--:|
| `POST /api/knowledge/query` | 知识库检索 | 需确认ECOS是否有对应 |
| `GET /api/security/audit-logs` | 安全审计日志 | 需确认ECOS是否有对应 |

**需新增（场景管理专属）**:
| 端点 | 用途 | 优先级 |
|------|------|:--:|
| `GET /api/v1/workspace/scenarios` | 场景列表 | P0 |
| `POST /api/v1/workspace/scenarios` | 创建场景 | P0 |
| `PUT /api/v1/workspace/scenarios/{id}` | 更新场景 | P1 |
| `DELETE /api/v1/workspace/scenarios/{id}` | 删除场景 | P1 |
| `GET /api/v1/workspace/scenarios/{id}/bindings` | 场景绑定关系查询 | P1 |

### 4.3 ScenarioController 实现

- 初期用内存存储（ConcurrentHashMap），与 ProposalController 风格一致
- 实体字段参照 `ScenarioManagementView.tsx` 中的 `BusinessScenario` 接口
- 数据模型: id/name/description/businessGoal/department/priority/status/budget/safetyIndexTarget/actualSafetyIndex/bindings/metrics

### 4.4 注册到父POM + Gateway路由

1. `databridge-v2/pom.xml` 添加 `<module>databridge-workspace</module>`
2. Gateway `application.yml` 添加路由: `/api/v1/workspace/**` → workspace模块
3. workspace的 `pom.xml` 依赖 `common` 模块（获取 `ApiResponse`）

### 验证

```bash
# 后端编译
cd /home/guorongxiao/databridge-v2 && mvn install -pl databridge-workspace -am -DskipTests
# curl验证场景CRUD
curl -s http://localhost:8080/api/v1/workspace/scenarios | jq .
```

---

## 注意事项

1. **不改已有API路径** — ScenarioManagementView内fetch路径如需调整，优先改Gateway路由而非改组件
2. **LucideIcon兼容** — ECOS的 `src/components/LucideIcon.tsx` 与 ceos_new的接口一致，已有多处使用
3. **showToast传递** — ceos_new通过props，ECOS可能需要适配全局toast。先检查路由注册处父组件是否有toast机制
4. **旧WorkshopView保留** — 不删文件，只取消路由注册
5. **workspace模块先轻后重** — 第一阶段只做内存存储+CRUD，后续可升级JPA/MyBatis-Plus

---

## 执行顺序

```
阶段一(改名) → 阶段二(迁移) → 阶段三(路由) → 阶段四(后端)
     ↓              ↓              ↓              ↓
  tsc验证        tsc验证      浏览器验证     curl验证
```

每阶段PASS才进下一阶段。
