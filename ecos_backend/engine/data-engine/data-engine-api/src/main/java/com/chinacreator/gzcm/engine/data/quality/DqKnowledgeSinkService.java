package com.chinacreator.gzcm.engine.data.quality;

import com.chinacreator.gzcm.engine.data.quality.model.DqAlertVO;
import com.chinacreator.gzcm.engine.data.quality.model.DqKnowledgeEntryVO;
import com.chinacreator.gzcm.engine.data.quality.model.DqWorkOrderVO;
import com.chinacreator.gzcm.engine.data.quality.model.PageResult;

/**
 * DQ 知识沉淀服务契约（PMO-48-D T16）。
 *
 * <p>从 {@code dq_rule} / {@code dq_alert_record} / {@code dq_work_order} 沉淀知识条目
 * 至 {@code ecos_dq.dq_knowledge_entry}，供 RCA 真接 cognitive + RAG 相似检索。</p>
 *
 * <p><b>命名说明</b>：本接口为全新接口，<b>不</b> implements 任何已有 Service（铁律 1.3）。
 * T15 只建了 {@code DqReportService}（报告域），无同名 {@code DqKnowledgeSinkService}，
 * 故直接用清晰命名 {@code DqKnowledgeSinkService}。</p>
 *
 * <p><b>embedding 策略</b>：V114 把 {@code embedding} 列降为 {@code TEXT}（JSON 数组字符串
 * DIM=1024，pgvector 未启用兼容）。本服务用 MD5 + 字节展开计算哈希向量（不调 LLM / 不装
 * {@code pgvector <>\>} 插件）。</p>
 *
 * <p><b>相似检索</b>：{@link #searchSimilar(String, int)} 全表扫描（数据 < 5000 条），
 * 反序列化 embedding 后计算 Jaccard 字符二元组重叠度（不用 cosine，不用 SQL 向量运算符）。</p>
 *
 * <p>所有沉淀方法均为<b>异步 fire-and-forget</b>（{@code @Async}），不阻塞告警/工单主流程。
 * 失败打 warn 日志，不抛。</p>
 *
 * <p>Bean 名 {@code ecosDqKnowledgeSinkService}（铁律 1.3 防多 Bean 冲突）。</p>
 *
 * @author PMO-48-D T16
 */
public interface DqKnowledgeSinkService {

    /**
     * 规则知识沉淀（DRAFT→ACTIVE 时触发；本 Task 仅预置能力，审批成功后由调用方 fire-and-forget 触发）。
     *
     * @param ruleId DQ 规则 ID（dq_rule.id）
     */
    void ingestFromRule(String ruleId);

    /**
     * 告警知识沉淀（dispatchEvent 落库 dq_alert_record 后 fire-and-forget 触发）。
     *
     * @param alertId DQ 告警 ID（dq_alert_record.id）
     * @param alert   告警 VO（含 alertLevel / ruleName / assetId / message 等）
     */
    void ingestFromAlert(String alertId, DqAlertVO alert);

    /**
     * 工单知识沉淀（close 成功后 fire-and-forget 触发）。
     *
     * @param workOrderId DQ 工单 ID（dq_work_order.id）
     * @param order       工单 VO（含 title / resolutionNote / rcaResult 等）
     */
    void ingestFromWorkOrder(String workOrderId, DqWorkOrderVO order);

    /**
     * RAG 相似检索：按查询文本找 top N 条最相似知识条目。
     *
     * <p>算法：全表扫描 {@code dq_knowledge_entry}，对每条反序列化 embedding 计算
     * Jaccard 字符二元组重叠度（0.0 ~ 1.0），降序取 top N。</p>
     *
     * @param query 查询文本（如 errorMessage）
     * @param top   返回条数上限（默认 3）
     * @return top N 知识条目（按相似度降序）
     */
    PageResult<DqKnowledgeEntryVO> searchSimilar(String query, int top);
}
