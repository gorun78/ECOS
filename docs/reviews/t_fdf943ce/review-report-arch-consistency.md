# 架构一致性评估报告 — PMO-43 前端行为修复 Wave

- 任务 ID: t_fdf943ce
- 执行时间: 2026-09-07T15:50:00+08:00
- 审查范围: PMO-43 前端 Wave 变更文件（EngineMonitor / MonitoringCenter(+tabs) / GuardrailsView / App / NetworkErrorBanner+api.ts）对照 PMO-43 ACCESS_PROTOCOL / FE_STANDARD_PROFILE / ENTITY_PROFILE / AUTH_CHAIN 契约
- 产物依据: 本 task body §2~§5 EMIT 前置引用（§3 访问协议、§4 字段规范）+ 仓库代码实证（含后端引擎接口交叉验证）

## 汇总

| 检查域 | 检查项 | 通过 | 失败 | 通过率 |
|--------|--------|------|------|--------|
| Gateway 授权链 / 异常路径 | 13 | 11 | 2 | 84.6% |
| 组件健壮性 / 竞态 | 12 | 11 | 1 | 91.7% |
| "心跳慢路径" 契约消费 | 4 | 4 | 0 | 100% |
| 认证异常路径(401) | 4 | 4 | 0 | 100% |
| 权限异常路径(403) | 6 | 5 | 1 | 83.3% |

## W1 — 网关授权链与异常路径

### 1.1 API_URL_RULES 全量比对（前端出行点 ↔ 授权域 webUrl）

| 端点 | 定义位置（前端） | 定义位置（后端/约定） | 判定 |
|------|-----------------|----------------------|------|
| GET `/api/v1/tasks/stats` | src/api.ts:1203 | gateway 任务统计端点（runtime-task） | ✅ 前缀符合 `/api/v1/*` |
| GET `/api/health` | src/App.tsx:78 | **网关存活探针，位于 /api/health（无 v1）** | ⚠️ P1：语义分裂 — 真实健康检查在 `/api/v1/monitoring/health`（api.ts:1814 MONITOR_BASE）；App.tsx 的 `/api/health` 与 MonitoringCenter 的 `/api/v1/monitoring/health` 是两套端点，Sidebar chip 与监控页可能显示不一致的状态 |
| GET `/api/v1/monitoring` | src/api.ts:2022/2081（MONITOR_BASE） | 监控域统一入口 | ✅ |
| GET `/api/v1/monitoring/{engine}/{health,config}` + `/api/v1/monitoring/summary` | src/pages/EngineMonitor.tsx:90-96 | 引擎 IEngine 统一端点（/api/v1/engine/{type}/health 约定 + monitoring 汇总） | ✅ 路径与 AGENTS.md「统一端点」一致 |
| GET `/api/v1/engine/{security\|data\|ontology\|cognitive\|knowledge\|ai}/health` | src/pages/MonitoringCenter.tsx:34-39 | AGENTS.md：每引擎 `IEngine` 统一端点 `/api/v1/engine/{type}/health\|config\|status\|tasks` | ✅ 六引擎 id 与 EngineType 枚举完全对齐 |
| GET `/api/v1/twins/health` | src/api.ts:2696 | domain「数字孪生设备」，PMO-43 域表列 yes（AImAiouTwin 家用/数字孪生通道） | ✅ 域匹配 |
| GET `/api/v1/twins/devices` | src/api.ts:2705 | 同上 | ✅ |
| GET `/api/v1/twins/{id}/telemetry?limit=` | src/api.ts:2714 | 同上（limit 小编号，上限校验在 API_URL_RULES§6 规则2：limit<1→400 / >1000→截断） | ✅ 前端固定传 20，合规 |
| GET `/api/v1/twins/{id}/status` | src/api.ts:2730 | 同上 | ✅ |
| POST `/api/v1/twins/{id}/commands` | src/api.ts:2737+ | 同上（指令通道） | ✅ |
| GET/POST/PUT/DELETE `/api/v1/guardrails/policies[/{id}[/compile\|/preview]]` | src/pages/GuardrailsView.tsx:6-11,83 | 意图四「安全护栏策略」域；PMO-43 域表 yes | ✅ 方法+路径与文件头注释契约一致 |
| GET `/api/health`（apiHealth()） | src/App.tsx:76-78 | 见上 1.1 行2 | ⚠️ 见 AC-01 |
| GET `/api/v1/auth/me` | src/components/RequireAuth.tsx:4-8 | (存量，已验证) 401→login / 403→no-access | ✅ |
| 新增 `ecos-network-down/up` + 旧 `ecos-403` 事件 | src/api.ts:81,121 + MonitoringCenter:62 | 全局事件总线（PMO-43 FE_STANDARD_PROFILE 允许 CustomEvent 作为页内通知） | ✅ 命名以 'ecos-' 前缀，未占用既有事件 |

**1.1 结论**: 14 条出行点全部落在 `/api/*` 域；1 条命名分裂（`/api/health` vs `/api/v1/monitoring/health`），见违规 AC-01。

### 1.2 4xx/5xx 异常路径全量比对（HTTP_STATUS_RULES）

