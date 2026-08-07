#!/usr/bin/env python3
"""
Migrate ChatbotStudioView.tsx: replace all hardcoded Chinese with t() calls.
Also collect all keys for i18n JSON files.
"""

import re
import json
import sys

FILE_PATH = 'src/pages/aiworkbench/ChatbotStudioView.tsx'
ZH_JSON_PATH = 'src/i18n/locales/aiworkbench/zh-CN.json'
EN_JSON_PATH = 'src/i18n/locales/aiworkbench/en.json'

# Read the file
with open(FILE_PATH, 'r') as f:
    content = f.read()

original_lines = content.split('\n')
total_lines = len(original_lines)

# ============================================================
# STEP 0: Add import and hook
# ============================================================

# Add useLanguage import after useTheme import
content = content.replace(
    "import { useTheme } from '../../components/ThemeContext';",
    "import { useTheme } from '../../components/ThemeContext';\nimport { useLanguage } from '../../components/LanguageContext';"
)

# Add t hook after styles
content = content.replace(
    "  const { styles } = useTheme();",
    "  const { styles } = useTheme();\n  const { t } = useLanguage();"
)

# ============================================================
# STEP 1: Collect all unique Chinese strings and assign keys
# ============================================================
# We'll build a mapping: {chinese_text: i18n_key}

key_counter = [0]  # mutable counter

def make_key(base):
    """Generate a unique i18n key"""
    key_counter[0] += 1
    # Convert Chinese to reasonable ascii key
    return f"aiworkbench.chatbot.{base}"

# I'll categorize the replacements by line context

# ============================================================
# STEP 2: Replace system strings
# ============================================================

replacements = [
    # --- TAB LABELS (line 639-643) ---
    ("'角色指令 (Prompt)'", "t('aiworkbench.chatbot.tabPrompt')"),
    ("'本体与逻辑绑定'", "t('aiworkbench.chatbot.tabOntology')"),
    ("'RAG 专属知识库'", "t('aiworkbench.chatbot.tabKnowledge')"),
    ("'安全拦截护栏'", "t('aiworkbench.chatbot.tabGuardrails')"),
    ("'发布与嵌入 (Embed)'", "t('aiworkbench.chatbot.tabPublish')"),
    
    # --- Metadata modal labels (lines 1505, 1517, 1529, 1541, 1551, 1569, 1575) ---
    ("'部署全新 AIP Chatbot 实例'", "t('aiworkbench.chatbot.modalCreateTitle')"),
    ("'修改 Chatbot 架构元参数'", "t('aiworkbench.chatbot.modalEditTitle')"),
    ("Chatbot 名称", "t('aiworkbench.chatbot.modalName')"),
    ("担当岗位角色", "t('aiworkbench.chatbot.modalRole')"),
    ("简述简介", "t('aiworkbench.chatbot.modalDesc')"),
    ("关联的模型后端 (Model Catalog)", "t('aiworkbench.chatbot.modalModel')"),
    ("取消", "t('aiworkbench.chatbot.cancel')"),
    ("保存部署", "t('aiworkbench.chatbot.saveDeploy')"),
    
    # --- Placeholders ---
    ("指定智能体的系统设定、专业知识限制、执行指令契约...", "t('aiworkbench.chatbot.promptPlaceholder')"),
    ("如: AOC 运行协同助手", "t('aiworkbench.chatbot.namePlaceholder')"),
    ("如: 航空运行控制中心智能协调助理", "t('aiworkbench.chatbot.rolePlaceholder')"),
    ("说明此 Chatbot 的业务目标和限制范围...", "t('aiworkbench.chatbot.descPlaceholder')"),
    ("测试输入问题...（如：'查询 UA102' 或 '改派 UA102 航班延误2小时'）", "t('aiworkbench.chatbot.chatPlaceholder')"),
    
    # --- System prompt instructions (line 670) ---
    ("System Instructions (系统提示词角色约束)", "t('aiworkbench.chatbot.systemInstructions')"),
    
    # --- Button texts ---
    ("配置元数据", "t('aiworkbench.chatbot.configMetadata')"),
    ("注销此智能体", "t('aiworkbench.chatbot.deleteAgent')"),
    ("清空历史", "t('aiworkbench.chatbot.clearHistory')"),
    
    # --- Document upload ---
    ("拖拽规章、SOP、PDF至此上传", "t('aiworkbench.chatbot.dragUpload')"),
    ("支持 PDF, Markdown, TXT, Excel 等 RAG 知识源 (单个最大 100MB)", "t('aiworkbench.chatbot.uploadHint')"),
    ("一键开始增量切片对齐", "t('aiworkbench.chatbot.startChunkAlign')"),
    ("向量化对齐中...", "t('aiworkbench.chatbot.vectorAligning')"),
    ("对齐成功", "t('aiworkbench.chatbot.alignSuccess')"),
    ("等待向量化", "t('aiworkbench.chatbot.waitVectorize')"),
    ("卸载文件", "t('aiworkbench.chatbot.unmountFile')"),
    
    # --- Document list labels ---
    ("已挂载的专有知识文件", "t('aiworkbench.chatbot.mountedDocs')"),
    ("大小:", "t('aiworkbench.chatbot.docSize')"),
    ("分块数:", "t('aiworkbench.chatbot.docChunks')"),
    ("待提取", "t('aiworkbench.chatbot.pendingExtract')"),
    
    # --- Knowledge tab doc types ---
    ("PDF 文档", "t('aiworkbench.chatbot.docTypePDF')"),
    ("TEXT 纯文本", "t('aiworkbench.chatbot.docTypeText')"),
    
    # --- Ontology tab info (line 753) ---
    ("本体对齐映射协议：", "t('aiworkbench.chatbot.ontologyProtocol')"),
    
    # --- Object type labels (line 761) ---
    ("授权读取的对象主体 (ObjectType Bindings)", "t('aiworkbench.chatbot.objectTypeBindings')"),
    
    # --- Action type labels (line 803) ---
    ("授权执行的写回算子 (ActionType Bindings)", "t('aiworkbench.chatbot.actionTypeBindings')"),
    
    # --- Guardrails tab info (line 969) ---
    ("安全拦截围网 (AIP Guardrails Protection)：", "t('aiworkbench.chatbot.guardrailsInfo')"),
    
    # --- Guardrails severity labels (line 1023) ---
    ("🛡️ 拦截拦截", "t('aiworkbench.chatbot.severityBlock')"),
    ("⚠️ 弹窗告警", "t('aiworkbench.chatbot.severityWarn')"),
    ("📝 仅审计存底", "t('aiworkbench.chatbot.severityAudit')"),
    
    # --- Publish tab ---
    ("编译部署发布 (Compile & Publish)", "t('aiworkbench.chatbot.publishTitle')"),
    ("检查本体映射规则约束、RAG 向量特征、幻觉护栏对账单并一键生成编译包。", "t('aiworkbench.chatbot.publishDesc')"),
    ("验证并重新发布上线", "t('aiworkbench.chatbot.publishBtn')"),
    ("编译验证中...", "t('aiworkbench.chatbot.publishCompiling')"),
    
    # --- Integration channels (line 1102) ---
    ("多终端应用与集成发布方式 (Integration Channels)", "t('aiworkbench.chatbot.integrationChannels')"),
    
    # --- Embed tab labels ---
    ("Workshop 网页嵌入 (iFrame)", "t('aiworkbench.chatbot.embedIframe')"),
    ("Web Component (自定义标签)", "t('aiworkbench.chatbot.embedWebComponent')"),
    ("AIP Widget JSON", "t('aiworkbench.chatbot.embedWidgetJson')"),
    
    # --- Embed descriptions ---
    ("您可以直接复制如下代码，在 Palantir Workshop 网页中通过 iframe 嵌入本智能交互界面：", "t('aiworkbench.chatbot.embedIframeDesc')"),
    ("采用原生 Shadow DOM 挂载，不破坏外层宿主微应用 CSS 样式，并完美兼容 ECOS AIP 沙箱：", "t('aiworkbench.chatbot.embedWcDesc')"),
    ("用于在 Web IDE 或定制前后端进行底层配置同步的 AIP 组件描述元文件：", "t('aiworkbench.chatbot.embedJsonDesc')"),
    
    # --- API section ---
    ("API 外部查询网关 SDK 接口 (API Integration)", "t('aiworkbench.chatbot.apiIntegration')"),
    
    # --- Sandbox header (line 1261) ---
    ("Chatbot 交互调试沙箱 (Sandbox)", "t('aiworkbench.chatbot.sandboxTitle')"),
    
    # --- Sandbox role labels (line 1290) ---
    ("沙箱模拟用户角色 (RBAC Role)", "t('aiworkbench.chatbot.sandboxRole')"),
    
    # --- Sandbox context labels (line 1308) ---
    ("数据上下文 (Grounded Scope)", "t('aiworkbench.chatbot.sandboxScope')"),
    
    # --- Sandbox select options ---
    ("王凯 (AOC签派总监 - 满权限)", "t('aiworkbench.chatbot.roleDirector')"),
    ("张杰 (外部承包商 - 安全隔离限权)", "t('aiworkbench.chatbot.roleContractor')"),
    ("整个 Aviation Core 本体库", "t('aiworkbench.chatbot.scopeAll')"),
    ("限制仅可读取 Flight 数据", "t('aiworkbench.chatbot.scopeFlightOnly')"),
    
    # --- Thinking trace label (line 1370) ---
    ("推理决策链路追踪 (CoT Thinking Trace)", "t('aiworkbench.chatbot.thinkingTraceLabel')"),
    
    # --- Action proposal card ---
    ("对账写回申请卡片 (Ontology Action Request)", "t('aiworkbench.chatbot.proposalCard')"),
    ("已授权写回", "t('aiworkbench.chatbot.proposalApproved')"),
    ("已安全拒绝", "t('aiworkbench.chatbot.proposalRejected')"),
    ("等待签派授权", "t('aiworkbench.chatbot.proposalPending')"),
    ("算子标识", "t('aiworkbench.chatbot.proposalActionId')"),
    ("目标物理表", "t('aiworkbench.chatbot.proposalTargetTable')"),
    ("变动字段/参数 mappings", "t('aiworkbench.chatbot.proposalFields')"),
    ("同意物理写入并双向对账", "t('aiworkbench.chatbot.proposalApprove')"),
    ("安全拒绝", "t('aiworkbench.chatbot.proposalRejectBtn')"),
    
    # --- Typing indicator ---
    ("本体语义对齐及 RAG 召回中...", "t('aiworkbench.chatbot.typingIndicator')"),
    
    # --- Empty state (lines 1492-1493) ---
    ("没有就绪的 Chatbot", "t('aiworkbench.chatbot.emptyTitle')"),
    ("请在左上角点击 + 按钮，创建部署您的首个 Chatbot 对齐工坊实例。", "t('aiworkbench.chatbot.emptyDesc')"),
    
    # --- Hyperparameters panel (line 686) ---
    ("大模型超参数精细微调 (Hyperparameters)", "t('aiworkbench.chatbot.hyperparamsTitle')"),
    ("Temperature (温度值)", "t('aiworkbench.chatbot.temperature')"),
    ("Top P (核采样)", "t('aiworkbench.chatbot.topP')"),
    ("Max Output Tokens (最大输出字数限制)", "t('aiworkbench.chatbot.maxTokens')"),
    ("较低值使回复稳定，较高值激发创意", "t('aiworkbench.chatbot.tempHint')"),
    ("多核概率截断阈值", "t('aiworkbench.chatbot.topPHint')"),
    
    # --- Prompt hint (line 678-679) ---
    ("提示词可引导模型在限定本体领域内提供专业解答，并强制规定动作提案生成语法规则。", "t('aiworkbench.chatbot.promptHint')"),
    
    # --- Object type data (line 765-768) ---
    ("AviationFlight (航班运行对象)", "t('aiworkbench.chatbot.objFlight')"),
    ("AviationPilot (机组飞行员档案)", "t('aiworkbench.chatbot.objPilot')"),
    ("AviationAirport (机场物理档案)", "t('aiworkbench.chatbot.objAirport')"),
    ("AviationEquipment (飞机MEL物料)", "t('aiworkbench.chatbot.objEquipment')"),
    ("计划起飞、客座率、延误情况物理表：ds_flights_clean", "t('aiworkbench.chatbot.objFlightDesc')"),
    ("执飞限制、CAAC执照物理表：ds_pilots_biography", "t('aiworkbench.chatbot.objPilotDesc')"),
    ("机场跑道、流量监控物理表：ds_airports_metadata", "t('aiworkbench.chatbot.objAirportDesc')"),
    ("飞机故障MEL清单、QAR飞安状态", "t('aiworkbench.chatbot.objEquipmentDesc')"),
    
    # --- Action data (line 807-808) ---
    ("重新指派航班与状态修改 (act_reschedule_flight)", "t('aiworkbench.chatbot.actionReschedule')"),
    ("强制接替指派飞行员 (act_assign_pilot)", "t('aiworkbench.chatbot.actionAssignPilot')"),
    ("修改物理 Doris 宽表的航班状态、延误时长参数，由 AOC 签派总监提权。", "t('aiworkbench.chatbot.actionRescheduleDesc')"),
    ("修改航班责任机长工号，自动校验 CAAC 121 部执勤超时规定。", "t('aiworkbench.chatbot.actionAssignPilotDesc')"),
]

