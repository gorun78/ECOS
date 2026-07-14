# ECOS 页面功能规格分析 — 系统管理 + 总览 + 安全组 (12页)

> 分析日期: 2026-06-26 | 基于源码静态分析

---

## 1. CognitiveOperatingSystem — 战略目标/总览

**路由**: `#/` (index) / `#/mission_control`

**交互元素**:
- 按钮: ["Run Alignment Diagnostic" / "对齐一致性审计与热加载", "Run Pareto Optimizer" / "执行帕累托优化", "Run Rule Reasoning" / "执行规则推理", 关闭Error Banner的 × 按钮]
- 可点击卡片: 6层蓝图架构卡片 (strategic/knowledge/agent_os/semantic/data_platform/security_infra)，点击选中高亮
- 指标卡片: 4个 (蓝图一致性评级 / 已对齐验证架构层 / 系统注册功能组件 / 认知系统链路负载)
- 诊断终端: 日志输出面板 (模拟终端)，显示逐行动态日志

**功能操作**:
- 蓝图层级诊断审计: 点击"Run Alignment Diagnostic"触发逐层覆盖率校验 (模拟超时后更新覆盖率至100%)
- 帕累托优化: 调用 `apiCognitiveOptimize`，显示多目标优化结果表 (方案ID/层级/X/Y/Z目标)
- 规则推理: 调用 `apiCognitiveReason`，输出匹配规则列表 (规则ID、优先级标签 high/medium/low、描述)
- 知识图谱搜索: 输入框搜索 `searchKnowledge`，空搜索重置全量KG
- 蓝图层切换: 点击6层卡片切换右侧详情面板 (描述/匹配代码页/蓝图元素列表)

**数据展示**:
- 4个KPI指标卡片 (评分百分比、层数统计、组件数、活动流数)
- 6层蓝图架构卡片列表 (含覆盖率进度条、Phase标签、VERIFIED徽章)
- 选中层详情 (描述文本 + 匹配功能页面 + 蓝图元素网格)
- 诊断终端 (黑底绿字模拟log输出)
- 帕累托结果表格 (Solution ID/Rank/X/Y/Z Objective)
- 规则推理结果列表 (规则卡片含优先级徽章)

**状态**:
- Loading: `goalsLoading`/`causalLinksLoading`/`kgLoading`/`blueprintApiLoading` 状态变量，无专用骨架屏但有转圈按钮
- Error: 顶部红色Error Banner (`wmError` 状态)，带关闭按钮和 AlertTriangle 图标
- Empty: 终端空闲状态提示; 蓝图API不可用时使用硬编码fallback数据
- 特殊: `optimizeLoading`/`reasonLoading` 使按钮进入loading态 + 文字变化

---

## 2. MonitoringCenter — 监控中心

**路由**: `#/monitor`

**交互元素**:
- Tab: ["基础指标" (metrics), "数字孪生" (twins)]
- 按钮: ["Refresh" / "刷新", "Run Diagnostics" / "系统诊断", "Send Command" / "发送指令", 设备卡片(可点击选中)]
- 输入框: 指令输入框(Command), 参数JSON文本框(Params JSON)
- 图表: Recharts AreaChart (实时指标折线图)
- 设备卡片: 5列网格，显示设备名/类型/在线状态，可点击选中

**功能操作** (基础指标Tab):
- 刷新仪表盘数据 (`fetchMonitoringDashboard`)
- 系统诊断 (`runSystemDiagnostics`)，显示诊断结果Toast
- 展示KPI指标卡片 (CPU/内存/磁盘/网络等)
- 展示实时趋势折线图 (AreaChart)
- 展示进程/守护进程列表表格 (job名/type/uptime/count)
- 展示告警列表 (level/time/module/message)

**功能操作** (数字孪生Tab):
- 加载设备列表 + 健康状态 (MQTT状态/设备计数)
- 选中设备查看遥测数据 (SVG迷你折线图 + 数据点列表)
- 设备影子展示: Reported状态 + Desired期望状态 (JSON)
- 指令下发: 输入命令名 + JSON参数，调用 `sendTwinCommand`
- 刷新设备列表
- 指令执行结果反馈 (成功/错误提示)

**数据展示**:
- KPI指标卡片网格 (4列)
- AreaChart 实时趋势图
- 进程表格 (名称/类型/运行时间/项目数)
- 告警面板 (level/time/module/message)
- 设备状态卡片网格 (在线状态指示灯)
- SVG迷你遥测折线图
- 设备影子JSON面板 (Reported/Desired 双栏)