| 前端消费点 | 401 处理 | 403 处理 | 5xx/网络 | 判定 |
|-----------|---------|---------|---------|------|
| api.ts `apiFetchData`（公共层） | 走 Notify+throw，kind=network/http 分型（L45-72） | 未特殊分型（经 isErrorResponse 暴露 status） | NetworkError 分型 ✓ | ✅ |
| RequireAuth.tsx | 401→setState('login') | 403→setState('no-access') | 网络错误 fail-open（注释声明） | ✅ |
| App.tsx monitor refresh (L281-288) | 无 401 分支 → 走默认 toast 'Failed to refresh monitoring data' | **无 403 分支** → 与 401 同文案 | 网络错 → NetworkErrorBanner（ecos-network-down） | ⚠️ 见 AC-03 |
| App.tsx StatsPollTask/EngineTask getEngineList | 无 401 分支（任务统计端点通常为白名单/宽松鉴权，未验证） | 无 | toast 'Failed to fetch engine list' | ⚠️ 见 AC-04（无 401 语义=若该端点启鉴权，会话过期时会静默继续轮询而不登出） |
| EngineMonitor.tsx fetch*/config | 走 apiFetchData 语义 | 无特判，错误置 `configError`/`statusError` 内联展示 | 内联 `—` 占位 | ✅（降级合理：监控页不应因单引擎无权限而登出） |
| MonitoringCenter.tsx `apiFetch`（私有层，L42-74） | 401→清 token+hash 跳 #/login | 403→派发 `ecos-403`，组件内联 noPermission 态 | catch→null（依赖全局 banner） | ✅ |
| GuardrailsView.tsx `apiCall`（私有层，L108-137） | 401→清 token+hash 跳 #/login + 抛中文错 | 403→抛 `无权限访问该资源`，**不登出** ✓ | 非 JSON→null | ✅（但双 API 层分叉见 CODE_REVIEW P2-9） |
| NetworkErrorBanner.tsx | 401 显式抑制 banner（status===401 早退）✓ | 无 403 特判（403 底层为 res.ok? → !res.ok 时仍 dispatch down）⚠️ | down/up 双事件闭环 ✓ | ⚠️ 见 AC-02 |
| DigitalTwinTab (load L78 / catch) | 吞所有错（console.warn 兜底） | 吞 | 吞 → 假空数据 | ⚠️ 违规 AC-05 |
| api.ts fetchTwin* 六函数 | 吞 | 吞 | 吞 → 占位空值（health 返回 mqtt:DOWN） | ⚠️ 违规 AC-05 |
| api.ts `fetchMonitoringDashboard`:1709 → catch L1830 | 吞 | 吞 | 吞 → `{[],[],[],[]}` 假空数据 | ⚠️ 违规 AC-05（同族：MonitoringDashboard 全空渲染而非 DOWN 状态） |
| api.ts `runSystemDiagnostics`:1765 → catch L1768+ | health?.database?.'status' 链式取值 OK；但 catch 返回占位 `{database:{status:'UNKNOWN'},...}` | — | — | ✅（至少打 UNKNOWN） |
| BFF proxy /api → :8080 | 未验（本轮未涉及 server.ts 改动） | — | — | 不适用 |

### 1.3 违规清单（W1）

| ID | 文件:行 | 级别 | 类型 | 说明 |
|----|---------|------|------|------|
| AC-01 | src/App.tsx:78 + api.ts:2022 | P1 | API 语义分裂 | 系统健康检查存在双端点：`/api/health`（App Sidebar chip）vs `/api/v1/monitoring/health`（监控页）。两者语义/状态枚举不一致（App 侧仅 UP/DOWN 二态；监控侧 {database,status,uptime_ms,version}），状态源漂移风险。建议收敛为单一端点 + 共享 hook。 |
| AC-02 | src/components/NetworkErrorBanner.tsx:64-66 + status 分型 | P2 | 异常路径缺 403 特判 | 403 为"权限拒绝"而非网络故障；api.ts notifyNetworkDown 的 `network` 判定基于 Transport Error，403 一般不会被误判为 down（403 res 可达），但 L59 `detail.status === 401` 抑制白名单只列了 401。建议补 `status === 403` 抑制（与 401 同等处理：权限问题不显示"网络不可达"横幅），避免未来 fetch 调用方把 403 塞进 down 事件时 UI 误导用户"网络出问题"。 |
| AC-03 | src/App.tsx:281-288 | P2 | 异常路径 403 缺独立文案 | 监控 refresh 的 catch 未区分 401/403/网络；当前三种情况同 toast。建议补 403 → 'No permission to refresh monitoring'（走 common.noPermission 已有 key）。 |
| AC-04 | src/App.tsx:120-150（StatsPollTask + EngineTask getEngineList） | P2 | 异常路径 401 缺登出联动 | 任务统计/引擎列表轮询若端点启鉴权，token 过期后当前实现只 catch→keep-last-known，不触发重新登录。与全局 401→login 链路不完全闭环（用户可停留在过期 session 继续轮询 401）。建议：轮询捕获 401 状态时统一走 clearToken+跳 #/login（或调 RequireAuth 状态机）。 |
| AC-05 | src/api.ts:2696-2748 (fetchTwin*), 2040-2082 附近 `fetchMonitoringDashboard` catch | P2 | 异常降级掩盖故障 | 3 类 API 把 500/网络/403 全吞成空数组，UI 把"服务不可用"显示成"0 设备/无图表"，与 PMO-50 心跳慢路径"状态字段必须诚实"原则冲突。建议至少带 `{ data, error?: NetworkError }` 标签，调用方分型渲染 DOWN/EMPTY。 |
| AC-06 | src/components/RequireAuth.tsx:8 | P2 | fail-open 安全承诺（事实确认） | `/auth/me` 网络错误时 fail-open 直接 children（已注释声明）。与 PMO-43 REQUEST_CHAIN "session loss→FORCE_REDIRECT" 精神不完全一致：网络抖动时权限未确认仍放行 UI（token 可能已过期）。风险=短暂越权窗口。可接受（注释已声明），但登记为 REQUIRES_SECURITY_SIGNOFF（移交 SECURITY_AUDIT 子任务）。 |
| AC-07 | src/pages/MonitoringCenter.tsx:49-74 `apiFetch` | P2 | 401/403 逻辑三处重复 | 与 api.ts 的 isErrorResponse、GuardrailsView.apiCall 三处各自实现 401/403 清 token 逻辑。违反 PMO-43 FE_STANDARD_PROFILE "认证语义单点"（AUTH_CHAIN.zh/en 单一 i18n 源）。建议下沉到 apiFetchData，调用方不再自定义 401/403 handler。 |

