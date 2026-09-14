# PMO-43 Swarm Gate Verdict — FAIL

**Swarm root:** t_04a6ee19
**Verifier task:** t_6d63b733 (ecos-fe, run 84)
**Worker under review:** t_64e6e69e (ecos-be, run 80)
**Date:** 2026-09-06 (CST)

## Gate: FAIL (fail-closed)

security_concerns: 0
logic_errors: 3 (2 critical + 1 moderate)
passed: false

Independent reviewer subagent (delegate_task, fresh context) + live code inspection.

## Fail-closed rule applied
security_concerns OR logic_errors non-empty -> passed=false. 3 logic errors found.

## Critical logic errors (P0 — block gate)

### 1. ecos_frontend/src/components/NetworkErrorBanner.tsx:65 — 120s auto-dismiss hides banner while network still down
```ts
// lines 50-66:
const onDown = (e: Event) => {
  ...
  if (timerRef.current) clearTimeout(timerRef.current);
  timerRef.current = setTimeout(() => setDown(false), 120_000);  // <-- BUG
};
```
`down` is a boolean latched to true on `ecos-network-down`. `visible = offline || down`.
The 120s timer unconditionally calls `setDown(false)` after 2 minutes REGARDLESS of
whether the network actually recovered. If the backend stays down past 120s (a
realistic outage window), the red offline banner disappears while the service is
still unreachable.

This directly violates the file's own docstring (lines 2-12: "renders a fixed top
banner ... the banner disappears automatically when online fires OR when a fetch
succeeds again") and the spec invariant "banner disappears 30s after Online, not
before". The banner should only auto-dismiss on a success event (`ecos-network-up`
or `online`), never on a wall-clock timer.

Additional: file is 134 lines vs the `<=80 lines` documented on its own header
(line 12: "Keep <=80 lines"). The file does not honor its self-declared budget.

**Fix:** remove the 120s `setTimeout(() => setDown(false), 120_000)` inside `onDown`.
`down` should only be cleared by `onUp` / `onOnline` (lines 67-80), which already
exist and are correct. Then trim the file to <=80 lines by moving the
`NetworkDownDetail` interface and the `AUTO_DISMISS_MS` constant (line 26) into a
co-located constants file or `src/api.ts`.

### 2. ecos_frontend/src/components/RequireAuth.tsx:28-43 — fail-open race on re-render
```ts
// lines 23-43:
useEffect(() => {
  if (!token) { setState("login"); return; }
  if (checkedRef.current) return;      // line 28
  checkedRef.current = true;           // line 29  <-- set BEFORE settle
  const ctrl = new AbortController();
  fetch("/api/v1/me", {..., signal: ctrl.signal})
    .then((res) => {
      if (res.status === 401) setState("login");
      else if (res.status === 403) setState("no-access");   // <-- bypassed
      else setState("ok");
    })
    .catch(() => setState("ok"));      // line 41  <-- AbortError -> "ok"
  return () => ctrl.abort();           // line 42
}, [token]);
```
Three compounding problems:
(a) `checkedRef.current = true` is set at line 29 BEFORE the fetch is resolved.
(b) `return () => ctrl.abort()` at line 42 fires on any unmount/re-render. The
    in-flight `fetch` is aborted, throwing `AbortError` on the promise chain.
(c) `.catch(() => setState("ok"))` at line 41 treats `AbortError` identically to a
    genuine network failure and sets state to `"ok"`.

Net effect: a re-render before /api/v1/me settles (state changes from other
components, StrictMode double-mount in dev, token-flip) causes the fetch to abort.
The catch at line 41 maps `AbortError` to `setState("ok")`. Because
`checkedRef.current` is already `true` (set at line 29), the guard at line 28
prevents a re-check on the next render. A 403 response that SHOULD redirect to
`/no-access` is permanently bypassed — the protected `<>{children}</>` at line 65
renders with a token that actually has insufficient permission. This contradicts
the file's own header (lines 1-9: "403 -> redirect to /no-access").