**状态**:
- Loading: 中央转圈图标 + "Loading monitoring data..." 文字
- Empty: 大图标 + "实时监控数据尚未就绪" 提示 + "点击刷新按钮重试"
- Error: 静默处理 (catch块空)，诊断为服务不可用显示fallback消息
- 遥测数据不足: 提示"数据不足，至少需要2个数据点"
- 无设备: "暂无设备连接" 虚线框提示
- 无遥测: "暂无遥测数据"
- 指令错误: 红色错误提示框

---

## 3. SecurityCenter — 安全中心

**路由**: `#/security-center` / `#/security` / `#/policy-engine`

**交互元素**:
- Tab: [安全审计(SecurityAudit), 密码审计(CryptoAuditPanel), 安全配置(SecurityConfigPanel), ABAC策略(AbacPolicyManager), OPA引擎(PolicyEngine), 数据脱敏(DataMaskingDemo)]
- 组件: 这是一个Tab容器页，本身无独立操作元素，内容由6个子组件渲染
- ErrorBoundary: 包裹整个组件

**功能操作**:
- Tab切换: 点击Tab切换子组件，使用 `ErrorBoundary` 包裹每个子组件渲染
- 6个子页面功能 (不在本文件范围内，仅在子组件中实现)

**数据展示**:
- Tab栏 (6个Tab，带图标)
- 内容区 (根据activeTab渲染对应子页面组件)

**状态**:
- Loading: 无独立loading态 (由子组件管理)
- Error: ErrorBoundary 捕获子组件错误，显示fallback UI
- Empty: 无组件匹配时显示"请选择一个标签页" / "Select a tab"

---

## 4. UserManagement — 用户管理(IAM)

**路由**: `#/iam`

**交互元素**:
- Tab: ["用户" (users), "角色" (roles), "组织机构" (orgs), "权限" (permissions)]
- 按钮: ["新建" (Plus图标), "刷新" (RefreshCw图标), 表格行操作: "编辑"(Edit3), "删除"(Trash2), "重置密码"(Key, 仅用户Tab), 状态切换按钮(点击toggle)]
- 弹窗: 新建/编辑表单弹窗 (modal)，重置密码弹窗
- 输入框: 用户表单(用户名/真实姓名/邮箱/手机/密码/所属组织下拉), 角色表单(角色名/角色编码/描述), 机构表单(机构名称/机构编码)
- 组织树: 递归可展开树形组件，支持展开/折叠

**功能操作**:
- CRUD: 用户/角色的 新建(Create)、编辑(Update)、删除(Delete)、刷新列表
- 密码重置: 为用户设置新密码 (≥6位验证)
- 状态切换: 点击用户状态按钮切换 ACTIVE ↔ DISABLED
- Tab切换: 切换时自动重新加载对应数据
- 组织树: 递归展开/折叠，选择组织
- 权限Tab: 只读，不支持新建/编辑/删除

**数据展示**:
- 用户表格列: [用户名, 姓名, 邮箱, 所属组织, 状态, 操作]
- 角色表格列: [角色名, 编码, 描述, 类型, 操作]
- 组织机构: 树形结构 (机构名/编码/状态/类型/描述，可展开折叠)
- 权限表格列: [资源, 操作, 描述] (只读)

**状态**:
- Loading: "加载中..." 文字
- Error: 红色背景错误提示框，显示错误消息
- Empty: "暂无数据" 居中文字 (各Tab独立判断)
- 确认: 删除前 `confirm("确认删除？")`

---

## 5. DictManager — 数据字典

**路由**: 未在main.tsx中注册独立路由 (导入但可能内嵌在其他页面中)

**交互元素**:
- 模式切换: ["数据表管理" (table), "字典项管理" (dict)] 两个切换按钮
- 按钮: ["新建数据表", "保存表信息", "取消", "删除此表", "添加字段", "更新字段"/"添加字段", "新增字典项", "创建"/"更新"]
- 状态流转按钮: DRAFT→"发布", PUBLISHED→"废弃", DEPRECATED→"重新启用"
- 输入框 (表): 表名*/中文名称/Schema/标签(逗号分隔)
- 下拉框 (表): 状态筛选(DRAFT/PUBLISHED/DEPRECATED/全部), 数据源类型(MySQL/PostgreSQL/Oracle/Hive/ClickHouse/其他)
- 输入框 (字段): 字段名*/类型(分类下拉)/长度/精度/标度/默认值/描述
- 复选框 (字段): 可为空、主键
- 输入框 (字典项): 编码*/值/标签*/状态(下拉)/排序/描述
- 搜索框: 表名搜索、字典类型搜索、字典项搜索
- Toast通知: 固定右上角成功/错误提示
- 删除确认弹窗: 自定义模态框