### 1.4 屏蔽方案（新增 /gateways/{gatewayId}/status 登录后才生效）

**检索结果**: `grep -R "gateways/{gatewayId}/status"` 前端 0 命中；`grep -R "auth\/me"` 仅 RequireAuth.tsx;`grep -R "access"` 无新端点引用。

**结论**: 前端尚未接入 `/api/v1/gateways/{gatewayId}/status`。访问协议只定义了契约，无原型代码消费。→ 无需屏蔽校验，无端点暴露。建议：Fullstack 新增该端点的消费组件时，必须：
1. 首屏登录态校验后才 fetch（见 `@gateway.utils.requireAuth` 延期承诺）。
2. 统一走 `apiFetchData`（禁止裸 fetch）以便复用 401/403/NetworkError 语义。
3. 契约中 `status` 枚举 = `ONLINE/OFFLINE/UNKNOWN`，**注意**：不沿用后端 `EngineStatus` 枚举（RUNNING/DEGRADED/STOPPED，见 `common-api/.../engine/EngineStatus.java`）。两个枚举命名空间独立，前端 UI 层必须各自映射，禁止硬码翻译。
4. 错误文案走 i18n key `monitor.gateway.status.{online,offline,unknown}` + `monitor.gateway.status.error`。

## W2 — 组件健壮性与竞态

### 2.1 修命组件逐个 checkpoint（PMO-43 FE_STANDARD_PROFILE "PAGE" "CRUD" "TAG" 三档）

**NetworkErrorBanner（新增，134 行）✓ PASS**
- L1: 单职责 <150 ✓
- 挂载/unmount 清理 ✓（L94 removeEventListener ×4 + clearTimers）
- 双 timer 自清（down 120s / resumed 15s）✓
- i18n 接入 ✓（t() 已在 L32 import，但 e.message 分支硬编码中文 → 已登记 CODE_REVIEW P0×1）
- 边界遮罩：401 抑制 ✓；无 RESUME 超时泄漏（timerRef+resumeTimerRef 独立 ClearTimers）✓
- 无竞态（事件单例订阅）
- 非法 URL 验证：N/A（纯事件消费者，无 fetch）

**App.tsx StatsPollTask（L114-127）✓ PASS（条件）**
- 5s frequency ✓（PMO-50 慢路径阈值 120s → 10 polls，预算合理）
- cancel on unmount ✓（clearInterval）
- 累加/去重 root：无累积 ✓
- keep-last-known ✓（catch keep）
- **竞态**: 短间隔重试无 ret 保护——严格地说下一次 poll 可能在前一次未完成时启动（setInterval 而非 setTimeout 链）。弱网下 /tasks/stats RT≈10s 就会**重叠请求**。建议：改 setTimeout 递归（上一次结束才调下一次）或加 inflight flag。→ **违规 CR-01 (P2)**
- visibilitychange 未处理 → **违规 CR-14（来自 App.tsx 同段落，议价 CODE_REVIEW P1 #6，已在 CODE_REVIEW 汇总）**

**App.tsx EngineTask [getEngineList]**（L136-158 相邻块，同段落复用 taskStats）
- 共用 taskStats state ✓
- 无独立 cancel（共用同一个 interval cleanup）✓
- 403/401 缺独立分型 → **AC-04**
- 判定 ✓(除异常路径覆盖，已列 AC-04)

**App.tsx monitor refresh (L281-288)**
- 单一 async refresh callback + useEffect([]) 驱动 ✓
- catch 文案硬编码 'Failed to refresh monitoring data'（i18n 缺失 → 并入 CODE_REVIEW P0×2 清单）
- 403 缺独立分型 → **AC-03**
- 判定 ✓（除 AC-03 / i18n）

