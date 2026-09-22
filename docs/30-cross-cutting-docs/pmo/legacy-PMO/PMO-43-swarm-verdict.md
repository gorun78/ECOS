# PMO-43 Swarm Verdict — FAIL (gate: fail)

**Swarm root:** `t_b413d3e4` (done)
**Verifier run:** `t_6d63b733` (this task), run 84, profile `ecos-fe`
**Worker under review:** `t_ece8327c` (ecos-be, status=blocked)
**Date:** 2026-09-06 (CST)

## Gate verdict: **FAIL — block, not pass**

Fail-closed rule: `security_concerns` OR `logic_errors` non-empty → `passed=false`.
- `security_concerns`: **0**
- `logic_errors`: **3** (2 critical + 1 moderate)
- `passed`: **false**

## Critical logic errors

### 1. `ecos_frontend/src/components/NetworkErrorBanner.tsx:65` — spec-invariant violation
`setTimeout(() => setDown(false), 120_000)` unconditionally hides the banner after 120 s
regardless of whether the network actually recovered. If the backend stays down past 120 s,
`down` flips to `false`, `visible` becomes `false`, and the red offline banner disappears
while the service is still unreachable. This is the exact off-by-time/off-by-char failure
flagged in the review brief. Also: file is 134 lines vs the `<=80` budget documented in its
own header (line 12).

**Fix:** only auto-dismiss on a successful fetch / `online` event, never on a wall-clock
timer. Remove the 120 s `setTimeout`. Trim the file to ≤80 lines by moving the
`NetworkDownDetail` interface and constants into `api.ts` (or a co-located constants file).

### 2. `ecos_frontend/src/components/RequireAuth.tsx` — fail-open race
`checkedRef.current = true` is set before `/api/v1/me` resolves. Any re-render before the
response aborts the fetch via the effect cleanup; the `AbortError` is swallowed by
`.catch(() => setState("ok"))`, and because `checkedRef` is already `true` no re-verification
ever happens. Net effect: a user whose token returns 403 is treated as `"ok"` and the
protected children render, silently bypassing the 403 → `/no-access` redirect documented
in the file's own header.

**Fix:** don't set `checkedRef` until the response settles, and treat `AbortError`
specifically (re-run the check on next mount) instead of mapping it to `"ok"`.

### 3. `ecos_frontend/src/pages/MonitoringCenter.tsx` → `EngineCard.load()` — cross-card 403 race
`load()` adds a **global** `window` `"ecos-403"` listener, fires two concurrent `apiFetch`
calls, then removes the listener after `Promise.all`. If two `EngineCard`s load in parallel,
a 403 from *either* card triggers `setNoPermission(true)` and a toast on *both* cards. The
no-permission state is non-atomic and cross-card.

**Fix:** route the 403 signal through a closure-local promise/callback instead of a window
event, or tag the event with the card's `def.id` and filter inside `on403`.

## Non-blocking suggestions
- `api.ts:~35-48` — `wrapNet` is defined but never called (dead code).
- `EngineMonitor.tsx` — `403` branch throws `new Error('NO_PERMISSION')` as a magic string;
  a call site that forgets `e.message !== 'NO_PERMISSION'` leaks raw `NO_PERMISSION` into a
  user-facing toast. Reuse `NoAccessError` from `api.ts`.
- `api.ts:fetchDatasets/fetchDataset` still degrade into mock tail on catch; the caller only
  sees `error:'MOCK'` on the returned rows. Verify the UI actually renders that tag before
  considering T1 complete.

## Evidence trail
- `git diff --stat HEAD -- <12 files>`: 522 insertions / 109 deletions.
- `grep -n '.catch(() => {})' api.ts` → 0 (T1 silent-catch anti-pattern eliminated).
- `api.ts:204-256` — `apiFetch`/`apiFetchData`/`doFetch` now `markNetworkDown` + 
  `notifyNetworkDown` + `throw NetworkError`; `401 → handleAuthExpired()`, 
  `403 → notifyNoAccess({ path })` + `throw NoAccessError(...)`.
- `main.tsx:97-103` — `ToastProvider` mounted around `HashRouter`, `showToastGlobal` wired 
  to `TasksCenterRoute`.
- `App.tsx:224-226` — `<NetworkErrorBanner/>` mounted at App root.
- `AgentMesh.tsx` / `aiworkbench/index.tsx` — `loadErrors` state + inline `ErrorState` 
  retry UI in place (T3).
- `EngineMonitor.tsx` / `MonitoringCenter.tsx` / `GuardrailsView.tsx` — 401/403 semantics 
  split (T4): 401 → logout + `#/login`, 403 → typed 403 error, no logout.
- `NoAccess.tsx` (93 lines) — new file, i18n keys `noAccess.{title,message,affected,back,home,switch}`
  present in both `locales/common/en.json` and `zh-CN.json`.
- i18n keys `network.{error,reconnect,resumed}.title / description / retry` present in both 
  locales.

## What I could not do
I could not call `kanban_block` directly: my tool roster has no kanban tool, and the CLI
refuses from a delegate child context ("delegate_task child contexts cannot mutate Kanban
tasks or boards"). The parent orchestrator must record this run as a block with the
reason above. If the daemon requires a literal `block` status, the `reason` to record is
the three numbered critical items in this file.

## Artifacts
- This file: `/home/guorongxiao/ECOS/docs/PMO/PMO-43-swarm-verdict.md`
- Machine-readable copy: `/tmp/pmo43-review/review-verdict.json`