**功能操作**:
- 数据表 CRUD: 新建/编辑/删除表
- 字段 CRUD: 添加/编辑/删除字段
- 状态流转: DRAFT→PUBLISHED→DEPRECATED→DRAFT 生命周期管理
- 状态筛选: 下拉框过滤表格列表
- 搜索: 表名模糊搜索 (防抖300ms)
- 字典项 CRUD: 新建/编辑/删除字典项
- 字典类型列表: 选择类型加载字典项
- 快速统计: 显示草稿/已发布/已废弃数量

**数据展示**:
- 左栏-表模式: 表列表 (表名/状态徽章/Schema/数据源/字段数)
- 左栏-字典模式: 字典类型列表 (类型名/编码/状态)
- 右栏-表模式: 表编辑表单 + 字段列表 (字段名/类型/可空/主键/默认值/操作) + 表元数据footer
- 右栏-字典模式: 字典项表格 (编码/值/标签/排序/状态/操作) + 字典类型描述
- 字段类型彩色徽章: 字符串(蓝)/数值(绿)/日期(紫)/布尔(琥珀)

**状态**:
- Loading: "加载中..." + RotateCw旋转图标
- Empty: "暂无数据表，点击上方「+ 新建数据表」开始创建" / 搜索无结果提示
- Error: Toast通知 (3秒自动消失, 成功绿色/错误红色)
- 保存中: 按钮显示旋转图标 + 禁用
- 删除确认: 自定义模态确认框 ("确认删除" 标题 + 取消/删除按钮)

---

## 6. SystemDictionary — 系统字典

**路由**: `#/dict`

**交互元素**:
- 左侧边栏: 字典分类按钮列表 (每项显示dictType + 条目计数徽章)
- 右侧: 字典项行 (可点击展开/折叠细节)
- 无按钮操作 (纯浏览页面，无CRUD)

**功能操作**:
- 分类导航: 点击左侧字典类型 → 右侧显示该类型下所有字典项
- 条目展开: 点击字典项行展开详情 (ID/Sort/Parent/Created/Ext)
- 数据加载: 从 `/api/v1/system/dict` 获取11类49条数据，按 dictType 分组、按 sortOrder 排序

**数据展示**:
- 左侧: 字典类型列表 (dictType + 条目数)
- 右侧: 字典项卡片列表 (dictCode/mono标签/dictLabel/status徽章/extValue)
- 展开详情: grid布局 (ID/Sort/Parent/Created/Ext)
- 底部: 总条目数统计

**状态**:
- Loading: Loader2旋转图标 + 可选"加载中..."文字
- Error: 红色背景错误提示 (AlertCircle图标 + 错误消息)
- Empty: 无选中类型时显示 BookOpen大图标 + "← 选择左侧字典分类查看数据"

---

## 7. SystemConfigManager — 系统配置

**路由**: `#/system-config`

**交互元素**:
- 左侧分组导航: 6个分组按钮 [全局/G1-数据底座/G2-业务语义/G3-运营/G4-Agent/G5-基础设施]
- 配置项: 每条显示 key(momo标签) + label + type徽章 + description
- 布尔类型: Toggle开关按钮 (即时保存)
- 文本/数字类型: input输入框 + Save按钮
- 保存确认: Check图标 (2秒后消失)

**功能操作**:
- 配置查询: 按分组加载配置项 (`fetchSysConfigs`)
- 配置编辑: 修改文本/数字值，点击Save保存 (`updateSysConfig`)
- 布尔切换: 点击开关即时保存
- 保存: 单项独立保存，成功显示绿色Check，失败显示红色错误
- 分组切换: 点击左侧分组切换右侧配置列表

**数据展示**:
- 左侧: 配置分组列表
- 右侧: 配置项列表 (key/label/type/description + 编辑控件)
- 类型徽章: boolean(amber)/number(blue)/text(gray)

**状态**:
- Loading: Loader2旋转图标 + "加载中..."
- Error: 红色AlertCircle + 错误消息 (per-key)
- 保存中: 按钮显示Loader2旋转 (per-key saving状态)
- 已保存: 绿色Check图标 (2秒后自动消失)

---

## 8. TenantManager — 租户管理

**路由**: `#/tenants`

