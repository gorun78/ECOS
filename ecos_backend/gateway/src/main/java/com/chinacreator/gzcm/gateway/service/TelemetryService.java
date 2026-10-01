package com.chinacreator.gzcm.gateway.service;

import com.chinacreator.gzcm.gateway.dao.TelemetryDao;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 遥测查询服务 — 从 TelemetryController 下沉的 JdbcTemplate 访问层。
 * SQL 语义与原 Controller 保持一致。
 *
 * <p>B6b 数据访问收口（架构铁律 §2.5 / IR06）：裸 SQL 全量下沉至 {@link TelemetryDao}；
 * 本 Service 减薄为编排层，公共方法签名维持不变。</p>
 */
@Service
public class TelemetryService {

    private final TelemetryDao dao;

    public TelemetryService(TelemetryDao dao) {
        this.dao = dao;
    }

    /** 查询 ecos_spans / ecos_token_usage 的记录数 */
    public Map<String, Object> queryHealthCounts() {
        return dao.queryHealthCounts();
    }

    /** Trace 列表（最近 limit 条） */
    public List<Map<String, Object>> queryTraces(int limit) {
        return dao.queryTraces(limit);
    }

    /** Trace 详情：指定 traceId 的所有 Span */
    public List<Map<String, Object>> queryTraceDetail(String traceId) {
        return dao.queryTraceDetail(traceId);
    }

    /** Token 用量汇总 */
    public Map<String, Object> queryTokenSummaryTotals(int days) {
        return dao.queryTokenSummaryTotals(days);
    }

    /** Token 用量按模型汇总 */
    public List<Map<String, Object>> queryTokenSummaryByModel(int days) {
        return dao.queryTokenSummaryByModel(days);
    }

    /** Token 用量按操作汇总 */
    public List<Map<String, Object>> queryTokenSummaryByOperation(int days) {
        return dao.queryTokenSummaryByOperation(days);
    }
}
