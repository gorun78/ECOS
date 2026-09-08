# PMO-43 Architecture Rule Compliance — Arch Review

> Reviewer: ecos-arch | Date: 2026-09-07 | Task: t_a1dfedf8
> Rule set: `/home/guorongxiao/ECOS/.trae/rules/前端开发规范.md`
> Scope: code changes in T1-T5 of PMO-43 (branch `feature/pmo-43-fe-p0-infra-network`)

## Verdict: **FAIL** — 7 findings (2 CRITICAL functionally-broken + 5 rule violations)

---

## CRITICAL — Functionally broken (gate-blockers)

### C-001 NetworkErrorBanner.tsx:65 — 120s auto-dismiss hides banner while service still down
- **File**: `ecos_frontend/src/components/NetworkErrorBanner.tsx` line 65
- **Rule violated**: PMO-43 §T5 "banner disappears 30s after Online, not before"; §一 安全与健壮 (incorrect boundary handling)
- **Finding**: `setTimeout(() => setDown(false), 120_000)` in `onDown` dismisses the offline banner 2 minutes after the network-down event fires, even if the service is still unreachable. The banner should only clear when `ecos-network-up` or `online` fires.
- **Secondary**: File is 134 lines vs self-declared "Keep ≤80 lines" in the docstring (line 12).
- **Fix**: Remove `timerRef` / 120s timer entirely. `down` state clears only on `onUp` or `onOnline`.

### C-002 RequireAuth.tsx:28-43 — AbortError race → silent permission bypass
- **File**: `ecos_frontend/src/components/RequireAuth.tsx` lines 28-43
- **Rule violated**: PMO-43 §T4 (403 → no-access, never "ok"); §一 安全与健壮 (exception handling)
- **Finding**:
  - `checkedRef.current = true` is set at line 31 **before** the fetch settles.
  - On re-render, the cleanup fires `ctrl.abort()`.
  - `AbortError` lands in `.catch(() => setState("ok"))` at line 43.
  - A 403 user (valid token, no permission) gets state `"ok"` → `<>{children}</>` renders → **silent permission bypass**.
  - The 401 → login path is also bypassed the same way.
- **Fix**: Set `checkedRef.current = true` only inside `.then()` after the response settles. In the catch block, check `e.name === "AbortError"` and leave state as `"loading"` (do not set `"ok"`).

---

## HIGH — Rule violations

### H-001 MonitoringCenter.tsx:62,117 — `ecos-403` global event without card identity
- **File**: `ecos_frontend/src/pages/MonitoringCenter.tsx` lines 62 (dispatch), 117 (listener)
- **Rule violated**: §一 精准与可维护 (clear logic boundaries); §四 (exception handling must be scoped)
- **Finding**: `window.dispatchEvent(new CustomEvent("ecos-403", {detail: {url, timestamp}}))` (line 62) fires a global window event with no card `id`. The `EngineCard.load()` listener at line 117 catches **any** `ecos-403`, so when N cards mount concurrently, a 403 from one card triggers `noPermission` + toast on **all N cards**. Cross-card state is non-atomic.
- **Fix**: Include `cardId` (e.g. `def.id`) in the event detail; filter in `on403` — only act when `e.detail.cardId === def.id`. Alternatively, drop the window event and use a closure-local signal.

### H-002 EngineMonitor.tsx:76,81 — magic strings for user-facing + control-flow text
- **File**: `ecos_frontend/src/pages/EngineMonitor.tsx` lines 76, 81
- **Rule violated**: §四 i18n (no hardcoded strings — use `t()`); §一 精准 (no magic values for control flow)
- **Finding**:
  - Line 76: `throw new Error('登录已过期，请重新登录')` — hardcoded Chinese, not i18n.
  - Line 81: `throw new Error('NO_PERMISSION')` — magic string used as control-flow contract (checked at lines 125, 140, 155 via `e.message !== 'NO_PERMISSION'`). This is a string-based protocol with no type safety, no i18n, and is inconsistent with `src/api.ts` which exports `NoAccessError`.
- **Fix**: Import a shared `NoAccessError` class (or a `NO_PERMISSION` constant exported from `api.ts`). Use `t("common.noPermission")` for display. Replace `throw new Error('NO_PERMISSION')` with `throw new NoAccessError(...)`.