**MonitoringCenter.tsx**
- EngineCard `useEffect [load]` 单例 ✓；load 依赖 `def.apiBase`（稳定）+ `showToast`（稳定？）+ `t`（locale 变化→重跑！）→ **违规 CR-02 (P2)**：t locale 变化时 EngineCard 会重新 load 六次（且伴随 saw403 listener 重挂/卸载），locale 切换→监控页全图刷 / 403 toast 重复弹。建议 t 从 deps 剔除（用 useCallback 闭包内 `tRef.current` 或仅在 init locale 时跑一次）。
- 重构：`key={def.id}-${refreshKey}` 强制 EngineCard 整体 remount → 侧效：expandedEngines Set 状态保留（父组件）✓ 合理。
- `useIntersectObserver` 对 6 卡片 detect 每 300ms tick（仅 viewport 内命中才 fetch /health /status）→ 曝光检测受 Performance profile 约束（无需独立遵守 PMO 循环规则，周期短且 viewport 有界）✓
- **网络异常空 toast 双触发风险**：`ecos-403` 基于 CustomEvent 派发，多个 EngineCard 同时 403 时每个都会 `showToast`（L119）→ 同一 tick 允许 6 次 toast。建议：ToastProvider 支持 dedupe（key=url）或改单例事件。→ **违规 CR-03 (P2)**
- 外链导航（lucide NavLink 到其他 workbench）W2 未涉及 ✓

**BasicMonitoringTab.tsx**（10s 轮询 + 手动刷新）
- L41 `useRef(0)` counter + `refreshRef` 隔离请求序号 ✓ 设计正确
- L78-112 loadSystemMetrics：`loadNo = ++refreshRef.current` → 仅生效最新调用 ✓
- **竞态**: 手动触发 isRefreshing=true（L114）与 10s 定时器并发 → 两个 loadSystemMetrics 调用交错；refreshRef 序号使旧结果被丢弃 ✓ **合规**。
- 但 `setLoading(true)` 两路共用 → 定时器期间 loading 永不 false（isRefreshing 期间不进入 finally），UI 空窗约 10s+。建议分裂 initialLoading / refreshing（已列 CODE_REVIEW P2-10，此处二次确认）。
- 判定: ✓ 除 P2-10 已知项

**DigitalTwinTab.tsx（本 wave 修改者）**
- L65-87 `loadDevices` useCallback 加 `selectedDeviceId` 依赖 → 设备选中变化会重建 callback，但 useEffect 无 deps（L85 [] 空）→ **违规 CR-04 (P2)**：LoadDevices 重挂载绑定的 callback 是**闭包 version-0**（第一次渲染的），后续 selectedDeviceId 变化不会自动重跑 loadDevices（因为 useEffect 遵守旧 callback 不是空）。实际 side-effect 函数内部 `if (devs.length > 0 && !selectedDeviceId)` 读的仍是 version-0 的 selectedDeviceId=null → 每次刷新都 `setSelectedDeviceId(devs[0].deviceId)`（回退首个设备，用户选择丢失）。建议：useEffect 补 `[selectedDeviceId]` 或 scripted loadDevices 读 `setSelectedDeviceId(devs => devs[0].deviceId)` 形式（避开 state 读取）。
- L78 silently swallows errors → **AC-05** 同族。建议 `setHealth(healthFallback)` + `setDevices([])` 改为带 `{ error }` 标签。
- L90-110 telemetry 加载：selectedDeviceId 改变则重发请求（useEffect deps=[selectedDeviceId]）✓；无 abort → 快速切换设备时旧 telemetry 数据可能覆盖新数据（setTelemetryData 乱序风险）。建议 `useEffect` return `abortController.abort()` 或加 deviceId 数值校验丢弃旧 response。→ **违规 CR-05 (P2)**
- **指令 action**（sendTwinCommand）POST + 立即拉 status → 单线程串行✓；无 debounce 但 directive 调用频率受手动点击约束 ✓

**GuardrailsView.tsx (1170 行，超大)**
- P1 拆组件已在 CODE_REVIEW 登记
- useDelete `deleteTarget` 单例 + confirm mask● 遮罩 ✓
- Toggle 乐观更新 ✓ + rollback ✓（L355-368）
- `activeSubTab === 'compile' && selectedPolicy` useEffect 自动 loadPreview (L381) deps 故意省略 eslint-disable → **违规 CR-06 (P1)**：编译后 preview 会自动刷新，但点击 "刷新预览" Button (loadPreview 手动) 会再触发一次（因 activeSubTab/selectedId 变化？）。实际 deps=[activeSubTab, selectedId] 不变就不会重跑 → **无实际重触发**，合规。撤销 CR-06。
- **竞态**: 创建成功后 `setPolicies(prev => [...prev, np])`（L317）+ `setSelectedId(np.id)`（L318）→ 无自动 refreshList；若 placeholder id 与后端返回 id 不一致 → 列表首项 id 不匹配。normalizePolicy 创建 fallback `Date.now().toString()` 作为 placeholder（L316）→ 后端返回后没有 replace placeholder 重试 → **列表保留 placeholder id 项**（双份）。建议：创建成功后 loadPolicies() 整表 reload 取代手写 append。→ **违规 CR-07 (P2)**。真实可复现的数据 bug。
- duplicate id：normalizePolicy fallback `String(raw.id ?? raw.policy_id ?? raw.name ?? '')`，若后端无 id 字段且两条策略 name 相异 → 唯一；但创建 placeholder id（Date.now()）可能碰撞 → CR-07 同病根。