# ============================================================
# Build the English translations
# ============================================================
en_translations = {
    'tabPrompt': 'Role Instructions (Prompt)',
    'tabOntology': 'Ontology & Logic Binding',
    'tabKnowledge': 'RAG Knowledge Base',
    'tabGuardrails': 'Safety Guardrails',
    'tabPublish': 'Publish & Embed',
    
    'modalCreateTitle': 'Deploy New AIP Chatbot Instance',
    'modalEditTitle': 'Modify Chatbot Architecture Parameters',
    'modalName': 'Chatbot Name',
    'modalRole': 'Role',
    'modalDesc': 'Description',
    'modalModel': 'Associated Model Backend (Model Catalog)',
    'cancel': 'Cancel',
    'saveDeploy': 'Save & Deploy',
    
    'promptPlaceholder': 'Specify system settings, domain knowledge limits, execution instruction contracts...',
    'namePlaceholder': 'e.g. AOC Operations Assistant',
    'rolePlaceholder': 'e.g. Aviation Operations Control Center Intelligent Coordination Assistant',
    'descPlaceholder': 'Describe business goals and scope limitations of this Chatbot...',
    'chatPlaceholder': "Test input... (e.g. 'Query UA102' or 'Reschedule UA102 flight delay 2 hours')",
    
    'systemInstructions': 'System Instructions (System Prompt Role Constraints)',
    'configMetadata': 'Configure Metadata',
    'deleteAgent': 'Delete This Agent',
    'clearHistory': 'Clear History',
    
    'dragUpload': 'Drag regulations, SOPs, PDFs here to upload',
    'uploadHint': 'Supports PDF, Markdown, TXT, Excel etc. RAG knowledge sources (max 100MB per file)',
    'startChunkAlign': 'Start Incremental Chunk Alignment',
    'vectorAligning': 'Vectorizing...',
    'alignSuccess': 'Aligned',
    'waitVectorize': 'Awaiting Vectorization',
    'unmountFile': 'Unmount File',
    
    'mountedDocs': 'Mounted Proprietary Knowledge Files',
    'docSize': 'Size:',
    'docChunks': 'Chunks:',
    'pendingExtract': 'Pending',
    
    'docTypePDF': 'PDF Document',
    'docTypeText': 'TEXT Plain Text',
    
    'ontologyProtocol': 'Ontology Alignment Mapping Protocol: ',
    'objectTypeBindings': 'Authorized Object Types (ObjectType Bindings)',
    'actionTypeBindings': 'Authorized Write-Back Actions (ActionType Bindings)',
    
    'guardrailsInfo': 'Safety Interception Mesh (AIP Guardrails Protection): ',
    'severityBlock': '🛡️ Block',
    'severityWarn': '⚠️ Warning',
    'severityAudit': '📝 Audit Only',
    
    'publishTitle': 'Compile & Publish',
    'publishDesc': 'Validate ontology mapping rules, RAG vector features, hallucination guardrail reconciliation and generate build package.',
    'publishBtn': 'Validate and Republish',
    'publishCompiling': 'Compiling...',
    
    'integrationChannels': 'Multi-Terminal Application Integration Channels',
    'embedIframe': 'Workshop iFrame Embed',
    'embedWebComponent': 'Web Component (Custom Tag)',
    'embedWidgetJson': 'AIP Widget JSON',
    'embedIframeDesc': 'Copy the code below to embed this intelligent interface via iframe in Palantir Workshop:',
    'embedWcDesc': 'Uses native Shadow DOM mounting, does not break outer host micro-app CSS, and fully compatible with ECOS AIP sandbox:',
    'embedJsonDesc': 'AIP component description meta-file for underlying configuration sync in Web IDE or custom frontend/backend:',
    
    'apiIntegration': 'API External Query Gateway SDK Interface (API Integration)',
    
    'sandboxTitle': 'Chatbot Interactive Debug Sandbox (Sandbox)',
    'sandboxRole': 'Sandbox Simulated User Role (RBAC Role)',
    'sandboxScope': 'Data Context (Grounded Scope)',
    'roleDirector': 'Wang Kai (AOC Dispatch Director - Full Access)',
    'roleContractor': 'Zhang Jie (External Contractor - Security Isolated Limited Access)',
    'scopeAll': 'Entire Aviation Core Ontology',
    'scopeFlightOnly': 'Restricted to Flight Data Only',
    
    'thinkingTraceLabel': 'Reasoning Decision Chain Trace (CoT Thinking Trace)',
    
    'proposalCard': 'Reconciliation Write-Back Request Card (Ontology Action Request)',
    'proposalApproved': 'Authorized Write-Back',
    'proposalRejected': 'Safely Rejected',
    'proposalPending': 'Awaiting Dispatch Authorization',
    'proposalActionId': 'Action ID',
    'proposalTargetTable': 'Target Physical Table',
    'proposalFields': 'Change Fields/Parameters Mappings',
    'proposalApprove': 'Approve Physical Write & Bi-Directional Reconciliation',
    'proposalRejectBtn': 'Reject Safely',
    
    'typingIndicator': 'Ontology alignment and RAG retrieval in progress...',
    
    'emptyTitle': 'No Ready Chatbot',
    'emptyDesc': 'Click the + button in the top left corner to create and deploy your first Chatbot alignment workshop instance.',
    
    'hyperparamsTitle': 'LLM Hyperparameter Fine-Tuning (Hyperparameters)',
    'temperature': 'Temperature',
    'topP': 'Top P (Nucleus Sampling)',
    'maxTokens': 'Max Output Tokens',
    'tempHint': 'Lower values produce stable responses, higher values stimulate creativity',
    'topPHint': 'Multi-core probability cutoff threshold',
    
    'promptHint': 'The prompt guides the model to provide professional answers within the defined ontology domain and enforces action proposal generation syntax rules.',
    
    'objFlight': 'AviationFlight (Flight Operation Object)',
    'objPilot': 'AviationPilot (Crew Pilot Profile)',
    'objAirport': 'AviationAirport (Airport Physical Profile)',
    'objEquipment': 'AviationEquipment (Aircraft MEL Material)',
    'objFlightDesc': 'Planned departure, load factor, delay status table: ds_flights_clean',
    'objPilotDesc': 'Flight restrictions, CAAC license table: ds_pilots_biography',
    'objAirportDesc': 'Airport runway, traffic monitoring table: ds_airports_metadata',
    'objEquipmentDesc': 'Aircraft fault MEL list, QAR flight safety status',
    
    'actionReschedule': 'Reschedule Flight & Status Modification (act_reschedule_flight)',
    'actionAssignPilot': 'Force Reassign Pilot (act_assign_pilot)',
    'actionRescheduleDesc': 'Modify flight status and delay parameters in Doris wide table, elevated by AOC Dispatch Director.',
    'actionAssignPilotDesc': 'Modify flight captain ID, auto-validate CAAC 121 duty time regulations.',
}