### H-003 aiworkbench/index.tsx:104-108,163 — hardcoded Chinese strings
- **File**: `ecos_frontend/src/pages/aiworkbench/index.tsx` lines 104-108 (sidebar), line 163 (footer)
- **Rule violated**: §四 i18n (all UI strings must use `t()`)
- **Finding**:
  - Line 105: `AI集成开发控制台` (hardcoded Chinese, no `t()`)
  - Line 108: `v2.4` (borderline — consider i18n if version labels should be localized)
  - Line 163: `安全代理: Active Shield v2.4 (Sovereign)` (hardcoded Chinese)
- **Note**: Pre-existing, not introduced by T1-T5, but present in the T3 file scope.
- **Fix**: Route all user-facing strings through `t()`.

### H-004 aiworkbench/index.tsx:287 — inline style with hardcoded color
- **File**: `ecos_frontend/src/pages/aiworkbench/index.tsx` line 287
- **Rule violated**: §三 (no inline styles except dynamic; no hardcoded hex colors)
- **Finding**: `style={{ borderColor: '#555', background: 'transparent' }}` on the ErrorState retry button.
- **Fix**: Use theme tokens via `useTheme().styles` or Tailwind utility classes consistent with the rest of the file.

### H-005 GuardrailsView.tsx / EngineMonitor.tsx / MonitoringCenter.tsx — duplicated `authHeaders()` + `apiFetch()`/`apiCall()`
- **Files**:
  - `ecos_frontend/src/pages/EngineMonitor.tsx` lines 63-93
  - `ecos_frontend/src/pages/MonitoringCenter.tsx` lines 42-74
  - `ecos_frontend/src/pages/GuardrailsView.tsx` lines 104-137
- **Rule violated**: §五 (shared logic must be extracted to `src/api/` or `src/utils/`); §一 (refuse duplicate code blocks — abstract common methods)
- **Finding**: All three files define a private `authHeaders()` (3 copies, identical logic) and a private `apiFetch`/`apiCall` wrapper with different 401/403 handling (inconsistent with `src/api.ts`). This is the pattern that caused the 403/401 conflation bug in the first place.
- **Fix**: Extract a single `ecosApiFetch` into `src/api/` (or extend `src/api.ts`) that handles 401 → login redirect, 403 → `NoAccessError`, network → `noticeNetworkDown`. All three files should import from it.

---

## MEDIUM

### M-001 aiworkbench/index.tsx:6-9 — import from `./mockData` in production code
- **File**: `ecos_frontend/src/pages/aiworkbench/index.tsx` lines 6-9
- **Rule violated**: §一 精准与可维护 (no mock data in production paths); §六 (no leftover dev/debug residue)
- **Finding**: `mockAIPAuditLogs` is imported and used to initialise `auditLogs` state (line 49). If this file ships in production, users see fake audit logs.
- **Note**: Pre-existing (not T1-T5-specific), but visible in the T3 file.
- **Fix**: Either remove the mock, or gate it behind a dev-only flag.

---

## SUMMARY TABLE

| ID | Severity | File | Rule § | Introduced by T1-T5 |
|----|----------|------|--------|---------------------|
| C-001 | CRITICAL | NetworkErrorBanner.tsx:65 | §一 boundary + PMO-43 spec | Yes (T5) |
| C-002 | CRITICAL | RequireAuth.tsx:28-43 | §一 安全 + PMO-43 spec | Yes (T4) |
| H-001 | HIGH | MonitoringCenter.tsx:62,117 | §一 精准 + §四 | Yes (T4) |
| H-002 | HIGH | EngineMonitor.tsx:76,81,125,140,155 | §四 i18n + §一 magic | Yes (T4) |
| H-003 | HIGH | aiworkbench/index.tsx:104,108,163 | §四 i18n | Pre-existing |
| H-004 | HIGH | aiworkbench/index.tsx:287 | §三 CSS | Yes (T3) |
| H-005 | HIGH | 3 files (duplicated api helpers) | §五 + §一 | Pre-existing, worsened by T4 |
| M-001 | MEDIUM | aiworkbench/index.tsx:6-9,49 | §一 + §六 | Pre-existing |

## Gate recommendation to PMO (ecos-pmo)

The 2 CRITICALs (C-001, C-002) are **gate-blockers** — they must be fixed before `gate: pass`.

The 3 HIGHs introduced by T1-T5 (H-001, H-002, H-004) are **gate-blockers** — they are architectural rule violations in new code.

H-003 and H-005 are **must-fix-before-MR** but reflect pre-existing debt that T4 widened. Recommend: include in PMO-43 rework if scope allows; otherwise track as PMO-45 clean-up.

M-001 is tracked, non-blocking.