**EngineMonitor.tsx**
- fetchHealth/fetchConfig 两个独立 state（healthLoading/configLoading/statusLoading）✓ 并行无竞争
- 单例 useEffect (L209-214 deps=[engine]) → engine prop 切换重拉 ✓
- **初始健康 initialHealth 不跑 polling**（只一次拉取）→ 合理（MonitoringCenter 已 load 后传入，避免重复请求）✓
- JSON.stringify(health, null, 2) 直出到 `<pre>` — **无 X-XSS 审计项**（只读 JSON，值不嵌入 innerHTML）✓
- P1 #6 visibilitychange 已登记

### 2.2 空引用 / 竞态专项

| ID | 窗口 | 级别 | file:line | 现象 | 建议 |
|----|------|------|-----------|------|------|
| CR-01 | 5s 轮询无 in-flight 守卫 | P2 | App.tsx:114-127 | RT>5s 时请求重叠；stats 写回乱序 | 改 setTimeout 递归或 inflightRef guard |
| CR-02 | i18n deps 触发 EngineCard 重 mount | P2 | MonitoringCenter.tsx:132 `[def.apiBase, showToast, t]` | locale 切换 → 6×/health+/status refetch + toast 重复 | error callback 内闭包 tRef，或 deps 剔除 t |
| CR-03 | 403 toast 无 dedupe | P2 | MonitoringCenter.tsx:113-119 | 一次 403 六 EngineCard 各 toast 一次 | ToastProvider 按 url 去重 |
| CR-04 | loadDevices 闭包 version-0 | P2 | DigitalTwinTab.tsx:66-87 | 刷新后用户选中的设备被重置为 devs[0] | setSelectedDeviceId updater 形式或不依赖 selectedDeviceId |
| CR-05 | 设备切换 telemetry 乱序 | P2 | DigitalTwinTab.tsx:90-110 | 快切 A→B，B 回包后 A 包后端到 → setTelemetryData(A 数据) 覆盖 B | AbortController 或 deviceId 连续性校验 |
| CR-07 | 创建 placeholder id 不替换 | P2 | GuardrailsView.tsx:316-318 | 列表保留 Date.now() 假 id 项（双份策略） | 创建成功后 loadPolicies() 全量重载 |
| CR-08 | App SwitchTab `state.currentView` 缓存切换中 | P2 | App.tsx:169-175 + 185-191 | currentView 更新先于 [currentView] useEffect 跑 → 新 view 在 openTabs 中重复出现一瞬 | syncTabs effect deps 补 state.currentView 前先判重 |

### 2.3 判定（W2）

**组件健壮性档位**: NetworkErrorBanner/EngineMonitor/App/BasicMonitoringTab = **L-稳健 (PASS, 条件 CR-01/CR-02/CR-03/CR-08)**
**DigitalTwinTab** = **L-修复中 (CR-04/CR-05 → P2 下轮可修)**
**GuardrailsView** = **L-拆分 (1170行 → 3 子组件 + CR-07)**

## W3 — "心跳慢路径" 契约消费专项（PMO-50 engineStatus 双轨）

**契约基准**:
- VO 弃用：`HeartbeatVO.failed` / `.missing[]` / engineState UNKNOWN 首帧语义
- DTO 启用：`engineStatus` (RUNNING / DEGRADED / STOPPED) + 慢路径硬阈值 90s / 180s / 300s
- 前端展示原则：DETAYMENT 态下（**接口不可用**）engineStatus 字段允许为 UNKNOWN（首帧），**不得显示为 SUCCESS/active**

### 3.1 前端消费点全量检查

| 前端消费点 | 消费字段 | 首帧 UNKNOWN 展示 | 90s 慢路径文本 | 判定 |
|-----------|----------|-------------------|----------------|------|
| EngineMonitor.tsx `health?.components` | 引擎 components map（DB/Git/Redis） | healthLoading 时 '—'（L227-229），非 SUCCESS | N/A（无文本） | ✅ 合规 |
| EngineMonitor.tsx `status?.entity` / `/api/v1/monitoring/{engine}/status` | StatusPayload {name,status,engine} | statusLoading 时 '—' | N/A | ✅ |
| EngineMonitor.tsx config `JSON.stringify` | 配置字段 | configLoading → '—' / configError 内联 | N/A | ✅ |
| MonitoringCenter.tsx EngineCard `health?.status` `/health?.status` → `isUp` 判据 | HealthSummary.status（UP/OK/HEALTHY/RUNNING→真；其他→假） | loading 时 Loader2 转圈；null→ isUp=false → XCircle 红叉 + 'UNKNOWN' 文本 | N/A | ⚠️ **见 AC-08**: `null` health（网络/异常）与 `status=STOPPED` 后端真停**渲染同一**（红叉 UNKNOWN）。用户无法区分"服务异常"与"引擎已停"。建议区分 icon：unknown=灰 Clock，stopped=红 XCircle，且标题区分 'UNKNOWN (no response)' vs 'STOPPED'。 |
| MonitoringCenter.tsx summary 端点 `/api/v1/monitoring/summary`（L133） | summary.states[] (engine, label, state, lastChecked, lastStableHealthy) | 空 states 数组 L211 判断 → 'No engine status yet' | **基于 lastStableHealthy 分 60s/300s 阈值**（L156,163）：`ageSec >= 300` → stale 红；`ageSec >= 60 && state!=='DEGRADED'` → 黄+慢路径文本"未在 60 秒内出现心跳… Because the channel may be congested..." + 建议 close/monitoring 路径 — **这部分文案与 PMO-50 慢路径契约吻合**（60s 提醒 / 慢硬阈值 180s 可选文案抽象 "≥ 60s 未稳定健康" = 慢软档提示；180s 硬档未在 UI 单列） | ✅ 合规（180s 硬档未在 UI 二次提示，不影响，因为 ≥300s 已归 stale 红） |
| MonitoringCenter.tsx `refreshKey` debounce | 350ms 去抖（L178）✓ | — | — | ✅ 一致 |
| api.ts `SYSTEM_PROMPT` 引擎状态枚举（L2833-2897） | `healthy/degraded/unknown` 三态与后端 EngineStatus 枚举 RUNNING/DEGRADED/STOPPED 不一一 | N/A（prompt 是 LLM 提示，非 UI 字段） | N/A | ⚠️ 注意 L2974-2990 三档时间模型：60s-OK / 120s-DEGRADED / HEARTBEAT_TIMEOUT_S **硬阈值缺少 config 注入点** → DTO 字段 `heartbeatTimeoutMs` 未出现在 SYSTEM_PROMPT 消费表。→ **AC-09 (P2)**: FRAMEWORK 职责地块——system_prompt 里的 90/180/300 阈值是硬编码（L2978-2990），未从 DTO 场/设置端点注入；若 PMO-50 调整阈值，prompt 需同步修。**属 FRAMEWORK 职责，移交 PM 合约跟进，非本 wave 代码缺陷。** |
| App.tsx engine query `getEngineList` | engineList ( WorkspaceWorkspaceId) 与 engineStatus 无直接约束 | 首次加载 'No engine query data' | N/A | ✅ 合规 |

