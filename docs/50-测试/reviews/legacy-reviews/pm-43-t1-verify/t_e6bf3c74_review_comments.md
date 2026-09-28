# 审查意见 (Review Comments): t_e6bf3c74

逐行审查 `ecos_frontend/src/api.ts`（228+/21-，相对 HEAD）。

## ✅ 已正确修复（fetch 层基础设施）

| 行 | 项目 | 判定 |
|----|------|------|
| 206-220 | `apiFetch`：`fetch()` 包 try/catch → `markNetworkDown()` + `notifyNetworkDown(e)` + `throw toNetworkError(e, 'fetch '+path)` | PASS — 符合 spec T1 ② |
| 233-249 | `apiFetchData`：同模式 + `res.text().catch(() => '')` 403 转 NoAccessError | PASS |
| 263-290 | `doFetch`：同模式 network 归一化 | PASS |
| 57-66 | `isErrorResponse` 导出 | PASS |
| 35-54 | `NetworkError extends Error` 类定义 | PASS — 符合 spec T1 ③ |
| 69-84 | `notifyNetworkDown` 单点声明 → `window.dispatchEvent('ecos-network-down')` | PASS — 符合 spec T1 ① |
| 338-348 | `MOCK_DATA_ASSETS_TAGGED` 生成（`error: 'MOCK'`） | PASS — 符合 spec T1 ④ 意图 |
| 350-395 | `fetchDatasets`：4 处 mock return 改为 `MOCK_DATA_ASSETS_TAGGED` | PASS（标注 MOCK）|
| 397-466 | `fetchDataset`：mock 命中 + 最小资产均标 `error: 'MOCK'` | PASS |
| 15-46 (types.ts:60) | `DataAsset.error?: 'MOCK' \| string` 字段新增 | PASS |

tsc --noEmit: exit 0 ✅

## ⛔ 未修复（spec T1 ① 的 5 处 catch 中 ≥3 处未触及）

Spec T1 明确列出 5 处需改造的 `.catch(e => { console.warn(...); return { data: [], total: 0 }; })`。
本 diff 实际触及的旧行号区间仅为 44-263（见 hunk 头），**spec 列出的 line 1254-1255 / 728-770 / 2318-2325 三处 catch 区域完全未被触及**。

| spec 旧行 | 当前文件位置 | 现状 | 判定 |
|-----------|------------|------|------|
| 144-188 (DatasetsArea) | api.ts:351-395 | catch→`console.warn`+`return MOCK_DATA_ASSETS_TAGGED`：是数据集 mock，但 catch 仍是静默吞错（无 notifyNetworkDown）。**部分满足**（有 MOCK 标注但非 notifyNetworkDown） | PARTIAL |
| 294-322 (authLogin 区) | api.ts:320-334 | `authLogin` 无 .catch，直接 throw。无需改造 | PASS（N/A） |
| 1254-1255 (PipelineArea) | 未在 diff 触及 | 未触及 | **方案** FAIL |
| 728-770 (SecurityProfile+Audit) | api.ts:671-774（含 fetchAuditLogs 710、fetchCryptAuditLogs 770） | `catch → console.warn(...) + return { data: [], total: 0 }` **原样保留** | FAIL |
| 2318-2325 (低层 fetch 封装 2318) | 未在 diff 触及 | 未触及 | **方案** FAIL |

## 评审验收 grep（spec 第 53 行）

```
grep -R "return { data: []" src/api.ts
```
**预期**：仅允许 `error: 'MOCK'` 标注命中。
**实际**：仍有 **9** 处非-MOCK `return { data: []`：
```
712  fetchAuditLogs          return { data: [], total: 0, page, pageSize }
772  fetchCryptAuditLogs     return { data: [], total: 0, page, pageSize }
1897 fetchUsers              return { data: [], total: 0 }   (+console.warn)
1923 fetchRoles              return { data: [], total: 0 }   (+console.warn)
2067 (2065 catch)            return { data: [], total: 0 }
2110 (2108 catch)            return { data: [], total: 0 }
2257 (2255 catch)            return { data: [], total: 0 }
2298 (2296 catch)            return { data: [], total: 0 }
2470 (2468 catch)            return { data: [], total: 0, page, pageSize }
```
**GATE FAIL** — 反模式 `.catch{warn, return empty}` 在 audit/iam/低层多处仍活跃，未替换为 NetworkDownEvent 或真实错误传播。

## 其他偏离

| # | 位置 | 描述 | 优先级 |
|---|------|------|--------|
| C-005-01 | api.ts:40-54 | `wrapNet` 函数定义但全项目零调用（死代码） | P2 |
| C-005-02 | api.ts:419 | `fetchDataset` schema fetch `catch (e) { /* schema optional */ }` 完全静默（连 warn 都无） | P2 |
| C-005-03 | api.ts:710-713, 770-773 | `fetchAuditLogs`/`fetchCryptAuditLogs` 反模式（spec 728-770 落点） | P1 |
| C-005-04 | api.ts:676-679 | `fetchUserSecurityProfile` catch→console.warn+return 空安全档案 | P2 |
| C-005-05 | api.ts:839-841 | 图查询端点 `catch { return { nodes: [], edges: [] } }` 纯静默（无 warn 无 notify） | P2 |

## 模拟后端宕机验收（spec 要求）

Spec T1 第 24 行要求「模拟后端宕机 → 任意列表页显示『服务暂不可用 + 重试』不是空白」。
- fetch helper 层现在会 `throw NetworkError` + dispatch `ecos-network-down` ✓
- 但 `fetchAuditLogs` / `fetchUsers` / `fetchRoles` 等仍在 catch 内吞错并返回空数组 → 这些列表页在后端宕机时**仍会显示空/静默**，与验收条矛盾。

→ 需修复后重新审查：将 C-005-03 两处 + 其余 catch 改为 `notifyNetworkDown(e)` / `throw NetworkError`，并复跑 spec grep 与宕机模拟验收。
