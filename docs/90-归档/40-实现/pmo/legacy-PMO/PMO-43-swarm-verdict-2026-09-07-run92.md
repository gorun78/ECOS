# PMO-43 Swarm Gate Verdict — FAIL (Run 92, re-verification after unblock)

**Swarm root:** t_04a6ee19
**Verifier task:** t_6d63b733 (ecos-fe, run 92 — retry after unblock)
**Worker under review:** t_64e6e69e (run 80 handoff)
**Date:** 2026-09-07 (CST)

## Gate: FAIL (fail-closed, re-confirmed independently)

Run 84 blocked this task with 2 CRITICAL + 1 MODERATE logic errors and a PM
scope question. After unblock, code on disk is byte-identical at all three
finding sites — no rework landed. This re-run independently re-confirmed every
prior-run finding plus three new ones.

Independent evidence: 2 fresh reviewer subagents (split diff, zero shared
context, fail-closed prompts) + live code inspection (filesystem + grep + grep
gates + re-ran lint/vitest myself).

## Findings (re-confirmed against current on-disk code)

### P0 — CRITICAL (2, both from run 84, still present)

**C1. NetworkErrorBanner.tsx:65 — wall-clock auto-dismiss hides banner while
service still down**
`onDown` sets `timerRef.current = setTimeout(() => setDown(false), 120_000)`.
If the backend stays down >120s, the red offline banner disappears while the
service is still unreachable. Violates the file's own docstring (lines 9-10:
banner clears on `online` or successful fetch) and the spec invariant
"banner disappears 30s after Online, not before".
**Fix:** delete the timer. `down` must clear only via `onUp`/`onOnline`
(both already correct at lines 67-80).

**C2. RequireAuth.tsx:28-43 — fail-open race → silent permission bypass**
`checkedRef.current = true` is set at line 29 BEFORE `/api/v1/me` settles.
`return () => ctrl.abort()` (line 42) fires on any re-render; the aborted
fetch throws `AbortError`, caught by `.catch(() => setState("ok"))` (line 41).
Once checkedRef is true, no re-check ever runs. A 403 (valid token, no
permission) that SHOULD reach `setState("no-access")` (line 38) is permanently
bypassed — protected children render with an insufficient-permission token.
Also swallows a late-arriving 401 (expired token → should be /login).
Contradicts the file header (line 6: "403 → redirect to /no-access").
**Fix:** (a) move `checkedRef.current = true` into `.then` after settle;
(b) in `.catch`, treat `AbortError` as pending — do NOT `setState("ok")`;
keep state "loading" until the next mount re-checks;
(c) fail-open ("ok") only on genuine network error (NetworkError/TypeError),
per the file header line 8.

### P1 — HIGH (2, one from run 84 + upgraded suggestion, one new)

**H1. MonitoringCenter.tsx:50-74,107-132 — cross-card `ecos-403` race
(run 84 MODERATE, confirmed cite-accurate at :115/:117, both reviewers agree)**
`apiFetch` (line 62) dispatches a global `ecos-403` with `detail: { url }`.
`EngineCard.load()` (line 117) adds a window listener that unconditionally
does `setNoPermission(true)` + toast on ANY `ecos-403`, and removes it only
after `Promise.all` (line 122). When N engine cards load concurrently, a 403
from card A fires the no-permission state + toast on every other card whose
listener is armed — false positive (their own endpoints may be fine or 5xx).
**Fix:** filter in the listener: `on403 = (ev) => { if (!detail.url.
startsWith(def.apiBase)) return; ... }`, and move `removeEventListener`
into `finally`; or return the 403 via the promise result instead of a
window event.

**H2. EngineMonitor.tsx:~81 — `e.message === 'NO_PERMISSION'` magic string
(run 84 suggestion, re-classified by reviewer A as HIGH: contradicts intent,
bugs, and violates the i18n hard rule)**
403 detection via English string equality instead of the purpose-built
`NoAccessError` / `isNoAccessError()` exported by api.ts. ① A 5xx whose body
happens to contain 'NO_PERMISSION' is misclassified as permission-denied;
② hardcoded English literal violates 前端开发规范 (no hardcoded strings, must
use `t()`); ③ the literal leaks verbatim into UI error slots if a call site
forgets the message check.
**Fix:** use `isNoAccessError(e)` (instanceof), UI copy via
`t("common.noPermission")`.

### P2 — NEW (1, both reviewers independently raised)

**M1. No UI renders the `error: 'MOCK'` tag T1 introduced
(types.ts + api.ts MOCK_DATA_ASSETS_TAGGED)**
T1 added `error?: 'MOCK' | string` and tagged mock fallback rows, but no
component in src/pages|components consumes the field (grep confirmed zero
render sites). A failing data-asset fetch silently returns synthetic data
with no user-visible signal — exactly the "API layer hides the real error"
failure mode PMO-43 T1 exists to remove. If a real backend error causes the
mock fallback to fire, the caller sees only fake rows and no banner.
**Fix:** render the tag (badge/label) where data assets are displayed, or
reconsider the mock fallback path (should it throw NetworkError like other
T1 catch sites per spec, instead of returning tagged empty data?).

### P2 — spec/size invariant (from run 84, still true)
- **S1.** NetworkErrorBanner.tsx is 134 lines vs its self-declared ≤80
  (header line 12). Trim: move `NetworkDownDetail` interface and
  `AUTO_DISMISS_MS` into api.ts / a co-located constants file; collapse
  the 401-suppress branch (lines 57-60) which `setDown(true)` then
  immediately `setDown(false)` — just `return` before any state change.