**交互元素**:
- Tab: ["配额管理" (quota), "用量仪表盘" (usage), "账单查看" (invoice)]
- 按钮: ["刷新", "编辑" (配额编辑按钮), 配额编辑弹窗保存/取消]
- 下拉框: 租户选择器, 时间范围(7d/30d/90d), 账单月份选择器(最近12个月)
- 配额编辑弹窗: 日限额输入/月限额输入, 含验证(日≤月)

**功能操作**:
- 租户选择: 下拉框切换租户，自动加载对应数据
- 配额管理: 查看配额表格 (配额类型/日限额/月限额/今日已用/使用率条形图)，编辑配额弹窗
- 用量仪表盘: 选择时间范围，查看API调用量柱状图 + 存储用量柱状图
- 账单查看: 选择月份，查看账单明细表格 (项目/用量/单价/费用) + 合计费用
- 数据刷新: 全局刷新按钮 (重新加载租户+Tab数据)

**数据展示**:
- 配额表格: [配额类型, 日限额, 月限额, 今日已用, 使用率(进度条+百分比), 操作]
- 用量仪表盘: 两个柱状图 (API Calls / Storage MB)
- 账单表格: [项目, 用量, 单价, 费用] + 合计费用醒目显示
- 红色(>80%)/黄色(>50%)/绿色 使用率进度条

**状态**:
- Loading: 旋转图标 + "加载配额数据…" / "加载用量数据…" / "加载账单数据…"
- Empty: 图标 + "该租户暂无配额数据" / "该月份暂无账单数据"
- Error: 红色错误提示框 (配额错误/用量错误/账单错误)
- 无租户: "无可用租户" / "加载中…" option
- 验证错误: "请输入有效数值，日限额不能超过月限额"

---

## 9. TaskCenter — 任务中心

**路由**: `#/task_center`

**交互元素**:
- 按钮: ["＋ 提交任务", "状态", "执行", "取消", 筛选按钮组, "重试"]
- 筛选按钮组: ["全部", "SUBMITTED", "RUNNING", "COMPLETED", "FAILED", "DORIS_SQL", "ETL"]
- 提交弹窗: 输入框(任务名称/描述/SQL语句textarea), 下拉框(任务类型: DORIS_SQL/ETL/数据质量/报表), 数字输入(优先级0-10), 提交/取消按钮
- Doris健康指示器: 显示Doris UP/DOWN状态

**功能操作**:
- 任务列表: 加载所有任务列表 (`apiTaskList`)
- 任务提交: 弹窗表单 → `apiTaskSubmit` (taskName/taskType/description/priority/params.sql)
- 任务执行: 对SUBMITTED状态任务调用 `POST /api/v1/task/{id}/execute`
- 任务取消: 对RUNNING/SUBMITTED状态任务调用 `apiTaskCancel`
- 状态查询: 弹窗显示状态和进度 (`apiTaskStatus`)
- 筛选: 按状态/类型筛选任务列表
- Doris健康检查: `GET /api/v1/task/doris/health`

**数据展示**:
- 任务卡片列表: 任务名/状态徽章(彩色)/类型标签/描述/任务ID后8位/创建时间/优先级
- 状态颜色: SUBMITTED(紫)/RUNNING(琥珀)/COMPLETED(绿)/FAILED(红)/CANCELLED(灰)/PAUSED(紫)
- Doris状态指示器

**状态**:
- Loading: "加载中..." 居中文字
- Empty: "暂无任务，点击"提交任务"创建第一个"
- Error: 暗红背景错误提示 + "重试"按钮
- 弹窗提交按钮: 任务名称为空时禁用

---

## 10. KanbanBoard — 项目看板

**路由**: `#/kanban`

**交互元素**:
- 标题栏: 图标 + "ECOS 项目看板" + Sprint标签
- iframe: 嵌入 `/kanban/ecos-kanban.html` (sandbox: allow-scripts allow-same-origin)

**功能操作**:
- 嵌入第三方看板页面 (kanban-web standalone)
- 所有实际功能由iframe内的HTML页面提供

**数据展示**:
- iframe内嵌页面

**状态**:
- Loading: 无 (iframe异步加载)
- Error: 无 (iframe sandbox隔离)
- Empty: N/A (静态嵌入)

---

## 11. TokenDashboard — Token仪表盘

**路由**: `#/tokens`

**交互元素**:
- 下拉框: 时间范围选择 [最近1天/最近7天/最近30天]
- 按钮: ["刷新"]
- 无CRUD操作 (纯审计浏览)