# Apply simple string replacements
for old, new in replacements:
    if old in content:
        content = content.replace(old, new)
    else:
        print(f"WARNING: Not found: {old[:60]}...")

# ============================================================
# STEP 3: Handle toast messages
# ============================================================
toast_replacements = [
    ("'info', `已添加 ${files.length} 个知识文档到待切块索引列表`",
     "'info', t('aiworkbench.chatbot.toastFilesAdded', { count: files.length })"),
    ("'success', 'RAG 文档向量化对齐成功！智能体已习得最新专业领域知识。'",
     "'success', t('aiworkbench.chatbot.toastRagSyncSuccess')"),
    ("'success', `智能体「${tempName}」发布成功！最新版本 v1.0.5 已于企业网内下发运行。`",
     "'success', t('aiworkbench.chatbot.toastPublishSuccess', { name: tempName })"),
    ("'success', '成功创建全新 AIP Chatbot'",
     "'success', t('aiworkbench.chatbot.toastCreateSuccess')"),
    ("'success', '已更新 Chatbot 架构参数'",
     "'success', t('aiworkbench.chatbot.toastUpdateSuccess')"),
    ("'success', 'Ontology Action 物理写回成功并通过双向核对对账！'",
     "'success', t('aiworkbench.chatbot.toastWritebackSuccess')"),
    ("'success', '已注销 Chatbot 实例'",
     "'success', t('aiworkbench.chatbot.toastDeleteSuccess')"),
    ("'info', '已拒绝该动作授权申请，操作已被安全拦截。'",
     "'info', t('aiworkbench.chatbot.toastRejectAction')"),
    ("'info', '沙箱交互历史已清空'",
     "'info', t('aiworkbench.chatbot.toastClearHistory')"),
    ("'error', '物理对账执行出错，请重试'",
     "'error', t('aiworkbench.chatbot.toastExecError')"),
    ("'error', `授权执行失败: ${data.message || data.error}`",
     "'error', t('aiworkbench.chatbot.toastExecFailed', { message: data.message || data.error })"),
    ("`已将沙箱测试身份切换为: ${e.target.value === 'AOC_DIRECTOR' ? 'AOC签派总监' : '外部承包商'}`",
     "t('aiworkbench.chatbot.toastRoleSwitch', { role: e.target.value === 'AOC_DIRECTOR' ? t('aiworkbench.chatbot.roleDirectorShort') : t('aiworkbench.chatbot.roleContractorShort') })"),
]

for old, new in toast_replacements:
    if old in content:
        content = content.replace(old, new)
    else:
        print(f"WARNING: Toast not found: {old[:60]}...")

# Add remaining toast keys to en_translations
en_translations['toastFilesAdded'] = 'Added {count} knowledge document(s) to pending chunk index list'
en_translations['toastRagSyncSuccess'] = 'RAG document vector alignment successful! Agent has learned the latest domain knowledge.'
en_translations['toastPublishSuccess'] = 'Agent "{name}" published successfully! Latest version v1.0.5 has been deployed across the enterprise network.'
en_translations['toastCreateSuccess'] = 'Successfully created new AIP Chatbot'
en_translations['toastUpdateSuccess'] = 'Chatbot architecture parameters updated'
en_translations['toastWritebackSuccess'] = 'Ontology Action physical write-back succeeded with bi-directional reconciliation!'
en_translations['toastDeleteSuccess'] = 'Chatbot instance decommissioned'
en_translations['toastRejectAction'] = 'Action authorization request rejected, operation safely intercepted.'
en_translations['toastClearHistory'] = 'Sandbox interaction history cleared'
en_translations['toastExecError'] = 'Physical reconciliation execution error, please retry'
en_translations['toastExecFailed'] = 'Authorization execution failed: {message}'
en_translations['toastRoleSwitch'] = 'Sandbox test identity switched to: {role}'
en_translations['roleDirectorShort'] = 'AOC Dispatch Director'
en_translations['roleContractorShort'] = 'External Contractor'

# ============================================================
# STEP 4: Handle simulation response content
# ============================================================

# Welcome message (line 123)
old_welcome = '''content: `### 🤖 ${activeChatbot.name} (AIP Chatbot Studio Sandbox)\\n\\n您好！我是工作于航空运行控制中心(AOC)的 **${activeChatbot.name}**。  \\n我已经基于您配置的 **本体关系** 与 **RAG安全规章知识库** 动态就绪。  \\n\\n**我的数据访问授权**：\\n- 📂 绑定实体：\\`AviationFlight (航班对象)\\`, \\`AviationPilot (飞行员对象)\\`\\n- ⚙️ 绑定动作：\\`act_reschedule_flight (重新指派航班与状态修改)\\`\\n- 🛡️ 安全合规：已装配 \\`${activeChatbot.guardrailIds.length} 个防护规则\\`, 包括 PII 数据动态脱敏与 Action 强人工对账核对。\\n\\n您可以用以下方式在沙箱中对我进行对话干涉测试：\\n1. **多模态实体关联查询**："查询 UA102 航班当前运行状态及机长是谁？"\\n2. **敏感个人隐私脱敏**："查询张建国机长的社会保障号 (SSN) 执勤底线记录"\\n3. **意图触发写回提案**："帮我将 UA102 航班延误状态改派为2小时"`'''

new_welcome = '''content: t('aiworkbench.chatbot.welcomeMessage', { name: activeChatbot.name, guardrailCount: activeChatbot.guardrailIds.length })'''

if old_welcome in content:
    content = content.replace(old_welcome, new_welcome)
    print("Replaced welcome message")
else:
    print("WARNING: Welcome message not found for exact replacement")
    # Try partial replacement
    if 'AIP Chatbot Studio Sandbox' in content:
        print("  Found partial match for welcome message")

en_translations['welcomeMessage'] = """### 🤖 {name} (AIP Chatbot Studio Sandbox)\\n\\nHello! I'm **{name}** working at the Aviation Operations Control Center (AOC).  \\nI'm dynamically ready based on your configured **ontology relationships** and **RAG safety regulation knowledge base**.  \\n\\n**My Data Access Authorization**:\\n- 📂 Bound Entities: \\`AviationFlight (Flight Object)\\`, \\`AviationPilot (Pilot Object)\\`\\n- ⚙️ Bound Actions: \\`act_reschedule_flight (Reschedule Flight & Status Modification)\\`\\n- 🛡️ Safety Compliance: Equipped with \\`{guardrailCount} guardrail rules\\`, including PII data dynamic masking and Action human reconciliation verification.\\n\\nYou can interact with me in the sandbox using the following approaches:\\n1. **Multi-Modal Entity Association Query**: "Query UA102 flight current status and who is the captain?"\\n2. **Sensitive PII Masking**: "Query Captain Zhang Jianguo's SSN duty floor records"\\n3. **Intent-Triggered Write-Back Proposal**: "Help me reschedule UA102 flight delay to 2 hours" """

# ============================================================
# STEP 5: Handle remaining Chinese in template literals and strings  
# ============================================================

# These are more complex replacements that need careful handling

