/**
 * agentStudioHelpers — Agent Studio 的纯逻辑模块（消息类型 + 交互沙箱 mock 回复决策）。
 * 由 AgentStudioView.tsx 机械抽取（PMO-74 H6-T4），if/else 决策分支与字符串常量逐行一致。
 * @license Apache-2.0
 */

export interface ChatMessage {
  id: string;
  sender: 'user' | 'agent' | 'system';
  content: string;
  timestamp: string;
  thinkingTrace?: string[];
  actionProposal?: {
    id?: string;
    actionId: string;
    actionName: string;
    payload: Record<string, string>;
    status: 'pending' | 'approved' | 'rejected';
  };
}

/**
 * 纯函数：根据用户输入文本决定沙箱 mock 回复内容 / 思考链 / 是否产生待确认 Ontology Action。
 * 抽取自 handleSendChat 的 setTimeout 回调内部（不含任何副作用与状态写入）。
 */
export function buildMockAgentReply(text: string): {
  replyContent: string;
  thinkingTrace: string[];
  proposal: ChatMessage['actionProposal'];
} {
  let replyContent = '';
  let thinkingTrace: string[] = [];
  let proposal: ChatMessage['actionProposal'] = undefined;

  const lowerText = text.toLowerCase();
  if (lowerText.includes('ua102') || lowerText.includes('查询')) {
    thinkingTrace = [
      '⚡ 正在解析用户请求，提取 Ontology 目标：航班 "UA102"',
      '🔍 触发系统集成查询：检索 ObjectType: Flight (ID: UA102)',
      '🔗 级联读取关联属性：执飞飞机 N101UA, 指派飞行员 P01 (张建国)',
      '📊 融合数据安全审计：PII 脱敏机制启动，正常运行。'
    ];
    replyContent = `已为您成功从航空本体库拉取 **UA102** 航班的实时多维详情：\n\n*   **航班号**: UA102 (芝加哥 ORD → 旧金山 SFO)\n*   **计划起飞**: 今日 08:00 (ON_TIME 准点)\n*   **执飞机型**: Boeing 737-800 (尾号: **N101UA**)\n*   **责任机长**: **张建国** (Captain, 累积飞行 8200 小时)\n\n**AI 安全评估建议**：\n执飞飞机 N101UA 的最后维保时间为 2026-05-12，气象检测显示 ORD 机场阵风 12 节，适航评级为【极佳(Excellent)】。无需调配改签。`;
  } else if (lowerText.includes('延误') || lowerText.includes('小时') || lowerText.includes('改') || lowerText.includes('reschedule')) {
    thinkingTrace = [
      '⚡ 用户请求对本体数据发起修改指令。操作意图: 重新调度/航班重新指派',
      '🛡️ 安全审查：触发 Guardrail: Ontology Action 强制人工确认 (gr-approval)',
      '⚠️ 检测到操作对象：Flight: UA102, 修改延误参数：120 分钟',
      '💾 构造 Ontology Action Payload, 暂停事务，发送授权请求卡片...'
    ];
    replyContent = `我已理解您的调配指令：因突发设备检测，需将 **UA102** 航班延误状态更新。由于该操作涉及本体状态修改，受 **AIP Guardrails 安全护栏约束**，必须由您点击下方卡片人工确认授权，方可写入企业主本体数据库。`;
    proposal = {
      actionId: 'act_reschedule_flight',
      actionName: '重新指派航班与状态修改 (act_reschedule_flight)',
      payload: {
        flight_number: 'UA102',
        new_status: 'DELAYED',
        delay_minutes: '120',
        auth_required_by: 'AOC_DIRECTOR'
      },
      status: 'pending'
    };
  } else {
    thinkingTrace = [
      '⚡ 解析通用会话指令...',
      '🧠 调用大语言模型大局观评估...'
    ];
    replyContent = `我是一个工作在航空运行控制大厅的智能助手。我可以协助您高效检索以下本体信息：\n\n1.  **航班与气象级联查询** (如："帮我查询 UA102 航班状态及风险")\n2.  **机组与CAAC合规审查** (如："评估飞行员 P02 的疲劳与资质风险")\n3.  **拟定 Ontology 修改意图** (如："帮我把 UA102 航班延误改派为2小时")`;
  }

  return { replyContent, thinkingTrace, proposal };
}
