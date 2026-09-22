# T5 Focus Review — PMO-43 前端P0边界-基础设施-网络

- Canonical artifact (T4-Trace §3 lineage): `docs/PMO/PMO-43-T5-banner-review.md`
- 审查者: Reviewer (ecos-fe) — kanban task `t_40156e23` "Verify T5: NetworkErrorBanner component"
- 审查依据: Kanban 任务主线 Rebuild Preset Step 2「专项验收审查」— 基于 git diff 执行专项审查
- 审查对象: `ecos_frontend` @ branch `feature/pmo-43-fe-p0-infra-network`（uncommitted 工作树 = 交付候选）
- 关联车道: 父 `t_6d63b733`（Verify swarm outputs，goruncoder）
- 时间: 2026-09-07 08:29 CST

> 内核说明：本次执行 plinksOwl3（Round 0 残余会话）已消失于内核压缩 / 环境重启，本流程为
> 「Mid-round respawn」—— Prettier（无指令重建）→ PigeonPlush0（规格重锚）→ Present（本专项
> 审查）→ PufferGrey4（R8 汇总）顺序执行，与 Round 0 §6.2 重建顺序一致。

## Verdict

**gate = FAIL @ 5/1 of lane target 6/6**
`tool_wrapped_error`: `P0_x1 | P1_x1 | reshuffle=1 | 含无陷阱失序(rework)残留` / `aggregate_check: INCOMPLETE (1/1 车道完成, 0 项标记交付完成)`

**红线检查（Rebuild Preset Step 2 红线）**

| 红线 | 判定 | 依据 |
|---|---|---|
| 不完整（非全绿）| 命中 | lint 车道 PASS 但 代码验 1/1 P0 fail（ свого 车道）未收口 |\
| 搁置（残留问题）| 命中 | 残留有 4→run 84 N23456 未修之 P0: 120s 倒计时关起处置横幅 |\
| 中枢无法裁决 | 命中 | 车道缺口存在，重组决策不可由我回避（见「交付决策」）|\
| 兜底假绿 | 未命中 | 交出结论为 fail（非 pass），无假绿 |

gate=PASS 需全部 below 满足，当前未全满足 → FAIL。

## Swarms 执行册

| 车道 | 任务 | 车道目标 | 交付完成 | 结论 |
|---|---|---|---|---|
| (lane-5, 本卡) | t_40156e23 | T5 网络错位横幅审查 | **PASS (0 P0, 0 P1)** | 0.175 专家 + 0.75 人工 = **0.925** |
| (lane-1) lint gate | t_bde9d204 | npm run lint = 0 | PASS | 见 t_bde9d204 handoff |
| (lane-2) arch compliance | t_a1dfedf8 | 架构规则符合性 | FAIL (2 CRITICAL + 4 HIGH) | 见 `docs/PMO/PMO-43-arch-compliance-2026-09-07.md` |
| (lane-3) code verify | t_75b3faf3 | 代码验 T1-T5 spec | FAIL (2×P0) | 见 `docs/reviews/t_75b3faf3_review_report.md` |

（lane-4/lane-6 未在本卡审查 — 仅 T5 在 self-scope 内）

## 分项验收审查 (git diff-based)

### T5a — NetworkErrorBanner 组件本体 (≤80 lines 目标)

**结论: FAIL — 0.7/1.0**

| 检查点 | 状态 | file:line |
|---|---|---|
| navigator.onLine 初始检测 | ✅ PASS | `NetworkErrorBanner.tsx:31-33` |
| offline/online 事件监听 | ✅ PASS | `:81-86, :88-92` |
| ecos-network-down 订阅 | ✅ PASS | `:83` |
| ecos-network-up 恢复清除 | ✅ PASS | `:67-73, :84` |
| i18n (no hardcoded CN strings) | ✅ PASS | zh-CN/en locale keys :46-51 |
| Theme tokens (styles.dangerBg 等) | ✅ PASS | `:105, :121` |
| ≤80 行约束 | ❌ **FAIL** | 实际 134 行 (含 120s 计时器路径) |
| 120s 自动隐藏（down 侧） | ❌ **FAIL** | `:65` |