- **S2. (new)** `types.ts` `error?: 'MOCK' | string` collapses to
  `string` — the union adds no type constraint. Use a dedicated
  `provenance` field or `'MOCK'` literal union.

## Out-of-scope, documented (NOT gate-blocking — PM decision deferred from run 84)

**api.ts has 33 residual `console.warn` + 9 `return {data: [], total: 0}`
call sites in caller-wrapper functions (fetchUsers, fetchRoles, fetchTenants,
...)** — the same "API layer maps 5xx/TypeError to empty data" anti-pattern
the spec's P0 rule names. Spec text lists only the 5 exact-range catch
sites, and those 5 are confirmed 0-hit. The 33 wrapper sites are a
PMO-44/45-scope question raised in run 84 to PM, still undecided. Re-raised
here; if PMO-43 scope is interpreted strictly as the 5 named sites, these
are deferred; if interpreted as "the anti-pattern class", they block.

## Spec invariants — measured this run

| Invariant | Result | Evidence |
|---|---|---|
| T1: 5 named-site `.catch{warn,return empty}` anti-pattern | **PASS** | `grep -E '\.catch\( e => { console.warn' src/api.ts` → 0 (exact shape); `catch` greps in api.ts → 0 of that shape (33 residual exist in wrappers — see out-of-scope note) |
| T2: `console.log` residue in src/pages/ | **PASS** | `grep -rc console.log src/pages/` → 0 |
| T2: ToastProvider mounted (main.tsx) | **PASS** | main.tsx:13 import, :100 open, :175 close |
| T3: AgentMesh + aiworkbench error/retry UI | **PASS** | loadErrors state + retry button refined in both (verified by reviewer A hunk read) |
| T4: 401/403 semantics split in api.ts core | **PASS** | apiFetch: 401→handleAuthExpired (clear token+roles→login); 403→notifyNoAccess + NoAccessError throw, NO logout. EngineMonitor/MonitoringCenter/GuardrailsView all split. **But** H2 shows EngineMonitor still uses magic string to detect the 403 it just threw. |
| T4: NoAccess route reachable | **PASS** | /no-access route in main.tsx:104; i18n noAccess.* keys in both locales (3 each); NoAccess page listens to `ecos-no-access` event |
| T5: NetworkErrorBanner mounted at App root | **PASS** | App.tsx:18 import, :227 mount; subscribes to all 4 events (ecos-network-down/up, online/offline); network.* i18n keys in both locales (4 each) |
| P0: banner clears only on up/online (no wall-clock) | **FAIL** | C1 / S1 |
| P0: RequireAuth 403→no-access, no bypass | **FAIL** | C2 (403 mapping correct in code; fail-open race makes the path unreachable) |
| Frontend rule: ≤80 lines (self-declared) | **FAIL** | S1 (134) |
| Frontend rule: no hardcoded strings (i18n) | **FAIL** | H2 (NO_PERMISSION literal) |

## Locally re-run quality gates (this run, current workspace)

- `npx tsc --noEmit` → **0 errors** (exit 0)
- `npx vitest run` → **12/12 passed, 3 files** (3.01s)
- Build: relied on worker's 1.5m build (not re-run — out of budget; worker's
  build was against the same file contents, byte-identical at all
  finding/fix sites)

## What is needed to PASS (exact work, for orchestrator to fan out)

1. **C1 fix** — NetworkErrorBanner.tsx:65 delete `setTimeout(() => setDown(false), 120_000)`; `down` clears only on `ecos-network-up`/`online`. (1 line + comment.)
2. **C2 fix** — RequireAuth.tsx:28-43: move `checkedRef.current=true` to after settle; `.catch` distinguishes `AbortError` (stay "loading", re-check next mount) from genuine network fail-open (state "ok"). (about 10 lines.)
3. **H1 fix** — MonitoringCenter.tsx:117-122: `on403` filters `detail.url.startsWith(def.apiBase)`; `removeEventListener` in `finally`. (about 6 lines.)
4. **H2 fix** — EngineMonitor.tsx:~81: replace `e.message === 'NO_PERMISSION'` with `isNoAccessError(e)` from `../api` (import), copy via `t("common.noPermission")`. (about 3 lines + import.)
5. **M1 decision (PM) + tiny fix (worker)** — decide mock-tag line is in-scope vs deferred; if in-scope, render the `error` tag in the data-asset list (badge/label) or change the fallback to throw NetworkError like other T1 sites.
6. **S1 fix** — NetworkErrorBanner.tsx trim to ≤80: move `NetworkDownDetail` + `AUTO_DISMISS_MS` to constants file or `api.ts`; collapse the 401-suppress early return.
7. **S2 fix** — types.ts: use `'MOCK'` literal union or a separate `provenance` field.

After fixes: re-run `npx tsc --noEmit` (=0), `npx vitest run` (=12/12), and the 4 grep gates. Gate then PASSes.

## Reviewer artifacts
- Part A: `/tmp/pmo43-review-a-verdict.json`
- Part B: `/home/guorongxiao/ECOS/tmp/review-part-b-PMO43.json`
- Diff snapshot: `/tmp/pmo43-diff-run92.txt`
- Run 84 (this file's predecessor): `docs/PMO/PMO-43-swarm-verdict-2026-09-06.md`, `docs/PMO/PMO-43-swarm-verdict.json`
- Machine-readable copy of THIS verdict JSON: `/tmp/pmo43-review/review-verdict-run92.json`
