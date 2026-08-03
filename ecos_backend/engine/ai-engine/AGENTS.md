# ai-engine — Agent运行时 + LLM网关 + 知识抽取

> 端口: **18084** | PMO: **ecos-pmo** | 依赖: runtime/llm-gateway, kb-engine-api, cognitive-engine-api

## 我负责的
- **AgentLoopService**: 多轮工具调用循环（think→act→observe→think，上限5轮）
- **ToolExecutorService**: SQL/REST/BUILTIN三种执行模式，30s超时
- **AgentSessionService**: PG持久化会话，30min空闲过期
- **AgentDelegationService**: 子Agent委托（`delegate_to_agent`内置工具，单层）
- **KnowledgeExtractorService**: KAG风格知识抽取（LLM→实体+关系+规则→SubGraph）
- **AgentLoopController**: 非流式对话 + SSE流式 + 会话CRUD

## 我暴露的端点
| 端点 | 方法 | 用途 |
|------|------|------|
| /api/v1/agent-loop/chat | POST | Agent对话(stream=false→JSON, stream=true→SSE) |
| /api/v1/agent-loop/sessions | POST | 创建会话 |
| /api/v1/agent-loop/sessions/{id} | GET | 会话详情 |
| /api/v1/agent-loop/sessions/{id}/chat | POST | 会话内对话 |
| /api/v1/knowledge/extract | POST | 知识抽取 |
| /api/v1/knowledge/extract/sources | GET | 抽取源类型列表 |
| /api/v1/knowledge/extract/history | GET | 抽取历史 |
| /api/v1/knowledge/reason | POST | 混合推理 |

## 我的数据库表
- sys_agent_session (id, agent_id, user_id, tenant_id, status, message_count, created_at, last_active_at)
- sys_agent_message (id, session_id, role, content, tool_calls, tool_results, tokens, created_at)

## 我依赖的外部端点
| 引擎 | 端点 | 用途 |
|------|------|------|
| cognitive-engine | POST :18089/api/v1/knowledge/reason | 混合推理委托 |

## 禁止
1. 不直接import其他引擎的impl模块
2. 不改LLMGatewayService接口
3. Agent Loop上限5轮
4. 不引入非Java依赖
5. Delegation单层