# Thinking traces (lines 291-293)
content = content.replace(
    "'⚡ 正在解析用户请求，检查 activeUserRole 鉴权身份...'",
    "t('aiworkbench.chatbot.thinkingCheckRole')"
)
content = content.replace(
    "'🛡️ 安全拦截：检测到用户角色 [EXTERNAL_CONTRACTOR] 隶属于外部承包方，未被授权读取核心航空实体或执行指令。'",
    "t('aiworkbench.chatbot.thinkingContractorBlock')"
)
content = content.replace(
    "'⛔ 触发安全防御规则：拒绝执行操作，隐藏物理数据结构。'",
    "t('aiworkbench.chatbot.thinkingDefenseRule')"
)

# Thinking traces for ua102 query (lines 299-303)
content = content.replace(
    "'⚡ 正在提取查询本体目标: Flight (ID: UA102)'",
    "t('aiworkbench.chatbot.thinkingExtractQuery')"
)
content = content.replace(
    "'🔍 运行 RAG 向量检索，召回相关业务上下文: CAAC AOC 运行调度 SOP、延误改派规定...'",
    "t('aiworkbench.chatbot.thinkingRagSearch')"
)
content = content.replace(
    "'🔗 查询底层物理表：从 Doris `ds_flights_clean` 宽表读取物理记录...'",
    "t('aiworkbench.chatbot.thinkingPhysicalQuery')"
)
content = content.replace(
    "'🧬 装配知识卡：关联 Pilot 实体的社会保障号码 (SSN)、保底工资与 CAAC 资质...'",
    "t('aiworkbench.chatbot.thinkingAssembleCard')"
)
content = content.replace(
    "'🛡️ 安全审查：Guardrail [gr-pii] 已激活，检测 Pilot PII 数据，执行动态遮蔽...'",
    "t('aiworkbench.chatbot.thinkingPiiGuard')"
)

# Thinking traces for reschedule (lines 314-318)
content = content.replace(
    "'⚡ 用户发起对本体数据修改之事务请求。操作目标: 航班重调度与状态变更'",
    "t('aiworkbench.chatbot.thinkingRescheduleRequest')"
)
content = content.replace(
    "'🔍 识别待调用动作算子: `act_reschedule_flight`'",
    "t('aiworkbench.chatbot.thinkingIdentifyAction')"
)
content = content.replace(
    "'⚙️ 验证参数白名单契约... delay_minutes: \"120\", new_status: \"DELAYED\"'",
    "t('aiworkbench.chatbot.thinkingValidateParams')"
)
content = content.replace(
    "'🛡️ 安全拦截：发现操作涉及逻辑写回，触发安全护栏 `gr-approval` 人工确认机制。'",
    "t('aiworkbench.chatbot.thinkingApprovalGuard')"
)
content = content.replace(
    "'💾 暂挂物理事务，注册暂挂写回提案 (Pending Proposal)，向前端生成操作授权 Consent 卡片...'",
    "t('aiworkbench.chatbot.thinkingPendingProposal')"
)

# Thinking traces for ssn/工资 query (lines 338-339)
content = content.replace(
    "'⚡ 检测到显式寻求敏感隐私要素 (SSN/Salary) 指令...'",
    "t('aiworkbench.chatbot.thinkingSsnDetect')"
)
content = content.replace(
    "`🛡️ 触发 PII 护栏匹配... 规则启用状态: [${isPiiMasked}]`",
    "t('aiworkbench.chatbot.thinkingPiiMatch', { enabled: isPiiMasked })"
)

# Thinking traces for generic query (lines 350-351)
content = content.replace(
    "'⚡ 提取通用交互会话意图...'",
    "t('aiworkbench.chatbot.thinkingGenericIntent')"
)
content = content.replace(
    "'🧠 解析 RAG 知识库大纲，提供操作指导路线...'",
    "t('aiworkbench.chatbot.thinkingParseRag')"
)

en_translations['thinkingCheckRole'] = '⚡ Parsing user request, checking activeUserRole authorization identity...'
en_translations['thinkingContractorBlock'] = '🛡️ Security Intercept: Detected user role [EXTERNAL_CONTRACTOR] belongs to external contractor, not authorized to read core aviation entities or execute commands.'
en_translations['thinkingDefenseRule'] = '⛔ Triggering security defense rule: Denying operation, hiding physical data structures.'
en_translations['thinkingExtractQuery'] = '⚡ Extracting query ontology target: Flight (ID: UA102)'
en_translations['thinkingRagSearch'] = '🔍 Running RAG vector retrieval, recalling relevant business context: CAAC AOC Operations SOP, delay rescheduling regulations...'
en_translations['thinkingPhysicalQuery'] = '🔗 Querying underlying physical table: reading physical records from Doris `ds_flights_clean` wide table...'
en_translations['thinkingAssembleCard'] = '🧬 Assembling knowledge card: linking Pilot entity SSN, base salary, and CAAC qualifications...'
en_translations['thinkingPiiGuard'] = '🛡️ Security Review: Guardrail [gr-pii] activated, detecting Pilot PII data, executing dynamic masking...'
en_translations['thinkingRescheduleRequest'] = '⚡ User initiated transactional request to modify ontology data. Target: Flight rescheduling & status change'
en_translations['thinkingIdentifyAction'] = '🔍 Identifying action operator to invoke: `act_reschedule_flight`'
en_translations['thinkingValidateParams'] = '⚙️ Validating parameter whitelist contract... delay_minutes: "120", new_status: "DELAYED"'
en_translations['thinkingApprovalGuard'] = '🛡️ Security Intercept: Operation involves logical write-back, triggering safety guardrail `gr-approval` human confirmation mechanism.'
en_translations['thinkingPendingProposal'] = '💾 Suspending physical transaction, registering pending write-back proposal (Pending Proposal), generating operation authorization Consent card for frontend...'
en_translations['thinkingSsnDetect'] = '⚡ Detected explicit sensitive privacy element (SSN/Salary) instruction...'
en_translations['thinkingPiiMatch'] = '🛡️ Triggering PII guardrail match... Rule enabled status: [{enabled}]'
en_translations['thinkingGenericIntent'] = '⚡ Extracting general interactive conversation intent...'
en_translations['thinkingParseRag'] = '🧠 Parsing RAG knowledge base outline, providing operational guidance roadmap...'

# Add en translations for thinking traces
for k in ['thinkingCheckRole', 'thinkingContractorBlock', 'thinkingDefenseRule',
           'thinkingExtractQuery', 'thinkingRagSearch', 'thinkingPhysicalQuery',
           'thinkingAssembleCard', 'thinkingPiiGuard', 'thinkingRescheduleRequest',
           'thinkingIdentifyAction', 'thinkingValidateParams', 'thinkingApprovalGuard',
           'thinkingPendingProposal', 'thinkingSsnDetect', 'thinkingPiiMatch',
           'thinkingGenericIntent', 'thinkingParseRag']:
    pass  # already set above

# ============================================================
# Handle long replyContent strings - these are SIMULATION responses
# ============================================================

# External contractor reply (line 295)  
old_contractor_reply = "replyContent = `⚠️ **安全网安全拦截通知 (Sovereign Safety Block)**：\\\n\\\n对不起，系统检测到您当前身份为 **[外部承包商 (External Contractor)]**。  \\\n由于您未持有民航 AOC 核心签派员鉴权角色，受安全中心 **AIP Row-Level & RBAC 隔离护栏** 强制阻断约束：\\\n- 🔒 禁止读取 \\`AviationPilot\\` 与 \\`AviationFlight\\` 核心物理实体数据。\\\n- 🔒 无法使用任何写回指令（Ontology Actions）。\\\n\\\n如有排班调配需求，请联系 AOC 签派总监王凯进行授权处理。`"

new_contractor_reply = "replyContent = t('aiworkbench.chatbot.simContractorBlock')"

if old_contractor_reply in content:
    content = content.replace(old_contractor_reply, new_contractor_reply)
    print("Replaced contractor reply")
else:
    print("WARNING: Contractor reply not found")

en_translations['simContractorBlock'] = """⚠️ **Safety Net Security Intercept Notification (Sovereign Safety Block)**:\\n\\nSorry, the system has detected your current identity as **[External Contractor]**.  \\nSince you do not hold the civil aviation AOC core dispatcher authorization role, you are blocked by the Security Center **AIP Row-Level & RBAC isolation guardrails**:\\n- 🔒 Prohibited from reading \\`AviationPilot\\` and \\`AviationFlight\\` core physical entity data.\\n- 🔒 Cannot use any write-back instructions (Ontology Actions).\\n\\nFor scheduling needs, please contact AOC Dispatch Director Wang Kai for authorization."""

