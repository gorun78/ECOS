package com.chinacreator.gzcm.engine.ai.service;

import com.chinacreator.gzcm.common.error.SystemErrorCode;
import com.chinacreator.gzcm.common.exception.DataBridgeException;
import com.chinacreator.gzcm.engine.ai.dao.AgentMetricsDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Agent 指标查询服务 — 读取 ecos_agent_metrics 和 ecos_agent_alert 表。
 *
 * <p>从 AgentMetricsController 下沉的访问层。R2-B4-ai 收口：SQL 落点下沉至 {@link AgentMetricsDao}。</p>
 */
@Service
public class AgentMetricsService {

    private static final Logger log = LoggerFactory.getLogger(AgentMetricsService.class);

    private final AgentMetricsDao dao;

    public AgentMetricsService(AgentMetricsDao dao) {
        this.dao = dao;
    }

    /**
     * 查询 Agent 的指标汇总数据。
     *
     * @param agentId Agent ID
     * @return total 行 (total_count, success_count, failure_count, avg_elapsed_ms, total_tokens_in, total_tokens_out)
     */
    public Map<String, Object> queryTotal(String agentId) {
        return dao.queryTotal(agentId);
    }

    /**
     * 查询 Agent 耗时 P50/P99 百分位。
     *
     * @param agentId Agent ID
     * @return pct 行 (p50_ms, p99_ms)
     */
    public Map<String, Object> queryPercentiles(String agentId) {
        return dao.queryPercentiles(agentId);
    }

    /**
     * 判断 agent 是否存在任何指标记录（用于 404 探测）。
     *
     * <p>主路径 {@link AgentMetricsController} 是 COUNT 永不报错（SUM 0），
     * 兼容性路径需要显式 404，因此走 exists 探测。
     *
     * <p><b>#142/B4-3e-58（差距 D-B4-88）</b>：收口前本方法在 {@code catch (Exception)} 顶层
     * {@code return true}，注释自证「宽松降级：查询失败不判 404，继续走主流程」——把「查不了」
     * 洗成「查到了」，调用方（{@code AgentMetricsCompatController}）随后按「存在」渲染空指标，
     * 违 §4.7.2「不可用 ≠ 无权限 ≠ 空结果」。本腿不能改判 {@code false}：那会把「查不了」
     * 折成「Agent 不存在 → 404」，是同一条铁律的反向违例。现改抛携带 503 ＋
     * {@code ECOS-SYS-503} 机读码的 {@link DataBridgeException}，由 {@code AiErrorAdvice}
     * 的根型腿按 {@code resolveHttpStatus} 单源透出状态行。</p>
     *
     * @return true 只要 ecos_agent_metrics 中有 ≥1 行记录
     */
    public boolean exists(String agentId) {
        try {
            Integer cnt = dao.queryExists(agentId);
            return cnt != null && cnt > 0;
        } catch (Exception e) {
            log.error("exists() 判定通道不可用, agentId={}: {}", agentId, e.getMessage(), e);
            throw new DataBridgeException(503, 503,
                    SystemErrorCode.ECOS_SYS_503 + ": Agent 指标存在性判定通道不可用");
        }
    }

    /**
     * 查询 Agent 的错误/告警记录（来自 ecos_agent_alert 表）。
     *
     * @param agentId Agent ID
     * @param limit   返回行数上限
     * @return 行列表
     */
    public List<Map<String, Object>> queryErrors(String agentId, int limit) {
        return dao.queryErrors(agentId, limit);
    }
}
