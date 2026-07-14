/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import { AIPLogicPipeline, AIPAgent, AIPModel, AIPGuardrail, AIPAuditLog } from '../../types/aiworkbench';

export const mockAIPModels: AIPModel[] = [
  {
    id: 'gemini-1.5-pro',
    displayName: 'Gemini 1.5 Pro (Sovereign Security)',
    provider: 'Google',
    type: 'language',
    status: 'connected',
    maxContext: '2,000,000 tokens',
    latencyMs: 140,
    costPerMillion: '$7.00',
    inputCost: '$1.25',
    outputCost: '$5.00',
    healthRate: 99.98,
    temperature: 0.4
  },
  {
    id: 'claude-3-5-sonnet',
    displayName: 'Claude 3.5 Sonnet (Enterprise Private)',
    provider: 'Anthropic',
    type: 'language',
    status: 'connected',
    maxContext: '200,000 tokens',
    latencyMs: 180,
    costPerMillion: '$15.00',
    inputCost: '$3.00',
    outputCost: '$12.00',
    healthRate: 99.95,
    temperature: 0.5
  },
  {
    id: 'gpt-4o',
    displayName: 'GPT-4o (Azure Secure VPC)',
    provider: 'OpenAI',
    type: 'language',
    status: 'connected',
    maxContext: '128,000 tokens',
    latencyMs: 160,
    costPerMillion: '$10.00',
    inputCost: '$2.50',
    outputCost: '$7.50',
    healthRate: 99.92,
    temperature: 0.3
  },
  {
    id: 'llama-3-70b-local',
    displayName: 'Llama 3 70B (On-Premises cluster)',
    provider: 'Meta',
    type: 'language',
    status: 'connected',
    maxContext: '8,192 tokens',
    latencyMs: 110,
    costPerMillion: '$0.00 (Self-Hosted)',
    inputCost: '$0.00',
    outputCost: '$0.00',
    healthRate: 100.00,
    temperature: 0.2
  }
];

export const mockAIPGuardrails: AIPGuardrail[] = [
  {
    id: 'gr-pii',
    name: '敏感数据(PII)动态脱敏',
    type: 'pii_redaction',
    description: '自动检测和脱敏输入及输出中的身份证、电话号码、真实姓名及常旅客账户等敏感信息。',
    isEnabled: true,
    severity: 'audit_only',
    parameters: {
      piiTypes: ['NAME', 'PHONE', 'SSN', 'ACCOUNT_NUMBER']
    }
  },
  {
    id: 'gr-approval',
    name: 'Ontology Action 强制人工确认',
    type: 'human_approval',
    description: '当 AI 智能体建议触发高危操作（如重新排班、航班延误确认等）时，强制弹框要求人工授权。',
    isEnabled: true,
    severity: 'block',
    parameters: {
      requiredActionIds: ['act_reschedule_flight', 'act_assign_pilot']
    }
  },
  {
    id: 'gr-hallucination',
    name: '生成内容幻觉检测(HAG)',
    type: 'hallucination_check',
    description: '比对生成的航班数据与 Ontology 黄金标准，若不匹配或置信度低于 92% 则触发警告。',
    isEnabled: true,
    severity: 'warn',
    parameters: {
      confidenceThreshold: 0.92
    }
  },
  {
    id: 'gr-harm',
    name: '有害及非合规指令拦截',
    type: 'harm_filter',
    description: '对输入提示词进行实时有害性、越狱词汇及无关业务对话内容检测。',
    isEnabled: true,
    severity: 'block',
    parameters: {
      toxicThreshold: 0.85
    }
  }
];