# UA102 query reply (line 310)
old_ua102_reply = "replyContent = `已为您在航空核心本体(Aviation Core)中成功拉取 **UA102** 航班的最新高精度数据：\\\n\\\n### ✈️ 航班运行档案 (ObjectType: Flight)\\n- **航班号**: UA102 (芝加哥 ORD → 旧金山 SFO)\\n- **计划起飞**: 今日 08:00 (ON_TIME 准点)\\n- **执飞机型**: Boeing 737-800 (尾号: **N101UA**)\\n- **适航状态**: 【极佳 (Excellent)】（最后一次 C 检维保于 2026-05-12）\\n\\\n### 👨‍✈️ 签派飞行员资质与安全审计 (ObjectType: Pilot)\\n- **责任机长**: **张建国** (资质: D-121部机长, 累积安全飞行 8200 小时)\\n- **CAAC 资质状态**: ✅ 资质在有效期内\\n- **机长社保保障号 (SSN)**: ${ssnValue}\\n- **机长保底薪资标准**: ${payrollValue}\\n\\\n**AIP 决策引擎建议**：\\n当前执飞方案完全符合 CAAC 121 部执勤时间规章。芝加哥与旧金山航路上目前无明显对流云团，气象评估结果为适航，推荐维持当前编排方案。`"

new_ua102_reply = "replyContent = t('aiworkbench.chatbot.simUa102Query', { ssnValue, payrollValue })"

if old_ua102_reply in content:
    content = content.replace(old_ua102_reply, new_ua102_reply)
    print("Replaced UA102 reply")
else:
    print("WARNING: UA102 reply not found")

en_translations['simUa102Query'] = """Successfully retrieved the latest high-precision data for **UA102** flight in the Aviation Core Ontology:\\n\\n### ✈️ Flight Operation Profile (ObjectType: Flight)\\n- **Flight Number**: UA102 (Chicago ORD → San Francisco SFO)\\n- **Scheduled Departure**: Today 08:00 (ON_TIME)\\n- **Aircraft Type**: Boeing 737-800 (Tail: **N101UA**)\\n- **Airworthiness Status**: [Excellent] (Last C-check maintenance on 2026-05-12)\\n\\n### 👨‍✈️ Dispatched Pilot Qualifications & Safety Audit (ObjectType: Pilot)\\n- **Captain**: **Zhang Jianguo** (Qualification: D-121 Captain, 8200 hours cumulative safe flight)\\n- **CAAC Qualification Status**: ✅ Valid\\n- **Captain SSN**: {ssnValue}\\n- **Captain Base Salary**: {payrollValue}\\n\\n**AIP Decision Engine Recommendation**:\\nCurrent flight plan fully complies with CAAC Part 121 duty time regulations. No significant convective clouds on Chicago-San Francisco route. Weather assessment is airworthy. Recommend maintaining current schedule."""

# Reschedule reply (line 321)
old_reschedule_reply = "replyContent = `我已理解您的操作意图：**因突发天气变化或签派调度要求，需将 UA102 航班更改为延误 2 小时**。\\\n\\\n根据系统预设的 **AIP Guardrails 安全审查规范**，由于该操作会物理影响底层 Doris 表 ` + \"`flights_raw`\" + `，系统必须暂挂物理写入，转为**对账写回提案 (Ontology Action Proposal)**。\\\n\\\n请您作为 **AOC 签派总监**，在下方授权卡片中核对物理映射并手动确认授权，方可物理落库生效。`"

new_reschedule_reply = "replyContent = t('aiworkbench.chatbot.simRescheduleProposal')"

if old_reschedule_reply in content:
    content = content.replace(old_reschedule_reply, new_reschedule_reply)
    print("Replaced reschedule reply")
else:
    print("WARNING: Reschedule reply not found")

en_translations['simRescheduleProposal'] = """I understand your operational intent: **Due to sudden weather changes or dispatch scheduling requirements, UA102 flight needs to be changed to a 2-hour delay**.\\n\\nAccording to the system's preset **AIP Guardrails Safety Review Specifications**, since this operation will physically affect the underlying Doris table `flights_raw`, the system must suspend physical writes and convert to a **Reconciliation Write-Back Proposal (Ontology Action Proposal)**.\\n\\nAs the **AOC Dispatch Director**, please verify the physical mapping in the authorization card below and manually confirm authorization to enable physical database commit."""

# PII masked reply (line 343)
old_pii_masked_reply = "replyContent = `⚠️ **敏感数据安全遮蔽阻断警告**：\\\n\\\n系统检测到您的指令正在尝试以明文拉取飞行员的核心个人隐私信息（SSN / 保底工资）。\\n根据 **AIP Guardrails 隐私合规条例 (gr-pii)**，所有输出中的关键敏感信息已被强制转换为脱敏标记，明文日志已被阻断：\\n- 🔒 **机长社会保障号 (SSN)**: \\`[REDACTED_SSN_NUMBER_BY_GUARDRAIL]\\`\\n- 🔒 **保底工资标准**: \\`[REDACTED_PAYROLL_DATA_BY_GDPR]\\`\\n\\\n审计详情已自动生成并发送给安全中心审计总揽大盘。`"

new_pii_masked_reply = "replyContent = t('aiworkbench.chatbot.simPiiMasked')"

if old_pii_masked_reply in content:
    content = content.replace(old_pii_masked_reply, new_pii_masked_reply)
    print("Replaced PII masked reply")
else:
    print("WARNING: PII masked reply not found")

en_translations['simPiiMasked'] = """⚠️ **Sensitive Data Security Masking Block Warning**:\\n\\nThe system has detected your instruction attempting to pull pilot core personal privacy information (SSN / Base Salary) in plaintext.\\nAccording to **AIP Guardrails Privacy Compliance Regulations (gr-pii)**, all critical sensitive information in the output has been forcibly converted to masking tokens, and plaintext logs have been blocked:\\n- 🔒 **Captain SSN**: \\`[REDACTED_SSN_NUMBER_BY_GUARDRAIL]\\`\\n- 🔒 **Base Salary Standard**: \\`[REDACTED_PAYROLL_DATA_BY_GDPR]\\`\\n\\nAudit details have been automatically generated and sent to the Security Center Audit Dashboard."""

# PII unmasked reply (line 345)
old_pii_unmasked_reply = "replyContent = `⚠️ **安全免责警告 (PII 护栏未启用)**：\\\n\\\n系统检测到您的指令正在尝试拉取敏感隐私信息（SSN / 薪资）。由于您在当前 Chatbot 中**未勾选启用 [gr-pii] 安全护栏**，数据将以明文导出，请妥善保管机密！\\\n\\\n- 👨‍✈️ 机长张建国身份证 SSN: \\`32010619841203XXXX\\`\\n- 💰 责任保底薪酬标准: \\`¥38,400 / 月\\`\\n\\\n*提示：为了生产环境合规，建议立即在左侧「安全护栏」设置中启用 PII 脱敏机制！*`"

new_pii_unmasked_reply = "replyContent = t('aiworkbench.chatbot.simPiiUnmasked')"

if old_pii_unmasked_reply in content:
    content = content.replace(old_pii_unmasked_reply, new_pii_unmasked_reply)
    print("Replaced PII unmasked reply")
else:
    print("WARNING: PII unmasked reply not found")

en_translations['simPiiUnmasked'] = """⚠️ **Security Disclaimer Warning (PII Guardrail Not Enabled)**:\\n\\nThe system has detected your instruction attempting to pull sensitive privacy information (SSN / Salary). Since you have **not enabled [gr-pii] safety guardrail** in the current Chatbot, data will be exported in plaintext. Please keep confidential information secure!\\n\\n- 👨‍✈️ Captain Zhang Jianguo ID SSN: \\`32010619841203XXXX\\`\\n- 💰 Base Salary Standard: \\`¥38,400 / month\\`\\n\\n*Tip: For production environment compliance, it is recommended to immediately enable PII masking in the left 'Safety Guardrails' settings!*"""

# Generic reply (line 353)
old_generic_reply = "replyContent = `我是您的 **${activeChatbot.name}**。我可以基于您的本地航空本体与 RAG 规章知识库，为您提供无缝、可信的多维航空签派问答：\\\n\\\n您可以尝试对我输入以下交互问题测试：\\n1. 🔍 **实体查询**：\"UA102 航班今天准点吗？机组配置如何？\"\\n2. 🛡️ **脱敏测试**：\"显示张建国机长的社会保障和薪水标准\"\\n3. ⚙️ **调配写回**：\"UA102 航班因暴雨改派，延误2小时，通知调度大厅\"`"

new_generic_reply = "replyContent = t('aiworkbench.chatbot.simGenericReply', { name: activeChatbot.name })"

if old_generic_reply in content:
    content = content.replace(old_generic_reply, new_generic_reply)
    print("Replaced generic reply")
else:
    print("WARNING: Generic reply not found")

en_translations['simGenericReply'] = """I am your **{name}**. Based on your local aviation ontology and RAG regulation knowledge base, I can provide seamless, trustworthy multi-dimensional aviation dispatch Q&A:\\n\\nYou can try entering the following interactive test questions:\\n1. 🔍 **Entity Query**: "Is UA102 flight on time today? How is the crew configuration?"\\n2. 🛡️ **Masking Test**: "Show Captain Zhang Jianguo's social security and salary standards"\\n3. ⚙️ **Dispatch Write-Back**: "UA102 flight rescheduled due to heavy rain, delayed 2 hours, notify dispatch center" """

