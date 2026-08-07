#!/usr/bin/env python3
"""Update i18n JSON files with chatbot keys."""

import json
import re

# Read current JSON files
with open('src/i18n/locales/aiworkbench/zh-CN.json', 'r') as f:
    zh_data = json.load(f)

with open('src/i18n/locales/aiworkbench/en.json', 'r') as f:
    en_data = json.load(f)

# Build the chatbot section with all keys
# zh-CN values (Chinese originals)
chatbot_zh = {
    # Tab labels
    "tabPrompt": "角色指令 (Prompt)",
    "tabOntology": "本体与逻辑绑定",
    "tabKnowledge": "RAG 专属知识库",
    "tabGuardrails": "安全拦截护栏",
    "tabPublish": "发布与嵌入 (Embed)",
    
    # Modal
    "modalCreateTitle": "部署全新 AIP Chatbot 实例",
    "modalEditTitle": "修改 Chatbot 架构元参数",
    "modalName": "Chatbot 名称",
    "modalRole": "担当岗位角色",
    "modalDesc": "简述简介",
    "modalModel": "关联的模型后端 (Model Catalog)",
    "cancel": "取消",
    "saveDeploy": "保存部署",
    
    # Placeholders
    "promptPlaceholder": "指定智能体的系统设定、专业知识限制、执行指令契约...",
    "namePlaceholder": "如: AOC 运行协同助手",
    "rolePlaceholder": "如: 航空运行控制中心智能协调助理",
    "descPlaceholder": "说明此 Chatbot 的业务目标和限制范围...",
    "chatPlaceholder": "测试输入问题...（如：'查询 UA102' 或 '改派 UA102 航班延误2小时'）",
    
    # System
    "systemInstructions": "System Instructions (系统提示词角色约束)",
    "configMetadata": "配置元数据",
    "deleteAgent": "注销此智能体",
    "clearHistory": "清空历史",
    
    # Document upload
    "dragUpload": "拖拽规章、SOP、PDF至此上传",
    "uploadHint": "支持 PDF, Markdown, TXT, Excel 等 RAG 知识源 (单个最大 100MB)",
    "startChunkAlign": "一键开始增量切片对齐",
    "vectorAligning": "向量化对齐中...",
    "alignSuccess": "对齐成功",
    "waitVectorize": "等待向量化",
    "unmountFile": "卸载文件",
    
    # Document list
    "mountedDocs": "已挂载的专有知识文件",
    "docSize": "大小:",
    "docChunks": "分块数:",
    "pendingExtract": "待提取",
    "docTypePDF": "PDF 文档",
    "docTypeText": "TEXT 纯文本",
    
    # Ontology
    "ontologyProtocol": "本体对齐映射协议：",
    "ontologyProtocolDesc": " 在此配置 Chatbot 允许调取的 **ObjectType（本体实体）**、**Ontology Actions（写回动作）** 及 **Functions（执行算子）**。AIP 引擎会自动将实体数据转化为上下文，供模型执行工具调用。",
    "objectTypeBindings": "授权读取的对象主体 (ObjectType Bindings)",
    "actionTypeBindings": "授权执行的写回算子 (ActionType Bindings)",
    
    # Object types
    "objFlight": "AviationFlight (航班运行对象)",
    "objPilot": "AviationPilot (机组飞行员档案)",
    "objAirport": "AviationAirport (机场物理档案)",
    "objEquipment": "AviationEquipment (飞机MEL物料)",
    "objFlightDesc": "计划起飞、客座率、延误情况物理表：ds_flights_clean",
    "objPilotDesc": "执飞限制、CAAC执照物理表：ds_pilots_biography",
    "objAirportDesc": "机场跑道、流量监控物理表：ds_airports_metadata",
    "objEquipmentDesc": "飞机故障MEL清单、QAR飞安状态",
    
    # Actions
    "actionReschedule": "重新指派航班与状态修改 (act_reschedule_flight)",
    "actionAssignPilot": "强制接替指派飞行员 (act_assign_pilot)",
    "actionRescheduleDesc": "修改物理 Doris 宽表的航班状态、延误时长参数，由 AOC 签派总监提权。",
    "actionAssignPilotDesc": "修改航班责任机长工号，自动校验 CAAC 121 部执勤超时规定。",
    
    # Guardrails
    "guardrailsInfo": "安全拦截围网 (AIP Guardrails Protection)：",
    "guardrailsInfoDesc": " 强制对输入和输出流量执行即时过滤、隐私（PII）脱敏、幻觉阻断及动作对账阻拦，拦截任何违反 GDPR、CAAC 规章或超出特权的操作指令。",
    "severityBlock": "🛡️ 拦截拦截",
    "severityWarn": "⚠️ 弹窗告警",
    "severityAudit": "📝 仅审计存底",
    
    # Publish
    "publishTitle": "编译部署发布 (Compile & Publish)",
    "publishDesc": "检查本体映射规则约束、RAG 向量特征、幻觉护栏对账单并一键生成编译包。",
    "publishBtn": "验证并重新发布上线",
    "publishCompiling": "编译验证中...",
    "integrationChannels": "多终端应用与集成发布方式 (Integration Channels)",
    
    # Embed
    "embedIframe": "Workshop 网页嵌入 (iFrame)",
    "embedWebComponent": "Web Component (自定义标签)",
    "embedWidgetJson": "AIP Widget JSON",
    "embedIframeDesc": "您可以直接复制如下代码，在 Palantir Workshop 网页中通过 iframe 嵌入本智能交互界面：",
    "embedWcDesc": "采用原生 Shadow DOM 挂载，不破坏外层宿主微应用 CSS 样式，并完美兼容 ECOS AIP 沙箱：",
    "embedJsonDesc": "用于在 Web IDE 或定制前后端进行底层配置同步的 AIP 组件描述元文件：",
    
    # API
    "apiIntegration": "API 外部查询网关 SDK 接口 (API Integration)",
    
    # Sandbox
    "sandboxTitle": "Chatbot 交互调试沙箱 (Sandbox)",
    "sandboxRole": "沙箱模拟用户角色 (RBAC Role)",
    "sandboxScope": "数据上下文 (Grounded Scope)",
    "roleDirector": "王凯 (AOC签派总监 - 满权限)",
    "roleContractor": "张杰 (外部承包商 - 安全隔离限权)",
    "scopeAll": "整个 Aviation Core 本体库",
    "scopeFlightOnly": "限制仅可读取 Flight 数据",
    
    # Thinking trace
    "thinkingTraceLabel": "推理决策链路追踪 (CoT Thinking Trace)",
    
    # Proposal card
    "proposalCard": "对账写回申请卡片 (Ontology Action Request)",
    "proposalApproved": "已授权写回",
    "proposalRejected": "已安全拒绝",
    "proposalPending": "等待签派授权",
    "proposalActionId": "算子标识",
    "proposalTargetTable": "目标物理表",
    "proposalFields": "变动字段/参数 mappings",
    "proposalApprove": "同意物理写入并双向对账",
    "proposalRejectBtn": "安全拒绝",
    
    # Typing
    "typingIndicator": "本体语义对齐及 RAG 召回中...",
    
    # Empty state
    "emptyTitle": "没有就绪的 Chatbot",
    "emptyDesc": "请在左上角点击 + 按钮，创建部署您的首个 Chatbot 对齐工坊实例。",
    
    # Hyperparameters
    "hyperparamsTitle": "大模型超参数精细微调 (Hyperparameters)",
    "temperature": "Temperature (温度值)",
    "topP": "Top P (核采样)",
    "maxTokens": "Max Output Tokens (最大输出字数限制)",
    "tempHint": "较低值使回复稳定，较高值激发创意",
    "topPHint": "多核概率截断阈值",
    
    # Prompt hint
    "promptHint": "提示词可引导模型在限定本体领域内提供专业解答，并强制规定动作提案生成语法规则。",
    
    # Toast messages
    "toastFilesAdded": "已添加 {count} 个知识文档到待切块索引列表",
    "toastRagSyncSuccess": "RAG 文档向量化对齐成功！智能体已习得最新专业领域知识。",
    "toastPublishSuccess": "智能体「{name}」发布成功！最新版本 v1.0.5 已于企业网内下发运行。",
    "toastCreateSuccess": "成功创建全新 AIP Chatbot",
    "toastUpdateSuccess": "已更新 Chatbot 架构参数",
    "toastWritebackSuccess": "Ontology Action 物理写回成功并通过双向核对对账！",
    "toastDeleteSuccess": "已注销 Chatbot 实例",
    "toastRejectAction": "已拒绝该动作授权申请，操作已被安全拦截。",
    "toastClearHistory": "沙箱交互历史已清空",
    "toastExecError": "物理对账执行出错，请重试",
    "toastExecFailed": "授权执行失败: {message}",
    "toastRoleSwitch": "已将沙箱测试身份切换为: {role}",
    
    # Short role names
    "roleDirectorShort": "AOC签派总监",
    "roleContractorShort": "外部承包商",
    
    # Simulation responses
    "welcomeMessage": "### 🤖 {name} (AIP Chatbot Studio Sandbox)\n\n您好！我是工作于航空运行控制中心(AOC)的 **{name}**。  \n我已经基于您配置的 **本体关系** 与 **RAG安全规章知识库** 动态就绪。  \n\n**我的数据访问授权**：\n- 📂 绑定实体：`AviationFlight (航班对象)`, `AviationPilot (飞行员对象)`\n- ⚙️ 绑定动作：`act_reschedule_flight (重新指派航班与状态修改)`\n- 🛡️ 安全合规：已装配 `{guardrailCount} 个防护规则`, 包括 PII 数据动态脱敏与 Action 强人工对账核对。\n\n您可以用以下方式在沙箱中对我进行对话干涉测试：\n1. **多模态实体关联查询**：\"查询 UA102 航班当前运行状态及机长是谁？\"\n2. **敏感个人隐私脱敏**：\"查询张建国机长的社会保障号 (SSN) 执勤底线记录\"\n3. **意图触发写回提案**：\"帮我将 UA102 航班延误状态改派为2小时\"",
    "simContractorBlock": "⚠️ **安全网安全拦截通知 (Sovereign Safety Block)**：\n\n对不起，系统检测到您当前身份为 **[外部承包商 (External Contractor)]**。  \n由于您未持有民航 AOC 核心签派员鉴权角色，受安全中心 **AIP Row-Level & RBAC 隔离护栏** 强制阻断约束：\n- 🔒 禁止读取 `AviationPilot` 与 `AviationFlight` 核心物理实体数据。\n- 🔒 无法使用任何写回指令（Ontology Actions）。\n\n如有排班调配需求，请联系 AOC 签派总监王凯进行授权处理。",
    "simUa102Query": "已为您在航空核心本体(Aviation Core)中成功拉取 **UA102** 航班的最新高精度数据：\n\n### ✈️ 航班运行档案 (ObjectType: Flight)\n- **航班号**: UA102 (芝加哥 ORD → 旧金山 SFO)\n- **计划起飞**: 今日 08:00 (ON_TIME 准点)\n- **执飞机型**: Boeing 737-800 (尾号: **N101UA**)\n- **适航状态**: 【极佳 (Excellent)】（最后一次 C 检维保于 2026-05-12）\n\n### 👨‍✈️ 签派飞行员资质与安全审计 (ObjectType: Pilot)\n- **责任机长**: **张建国** (资质: D-121部机长, 累积安全飞行 8200 小时)\n- **CAAC 资质状态**: ✅ 资质在有效期内\n- **机长社保保障号 (SSN)**: {ssnValue}\n- **机长保底薪资标准**: {payrollValue}\n\n**AIP 决策引擎建议**：\n当前执飞方案完全符合 CAAC 121 部执勤时间规章。芝加哥与旧金山航路上目前无明显对流云团，气象评估结果为适航，推荐维持当前编排方案。",
    "simRescheduleProposal": "我已理解您的操作意图：**因突发天气变化或签派调度要求，需将 UA102 航班更改为延误 2 小时**。\n\n根据系统预设的 **AIP Guardrails 安全审查规范**，由于该操作会物理影响底层 Doris 表 `flights_raw`，系统必须暂挂物理写入，转为**对账写回提案 (Ontology Action Proposal)**。\n\n请您作为 **AOC 签派总监**，在下方授权卡片中核对物理映射并手动确认授权，方可物理落库生效。",
    "simPiiMasked": "⚠️ **敏感数据安全遮蔽阻断警告**：\n\n系统检测到您的指令正在尝试以明文拉取飞行员的核心个人隐私信息（SSN / 保底工资）。\n根据 **AIP Guardrails 隐私合规条例 (gr-pii)**，所有输出中的关键敏感信息已被强制转换为脱敏标记，明文日志已被阻断：\n- 🔒 **机长社会保障号 (SSN)**: `[REDACTED_SSN_NUMBER_BY_GUARDRAIL]`\n- 🔒 **保底工资标准**: `[REDACTED_PAYROLL_DATA_BY_GDPR]`\n\n审计详情已自动生成并发送给安全中心审计总揽大盘。",
    "simPiiUnmasked": "⚠️ **安全免责警告 (PII 护栏未启用)**：\n\n系统检测到您的指令正在尝试拉取敏感隐私信息（SSN / 薪资）。由于您在当前 Chatbot 中**未勾选启用 [gr-pii] 安全护栏**，数据将以明文导出，请妥善保管机密！\n\n- 👨‍✈️ 机长张建国身份证 SSN: `32010619841203XXXX`\n- 💰 责任保底薪酬标准: `¥38,400 / 月`\n\n*提示：为了生产环境合规，建议立即在左侧「安全护栏」设置中启用 PII 脱敏机制！*",
    "simGenericReply": "我是您的 **{name}**。我可以基于您的本地航空本体与 RAG 规章知识库，为您提供无缝、可信的多维航空签派问答：\n\n您可以尝试对我输入以下交互问题测试：\n1. 🔍 **实体查询**：\"UA102 航班今天准点吗？机组配置如何？\"\n2. 🛡️ **脱敏测试**：\"显示张建国机长的社会保障和薪水标准\"\n3. ⚙️ **调配写回**：\"UA102 航班因暴雨改派，延误2小时，通知调度大厅\"",
    "resetWelcome": "### 🤖 {name} (沙箱调试已重置)\n\n请输入问题开始交互测试。你可以对我说：\"查询 UA102 航班运行情况\"",
    
    # Thinking traces
    "thinkingCheckRole": "⚡ 正在解析用户请求，检查 activeUserRole 鉴权身份...",
    "thinkingContractorBlock": "🛡️ 安全拦截：检测到用户角色 [EXTERNAL_CONTRACTOR] 隶属于外部承包方，未被授权读取核心航空实体或执行指令。",
    "thinkingDefenseRule": "⛔ 触发安全防御规则：拒绝执行操作，隐藏物理数据结构。",
    "thinkingExtractQuery": "⚡ 正在提取查询本体目标: Flight (ID: UA102)",
    "thinkingRagSearch": "🔍 运行 RAG 向量检索，召回相关业务上下文: CAAC AOC 运行调度 SOP、延误改派规定...",
    "thinkingPhysicalQuery": "🔗 查询底层物理表：从 Doris `ds_flights_clean` 宽表读取物理记录...",
    "thinkingAssembleCard": "🧬 装配知识卡：关联 Pilot 实体的社会保障号码 (SSN)、保底工资与 CAAC 资质...",
    "thinkingPiiGuard": "🛡️ 安全审查：Guardrail [gr-pii] 已激活，检测 Pilot PII 数据，执行动态遮蔽...",
    "thinkingRescheduleRequest": "⚡ 用户发起对本体数据修改之事务请求。操作目标: 航班重调度与状态变更",
    "thinkingIdentifyAction": "🔍 识别待调用动作算子: `act_reschedule_flight`",
    "thinkingValidateParams": "⚙️ 验证参数白名单契约... delay_minutes: \"120\", new_status: \"DELAYED\"",
    "thinkingApprovalGuard": "🛡️ 安全拦截：发现操作涉及逻辑写回，触发安全护栏 `gr-approval` 人工确认机制。",
    "thinkingPendingProposal": "💾 暂挂物理事务，注册暂挂写回提案 (Pending Proposal)，向前端生成操作授权 Consent 卡片...",
    "thinkingSsnDetect": "⚡ 检测到显式寻求敏感隐私要素 (SSN/Salary) 指令...",
    "thinkingPiiMatch": "🛡️ 触发 PII 护栏匹配... 规则启用状态: [{enabled}]",
    "thinkingGenericIntent": "⚡ 提取通用交互会话意图...",
    "thinkingParseRag": "🧠 解析 RAG 知识库大纲，提供操作指导路线...",
    
    # Audit
    "auditUser": "王凯 (AOC签派总监)",
    "auditPublishSuccess": "编译发布成功",
    "auditPublishDetails": "发布了智能对话工坊的新版本 v1.0.5。绑定关系：Objects={objects}, Actions={actions}",
    "auditReconciliationSuccess": "双向核对成功",
    "auditExecDetails": "人工授权执行动作 act_reschedule_flight: 航班 UA102 成功延误 120 分钟并完成 Doris 双向字段值核算对账。",
    
    # RAG logs
    "ragLogStart": "🔄 [0.0s] 启动 AIP Chatbot RAG 向量增量切片管道...",
    "ragLogRead": "⚡ [0.5s] 读取未同步文档：寻找状态为 [pending] 的知识源...",
    "ragLogExtract": "🔍 [1.0s] 提取文档文字、消除特殊标记并清洗格式 (TEXT/PDF Parser)...",
    "ragLogChunk": "✂️ [1.8s] 按照块大小: 512, 重叠度: 50 运行滑动窗口递归分块(Recursive Character Splitting)...",
    "ragLogEmbed": "🤖 [2.5s] 调用 LLM 嵌入式模型 `text-embedding-004` 计算向量特征值...",
    "ragLogInject": "💾 [3.2s] 将切片成果注入私有 pgvector 特征元数据表，双向对齐底层本体索引...",
    "ragLogComplete": "✅ [4.0s] RAG 知识检索网格重构完成！所有新挂载文档均已标记为已对齐状态。",
    
    # Publish logs
    "pubLogStart": "🚀 [0.0s] 启动 Chatbot Studio 构建流，检查模型及本体连接...",
    "pubLogVerify": "🔍 [0.4s] 验证逻辑绑定契约... Objects: Ok, Actions: 1 Ok, Functions: 1 Ok",
    "pubLogCompile": "🛡️ [0.9s] 编译安全护栏网格，注入列级 Redaction 规则与 row-filter...",
    "pubLogTest": "🧪 [1.5s] 触发集成测试用例: 对话幻觉防御 HAG 自检测试 (置信度阀值: 92%)... ✅ PASSED",
    "pubLogPackage": "📦 [2.1s] 生成独立 Web UI 视图容器，构建 Workshop 桥接接口与元数据 API 终端...",
    "pubLogRegister": "💾 [2.8s] 注册服务版本, 升级编译版本至 v1.0.5 并提交全站对账元数据记录...",
    "pubLogDeploy": "🎉 [3.5s] 部署完成！Chatbot 节点在 Sovereign 边界内正常运行，已在控制台中激活上线。",
    
    # Defaults
    "defaultRole": "AOC 调度大厅专属 AI 助理",
    "defaultDesc": "服务于运行大厅。结合本地本体与 RAG 知识回答各类签派问题。",
    "defaultPrompt": "你是一个工作在运行控制中心 (AOC) 的 AI 协同助理...",
    
    # System report
    "sysReport": "### 📊 完美对账写回双向审计报告 (Bi-directional Physical Validation Report)\n\n**物理更新动作**: {executionDetail}  \n**安全事务标识**: `{transactionHash}`  \n\n**物理-逻辑字段强一致性对账核对 (Read-back Verification Check)**:\n{checkItems}\n\n🎉 底层 Doris 物理 Bronze/Gold 级宽表已成功写回刷新，血缘级联对齐。",
    
    # Proposal
    "proposalUserRole": "签派总监",
    "proposalUserName": "王凯",
    "proposedBy": "Chatbot Sandbox ({name})",
    
    # SSN/Salary
    "ssnRedacted": "`[REDACTED_BY_PII_GUARDRAIL_MASK_SSN]`",
    "ssnReal": "`32010619841203XXXX (真实值: S-2289410)`",
    "salaryRedacted": "`[REDACTED_BY_PII_GUARDRAIL_MASK_SALARY]`",
    "salaryReal": "`¥38,400 / 月 (Base)`",
    
    # Verification
    "verificationItem": "• **[逻辑字段对齐]** `{logicalField}` ➔ `{physicalCol}`: 预估 [{expectedValue}] ↔ 物理读回 [{readbackValue}] ✅ 强一致对齐",
    
    # Embed
    "wcPlaceholder": "UA102 航班今天能准时飞吗？",
    "embedWcComment1": "<!-- 引入 AIP 统一 SDK Web-Components 库 -->",
    "embedWcComment2": "<!-- 在 HTML 任意处悬挂智能对话悬浮按钮 -->",
    
    # API examples  
    "apiExampleQuery": "查询 UA102 航班运行状态",
    "apiExampleQuery2": "查询 UA102 航班运行状态并对齐机组",
    
    # Confirm
    "confirmDelete": "您确定要注销这个 Chatbot 实例吗？此操作不可逆。",
    
    # Query keywords (for includes matching)
    "queryKeyword": "查询",
    "delayKeyword": "延误",
    "rescheduleKeyword": "改期",
    "hourKeyword": "小时",
    "salaryKeyword": "工资",
    "salaryKeyword2": "薪",
    "idCardKeyword": "身份证",
}

