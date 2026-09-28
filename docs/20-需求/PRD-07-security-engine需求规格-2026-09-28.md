# PRD-07 security-engine 需求规格（分册 07）

> 来源: 肖国荣 | 日期: 2026-09-28 | 责任人: AI Agent
> 版本: v1.0
> 上游: [PRD-00 总纲](PRD-00-ECOS平台需求规格说明书-2026-09-28.md) · [PRD-01 平台级](PRD-01-平台级与横切需求规格-2026-09-28.md)
> 覆盖: REQ-SEC-01~04
> 模块: `ecos_backend/engine/security-engine`（sysman:18081）

---

## 一、REQ-SEC-01 领域链路安全接入（P0）

### 1.1 敏感数据目录（年度经营预测场景，首批）

| 数据对象 | 字段 | 敏感级 | 防护组合 |
|---|---|:--:|---|
| 合同 | base_amount | S2 | RLS(部门域) + CLS(非财务隐藏) + mask(AMOUNT 对无权角色) |
| 资源投入 | staff_ref, hourly_rate | S3 | staff_ref 脱敏存储（工号哈希）；rate CLS 仅财务/HR |
| 成本 | amount(SALARY 类) | S3 | RLS + CLS + mask |
| 成本 | amount(MANAGEMENT/MARKETING) | S2 | RLS + CLS |
| 预测结果 | 利润/收入聚合值 | S2 | RLS（项目负责人仅本项目）；导出通道同权 |
| 下钻明细 | stage_fact 行级 | S2 | RLS 行过滤 |

> 金额列不做存储加密（PRD-02 §1.3-4 裁定：聚合计算需要，防护=RLS+CLS+mask 组合）；该例外须在 ST03 评审记录登记理由。

### 1.2 策略种子（DDL 只加不删；RLS/CLS 策略表无物理外键——既有裁定）

**RLS（ecos_rls_policy，同表多策略 AND 连接、按 priority）**：

| 策略 | 表 | 条件模板 | 角色 |
|---|---|---|---|
| rls-project-owner | ecos_biz_stage_fact / cost / resource | `project_id IN (SELECT project_id FROM ecos_biz_project_attribution WHERE department_id = :userDeptId)` | 项目负责人 |
| rls-dept-scope | 同上 | `department_id = :userDeptId` | 部门用户 |
| rls-finance-all | 同上 | `1=1` | 财务负责人 |

**CLS（ecos_cls_policy，用户级>角色级>全局）**：非财务角色隐藏 `hourly_rate`、SALARY 成本 `amount` 列。

### 1.3 三通道强制点

| 通道 | 强制点 | 规格 |
|---|---|---|
| 页面查询 | datanet facts 查询端点前置 `rls/apply`，返回前 `cls/columns` + `mask` | 既有 §2.4-1/2/3 落地点清单化 |
| **导出** | 导出服务复用与页面查询**同一** RLS/CLS 管道（禁止导出走独立 SQL 绕过滤）；导出文件头写入 权限范围+口径+asOf（联动 SEC-04） | 新增导出前置检查 |
| **AI 工具** | PRD-06 AI-01 OPA 裁决 + obligations 脱敏；工具后端查询同样过 RLS/CLS（双保险，OPA 管"能不能调"，RLS/CLS 管"看到什么"） | 护栏接线联动 |

### 1.4 测试矩阵（角色 × 通道 × 期望）

| 角色 | 页面 | 导出 | AI |
|---|---|---|---|
| 财务负责人 | 全量可见 | 全量 | 全量 |
| 项目负责人(p1) | 仅 p1 行；rate 列不可见 | 仅 p1；文件头含范围 | forecast.drilldown p1 ALLOW / p2 DENY |
| 部门用户(d01) | 仅 d01 行 | 仅 d01 | 同上按部门域 |
| 无权用户 | 403/空集（不泄露存在性） | 拒绝 | GUARDRAIL_DENIED |

### 1.5 验收标准

1. 测试矩阵 12 格全过（集成测试，PMO-73 G3 联动）；
2. security 进程停止 → 三通道全部默认 DENY（页面 503 拒绝、导出拒绝、AI FAIL_CLOSED）；
3. 导出绕过滤的旁路代码 grep 审查 = 0（导出与查询共用过滤管道的代码结构证明）。

---

## 二、REQ-SEC-02 默认 DENY 守护与三滤波器 CI 化（P0）

### 2.1 whitelist 唯一清单源

`auth.whitelist.paths`（application.yml）为唯一事实源；**匿名登记文件** `docs/40-实现/auth-anonymous-registry.md`：每条匿名路径必须登记（路径 + 理由 + 批准人），未登记的 permitAll = 违规。

### 2.2 匿名回归脚本（PMO-73 G3-T3）