# Reset welcome message (lines 566, 1270)
old_reset_welcome = "content: `### 🤖 ${activeChatbot.name} (沙箱调试已重置)\\n\\n请输入问题开始交互测试。你可以对我说：\"查询 UA102 航班运行情况\"`"
new_reset_welcome = "content: t('aiworkbench.chatbot.resetWelcome', { name: activeChatbot.name })"

content = content.replace(old_reset_welcome, new_reset_welcome)
print("Replaced reset welcome")

en_translations['resetWelcome'] = """### 🤖 {name} (Sandbox Debug Reset)\\n\\nPlease enter questions to start interactive testing. You can say to me: "Query UA102 flight operation status" """

# Welcome-reset same pattern 
old_reset_welcome2 = "content: `### 🤖 ${activeChatbot.name} (沙箱调试已重置)\\n\\n请输入问题开始交互测试。你可以对我说：\"查询 UA102 航班运行情况\"`"
# already handled by replace_all above

# ============================================================
# Handle audit log strings
# ============================================================
content = content.replace("'王凯 (AOC签派总监)'", "t('aiworkbench.chatbot.auditUser')")
content = content.replace("'编译发布成功'", "t('aiworkbench.chatbot.auditPublishSuccess')")
content = content.replace("'Chatbot Studio'", "'Chatbot Studio'")  # keep as-is

# Audit log details
old_audit_details = "`发布了智能对话工坊的新版本 v1.0.5。绑定关系：Objects=${selectedObjects.join(',')}, Actions=${selectedActions.join(',')}`"
new_audit_details = "t('aiworkbench.chatbot.auditPublishDetails', { objects: selectedObjects.join(','), actions: selectedActions.join(',') })"
content = content.replace(old_audit_details, new_audit_details)

en_translations['auditPublishDetails'] = 'Published new version v1.0.5 of the intelligent workshop. Bindings: Objects={objects}, Actions={actions}'

# Second audit log
content = content.replace("'Ontology Engine'", "'Ontology Engine'")  # keep

old_audit_action = "'双向核对成功'"
new_audit_action = "t('aiworkbench.chatbot.auditReconciliationSuccess')"
content = content.replace(old_audit_action, new_audit_action)

old_audit_exec_details = "`人工授权执行动作 act_reschedule_flight: 航班 UA102 成功延误 120 分钟并完成 Doris 双向字段值核算对账。`"
new_audit_exec_details = "t('aiworkbench.chatbot.auditExecDetails')"
content = content.replace(old_audit_exec_details, new_audit_exec_details)

en_translations['auditUser'] = 'Wang Kai (AOC Dispatch Director)'
en_translations['auditPublishSuccess'] = 'Compile and publish successful'
en_translations['auditReconciliationSuccess'] = 'Bi-directional reconciliation successful'
en_translations['auditExecDetails'] = 'Manually authorized action act_reschedule_flight: Flight UA102 successfully delayed 120 minutes with Doris bi-directional field value reconciliation completed.'

# ============================================================
# Handle web component placeholder and userRole
# ============================================================
content = content.replace("user-role=\"签派总监\"", "user-role={t('aiworkbench.chatbot.roleDirectorShort')}")
content = content.replace("placeholder=\"UA102 航班今天能准时飞吗？\"", "placeholder={t('aiworkbench.chatbot.wcPlaceholder')}")

en_translations['wcPlaceholder'] = 'Can UA102 flight depart on time today?'

# ============================================================
# Handle the confirmation dialog
# ============================================================
content = content.replace(
    "window.confirm('您确定要注销这个 Chatbot 实例吗？此操作不可逆。')",
    "window.confirm(t('aiworkbench.chatbot.confirmDelete'))"
)

en_translations['confirmDelete'] = 'Are you sure you want to decommission this Chatbot instance? This action is irreversible.'

# ============================================================
# Handle RAG sync logs (lines 176-185)
# ============================================================
content = content.replace(
    "setRagLogs(['🔄 [0.0s] 启动 AIP Chatbot RAG 向量增量切片管道...'])",
    "setRagLogs([t('aiworkbench.chatbot.ragLogStart')])"
)

for i, (old, new_key) in enumerate([
    ("'⚡ [0.5s] 读取未同步文档：寻找状态为 [pending] 的知识源...'", 'ragLogRead'),
    ("'🔍 [1.0s] 提取文档文字、消除特殊标记并清洗格式 (TEXT/PDF Parser)...'", 'ragLogExtract'),
    ("'✂️ [1.8s] 按照块大小: 512, 重叠度: 50 运行滑动窗口递归分块(Recursive Character Splitting)...'", 'ragLogChunk'),
    ("'🤖 [2.5s] 调用 LLM 嵌入式模型 `text-embedding-004` 计算向量特征值...'", 'ragLogEmbed'),
    ("'💾 [3.2s] 将切片成果注入私有 pgvector 特征元数据表，双向对齐底层本体索引...'", 'ragLogInject'),
    ("'✅ [4.0s] RAG 知识检索网格重构完成！所有新挂载文档均已标记为已对齐状态。'", 'ragLogComplete'),
]):
    content = content.replace(old, f"t('aiworkbench.chatbot.{new_key}')")
    en_translations[new_key] = old.strip("'")

# ============================================================
# Handle publish logs (lines 203, 206-211)
# ============================================================
content = content.replace(
    "setPublishingLogs(['🚀 [0.0s] 启动 Chatbot Studio 构建流，检查模型及本体连接...'])",
    "setPublishingLogs([t('aiworkbench.chatbot.pubLogStart')])"
)

for old, new_key in [
    ("'🔍 [0.4s] 验证逻辑绑定契约... Objects: Ok, Actions: 1 Ok, Functions: 1 Ok'", 'pubLogVerify'),
    ("'🛡️ [0.9s] 编译安全护栏网格，注入列级 Redaction 规则与 row-filter...'", 'pubLogCompile'),
    ("'🧪 [1.5s] 触发集成测试用例: 对话幻觉防御 HAG 自检测试 (置信度阀值: 92%)... ✅ PASSED'", 'pubLogTest'),
    ("'📦 [2.1s] 生成独立 Web UI 视图容器，构建 Workshop 桥接接口与元数据 API 终端...'", 'pubLogPackage'),
    ("'💾 [2.8s] 注册服务版本, 升级编译版本至 v1.0.5 并提交全站对账元数据记录...'", 'pubLogRegister'),
    ("'🎉 [3.5s] 部署完成！Chatbot 节点在 Sovereign 边界内正常运行，已在控制台中激活上线。'", 'pubLogDeploy'),
]:
    content = content.replace(old, f"t('aiworkbench.chatbot.{new_key}')")
    en_translations[new_key] = old.strip("'")

# ============================================================
# Handle handleStartCreate defaults (lines 539-542)
# ============================================================
content = content.replace(
    "setTempRole('AOC 调度大厅专属 AI 助理')",
    "setTempRole(t('aiworkbench.chatbot.defaultRole'))"
)
content = content.replace(
    "setTempDesc('服务于运行大厅。结合本地本体与 RAG 知识回答各类签派问题。')",
    "setTempDesc(t('aiworkbench.chatbot.defaultDesc'))"
)
content = content.replace(
    "setTempPrompt('你是一个工作在运行控制中心 (AOC) 的 AI 协同助理...')",
    "setTempPrompt(t('aiworkbench.chatbot.defaultPrompt'))"
)

en_translations['defaultRole'] = 'AOC Dispatch Center Exclusive AI Assistant'
en_translations['defaultDesc'] = 'Serves the operations center. Answers various dispatch questions using local ontology and RAG knowledge.'
en_translations['defaultPrompt'] = 'You are an AI collaborative assistant working at the Operations Control Center (AOC)...'

# ============================================================
# Handle system report message (line 444)
# ============================================================
old_sys_report = "content: `### 📊 完美对账写回双向审计报告 (Bi-directional Physical Validation Report)\\n\\n**物理更新动作**: ${data.executionDetail}  \\n**安全事务标识**: \\`${data.transactionHash || 'TX_AOC_823901'}\\`  \\n\\n**物理-逻辑字段强一致性对账核对 (Read-back Verification Check)**:\\n${checkItems}\\n\\n🎉 底层 Doris 物理 Bronze/Gold 级宽表已成功写回刷新，血缘级联对齐。`"

new_sys_report = "content: t('aiworkbench.chatbot.sysReport', { executionDetail: data.executionDetail, transactionHash: data.transactionHash || 'TX_AOC_823901', checkItems })"

if old_sys_report in content:
    content = content.replace(old_sys_report, new_sys_report)
    print("Replaced system report")
else:
    print("WARNING: System report not found")

en_translations['sysReport'] = """### 📊 Perfect Reconciliation Write-Back Bi-Directional Audit Report (Bi-directional Physical Validation Report)\\n\\n**Physical Update Action**: {executionDetail}  \\n**Security Transaction ID**: \\`{transactionHash}\\`  \\n\\n**Physical-Logical Field Strong Consistency Reconciliation Check (Read-back Verification Check)**:\\n{checkItems}\\n\\n🎉 Underlying Doris physical Bronze/Gold-level wide table has been successfully written back and refreshed, lineage cascade aligned."""