**功能操作**:
- Token用量查询: `GET /api/telemetry/tokens/summary?range=7d`
- 时间范围切换: 自动重新加载数据
- 手动刷新

**数据展示**:
- 总量卡片: Token总量 (大号数字 + tokens unit)，支持格式化 (B/M/K)
- 按模型分布: 模型名 + token数 + 进度条 + 百分比
- 按操作分布: 操作名 + token数 + 进度条 + 百分比
- 两列布局

**状态**:
- Loading: 总量卡片内旋转图标 + "加载中…"
- Error: 红色错误提示框
- Empty: "暂无数据" (BarChart3/TrendingUp图标 + 提示文字)
- ErrorBoundary包裹

---

## 12. TelemetryViewer — 遥测

**路由**: `#/telemetry`

**交互元素**:
- 按钮: ["刷新"]
- 可点击行: Trace列表行，点击展开/折叠Span详情
- Span树: 可展开/折叠的子Span (ChevronDown/ChevronRight按钮)

**功能操作**:
- 健康检查: `GET /api/telemetry/health` → 显示状态/Spans数/Token记录数/数据库
- 链路列表: `GET /api/telemetry/traces?limit=20` → TraceSummary列表
- 链路详情: 点击Trace行 → `GET /api/telemetry/traces/{traceId}` → Span详情树
- Span树展开/折叠: 默认展开深度<2的节点

**数据展示**:
- 健康卡片: 3列 (Spans数 / Token Records数 / Database名) + 状态徽章
- Trace表格: [Trace ID(截断16字符), 根操作, Spans数, 耗时]
- Span详情表: [Span ID(树形缩进), Operation, Duration, Status]
- 耗时格式化: ≥1s显示秒 / ≥1ms显示毫秒 / 其余显示微秒
- 状态徽章: OK/Healthy/Connected(绿) / Error/Failed(红) / 其他(琥珀)

**状态**:
- Loading: Activity旋转图标 + "加载中…" (列表级和详情级分离)
- Error: 红色错误提示框
- Empty: Search图标 + "暂无链路数据" / "无 Span 数据"
- 加载Span详情: 行内小型旋转图标 + "加载 Span 详情…"
- ErrorBoundary包裹

---

## 汇总矩阵

| # | 页面 | 路由 | Tab数 | CRUD | 搜索/筛选 | 图表 | Modal |
|---|------|------|-------|------|-----------|------|-------|
| 1 | CognitiveOperatingSystem | #/ (index) | 0 | ✗ | KG搜索 | ✗ | ✗ |
| 2 | MonitoringCenter | #/monitor | 2 | ✗ | ✗ | AreaChart | ✗ |
| 3 | SecurityCenter | #/security-center | 6 | 子页面 | ✗ | ✗ | ✗ |
| 4 | UserManagement | #/iam | 4 | 用户/角色/组织CRUD | ✗ | ✗ | 新建/编辑/重置密码 |
| 5 | DictManager | (内嵌) | 2模式 | 表/字段/字典项CRUD | 表名/字典类型/字典项搜索 + 状态筛选 | ✗ | 删除确认 |
| 6 | SystemDictionary | #/dict | 0 | ✗ (只读) | ✗ | ✗ | ✗ |
| 7 | SystemConfigManager | #/system-config | 6分组 | Update | ✗ | ✗ | ✗ |
| 8 | TenantManager | #/tenants | 3 | 配额Edit | 租户下拉/时间范围/月份 | 柱状图(x2) | 编辑配额 |
| 9 | TaskCenter | #/task_center | 0 | 提交/执行/取消 | 状态/类型筛选按钮组 | ✗ | 提交任务 |
| 10 | KanbanBoard | #/kanban | 0 | ✗ (iframe) | ✗ | ✗ | ✗ |
| 11 | TokenDashboard | #/tokens | 0 | ✗ (只读) | 时间范围下拉 | 进度条 | ✗ |
| 12 | TelemetryViewer | #/telemetry | 0 | ✗ (只读) | ✗ | ✗ | ✗ |

**状态处理统一特征**:
- Loading: 大部分使用旋转图标+文字 (RotateCw/RefreshCw/Loader2 animate-spin)
- Error: 红色/暗红背景框，带图标 (AlertTriangle/AlertCircle)，可关闭或自动消失
- Empty: 虚线边框区域 + 灰色图标 + 提示文字
- 6个页面使用 ErrorBoundary 包裹 (SecurityCenter/SystemConfigManager/TenantManager/TokenDashboard/TelemetryViewer/KanbanBoard)