export const mockAIPPipelines: AIPLogicPipeline[] = [
  {
    id: 'pipe-delay-reason',
    name: '航班延误原因智能判定与排班预测',
    description: '调取 Ontology 航班异常、气象监控及飞机维保数据，自动归结主观与客观原因，输出官方通告文案。',
    status: 'active',
    creator: '刘海波 (高级运行架构师)',
    lastUpdated: '2026-07-02 18:24',
    inputs: [
      { name: 'flight_number', type: 'string', placeholder: '如 DL440' }
    ],
    testInputs: {
      flight_number: 'DL440'
    },
    blocks: [
      {
        id: 'blk-1',
        type: 'input',
        name: '航班号输入参数',
        config: { variableName: 'flight_number', dataType: 'string' }
      },
      {
        id: 'blk-2',
        type: 'query_ontology',
        name: '读取 Ontology 航班与执飞飞机维保历史',
        config: {
          queryTarget: 'ObjectType: Flight',
          queryFilter: 'id === input.flight_number'
        }
      },
      {
        id: 'blk-3',
        type: 'llm',
        name: 'LLM 延误根因深度解析',
        config: {
          modelId: 'gemini-1.5-pro',
          systemPrompt: '你是一个精通民航签派与排班优化的 AI 专家。请结合给定的航班和飞机维保历史，分析延误的根本原因并提供签派建议。',
          userPromptTemplate: '航班数据: {{blk-2.flight_data}}\n飞机情况: {{blk-2.aircraft_data}}\n请分析：\n1. 是由于天气原因还是设备故障(主客观因素)？\n2. 预计维保需要多久？\n3. 是否需要触发 Reschedule 备选预案？',
          temperature: 0.2
        }
      },
      {
        id: 'blk-4',
        type: 'ontology_action',
        name: '推荐执行 Ontology 操作: 重新指派飞机',
        config: {
          actionTypeId: 'act_reschedule_flight',
          actionMapping: {
            flight_number: 'flight_number',
            delay_reason: 'blk-3.reason'
          }
        }
      },
      {
        id: 'blk-5',
        type: 'output',
        name: '输出判定结果及建议',
        config: {
          outputSchema: '{\n  "delayType": "string",\n  "primaryCause": "string",\n  "recommendedAction": "string",\n  "notificationTemplate": "string"\n}'
        }
      }
    ]
  },
  {
    id: 'pipe-crew-compliance',
    name: '机组排班执勤时间及资质AI合规性评估',
    description: '智能评估飞行员执勤记录，检验是否违反民航局(CAAC)关于最大连续飞行小时及机型资质认证的规定。',
    status: 'active',
    creator: '张静茹 (机组运营专员)',
    lastUpdated: '2026-06-30 11:45',
    inputs: [
      { name: 'pilot_id', type: 'string', placeholder: '如 P02' }
    ],
    testInputs: {
      pilot_id: 'P02'
    },
    blocks: [
      {
        id: 'blk-c1',
        type: 'input',
        name: '飞行员ID输入',
        config: { variableName: 'pilot_id', dataType: 'string' }
      },
      {
        id: 'blk-c2',
        type: 'query_ontology',
        name: '读取 Ontology 飞行员及执飞执照资质',
        config: {
          queryTarget: 'ObjectType: Pilot',
          queryFilter: 'id === input.pilot_id'
        }
      },
      {
        id: 'blk-c3',
        type: 'llm',
        name: '合规性规则评估',
        config: {
          modelId: 'claude-3-5-sonnet',
          systemPrompt: '请严格对照民航执勤法规评估：飞行员总飞行小时，30天内飞行小时是否超限，执飞型号是否与评级一致。',
          userPromptTemplate: '飞行员档案: {{blk-c2.pilot_data}}\n执飞机型: Boeing 737-MAX9\n评估要点：\n1. 是否持有 737 型号评级？\n2. 执勤时间是否合规？',
          temperature: 0.1
        }
      },
      {
        id: 'blk-c4',
        type: 'output',
        name: '合规评估报告',
        config: {
          outputSchema: '{\n  "isCompliant": "boolean",\n  "violationDetails": "string",\n  "suggestedAlternatePilots": "string[]"\n}'
        }
      }
    ]
  }
];

