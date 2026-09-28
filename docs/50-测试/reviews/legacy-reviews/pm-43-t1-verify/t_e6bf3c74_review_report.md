# 代码审查报告：t_e6bf3c74 — Verify T1: api.ts error handling refactoring

**任务 ID**: t_e6bf3c74
**执行时间**: 2026-09-07 08:16 CST
**审查引擎**: Manual review + tsc gate（OCR engine 未启用：228 行 diff，无安全相关改动）
**artifact**: `ecos_frontend/src/api.ts` @ wt-diff-HEAD（228 insertions / 21 deletions）
**deliverable_allowed**: **false**

---

## 质量门禁结果

| 门禁 | 状态 | 实际值 | 阈值 | 说明 |
|------|------|--------|------|------|
| P0_GATE | **PASS** | 0 个 P0 | 必须 0 | 无 P0 缺陷 |
| P1_GATE | **FAIL** | 2 个 P1 | ≤ 3 | 见 P1 列表 |
| SECURITY_GATE | **PASS** | 0 CRITICAL / 0 HIGH | 0 | 未新增 import 或权限相关变更；403 处理是行为修正而非安全漏洞 |
| ARCH_GATE | **PASS** | 0 P0/P1 架构违规 | 0 | 事件分发是 additive，无架构边界变更 |

**最终判定**：**FAIL**（P1_GATE = FAIL：2 个遗漏的 `.catch{warn, return empty}` 反模式）
**deliverable_allowed**: **false**

---

## P0 / P1 缺陷

### C-001 (P1): `fetchDatasets` .catch{warn, return mock} 仍然存在
- **位置**: `ecos_frontend/src/api.ts:391-394`
- **描述**: `catch (e) { console.warn("fetchDatasets: backend unavailable, using mock", e); return MOCK_DATA_ASSETS_TAGGED; }` 仍是原始反模式：网络错误被 console.warn 吞掉，返回空/ mock 数据。任务体声称"all 5 instances replaced with NetworkDownEvent"，但此实例未被替换。
- **影响**: UI 层不会收到 `ecos-network-down` 事件，NetworkErrorBanner 无法展示；silent failure 仍在。
- **建议**: 在 catch 块中 `markNetworkDown(); await notifyNetworkDown(e); throw toNetworkError(e, 'fetchDatasets');`（与 `apiFetch`/`apiFetchData`/`doFetch` 的新模式一致）。

### C-002 (P1): `fetchDataset` schema 错误被静默吞掉
- **位置**: `ecos_frontend/src/api.ts:419`
- **描述**: `catch (e) { /* schema optional */ }` — 字段详情获取失败时完全静默，既不 warn 也不上报。若 `doFetch` 返回 NetworkError（新行为），此 catch 会吞掉可见的网络错误信号，schema 保持 `[]` 而 UI 无法区分"无字段"与"网络故障"。
- **影响**: 与 C-001 同类 — 静默失败。但影响面较小（字段 schema 是 optional），故 P1 而非 P0。
- **建议**: 至少 `console.warn('fetchDataset: schema fetch failed', e);`，或 if (`e instanceof NetworkError`) 则 throw。

### 残留反模式（未修，同类）

| 位置 | 函数 | 行为 |
|------|------|------|
| api.ts:710-713 | `fetchAuditLogs` | `catch → console.warn + return { data: [], total: 0 }` |
| api.ts:770-773 | `fetchCryptAuditLogs` | `catch → console.warn + return { data: [], total: 0 }` |
| api.ts:676-679 | `fetchUserSecurityProfile` | `catch → console.warn + return { clearanceLevel: 0, ... }` |
| api.ts:839-842 | graph endpoint | `catch { return { nodes: [], edges: [] } }` （无 warn，纯静默） |

以上 4 处也是 "返回空数据" 反模式的实例，但未被本 diff 触及，可能属于其他任务（T2-T5）的 scope。若 T1 的 scope 严格限定为 datasets + fetch helpers，则不在 T1 审查范围内 — 需 PM 确认 scope 边界。