# English translations
chatbot_en = {
    "tabPrompt": "Role Instructions (Prompt)",
    "tabOntology": "Ontology & Logic Binding",
    "tabKnowledge": "RAG Knowledge Base",
    "tabGuardrails": "Safety Guardrails",
    "tabPublish": "Publish & Embed",
    
    "modalCreateTitle": "Deploy New AIP Chatbot Instance",
    "modalEditTitle": "Modify Chatbot Architecture Parameters",
    "modalName": "Chatbot Name",
    "modalRole": "Role",
    "modalDesc": "Description",
    "modalModel": "Associated Model Backend (Model Catalog)",
    "cancel": "Cancel",
    "saveDeploy": "Save & Deploy",
    
    "promptPlaceholder": "Specify system settings, domain knowledge limits, execution instruction contracts...",
    "namePlaceholder": "e.g. AOC Operations Assistant",
    "rolePlaceholder": "e.g. Aviation Operations Control Center Intelligent Coordination Assistant",
    "descPlaceholder": "Describe business goals and scope limitations of this Chatbot...",
    "chatPlaceholder": "Test input... (e.g. 'Query UA102' or 'Reschedule UA102 flight delay 2 hours')",
    
    "systemInstructions": "System Instructions (System Prompt Role Constraints)",
    "configMetadata": "Configure Metadata",
    "deleteAgent": "Delete This Agent",
    "clearHistory": "Clear History",
    
    "dragUpload": "Drag regulations, SOPs, PDFs here to upload",
    "uploadHint": "Supports PDF, Markdown, TXT, Excel etc. RAG knowledge sources (max 100MB per file)",
    "startChunkAlign": "Start Incremental Chunk Alignment",
    "vectorAligning": "Vectorizing...",
    "alignSuccess": "Aligned",
    "waitVectorize": "Awaiting Vectorization",
    "unmountFile": "Unmount File",
    
    "mountedDocs": "Mounted Proprietary Knowledge Files",
    "docSize": "Size:",
    "docChunks": "Chunks:",
    "pendingExtract": "Pending",
    "docTypePDF": "PDF Document",
    "docTypeText": "TEXT Plain Text",
    
    "ontologyProtocol": "Ontology Alignment Mapping Protocol: ",
    "ontologyProtocolDesc": " Configure here which **ObjectTypes (ontology entities)**, **Ontology Actions (write-back actions)**, and **Functions (execution operators)** the Chatbot is allowed to invoke. The AIP engine automatically converts entity data into context for the model to perform tool calls.",
    "objectTypeBindings": "Authorized Object Types (ObjectType Bindings)",
    "actionTypeBindings": "Authorized Write-Back Actions (ActionType Bindings)",
    
    "objFlight": "AviationFlight (Flight Operation Object)",
    "objPilot": "AviationPilot (Crew Pilot Profile)",
    "objAirport": "AviationAirport (Airport Physical Profile)",
    "objEquipment": "AviationEquipment (Aircraft MEL Material)",
    "objFlightDesc": "Planned departure, load factor, delay status table: ds_flights_clean",
    "objPilotDesc": "Flight restrictions, CAAC license table: ds_pilots_biography",
    "objAirportDesc": "Airport runway, traffic monitoring table: ds_airports_metadata",
    "objEquipmentDesc": "Aircraft fault MEL list, QAR flight safety status",
    
    "actionReschedule": "Reschedule Flight & Status Modification (act_reschedule_flight)",
    "actionAssignPilot": "Force Reassign Pilot (act_assign_pilot)",
    "actionRescheduleDesc": "Modify flight status and delay parameters in Doris wide table, elevated by AOC Dispatch Director.",
    "actionAssignPilotDesc": "Modify flight captain ID, auto-validate CAAC 121 duty time regulations.",
    
    "guardrailsInfo": "Safety Interception Mesh (AIP Guardrails Protection): ",
    "guardrailsInfoDesc": " Enforce real-time filtering, PII masking, hallucination blocking, and action reconciliation on input/output traffic, intercepting any operations violating GDPR, CAAC regulations, or exceeding privileges.",
    "severityBlock": "🛡️ Block",
    "severityWarn": "⚠️ Warning",
    "severityAudit": "📝 Audit Only",
    
    "publishTitle": "Compile & Publish",
    "publishDesc": "Validate ontology mapping rules, RAG vector features, hallucination guardrail reconciliation and generate build package.",
    "publishBtn": "Validate and Republish",
    "publishCompiling": "Compiling...",
    "integrationChannels": "Multi-Terminal Application Integration Channels",
    
    "embedIframe": "Workshop iFrame Embed",
    "embedWebComponent": "Web Component (Custom Tag)",
    "embedWidgetJson": "AIP Widget JSON",
    "embedIframeDesc": "Copy the code below to embed this intelligent interface via iframe in Palantir Workshop:",
    "embedWcDesc": "Uses native Shadow DOM mounting, does not break outer host micro-app CSS, and fully compatible with ECOS AIP sandbox:",
    "embedJsonDesc": "AIP component description meta-file for underlying configuration sync in Web IDE or custom frontend/backend:",
    
    "apiIntegration": "API External Query Gateway SDK Interface (API Integration)",
    
    "sandboxTitle": "Chatbot Interactive Debug Sandbox (Sandbox)",
    "sandboxRole": "Sandbox Simulated User Role (RBAC Role)",
    "sandboxScope": "Data Context (Grounded Scope)",
    "roleDirector": "Wang Kai (AOC Dispatch Director - Full Access)",
    "roleContractor": "Zhang Jie (External Contractor - Security Isolated Limited Access)",
    "scopeAll": "Entire Aviation Core Ontology",
    "scopeFlightOnly": "Restricted to Flight Data Only",
    
    "thinkingTraceLabel": "Reasoning Decision Chain Trace (CoT Thinking Trace)",
    
    "proposalCard": "Reconciliation Write-Back Request Card (Ontology Action Request)",
    "proposalApproved": "Authorized Write-Back",
    "proposalRejected": "Safely Rejected",
    "proposalPending": "Awaiting Dispatch Authorization",
    "proposalActionId": "Action ID",
    "proposalTargetTable": "Target Physical Table",
    "proposalFields": "Change Fields/Parameters Mappings",
    "proposalApprove": "Approve Physical Write & Bi-Directional Reconciliation",
    "proposalRejectBtn": "Reject Safely",
    
    "typingIndicator": "Ontology alignment and RAG retrieval in progress...",
    
    "emptyTitle": "No Ready Chatbot",
    "emptyDesc": "Click the + button in the top left corner to create and deploy your first Chatbot alignment workshop instance.",
    
    "hyperparamsTitle": "LLM Hyperparameter Fine-Tuning (Hyperparameters)",
    "temperature": "Temperature",
    "topP": "Top P (Nucleus Sampling)",
    "maxTokens": "Max Output Tokens",
    "tempHint": "Lower values produce stable responses, higher values stimulate creativity",
    "topPHint": "Multi-core probability cutoff threshold",
    
    "promptHint": "The prompt guides the model to provide professional answers within the defined ontology domain and enforces action proposal generation syntax rules.",
    
    "toastFilesAdded": "Added {count} knowledge document(s) to pending chunk index list",
    "toastRagSyncSuccess": "RAG document vector alignment successful! Agent has learned the latest domain knowledge.",
    "toastPublishSuccess": "Agent \"{name}\" published successfully! Latest version v1.0.5 has been deployed across the enterprise network.",
    "toastCreateSuccess": "Successfully created new AIP Chatbot",
    "toastUpdateSuccess": "Chatbot architecture parameters updated",
    "toastWritebackSuccess": "Ontology Action physical write-back succeeded with bi-directional reconciliation!",
    "toastDeleteSuccess": "Chatbot instance decommissioned",
    "toastRejectAction": "Action authorization request rejected, operation safely intercepted.",
    "toastClearHistory": "Sandbox interaction history cleared",
    "toastExecError": "Physical reconciliation execution error, please retry",
    "toastExecFailed": "Authorization execution failed: {message}",
    "toastRoleSwitch": "Sandbox test identity switched to: {role}",
    
    "roleDirectorShort": "AOC Dispatch Director",
    "roleContractorShort": "External Contractor",
    
    "welcomeMessage": "### 🤖 {name} (AIP Chatbot Studio Sandbox)\n\nHello! I'm **{name}** working at the Aviation Operations Control Center (AOC).  \nI'm dynamically ready based on your configured **ontology relationships** and **RAG safety regulation knowledge base**.  \n\n**My Data Access Authorization**:\n- 📂 Bound Entities: `AviationFlight (Flight Object)`, `AviationPilot (Pilot Object)`\n- ⚙️ Bound Actions: `act_reschedule_flight (Reschedule Flight & Status Modification)`\n- 🛡️ Safety Compliance: Equipped with `{guardrailCount} guardrail rules`, including PII data dynamic masking and Action human reconciliation verification.\n\nYou can interact with me in the sandbox using the following approaches:\n1. **Multi-Modal Entity Association Query**: \"Query UA102 flight current status and who is the captain?\"\n2. **Sensitive PII Masking**: \"Query Captain Zhang Jianguo's SSN duty floor records\"\n3. **Intent-Triggered Write-Back Proposal**: \"Help me reschedule UA102 flight delay to 2 hours\"",
    "simContractorBlock": "⚠️ **Safety Net Security Intercept Notification (Sovereign Safety Block)**:\n\nSorry, the system has detected your current identity as **[External Contractor]**.  \nSince you do not hold the civil aviation AOC core dispatcher authorization role, you are blocked by the Security Center **AIP Row-Level & RBAC isolation guardrails**:\n- 🔒 Prohibited from reading `AviationPilot` and `AviationFlight` core physical entity data.\n- 🔒 Cannot use any write-back instructions (Ontology Actions).\n\nFor scheduling needs, please contact AOC Dispatch Director Wang Kai for authorization.",
    "simUa102Query": "Successfully retrieved the latest high-precision data for **UA102** flight in the Aviation Core Ontology:\n\n### ✈️ Flight Operation Profile (ObjectType: Flight)\n- **Flight Number**: UA102 (Chicago ORD → San Francisco SFO)\n- **Scheduled Departure**: Today 08:00 (ON_TIME)\n- **Aircraft Type**: Boeing 737-800 (Tail: **N101UA**)\n- **Airworthiness Status**: [Excellent] (Last C-check maintenance on 2026-05-12)\n\n### 👨‍✈️ Dispatched Pilot Qualifications & Safety Audit (ObjectType: Pilot)\n- **Captain**: **Zhang Jianguo** (Qualification: D-121 Captain, 8200 hours cumulative safe flight)\n- **CAAC Qualification Status**: ✅ Valid\n- **Captain SSN**: {ssnValue}\n- **Captain Base Salary**: {payrollValue}\n\n**AIP Decision Engine Recommendation**:\nCurrent flight plan fully complies with CAAC Part 121 duty time regulations. No significant convective clouds on Chicago-San Francisco route. Weather assessment is airworthy. Recommend maintaining current schedule.",
    "simRescheduleProposal": "I understand your operational intent: **Due to sudden weather changes or dispatch scheduling requirements, UA102 flight needs to be changed to a 2-hour delay**.\n\nAccording to the system's preset **AIP Guardrails Safety Review Specifications**, since this operation will physically affect the underlying Doris table `flights_raw`, the system must suspend physical writes and convert to a **Reconciliation Write-Back Proposal (Ontology Action Proposal)**.\n\nAs the **AOC Dispatch Director**, please verify the physical mapping in the authorization card below and manually confirm authorization to enable physical database commit.",
    "simPiiMasked": "⚠️ **Sensitive Data Security Masking Block Warning**:\n\nThe system has detected your instruction attempting to pull pilot core personal privacy information (SSN / Base Salary) in plaintext.\nAccording to **AIP Guardrails Privacy Compliance Regulations (gr-pii)**, all critical sensitive information in the output has been forcibly converted to masking tokens, and plaintext logs have been blocked:\n- 🔒 **Captain SSN**: `[REDACTED_SSN_NUMBER_BY_GUARDRAIL]`\n- 🔒 **Base Salary Standard**: `[REDACTED_PAYROLL_DATA_BY_GDPR]`\n\nAudit details have been automatically generated and sent to the Security Center Audit Dashboard.",
    "simPiiUnmasked": "⚠️ **Security Disclaimer Warning (PII Guardrail Not Enabled)**:\n\nThe system has detected your instruction attempting to pull sensitive privacy information (SSN / Salary). Since you have **not enabled [gr-pii] safety guardrail** in the current Chatbot, data will be exported in plaintext. Please keep confidential information secure!\n\n- 👨‍✈️ Captain Zhang Jianguo ID SSN: `32010619841203XXXX`\n- 💰 Base Salary Standard: `¥38,400 / month`\n\n*Tip: For production environment compliance, it is recommended to immediately enable PII masking in the left 'Safety Guardrails' settings!*",
    "simGenericReply": "I am your **{name}**. Based on your local aviation ontology and RAG regulation knowledge base, I can provide seamless, trustworthy multi-dimensional aviation dispatch Q&A:\n\nYou can try entering the following interactive test questions:\n1. 🔍 **Entity Query**: \"Is UA102 flight on time today? How is the crew configuration?\"\n2. 🛡️ **Masking Test**: \"Show Captain Zhang Jianguo's social security and salary standards\"\n3. ⚙️ **Dispatch Write-Back**: \"UA102 flight rescheduled due to heavy rain, delayed 2 hours, notify dispatch center\"",
    "resetWelcome": "### 🤖 {name} (Sandbox Debug Reset)\n\nPlease enter questions to start interactive testing. You can say to me: \"Query UA102 flight operation status\"",
    
    "thinkingCheckRole": "⚡ Parsing user request, checking activeUserRole authorization identity...",
    "thinkingContractorBlock": "🛡️ Security Intercept: Detected user role [EXTERNAL_CONTRACTOR] belongs to external contractor, not authorized to read core aviation entities or execute commands.",
    "thinkingDefenseRule": "⛔ Triggering security defense rule: Denying operation, hiding physical data structures.",
    "thinkingExtractQuery": "⚡ Extracting query ontology target: Flight (ID: UA102)",
    "thinkingRagSearch": "🔍 Running RAG vector retrieval, recalling relevant business context: CAAC AOC Operations SOP, delay rescheduling regulations...",
    "thinkingPhysicalQuery": "🔗 Querying underlying physical table: reading physical records from Doris `ds_flights_clean` wide table...",
    "thinkingAssembleCard": "🧬 Assembling knowledge card: linking Pilot entity SSN, base salary, and CAAC qualifications...",
    "thinkingPiiGuard": "🛡️ Security Review: Guardrail [gr-pii] activated, detecting Pilot PII data, executing dynamic masking...",
    "thinkingRescheduleRequest": "⚡ User initiated transactional request to modify ontology data. Target: Flight rescheduling & status change",
    "thinkingIdentifyAction": "🔍 Identifying action operator to invoke: `act_reschedule_flight`",
    "thinkingValidateParams": "⚙️ Validating parameter whitelist contract... delay_minutes: \"120\", new_status: \"DELAYED\"",
    "thinkingApprovalGuard": "🛡️ Security Intercept: Operation involves logical write-back, triggering safety guardrail `gr-approval` human confirmation mechanism.",
    "thinkingPendingProposal": "💾 Suspending physical transaction, registering pending write-back proposal (Pending Proposal), generating operation authorization Consent card for frontend...",
    "thinkingSsnDetect": "⚡ Detected explicit sensitive privacy element (SSN/Salary) instruction...",
    "thinkingPiiMatch": "🛡️ Triggering PII guardrail match... Rule enabled status: [{enabled}]",
    "thinkingGenericIntent": "⚡ Extracting general interactive conversation intent...",
    "thinkingParseRag": "🧠 Parsing RAG knowledge base outline, providing operational guidance roadmap...",
    
    "auditUser": "Wang Kai (AOC Dispatch Director)",
    "auditPublishSuccess": "Compile and publish successful",
    "auditPublishDetails": "Published new version v1.0.5 of the intelligent workshop. Bindings: Objects={objects}, Actions={actions}",
    "auditReconciliationSuccess": "Bi-directional reconciliation successful",
    "auditExecDetails": "Manually authorized action act_reschedule_flight: Flight UA102 successfully delayed 120 minutes with Doris bi-directional field value reconciliation completed.",
    
    "ragLogStart": "🔄 [0.0s] Starting AIP Chatbot RAG vector incremental chunk pipeline...",
    "ragLogRead": "⚡ [0.5s] Reading unsynchronized documents: searching for knowledge sources with [pending] status...",
    "ragLogExtract": "🔍 [1.0s] Extracting document text, removing special markers and cleaning format (TEXT/PDF Parser)...",
    "ragLogChunk": "✂️ [1.8s] Running sliding window recursive character splitting with chunk size: 512, overlap: 50...",
    "ragLogEmbed": "🤖 [2.5s] Calling LLM embedding model `text-embedding-004` to compute vector features...",
    "ragLogInject": "💾 [3.2s] Injecting chunk results into private pgvector feature metadata table, bi-directionally aligning underlying ontology index...",
    "ragLogComplete": "✅ [4.0s] RAG knowledge retrieval grid reconstruction complete! All newly mounted documents marked as aligned.",
    
    "pubLogStart": "🚀 [0.0s] Starting Chatbot Studio build flow, checking model and ontology connections...",
    "pubLogVerify": "🔍 [0.4s] Validating logic binding contract... Objects: Ok, Actions: 1 Ok, Functions: 1 Ok",
    "pubLogCompile": "🛡️ [0.9s] Compiling safety guardrail mesh, injecting column-level Redaction rules and row-filter...",
    "pubLogTest": "🧪 [1.5s] Triggering integration test case: dialogue hallucination defense HAG self-test (confidence threshold: 92%)... ✅ PASSED",
    "pubLogPackage": "📦 [2.1s] Generating independent Web UI view container, building Workshop bridge interface and metadata API endpoints...",
    "pubLogRegister": "💾 [2.8s] Registering service version, upgrading build version to v1.0.5 and submitting site-wide reconciliation metadata records...",
    "pubLogDeploy": "🎉 [3.5s] Deployment complete! Chatbot node running normally within Sovereign boundary, activated and online in console.",
    
    "defaultRole": "AOC Dispatch Center Exclusive AI Assistant",
    "defaultDesc": "Serves the operations center. Answers various dispatch questions using local ontology and RAG knowledge.",
    "defaultPrompt": "You are an AI collaborative assistant working at the Operations Control Center (AOC)...",
    
    "sysReport": "### 📊 Perfect Reconciliation Write-Back Bi-Directional Audit Report (Bi-directional Physical Validation Report)\n\n**Physical Update Action**: {executionDetail}  \n**Security Transaction ID**: `{transactionHash}`  \n\n**Physical-Logical Field Strong Consistency Reconciliation Check (Read-back Verification Check)**:\n{checkItems}\n\n🎉 Underlying Doris physical Bronze/Gold-level wide table has been successfully written back and refreshed, lineage cascade aligned.",
    
    "proposalUserRole": "Dispatch Director",
    "proposalUserName": "Wang Kai",
    "proposedBy": "Chatbot Sandbox ({name})",
    
    "ssnRedacted": "`[REDACTED_BY_PII_GUARDRAIL_MASK_SSN]`",
    "ssnReal": "`32010619841203XXXX (Real: S-2289410)`",
    "salaryRedacted": "`[REDACTED_BY_PII_GUARDRAIL_MASK_SALARY]`",
    "salaryReal": "`¥38,400 / month (Base)`",
    
    "verificationItem": "• **[Logical Field Alignment]** `{logicalField}` ➔ `{physicalCol}`: Expected [{expectedValue}] ↔ Physical Readback [{readbackValue}] ✅ Strongly Consistent Alignment",
    
    "wcPlaceholder": "Can UA102 flight depart on time today?",
    "embedWcComment1": "<!-- Include AIP Unified SDK Web-Components Library -->",
    "embedWcComment2": "<!-- Mount intelligent conversation floating button anywhere in HTML -->",
    
    "apiExampleQuery": "Query UA102 flight operation status",
    "apiExampleQuery2": "Query UA102 flight operation status and align crew",
    
    "confirmDelete": "Are you sure you want to decommission this Chatbot instance? This action is irreversible.",
    
    "queryKeyword": "query",
    "delayKeyword": "delay",
    "rescheduleKeyword": "reschedule",
    "hourKeyword": "hour",
    "salaryKeyword": "salary",
    "salaryKeyword2": "pay",
    "idCardKeyword": "id",
}

# Add chatbot section to both JSON files
zh_data['chatbot'] = chatbot_zh
en_data['chatbot'] = chatbot_en

# Write files
with open('src/i18n/locales/aiworkbench/zh-CN.json', 'w') as f:
    json.dump(zh_data, f, ensure_ascii=False, indent=2)

with open('src/i18n/locales/aiworkbench/en.json', 'w') as f:
    json.dump(en_data, f, ensure_ascii=False, indent=2)

print(f"Updated zh-CN.json with {len(chatbot_zh)} chatbot keys")
print(f"Updated en.json with {len(chatbot_en)} chatbot keys")
print("DONE")