```
输入：whitelist 清单 + 匿名登记文件
对每条 whitelist 路径：
  case A（在匿名登记）：无 token 请求 → 期望非 401/403（真匿名）
  case B（不在登记）  ：无 token 请求 → 期望 403（默认 DENY 证明）
  两种 case：带 token 请求 → 期望非 401/403
输出：违规清单（case B 却放行的路径 = P0 事故级，直接 FAIL）
挂点：CI pr-gate 新阶段；本地可跑（脚本落 ecos-tests/ 或 gateway test）
```

### 2.3 新增端点检查单（评审模板固化）

1. 该端点应匿名？否 → permitAll 不写；
2. 前缀在 `V1_REWRITE_MAP` 内？是 → 鉴权/豁免层只写裸路径；否 → 双路径各写一遍；
3. 含连字符/近似前缀 → 显式写完整前缀（Ant 路径陷阱）；
4. 提交前跑匿名回归脚本本路径三项断言。

### 2.4 验收

1. 脚本对现网 whitelist 全量扫描 0 意外放行；
2. 故意加一条未登记 permitAll → CI FAIL（负样例演练一次）；
3. 评审模板含检查单（Reviewer skill 同步）。

---

## 三、REQ-SEC-03 前端 authHeaders 统一（P1，= D5 / PMO-73 G2-T1/T2）

### 3.1 模块规格 `ecos_frontend/src/services/auth.ts`

```typescript
export function getToken(): string | null;              // localStorage 读取（键名沿用现状）
export function authHeaders(): Record<string, string>;  // { Authorization: `Bearer ${token}` }，无 token 返回 {}
export function parseTokenPayload(): TokenPayload | null; // 唯一 JWT 解析实现（base64url + JSON，容错 null）
export function setAuthGracePeriod(ms?: number): void;  // 10s 宽限期（既有登录误伤修复，逻辑迁入）
export function handleAuthExpired(): void;              // 宽限期内 401 只 throw 不清 token
```

### 3.2 替换清单（2026-09-28 grep 实证 ≥10 文件）

`pages/data-workbench/api.ts` · `services/taskCenter.ts` · `pages/DataAssetsDashboard.tsx` · `pages/business-workbench/BusinessObjectExplorer.tsx` · `services/aiworkbenchApi.ts` · `pages/MonitoringCenter.tsx` · `pages/GuardrailsView.tsx` · `pages/EngineMonitor.tsx` · `pages/AIPKnowledgeView.tsx` · `pages/data-workbench/pipelineDebugApi.ts`（执行时以最新 grep 为准，逐一删除本地实现改导入）。

### 3.3 验收

1. grep `function authHeaders|const authHeaders` 全仓仅 auth.ts 1 处；
2. tsc 0 错误；登录→数据工作台→任务中心→AI 工作台冒烟通过（宽限期行为不回归）；
3. Token 解析行为单测（合法/过期/畸形 token 三用例）。

---

## 四、REQ-SEC-04 审计包导出（P2）

### 4.1 包结构（zip）

```
forecast_{runId}_audit_{yyyyMMddHHmmss}.zip
├── manifest.json          // 元数据：runId/caliberId@version/asOfTime/导出人/导出时间/权限范围/各文件 checksum
├── input-snapshot.json    // 快照引用清单（factRefs 计数+ID 列表，不含越权行）
├── formulas.json          // 口径公式快照（caliber_snapshot_json）+ 指标版本
├── assumptions.csv        // 引用的画像/假设版本清单
├── results.csv            // 结果明细（导出人权限范围内，RLS/CLS 已过滤）
├── actions.csv            // 关联经营动作及审批记录
└── evidence-index.csv     // 证据引用索引（ref → 定位信息）
```

### 4.2 规则

1. 导出内容 = 导出人权限范围（复用 SEC-01 管道，禁全量旁路）；
2. manifest.json 头部元数据完整（币种/单位/口径版本/截至时点——对齐附件 §6.1-5）；
3. 导出动作发审计事件（eventType=`AUDIT_PACK_EXPORT`）；
4. 端点：`GET /api/v1/workspace/forecast-runs/{runId}/audit-pack`（workspace 聚合各 service 数据打包，OPA 裁决 `audit:export`）。

### 4.3 验收

1. 财务角色导出包含全量；项目负责人导出仅本项目行且 manifest 标明范围；
2. zip 各文件 checksum 与 manifest 一致；
3. 审计事件可查。

---

## 五、追溯与依赖

| REQ | 依赖 | 被依赖 | 批次 |
|---|---|---|---|
| SEC-01 | PRD-02 事实表落地、OPA 策略管理 | PRD-06 AI-01/04、演练全项 | 场景批次 A（P0 门槛） |
| SEC-02 | whitelist 清单 | 全部新增端点批次 | **PMO-73 G3** |
| SEC-03 | — | PRD-08 FE-06 迁移 | **PMO-73 G2** |
| SEC-04 | FC-02 运行服务、SEC-01 管道 | 附件 §6.3-4 | 场景批次 C（P2） |

<!-- PRD-07-security-engine需求规格 / 2026-09-28 / v1.0 -->