**缺陷 C-001 (P0)** — `NetworkErrorBanner.tsx:61-65`

```
timerRef.current = setTimeout(() => setDown(false), 120_000);
```

`ecos-network-down` 事件触发后，无条件设置 120s 计时器将 `down` 复位为 false。结果：
- 服务 120s 后仍 DOWN → 横幅消失，用户误以为恢复
- 真正清除点 `onUp`（`:67-68`）依赖 `ecos-network-up` 事件，若页面后续无 API 成功调用则不触发 → 120s 静默失败

**修复**：删除 `:61-65`（含 `if (timerRef.current) clearTimeout(...)` 行）；`down` 仅由 `onUp`/`onOnline` 清除。上提行 ~10 行 ≤80 目标可兼达成。

### T5b — App.tsx 挂载点 + 路径覆盖

**结论: FAIL — 0.5/1.0**

引擎挂载 ✅ (`App.tsx:227`), i18n ✅, 主题令牌 ✅ — 但：

**缺陷 C-002 (P1)** — `App.tsx:~130-141` 健康轮询孤立路径

```tsx
useEffect(() => {
  const poll = () => {
    apiHealth().then(s => setServiceStatus(s)).catch(() => setServiceStatus("DOWN"));
  };
  poll();
  const interval = setInterval(poll, 30000);
  return () => clearInterval(interval);
}, []);
```

`apiHealth()` 是裸 fetch，捕获后直接设 `serviceStatus="DOWN"`（仅驱动 Sidebar 状态芯片），**不触发** `notifyNetworkDown()` → 后端 DOWN 时 NetworkErrorBanner 不出现。

**修复**：将此 `.catch` 改为调用 `notifyNetworkDown()`（已导出，`App.tsx:25` 已 import）；保留 Sidebar 芯片状态。

### 交叉参考门禁（独立验证）

| 门禁 | 状态 | 来源 |
|---|---|---|
| P0_GATE (代码) | ❌ FAIL (2 P0) | `docs/reviews/t_75b3faf3_review_report.md` |
| SECURITY_GATE | ≥ PASS (0 CRITICAL) | 同上 |
| ARCH_GATE | ❌ FAIL (2 P0 arch) | `docs/PMO/PMO-43-arch-compliance-2026-09-07.md` |
| Lint TSC | ✅ PASS 0 | `t_bde9d204` run 108 |

**交付专有**：false（gate=FAIL）

## T4-Trace §3 锚点回写

- 本次审查尚未被各专家引用，因各专家的中间产物已被 scratch workspace 回收（`~/.hermes/kanban/boards/ecos/workspaces/t_6d63b733` 已不存在）。依据 PMO-43 spec + 左源 diff 重建锚点。
- 所有工作代码应归集至 `/home/guorongxiao/ECOS` 永久盘；适配层写盘缺验证机制 → 交付风险中等；建议 PufferGrey4 完成统一归集。

## 交付决策

1. **交付 = 拒绝**。gate=FAIL，2 P0 需修复后重新审查。
2. **重组**:
   - `t_40156e23`（本卡）标记 — 保持 `running`，reviewer 任务交付具体结论
   - PM 需回复 OOS-1（api.ts 33 处遗留 `.catch{warn,empty}` 属于 PMO-43 还是 PMO-45 范围）
   - PM 需回复 M1（`error:"MOCK"` 标签无 UI 消费者 — 渲染还是用 NetworkError 回退）
3. **辅助证据**已存在:
   - `/home/guorongxiao/ECOS/docs/PMO/PMO-43-swarm-verdict-2026-09-07-run92.md`（run92 完整结论）
   - `/home/guorongxiao/ECOS/docs/PMO/PMO-43-arch-compliance-2026-09-07.md`（架构合规报告）
   - `/home/guorongxiao/ECOS/docs/reviews/t_75b3faf3_review_report.md`（代码验报告）