# ============================================================
# Handle the proposal data (line 401-403)
# ============================================================
content = content.replace(
    "userRole: '签派总监'",
    "userRole: t('aiworkbench.chatbot.proposalUserRole')"
)
content = content.replace(
    "userName: '王凯'",
    "userName: t('aiworkbench.chatbot.proposalUserName')"
)

en_translations['proposalUserRole'] = 'Dispatch Director'
en_translations['proposalUserName'] = 'Wang Kai'

# ============================================================
# Handle SSN value strings (line 307-308)
# ============================================================
content = content.replace(
    "'`[REDACTED_BY_PII_GUARDRAIL_MASK_SSN]`'",
    "t('aiworkbench.chatbot.ssnRedacted')"
)
content = content.replace(
    "'`32010619841203XXXX (真实值: S-2289410)`'",
    "t('aiworkbench.chatbot.ssnReal')"
)
content = content.replace(
    "'`[REDACTED_BY_PII_GUARDRAIL_MASK_SALARY]`'",
    "t('aiworkbench.chatbot.salaryRedacted')"
)
content = content.replace(
    "'`¥38,400 / 月 (Base)`'",
    "t('aiworkbench.chatbot.salaryReal')"
)

en_translations['ssnRedacted'] = '`[REDACTED_BY_PII_GUARDRAIL_MASK_SSN]`'
en_translations['ssnReal'] = '`32010619841203XXXX (Real: S-2289410)`'
en_translations['salaryRedacted'] = '`[REDACTED_BY_PII_GUARDRAIL_MASK_SALARY]`'
en_translations['salaryReal'] = '`¥38,400 / month (Base)`'

# ============================================================
# Handle fetch proposal body data
# ============================================================
content = content.replace(
    "proposedBy: `Chatbot Sandbox (${activeChatbot.name})`",
    "proposedBy: t('aiworkbench.chatbot.proposedBy', { name: activeChatbot.name })"
)

en_translations['proposedBy'] = 'Chatbot Sandbox ({name})'

# ============================================================
# Handle the checkItems message (line 438)
# ============================================================
content = content.replace(
    "`• **[逻辑字段对齐]** \\`${m.logicalField}\\` ➔ \\`${m.physicalCol}\\`: 预估 [${m.expectedValue}] ↔ 物理读回 [${m.readbackValue}] ✅ 强一致对齐`",
    "t('aiworkbench.chatbot.verificationItem', { logicalField: m.logicalField, physicalCol: m.physicalCol, expectedValue: m.expectedValue, readbackValue: m.readbackValue })"
)

en_translations['verificationItem'] = '• **[Logical Field Alignment]** \\`{logicalField}\\` ➔ \\`{physicalCol}\\`: Expected [{expectedValue}] ↔ Physical Readback [{readbackValue}] ✅ Strongly Consistent Alignment'

# ============================================================
# Write the modified file
# ============================================================
with open(FILE_PATH, 'w') as f:
    f.write(content)

print(f"\nFile written: {FILE_PATH}")

# ============================================================
# STEP 6: Count remaining Chinese
# ============================================================
with open(FILE_PATH, 'r') as f:
    modified = f.read()

cn_lines = [i for i, line in enumerate(modified.split('\n'), 1) if re.search(r'[\u4e00-\u9fff]', line)]
cn_count = len(cn_lines)
    
print(f"\nRemaining lines with Chinese: {cn_count}")
if cn_count > 0:
    print("First 20 remaining Chinese lines:")
    for ln in cn_lines[:20]:
        print(f"  L{ln}: {modified.split(chr(10))[ln-1][:150]}")
else:
    print("✓ ALL Chinese strings replaced!")

# ============================================================
# STEP 7: Build i18n JSON updates
# ============================================================
print("\n=== i18n Key Summary ===")
print(f"Total en keys: {len(en_translations)}")

# Write zh-CN keys (same as original Chinese values)
# We need to extract the original Chinese values from the replacements
zh_keys = {}
for old, new in replacements:
    # Extract key name from t('aiworkbench.chatbot.KEY')
    m = re.search(r"t\('aiworkbench\.chatbot\.(\w+)'\)", new)
    if m:
        key = m.group(1)
        # The original Chinese is in 'old' (but it's the string as it appears in file)
        zh_keys[key] = old.strip("'\"")
    # Also handle the form with params
    m2 = re.search(r"t\('aiworkbench\.chatbot\.(\w+)',", new)
    if m2:
        key = m2.group(1)
        zh_keys[key] = old.strip("'`")