---

## P2 / P3 改进建议

### C-003 (P2): `wrapNet` 是死代码
- **位置**: `ecos_frontend/src/api.ts:40-54`
- **描述**: `wrapNet` 已实现但从未被调用（全项目搜索 `wrapNet` 零匹配）。
- **建议**: 若后续会用于某个场景则保留，否则删除以避免维护混淆。

### C-004 (P3): `isErrorResponse` 导出了但语义与 `doFetch` 的过滤逻辑不一致
- **位置**: `ecos_frontend/src/api.ts:57-66`
- **描述**: `isErrorResponse` 返回 `{message, kind, status}` 但未被 `fetchDatasets`/`fetchDataset`/`fetchAuditLogs` 的 catch 块使用 — 它们直接 `console.warn` + return。该导出可能预留给 UI 层，建议加 TODO 注释或确认消费方。

### C-005 (P3): `lastNetworkDownAt` 是模块级单例，SSR 安全但多实例不安全
- 若未来 api.ts 被 module-load 多次（e.g. HMR 热更新），`lastNetworkDownAt` 会失去一致性。当前项目可接受，仅记录。

---

## 通过项

- ✅ **fetch 层错误传播**（`apiFetch` :209-221, `apiFetchData` :236-249, `doFetch` :267-274）：3 处 `fetch()` 均包裹 `try/catch`，出现网络错误时 `markNetworkDown()` + `notifyNetworkDown(e)` + `throw toNetworkError(...)`。与任务接受的"NetworkDownEvent 或 real error propagation"一致。
- ✅ **403 处理**：`NoAccessError` 类不再清 token，仅发 `ecos-no-access` 事件。与注释意图一致。
- ✅ **mock 数据标记**：`MOCK_DATA_ASSETS_TAGGED` + `error: 'MOCK'` — 类型 `DataAsset.error?: 'MOCK' | string`（types.ts:60）已存在，所有 mock 返回值均带标记，UI 可区分真数据与 placeholder。
- ✅ **tsc --noEmit**: exit 0，无新类型错误。

---

## 缺陷 → PRD 追溯表

| 缺陷 ID | 缺陷描述 | 优先级 | 来源 | 文件位置 |
|---------|---------|--------|------|---------|
| C-001 | fetchDatasets 反模式未修，静默返回 mock | P1 | T1 验收标准 | api.ts:391-394 |
| C-002 | fetchDataset schema 错误被静默 | P1 | T1 验收标准 | api.ts:419 |
| C-003 | wrapNet 死代码 | P2 | 代码卫生 | api.ts:40-54 |
| C-004 | isErrorResponse 导出无消费方 | P3 | API 设计 | api.ts:57-66 |
| C-005 | lastNetworkDownAt 模块级单例 | P3 | 健壮性 | api.ts:89 |

---

## 审查结论

**质量评估**：FAIL（P1_GATE = 2 > 0 FAIL，不满足 T1 "all 5 instances replaced" 验收标准）

| 判定 | 原因 |
|------|------|
| deliverable_allowed = false | C-001 (P1) + C-002 (P1) 未满足 T1 验收标准："no silent failures remain" / "no empty returns on network failure" |
| 阻断原因 | fetchDatasets catch 仍静默返回 mock；fetchDataset schema catch 完全静默 |

**修复方向**（fullstack 侧）：
1. 将 `fetchDatasets` 的 catch 块改为 `markNetworkDown() + notifyNetworkDown(e) + throw toNetworkError(e, 'fetchDatasets')`，并在其调用方添加 try/catch 或改为返回 tagged mock（但需在 UI 层可感知）
2. 将 `fetchDataset:419` 的 `catch {}` 改为至少 `console.warn`
3. 若本任务的 scope 严格限定为 fetch helpers + datasets main path，需 PM 确认残留的 4 处（audit/security-profile/graph）属于哪个后续任务，否则 C-001 可降为 P2

→ 修复后需重新审查（P1 修复 + 本次全部 P1 清零）