export const mockAIPAgents: AIPAgent[] = [
  {
    id: 'agent-control-copilot',
    name: 'Aviation Control Copilot (航空运行协同助手)',
    role: '航空运行控制中心智能协调助理',
    description: '服务于 AOC 调度大厅。能够自动化多维检索航班、机组和飞机实体的实时状态，并在极端天气或突发特情下，协助签派员生成重排班方案及机组接替推荐。',
    avatar: 'Layers',
    modelId: 'gemini-1.5-pro',
    systemPrompt: '你是一个工作于航空运行控制中心(AOC)的超级智能助理。你拥有访问航空核心本体(Aviation Core)的权限，包括航班、飞机、飞行员和气象。你被授权在确认后建议触发重新指派航班(act_reschedule_flight)等 Ontology Actions。\n请时刻保持冷静、专业、符合安全规程的原则。所有的业务词汇应当专业（如 AOC, QAR, CAAC 规章等）。',
    assignedTools: {
      actionIds: ['act_reschedule_flight', 'act_assign_pilot'],
      functionIds: ['func_get_flight_weather', 'func_get_pilot_rating']
    },
    guardrailIds: ['gr-pii', 'gr-approval', 'gr-hallucination'],
    status: 'active',
    lastModified: '2026-07-03 10:15'
  },
  {
    id: 'agent-dispatch-guard',
    name: 'Crew Dispatch Guard (机组调度合规卫士)',
    role: '机组排班CAAC法规合规性自动化审查官',
    description: '专注于对机组排班、临期替补安排进行CAAC 121部规章的实时符合性审查，拦截任何超时、无资质或疲劳飞行的排班操作。',
    avatar: 'ShieldCheck',
    modelId: 'claude-3-5-sonnet',
    systemPrompt: '你是一个严格的飞行安全合规性审查助理。你的唯一使命是严格执行 CAAC 航空法规。对待机组执勤超时、大机型资格不符合等情况零容忍。回答应引经据典（如 121.483 条执勤限制等）。',
    assignedTools: {
      actionIds: ['act_assign_pilot'],
      functionIds: ['func_get_pilot_rating']
    },
    guardrailIds: ['gr-pii', 'gr-harm'],
    status: 'active',
    lastModified: '2026-07-01 14:30'
  }
];

export const mockAIPAuditLogs: AIPAuditLog[] = [
  {
    id: 'log-1',
    timestamp: '2026-07-03 11:32:15',
    source: 'Agent Studio',
    assetName: '航空运行协同助手',
    user: '王凯 (AOC签派总监)',
    inputTokens: 1245,
    outputTokens: 420,
    status: 'allowed',
    details: '查询 DL440 航班的执飞飞机设备缺陷清单(MEL)及天气预警。'
  },
  {
    id: 'log-2',
    timestamp: '2026-07-03 11:30:04',
    source: 'Logic Pipeline',
    assetName: '航班延误原因智能判定与排班预测',
    user: 'System (Scheduler Event)',
    inputTokens: 2540,
    outputTokens: 580,
    status: 'flagged',
    actionTaken: '内容幻觉检测标记 - 置信度 89%',
    details: '批处理中 N309AA 飞机执修时间预测偏离正常范围，需要人工确认。'
  },
  {
    id: 'log-3',
    timestamp: '2026-07-03 11:28:44',
    source: 'Agent Studio',
    assetName: '航空运行协同助手',
    user: '李明 (调度员)',
    inputTokens: 1580,
    outputTokens: 120,
    status: 'pending_approval',
    actionTaken: '触发 Ontology Action 拦截',
    details: '用户请求将航班 DL440 重新指派给飞行员 P01。此操作需要管理员确认执勤时间。'
  },
  {
    id: 'log-4',
    timestamp: '2026-07-03 11:25:12',
    source: 'Agent Studio',
    assetName: '机组调度合规卫士',
    user: '陈雪 (人力资源)',
    inputTokens: 980,
    outputTokens: 0,
    status: 'blocked',
    actionTaken: '有害指令拦截 (PII暴露阻断)',
    details: '尝试以明文格式导出飞行员 P01-P04 的个人家庭住址与电话清单。被 PII 护栏阻断。'
  }
];