# Manual zh-CN values for keys not in replacements list
manual_zh = {
    'toastFilesAdded': '已添加 {count} 个知识文档到待切块索引列表',
    'toastRagSyncSuccess': 'RAG 文档向量化对齐成功！智能体已习得最新专业领域知识。',
    'toastPublishSuccess': '智能体「{name}」发布成功！最新版本 v1.0.5 已于企业网内下发运行。',
    'toastCreateSuccess': '成功创建全新 AIP Chatbot',
    'toastUpdateSuccess': '已更新 Chatbot 架构参数',
    'toastWritebackSuccess': 'Ontology Action 物理写回成功并通过双向核对对账！',
    'toastDeleteSuccess': '已注销 Chatbot 实例',
    'toastRejectAction': '已拒绝该动作授权申请，操作已被安全拦截。',
    'toastClearHistory': '沙箱交互历史已清空',
    'toastExecError': '物理对账执行出错，请重试',
    'toastExecFailed': '授权执行失败: {message}',
    'toastRoleSwitch': '已将沙箱测试身份切换为: {role}',
    'roleDirectorShort': 'AOC签派总监',
    'roleContractorShort': '外部承包商',
    'welcomeMessage': '### 🤖 {name} (AIP Chatbot Studio Sandbox)\\n\\n您好！我是工作于航空运行控制中心(AOC)的 **{name}**。  \\n我已经基于您配置的 **本体关系** 与 **RAG安全规章知识库** 动态就绪。  \\n\\n**我的数据访问授权**：\\n- 📂 绑定实体：`AviationFlight (航班对象)`, `AviationPilot (飞行员对象)`\\n- ⚙️ 绑定动作：`act_reschedule_flight (重新指派航班与状态修改)`\\n- 🛡️ 安全合规：已装配 `{guardrailCount} 个防护规则`, 包括 PII 数据动态脱敏与 Action 强人工对账核对。\\n\\n您可以用以下方式在沙箱中对我进行对话干涉测试：\\n1. **多模态实体关联查询**："查询 UA102 航班当前运行状态及机长是谁？"\\n2. **敏感个人隐私脱敏**："查询张建国机长的社会保障号 (SSN) 执勤底线记录"\\n3. **意图触发写回提案**："帮我将 UA102 航班延误状态改派为2小时"',
    'thinkingCheckRole': '⚡ 正在解析用户请求，检查 activeUserRole 鉴权身份...',
    'thinkingContractorBlock': '🛡️ 安全拦截：检测到用户角色 [EXTERNAL_CONTRACTOR] 隶属于外部承包方，未被授权读取核心航空实体或执行指令。',
    'thinkingDefenseRule': '⛔ 触发安全防御规则：拒绝执行操作，隐藏物理数据结构。',
    'thinkingExtractQuery': '⚡ 正在提取查询本体目标: Flight (ID: UA102)',
    'thinkingRagSearch': '🔍 运行 RAG 向量检索，召回相关业务上下文: CAAC AOC 运行调度 SOP、延误改派规定...',
    'thinkingPhysicalQuery': '🔗 查询底层物理表：从 Doris `ds_flights_clean` 宽表读取物理记录...',
    'thinkingAssembleCard': '🧬 装配知识卡：关联 Pilot 实体的社会保障号码 (SSN)、保底工资与 CAAC 资质...',
    'thinkingPiiGuard': '🛡️ 安全审查：Guardrail [gr-pii] 已激活，检测 Pilot PII 数据，执行动态遮蔽...',
    'thinkingRescheduleRequest': '⚡ 用户发起对本体数据修改之事务请求。操作目标: 航班重调度与状态变更',
    'thinkingIdentifyAction': '🔍 识别待调用动作算子: `act_reschedule_flight`',
    'thinkingValidateParams': '⚙️ 验证参数白名单契约... delay_minutes: "120", new_status: "DELAYED"',
    'thinkingApprovalGuard': '🛡️ 安全拦截：发现操作涉及逻辑写回，触发安全护栏 `gr-approval` 人工确认机制。',
    'thinkingPendingProposal': '💾 暂挂物理事务，注册暂挂写回提案 (Pending Proposal)，向前端生成操作授权 Consent 卡片...',
    'thinkingSsnDetect': '⚡ 检测到显式寻求敏感隐私要素 (SSN/Salary) 指令...',
    'thinkingPiiMatch': '🛡️ 触发 PII 护栏匹配... 规则启用状态: [{enabled}]',
    'thinkingGenericIntent': '⚡ 提取通用交互会话意图...',
    'thinkingParseRag': '🧠 解析 RAG 知识库大纲，提供操作指导路线...',
    'simContractorBlock': '⚠️ **安全网安全拦截通知 (Sovereign Safety Block)**：\\n\\n对不起，系统检测到您当前身份为 **[外部承包商 (External Contractor)]**。  \\n由于您未持有民航 AOC 核心签派员鉴权角色，受安全中心 **AIP Row-Level & RBAC 隔离护栏** 强制阻断约束：\\n- 🔒 禁止读取 `AviationPilot` 与 `AviationFlight` 核心物理实体数据。\\n- 🔒 无法使用任何写回指令（Ontology Actions）。\\n\\n如有排班调配需求，请联系 AOC 签派总监王凯进行授权处理。',
    'simUa102Query': '已为您在航空核心本体(Aviation Core)中成功拉取 **UA102** 航班的最新高精度数据：\\n\\n### ✈️ 航班运行档案 (ObjectType: Flight)\\n- **航班号**: UA102 (芝加哥 ORD → 旧金山 SFO)\\n- **计划起飞**: 今日 08:00 (ON_TIME 准点)\\n- **执飞机型**: Boeing 737-800 (尾号: **N101UA**)\\n- **适航状态**: 【极佳 (Excellent)】（最后一次 C 检维保于 2026-05-12）\\n\\n### 👨‍✈️ 签派飞行员资质与安全审计 (ObjectType: Pilot)\\n- **责任机长**: **张建国** (资质: D-121部机长, 累积安全飞行 8200 小时)\\n- **CAAC 资质状态**: ✅ 资质在有效期内\\n- **机长社保保障号 (SSN)**: {ssnValue}\\n- **机长保底薪资标准**: {payrollValue}\\n\\n**AIP 决策引擎建议**：\\n当前执飞方案完全符合 CAAC 121 部执勤时间规章。芝加哥与旧金山航路上目前无明显对流云团，气象评估结果为适航，推荐维持当前编排方案。',
    'simRescheduleProposal': '我已理解您的操作意图：**因突发天气变化或签派调度要求，需将 UA102 航班更改为延误 2 小时**。\\n\\n根据系统预设的 **AIP Guardrails 安全审查规范**，由于该操作会物理影响底层 Doris 表 `flights_raw`，系统必须暂挂物理写入，转为**对账写回提案 (Ontology Action Proposal)**。\\n\\n请您作为 **AOC 签派总监**，在下方授权卡片中核对物理映射并手动确认授权，方可物理落库生效。',
    'simPiiMasked': '⚠️ **敏感数据安全遮蔽阻断警告**：\\n\\n系统检测到您的指令正在尝试以明文拉取飞行员的核心个人隐私信息（SSN / 保底工资）。\\n根据 **AIP Guardrails 隐私合规条例 (gr-pii)**，所有输出中的关键敏感信息已被强制转换为脱敏标记，明文日志已被阻断：\\n- 🔒 **机长社会保障号 (SSN)**: `[REDACTED_SSN_NUMBER_BY_GUARDRAIL]`\\n- 🔒 **保底工资标准**: `[REDACTED_PAYROLL_DATA_BY_GDPR]`\\n\\n审计详情已自动生成并发送给安全中心审计总揽大盘。',
    'simPiiUnmasked': '⚠️ **安全免责警告 (PII 护栏未启用)**：\\n\\n系统检测到您的指令正在尝试拉取敏感隐私信息（SSN / 薪资）。由于您在当前 Chatbot 中**未勾选启用 [gr-pii] 安全护栏**，数据将以明文导出，请妥善保管机密！\\n\\n- 👨‍✈️ 机长张建国身份证 SSN: `32010619841203XXXX`\\n- 💰 责任保底薪酬标准: `¥38,400 / 月`\\n\\n*提示：为了生产环境合规，建议立即在左侧「安全护栏」设置中启用 PII 脱敏机制！*',
    'simGenericReply': '我是您的 **{name}**。我可以基于您的本地航空本体与 RAG 规章知识库，为您提供无缝、可信的多维航空签派问答：\\n\\n您可以尝试对我输入以下交互问题测试：\\n1. 🔍 **实体查询**："UA102 航班今天准点吗？机组配置如何？"\\n2. 🛡️ **脱敏测试**："显示张建国机长的社会保障和薪水标准"\\n3. ⚙️ **调配写回**："UA102 航班因暴雨改派，延误2小时，通知调度大厅"',
    'resetWelcome': '### 🤖 {name} (沙箱调试已重置)\\n\\n请输入问题开始交互测试。你可以对我说："查询 UA102 航班运行情况"',
    'auditUser': '王凯 (AOC签派总监)',
    'auditPublishSuccess': '编译发布成功',
    'auditPublishDetails': '发布了智能对话工坊的新版本 v1.0.5。绑定关系：Objects={objects}, Actions={actions}',
    'auditReconciliationSuccess': '双向核对成功',
    'auditExecDetails': '人工授权执行动作 act_reschedule_flight: 航班 UA102 成功延误 120 分钟并完成 Doris 双向字段值核算对账。',
    'wcPlaceholder': 'UA102 航班今天能准时飞吗？',
    'confirmDelete': '您确定要注销这个 Chatbot 实例吗？此操作不可逆。',
    'ragLogStart': '🔄 [0.0s] 启动 AIP Chatbot RAG 向量增量切片管道...',
    'ragLogRead': '⚡ [0.5s] 读取未同步文档：寻找状态为 [pending] 的知识源...',
    'ragLogExtract': '🔍 [1.0s] 提取文档文字、消除特殊标记并清洗格式 (TEXT/PDF Parser)...',
    'ragLogChunk': '✂️ [1.8s] 按照块大小: 512, 重叠度: 50 运行滑动窗口递归分块(Recursive Character Splitting)...',
    'ragLogEmbed': '🤖 [2.5s] 调用 LLM 嵌入式模型 `text-embedding-004` 计算向量特征值...',
    'ragLogInject': '💾 [3.2s] 将切片成果注入私有 pgvector 特征元数据表，双向对齐底层本体索引...',
    'ragLogComplete': '✅ [4.0s] RAG 知识检索网格重构完成！所有新挂载文档均已标记为已对齐状态。',
    'pubLogStart': '🚀 [0.0s] 启动 Chatbot Studio 构建流，检查模型及本体连接...',
    'pubLogVerify': '🔍 [0.4s] 验证逻辑绑定契约... Objects: Ok, Actions: 1 Ok, Functions: 1 Ok',
    'pubLogCompile': '🛡️ [0.9s] 编译安全护栏网格，注入列级 Redaction 规则与 row-filter...',
    'pubLogTest': '🧪 [1.5s] 触发集成测试用例: 对话幻觉防御 HAG 自检测试 (置信度阀值: 92%)... ✅ PASSED',
    'pubLogPackage': '📦 [2.1s] 生成独立 Web UI 视图容器，构建 Workshop 桥接接口与元数据 API 终端...',
    'pubLogRegister': '💾 [2.8s] 注册服务版本, 升级编译版本至 v1.0.5 并提交全站对账元数据记录...',
    'pubLogDeploy': '🎉 [3.5s] 部署完成！Chatbot 节点在 Sovereign 边界内正常运行，已在控制台中激活上线。',
    'defaultRole': 'AOC 调度大厅专属 AI 助理',
    'defaultDesc': '服务于运行大厅。结合本地本体与 RAG 知识回答各类签派问题。',
    'defaultPrompt': '你是一个工作在运行控制中心 (AOC) 的 AI 协同助理...',
    'sysReport': '### 📊 完美对账写回双向审计报告 (Bi-directional Physical Validation Report)\\n\\n**物理更新动作**: {executionDetail}  \\n**安全事务标识**: `{transactionHash}`  \\n\\n**物理-逻辑字段强一致性对账核对 (Read-back Verification Check)**:\\n{checkItems}\\n\\n🎉 底层 Doris 物理 Bronze/Gold 级宽表已成功写回刷新，血缘级联对齐。',
    'proposalUserRole': '签派总监',
    'proposalUserName': '王凯',
    'ssnRedacted': '`[REDACTED_BY_PII_GUARDRAIL_MASK_SSN]`',
    'ssnReal': '`32010619841203XXXX (真实值: S-2289410)`',
    'salaryRedacted': '`[REDACTED_BY_PII_GUARDRAIL_MASK_SALARY]`',
    'salaryReal': '`¥38,400 / 月 (Base)`',
    'proposedBy': 'Chatbot Sandbox ({name})',
    'verificationItem': '• **[逻辑字段对齐]** `{logicalField}` ➔ `{physicalCol}`: 预估 [{expectedValue}] ↔ 物理读回 [{readbackValue}] ✅ 强一致对齐',
}

zh_keys.update(manual_zh)

print(f"Total zh keys collected: {len(zh_keys)}")

# Save zh-CN and en key data for JSON update
with open('/tmp/i18n_zh_data.json', 'w') as f:
    json.dump(zh_keys, f, ensure_ascii=False, indent=2)
with open('/tmp/i18n_en_data.json', 'w') as f:
    json.dump(en_translations, f, ensure_ascii=False, indent=2)

print("\nKey data saved to /tmp/i18n_zh_data.json and /tmp/i18n_en_data.json")
print("DONE with migration script")