**Fix:** (a) move `checkedRef.current = true` to inside the `.then()` after the
response settles (not at effect start); (b) in `.catch`, distinguish
`AbortError` from a genuine network failure — treat `AbortError` as "pending"
(state stays "loading", re-check on next mount), but still fail-open on genuine
network errors as the spec (line 8: "network error -> render children
optimistically (fail-open for network)") requires.

### 3. ecos_frontend/src/pages/MonitoringCenter.tsx:52-74,108-132 — cross-card 403 race via window event
`fetchApi<T>` (lines 50-74) dispatches `window.dispatchEvent(new CustomEvent("ecos-403", { detail: { url, timestamp } }))` on status 403.
`EngineCard.load()` (lines 108-132) adds a GLOBAL window listener:
```ts
const on403 = () => { saw403 = true; setNoPermission(true); showToast("error", t("common.noPermission")); };
window.addEventListener("ecos-403", on403);                                  // line 117
const [h, s] = await Promise.all([apiFetch(.../health), apiFetch(.../status)]);
window.removeEventListener("ecos-403", on403);                                // line 122
```
Problem: the window `ecos-403` event is NOT tagged with card identity. Each
EngineCard adds its own listener to the same global event. When N cards mount
concurrently (the normal case — the monitoring page loads all engine cards at
once), a 403 from card A's `/health` fires the global event, and EVERY other
card's `on403` handler (cards B..N) also runs: `setNoPermission(true);
showToast("error", t("common.noPermission"))`. The toast count is N (one per
card), and cards B..N display a "无权限" state even though THEIR OWN requests
may have returned 200. State is cross-card and non-atomic.

**Fix:** tag the event with the card's `def.id` in the `detail` and filter in
`on403`: `window.dispatchEvent(new CustomEvent("ecos-403", { detail: { id: def.id, url, timestamp } }))`
then `const on403 = (e: Event) => { const d = (e as CustomEvent).detail; if (d?.id === def.id) { saw403=true; setNoPermission(true); showToast(...); } }`.
Alternatively, route the 403 signal through a closure-local flag set synchronously
inside `fetchApi` (e.g. pass a callback), not through a window event.

## Non-blocking suggestions (P2)
- `ecos_frontend/src/api.ts` lines 35-48: `wrapNet` is defined but never called (dead code).
- `EngineMonitor.tsx` line 76/81: `throw new Error('NO_PERMISSION')` magic string; call sites check `e.message !== 'NO_PERMISSION'`. A call site that forgets the check leaks raw `NO_PERMISSION` into a user-facing toast. Reuse `NoAccessError` from `src/api.ts`.
- `api.ts:fetchDatasets/fetchDataset` still degrade into mock tail on 404 (lines 350-393); caller only sees `error:'MOCK'` on the returned rows. Verify the UI actually renders that tag before considering T1 complete.
- `api.ts` has 33 `console.warn` occurrences in caller-wrapper functions (fetchUsers, fetchRoles, fetchTenants, etc.) that still silently swallow errors and return `{data:[], total:0}`. This is the SAME anti-pattern the spec calls out ("API 层禁止把 5xx/TypeError 映射成空数据"). The spec lists 5 specific line ranges, but the architectural invariant suggests the pattern should be eliminated. Flag for PM decision on scope: is this in-scope for PMO-43 or deferred to PMO-45?

## What I verified independently (dual evidence)
1. `npm run lint` (tsc --noEmit) = 0 errors                             [VERIFIED]
2. `npx vitest run` = 3 test files, 12/12 tests pass (5.98s)          [VERIFIED]
3. `npm run build` = success (35.87s, chunk warnings only)             [VERIFIED]
4. `search_files src/pages/ / "console.log"` = 0 hits                  [VERIFIED — T2 gate]
5. `search_files src/api.ts / ".catch(e => { console.warn"` = 0 hits   [VERIFIED — T1 precise pattern]
6. `search_files src/api.ts / "MOCK_DATA_ASSETS_TAGGED"` = 5 call sites + 1 def [VERIFIED — T1 mock tagging]
7. `search_files src/api.ts / "notifyNetworkDown|NetworkError|handleAuthExpired|NoAccessError"` — all present at core layer [VERIFIED — T1 rethink design]

## Artifacts
- Reviewer verdict artifact: /home/guorongxiao/ECOS/docs/PMO/PMO-43-swarm-verdict.md (same content)
- Reviewer JSON (independent subagent): /tmp/pmo43-review/review-verdict.json

## Missing work (for orchestrator to fan out)
1. Fix NetworkErrorBanner 120s auto-dismiss (remove `setTimeout(setDown(false),120_000)` in `onDown`); trim file to <=80 lines.
2. Fix RequireAuth: don't set `checkedRef=true` before settle; treat `AbortError` as pending, not `setState("ok")`.
3. Fix MonitoringCenter: tag `ecos-403` event with card `def.id`, filter in `on403`; or use closure-local signal.
4. (Decision needed) Constrain or explicitly defer the 33 residual `console.warn` + `return {data:[],total:0}` call sites in api.ts.