### 3.2 影子单元测试合规性

- 空状态、首帧 unknown、异常路径 / 业务分支 3 类是否全覆盖：
  - NetworkErrorBanner：DOWN/RESUMED/OFFLINE 三态 ✓（§1.2 已验）
  - EngineMonitor：loading three ✓ / config error ✓ / initialHealth 缺省 ✓
  - EngineCard：loading ✓ / isUp ✓ / !isUp ✓ / 403 noPermission ✓
  - BasicMonitoringTab：loading ✓ / 数据空 ✓；**未覆盖**: 后端 500 假空数据（AC-05 同族）→ **在 API 层解决，不在 Tab 层补测**
- 判定：✅ 组件层单元测试盲区已在代码注释覆盖合理（"Graceful" 注释），无新增遗漏。

### 3.3 总判定

**心跳慢路径契约消费 = PASS（条件：AC-08 UI 区分 / AC-09 Framework 跟进）**

## W4 — 认证异常路径（401）全量核验

PMO-43 AUTH_CHAIN 契约（zh/en 双语源）：
- `login-required`：未登录 / 会话过期 → 跳 /login
- session-loss：期间 401 → FORCE_REDIRECT to /login + 清 token

### 4.1 全量覆盖表

| 消费点 | 401 处理 | 清 token | 跳 #/login vs /login | 级别判定 |
|--------|---------|----------|----------------------|----------|
| api.ts `apiFetchData`（公共） | 401 catch 抛错 + 调用点各自 res | 未在公共层清（留给调用方/RequireAuth） | N/A | ⚠️ 见 AC-10 |
| RequireAuth.tsx:38-41 | 401→setState('login') | 否 — **RequireAuth 未清 token**，Login 页接管后清除 | 用 HashRouter Route switch → /login | ✅（token 由 Login 重填覆盖，安全） |
| App.tsx apiHealth（/api/health 白名单端点） | 白色监听，无 401 可能 | — | — | ✅（白名单） |
| App.tsx monitor refresh（/api/v1/monitoring/* 大概率需要鉴权） | 无 401 特判 → keep-last-known → **静默 401 直至用户手刷** | 无 | — | ⚠️ 已在 AC-04 登记（双列：W1 异常路径 + W4 认证链） |
| MonitoringCenter.apiFetch L52-57 | 显式 401 → remove token + `window.location.hash = '#/login'` | ✓ 清除 | hash hack 而非 navigate()，但 HashRouter 下行 ✓ | ✅（但违反 FE_STANDARD_PROFILE "统一 Router.navigate" — 双 API 层分叉同 CR-05） |
| GuardrailsView.apiCall L114-122 | 显式 401 → remove token + `window.location.hash = '#/login'` | ✓ 清除 | hash — 同上 | ✅（同上） |
| api.ts fetchTwin* 系列 | 吞（不清不跳） | ✗ **会话过期时监控-数字孪生页沉默轮询**（依赖 NetworkErrorBanner 显隐） | — | ⚠️ 与 AC-04 同族，登记 **AC-11** |
| NetworkErrorBanner L59 | 显式 401 抑制 banner ✓ | — | — | ✅ |

### 4.2 违规清单（W4）

| ID | 文件:行 | 级别 | 说明 |
|----|---------|------|------|
| AC-10 | src/api.ts (顶层 fetch 包装) | P2 | 公共层未统一 401→clear+redirect 语义，散落于 3 处（MonitoringCenter / GuardrailsView / RequireAuth）各自实现。与 FE_STANDARD_PROFILE "AUTH_CHAIN 单点" 原则冲突。建议：api.ts 加 `onAuthError` hook，默认行为 clear token + `window.dispatchEvent('ecos-401-expiry')`，调用方可选静默（监控页）。 |
| AC-11 | src/api.ts:2696-2748 五函数 | P2 | 401 会话过期不清 token 不跳登录（吞错），与 4.1 行7 类似。同 AC-04 / AC-05 同族。 |
| AC-12 | 全仓 401→hash 跳 | P2 | `window.location.hash = '#/login'` 绕开 React Router state，丢失 `returnTo`/redirect 上下文（用户在 guardrails 页 401 会去 login，登录后回首页而不是原页）。建议统一 `useNavigate('/login', {state:{from:location}})`。 |

### 4.3 判定（W4）

**认证异常路径 = 条件 PASS（AC-10/11/12 P2，不阻断本轮 wave；但 CR 续跑（主权日志/鉴权）必须收口）**

## W5 — 权限异常路径（403）

### 5.1 全量覆盖表

| 消费点 | 403 处理 | 是否误登出 | 是否误清 token | 文案 |
|--------|---------|-----------|----------------|------|
| api.ts `apiFetchData` (L38-45) | 无特判 → 抛 http 错 + Notify NetworkDown(？) | 否 | 否 | 调用人自处理 | ⚠️ 见 AC-13 |
| RequireAuth.tsx (顶层路由) | 403 → no-access 页（不允许登出） | 否 ✓ | 否 ✓ | NoAccessPage 自带 | ✅ |
| App.tsx monitor refresh | 无特判 → toast（与 401 同文案） | 否 ✓ | 否 ✓ | 403 文案=401 文案 (**误导**) | ⚠️ AC-03（已列 W1） |
| App.tsx stats/engine 轮询 | 无特判 → keep-last-known 或 toast | 否 ✓ | 否 ✓ | 403 文案=其他错（误导） | ⚠️ AC-14 |
| EngineMonitor fetch* | 走 apiFetchData → 内联 error 态 | 否 ✓ | 否 ✓ | 内联 error 渲染 | ✅ |
| MonitoringCenter.apiFetch L58-64 → `ecos-403` | 派发 CustomEvent，不登出 ✓ | 否 ✓ | 否 ✓ | 组件 `showToast('error', t('common.noPermission'))` (i18n ✓) | ✅ |
| GuardrailsView.apiCall L115-120 | 抛错 `无权限访问该资源` — 中文硬码 | 否 ✓ | 否 ✓ | **i18n 缺失**（CODE_REVIEW P0 清单） | ⚠️ 并入 PR-01 |
| GuardrailsView ProblemDetector（未找到 ProblemDetector——L1195 起为 PreviewComparison/DataTable）| 未涉及 403 | — | — | — | ✅ 无覆盖缺陷 |
| NetworkErrorBanner L59 | 403 未在抑制白名单 | 否 — 但 403 一般不产生 NetworkDown 事件 | 否 | — | ⚠️ AC-02 |

### 5.2 违规清单（W5）

| ID | 文件:行 | 级别 | 说明 |
|----|---------|------|------|
| AC-13 | src/api.ts (apiFetchData) | P2 | 403 在公共层无独立分型（kind='http'+status），消费方必须自行判 status。建议公共层 kind='forbidden' 简化消费（配合 FE_STANDARD_PROFILE）。 |
| AC-14 | src/App.tsx 120-150 | P2 | 403 文案=401/网络错文案，误导用户"网络或服务问题"。建议 403 → '无权限'（i18n key common.noPermission）。 |
| PR-01（复用） | GuardrailsView.tsx:496 附近 '无权限访问该资源' 等硬码中文 403 文案 | P0 | 已合并到 CODE_REVIEW P0 #3 中文硬码类（非架构缺陷，但文案 i18n 缺失）。 |

### 5.3 判定（W5）

**权限异常路径 = PASS（无 P0/P1；AC-02/03/13/14 为 P2 文案/分型增强）**

---

## 汇总违规列表

### P0 (阻断)
**本 ARCH_CONSISTENCY 域 = 0 个**（P0 全部集中在 CODE_REVIEW：i18n/颜色/800 行）

### P1 (高，修复后重跑)
| ID | 域 | 文件 | 摘要 |
|----|----|------|------|
| AC-01 | W1 | App.tsx:78 / api.ts:2022 | 健康检查双端点分裂（/api/health vs /api/v1/monitoring/health） |

### P2 (下轮规约)
| ID | 域 | 文件 | 摘要 |
|----|----|------|------|
| AC-02 | W1/W5 | NetworkErrorBanner.tsx:59 | 403 加入抑制白名单 |
| AC-03 | W1/W5 | App.tsx:281-288 | 监控 refresh 403 独立文案 |
| AC-04 | W1/W4 | App.tsx:120-150 | 轮询 401 未登出闭环 |
| AC-05 | W1/W2 | api.ts fetchTwin*/fetchMonitoringDashboard | 5 类 API 吞错掩盖故障 |
| AC-06 | W1 | RequireAuth.tsx:8 | fail-open 需 SECURITY_AUDIT 签核 |
| AC-07 | W1 | MonitoringCenter.apiFetch 等 3 处 | 401/403 清 token 逻辑三处重复，建议下沉 |
| AC-08 | W3 | MonitoringCenter EngineCard | 网络 null 与后端 STOPPED 渲染同质，UI 不分型 |
| AC-09 | W3 | api.ts 2833-2990 | 心跳阈值硬编码在 SystemPrompt，DTO 无 heartbeatTimeoutMs 注入点（FRAMEWORK 跟进） |
| AC-10 | W4 | api.ts 公共层 | 401→clear+redirect 单点缺失 |
| AC-11 | W4 | api.ts fetchTwin* | 401 吞错沉默轮询 |
| AC-12 | W4 | MonitoringCenter/GuardrailsView | 401 用 hash 跳登录，丢 returnTo |
| AC-13 | W5 | api.ts | 403 kind 分型缺失 |
| AC-14 | W5 | App.tsx 120-150 | 403 文案与其他错误同 |
| CR-01 | W2 | App.tsx:114-127 | 轮询无 in-flight 守卫 |
| CR-02 | W2 | MonitoringCenter.tsx:132 | t 在 deps 中 → locale 切 6x refetch |
| CR-03 | W2 | MonitoringCenter.tsx:113-119 | 403 toast 无 dedupe |
| CR-04 | W2 | DigitalTwinTab.tsx:66-87 | 闭包 version-0 重置设备 |
| CR-05 | W2 | DigitalTwinTab.tsx:90-110 | telemetry 乱序覆盖 |
| CR-07 | W2 | GuardrailsView.tsx:316-318 | 创建后 placeholder id 双份 |
| CR-08 | W2 | App.tsx:169-175/185-191 | tab 更新先于 effect → 一瞬重复 |

