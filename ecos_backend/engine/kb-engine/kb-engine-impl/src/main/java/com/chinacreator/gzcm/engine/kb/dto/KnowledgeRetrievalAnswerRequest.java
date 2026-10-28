package com.chinacreator.gzcm.engine.kb.dto;

/**
 * 问答生成请求（{@code POST /api/v1/knowledge/retrieval/answer} 入参，档 B）。
 *
 * <p>{@code conversationId} 是契约里的多轮上下文位，本引擎<b>无会话存储载体</b>
 * （kb 侧零会话表、零上下文装配链）。为免"传了参数但不生效"的假能力，服务侧对该字段
 * 非空时直接 400 明示不支持，而不是静默忽略后按单轮作答。</p>
 */
public class KnowledgeRetrievalAnswerRequest {

    /** 问题（必填；空/空白由服务侧拒 400）。 */
    private String question;

    /** 多轮会话标识（可选；非空 ⇒ 400「载体缺失」，禁静默降级为单轮）。 */
    private String conversationId;

    public String getQuestion() { return question; }

    public void setQuestion(String question) { this.question = question; }

    public String getConversationId() { return conversationId; }

    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
}
