# E2E scenario replay (run e8-95d9864c64)

| Scenario | ON | OFF | Evidence | Pass |
|---|---|---|---|---|
| backend down -> list page shows service-unavailable+retry (not blank) | Y | Y | api.ts transport fail -> notifyNetworkDown(timestamp) -> NetworkErrorBanner network.error.* i18n + retry (:129-134) | 2/2 |
| backend recover -> banner gone on retry/online | Y | Y | ecos-network-up on first success + online event clears, 15s resumed strip | 2/2 |
| 403 no-perm -> toast "no permission", no login redirect | Y | Y | MonitoringCenter.tsx:112-116 on403 -> setNoPermission(true)+showToast(common.noPermission zh=无权限访问该资源); no removeItem/hash | 2/2 |
| 401 token-kick -> redirect to login | Y | - | EngineMonitor.tsx:72-75 / GuardrailsView:114-116 / api.ts handleAuthExpired -> removeItem + #/login | 1/1 |
| DevTools Offline 30s -> banner; Online -> gone within 30s; no blank list | Y | Y | offline event setOffline(true) instant render; online instant clear (<30s); page stays mounted | 2/2 |
| EngineMonitor 403 -> no-permission card (sibling-aligned) | X | Y | EngineMonitor.tsx:81 throws NO_PERMISSION string -> raw literal rendered in healthError card (:231-234); silent catch, no card state | 0/1 |

Total: 4/5 primary scenarios pass; P0=0.