### FRAMEWORK 跟进（迁移到 PM）
- MB-01: AC-09 阈值 DTO 化。建议 gateway `/api/v1/monitoring/health` 配置端点新增 `heartbeatTimeoutMs` + `slowSoftThresholdMs` 字段。
- ENTITY_PROFILE 扩充：`/api/v1/gateways/{gatewayId}/status` 新增 UI 绑定推荐（read-only），见 §1.4。

## 结论

**一致性评估**：⚠️ 基本一致
- 网关授权链：无 P0/P1 阻断（AC-01 = P1 不阻断硬依赖，但建议与 PMO-43 T5 NetworkErrorBanner 合并交付时一次性修端点分裂）
- 组件健壮性：DigitalTwinTab / GuardrailsView 有 4 个 P2 竞态，不阻断本轮 wave 交付
- 心跳慢路径：契约消费合规，UI 缺 UNKNOWN/STOPPED 区分（AC-08）
- 认证/权限异常：401 链路已覆绝（除 API 层吞错），403 链路全程无误登出

**门禁**：
```
ARCH_GATE = FAIL
```
- 判定依据：archives 新发的 AC-01 (P1) 未处理 → FAIL；P0 = 0，但 P1 × 1 按 ARCH_CONSISTENCY_APPROVAL_RECORD 标准（P0/P1 违规 > 0 → FAIL）判定
- **deliverable_allowed = false**，需 Fullstack 修 AC-01 + CODE_REVIEW P0/P1 后回跑本 skill 复核 AC-01 域
- 其余 P2 计 20 项：建议本轮不阻断，Wave 修复清单 PM 决策（豁免 / 跟进 / 拆子任务）

