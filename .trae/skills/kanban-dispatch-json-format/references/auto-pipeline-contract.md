# PM 全自动流程编排契约（auto_pipeline）

> 本文档固化 PM 全自动（零人工干预）编排契约，作为 `kanban-dispatch-json-format` Skill 的全自动模式参考。
> 需求来源：`docs/01需求分析/01-软件开发工厂/PM全自动流程编排优化方案.md`（REQ-027，v2.0，需求审计 t_88ecb050 gate=pass）。
> 复杂度评估与 L1/L2/L3 工作流见 `pm-complexity-assessment` Skill；标准（非自动）协作契约见本目录 `collaboration-contract.md`。

## 1. 触发条件

全自动模式仅当**同时满足**以下条件时激活：

1. PM `config.yaml` 的 `auto_pipeline.enabled: true`
2. 用户消息包含完整触发词之一：`零干预` / `全自动执行` / `自动编排`

> 「自动」等单关键词不触发，避免误入全自动模式。

## 2. 分级放行（phase）

全自动并非一次性放开全部门禁，而是按 `auto_pipeline.phase` 分级：

| phase | 复杂度评估→审查 | 部署验证 | Git 提交推送 |
|-------|-----------------|---------|-------------|
| 1（默认） | 自动 | 人工确认 | 人工确认 |
| 2 | 自动 | 自动 | 人工确认 |
| 3 | 自动 | 自动 | 自动 |

- **默认 phase=1**：仅复杂度评估→派发→审查自动，部署与提交仍保留人工确认
- 升级到 phase 2/3 需显式治理审批，非代码可自行决定
- `skip_user_confirm` 与 `phase` 同时存在时，`phase` 优先（分级放行是权威门禁）

## 3. 编排主流程

```
复杂度评估（pm-complexity-assessment，全自动模式）
  ├─ L1：直接开发派发
  ├─ L2：自动架构 → 自动开发 → 自动审查 → 部署（按 phase）→ 提交（按 phase）
  └─ L3：自动 PRD → 自动架构 → 自动开发 → 自动审查 → 部署 → 提交
  ▼
派发 swarm（Worker + Verifier + Synthesizer）
  ├─ Worker 完成 → 自 block（review-required）
  ├─ PM 自动验证 git diff 代码落地 → 自动 unblock
  ├─ Verifier 审查 → Gate PASS/BLOCKED
  ├─ Synthesizer crash 2 次 → 自动降级（PM 直接汇合 Worker/Verifier summary）
  ├─ Gate PASS → 进入部署验证
  └─ Gate BLOCKED（P0）→ 自动派发修复 swarm → 重新审查（最多 3 轮）
  ▼
部署验证（pm-auto-deploy，按 phase）
  ├─ 后端 mvn → 强杀 → 启动 → API 验证
  ├─ 前端 build → 启动 → 连通验证
  ├─ 失败 → 自动回退 + 升级人工
  └─ 成功 → 进入 Git 提交
  ▼
Git 提交推送（pm-auto-commit，按 phase）
  ├─ 分支白名单校验（主干拒绝）
  ├─ 生成 type(模块): 动宾结构 commit message
  └─ add → commit → pull --rebase → push
```

## 4. Synthesizer 崩溃降级

根因：Synthesizer profile（pm-*）执行 `humanizer` skill 时 exit code 1。

**双层策略**：
1. **根因修复**：排查 humanizer 的 exit code 1（skill 配置/依赖/权限）；短期无法修复则从 PM `config.yaml` 的 `skills.load` 移除 humanizer
2. **降级兜底**：Synthesizer crash 2 次后自动降级，PM 直接从 Worker/Verifier 的 `completed` 事件提取 summary 汇合，确保流程不阻塞

## 5. P0 修复循环（最多 3 轮）

Verifier Gate BLOCKED（P0）时，自动派发修复 swarm 给对应角色（见 `collaboration-contract.md`「修复任务分发」），修复后重新审查：

| 轮次 | 超时 | 动作 |
|------|------|------|
| 第 1 轮 | 600s | 派发修复 → 重新审查 |
| 第 2 轮 | 600s | 换策略重新修复 → 重新审查 |
| 第 3 轮 | 600s | 激进修复（换 worker / 拆分任务）→ 重新审查 |
| 3 轮未修复 | — | 升级人工介入 |

> 架构（Arch）Gate BLOCKED 同样纳入修复循环，非直接升级人工。

## 6. 超时权威值（统一）

| 层级 | 阈值 |
|------|------|
| worker | 900s |
| verifier | 600s |
| synthesizer | 180s |
| repair 单轮 | 600s |
| 部署后端编译 | 600s |
| 部署前端构建 | 300s |
| API 响应阈值 | 3s |

## 7. 中断与异常

| 场景 | 处理 |
|------|------|
| 用户 out-of-band 指令 | 中断标志位置位，当前阶段结束后暂停，等待人工 |
| 后端编译失败 | 不启动，升级人工 |
| 前端构建失败 | `git checkout HEAD -- frontend/` 回退，升级人工 |
| 部署失败 | 自动回退 + 通知用户 |
| 端口被其他项目占用 | 拒绝操作，升级人工 |
| 主干分支 | 拒绝 push，走 MR/PR |
| 敏感文件（.env/key/credential） | 不纳入 add，升级人工剔除 |

## 8. 相关 Skill

| Skill | 职责 |
|-------|------|
| `pm-complexity-assessment` | 复杂度评估（全自动模式跳过确认） |
| `kanban-dispatch-json-format` | swarm 派发与追踪 |
| `pm-auto-deploy` | 部署验证（后端/前端/API） |
| `pm-auto-commit` | Git 提交推送（分支白名单 + commit 规范） |