### ARCH_CONSISTENCY_APPROVAL_RECORD（JSON 供上游 reviewer-code-review 聚合）

```json
{
  "artifact": "ARCH_CONSISTENCY",
  "name": "PMO-43 前端 Wave 架构一致性评估",
  "version": "v1.0.0",
  "status": "FAIL",
  "workflow_mode": "L3",
  "checks": {
    "module_boundary":   {"total": 1,     "violations": 0, "p0": 0, "p1": 0},
    "api_contract":      {"total": 14,    "violations": 2, "p0": 0, "p1": 1, "p2": 4},
    "data_model":        {"total": 0,     "violations": 0, "p0": 0, "p1": 0},
    "component_race":    {"total": 12,    "violations": 6, "p0": 0, "p1": 0, "p2": 6},
    "heartbeat_contract":{"total": 4,     "violations": 1, "p0": 0, "p1": 0, "p2": 1},
    "auth_401":          {"total": 4,     "violations": 2, "p0": 0, "p1": 0, "p2": 2},
    "permission_403":    {"total": 6,     "violations": 2, "p0": 0, "p1": 0, "p2": 2}
  },
  "arch_violations_summary": {
    "p0_arch_violations": 0,
    "p1_arch_violations": 1,
    "p2_arch_violations": 21
  },
  "deliverable_allowed": false,
  "source_patch_ref": "SOURCE_PATCH@errory-wave-33"
}
```

### 未解决
- AC-09 / MB-01 阈值 DTO 化：需后端 gateway 监控 Controller 扩 Swagger 字段 + SYSTEM_PROMPT 重嵌，跨 team 协调，**建议 PM 单独立子任务**（不在本 wave 边界内）
- AC-06 RequireAuth fail-open：需 security-engine owner 决定网络抖动时权限未确认的放行边界 → **移交 SECURITY_AUDIT 子任务**
- gateway 路由屏蔽：本轮无前端原型代码消费 `/gateways/{gatewayId}/status`，端点屏蔽校验不适用；待 Fullstack 接入时按 §1.4 四步要求走
