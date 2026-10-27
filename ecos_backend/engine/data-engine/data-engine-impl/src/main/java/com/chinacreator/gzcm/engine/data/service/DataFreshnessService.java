package com.chinacreator.gzcm.engine.data.service;

import com.chinacreator.gzcm.engine.data.dao.PipelineRunDao;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 数据新鲜度供数面（WAgent Readiness data.staleness_days）。
 *
 * <p>只读一次 {@link PipelineRunDao#lastSucceededFinishedAt()}，封装为
 * {@code lastSucceededFinishedAt}（ISO-8601 字符串，可能为 {@code null}）+
 * {@code source = "ecos_pipeline_run"}。无任何成功行时显式返回 {@code null}
 * （不得落 0 值或估算值），避免下游把「没有数据」误判为「刚刚入湖」。</p>
 *
 * <p>不实现既有 Service 接口（域内无 freshness 契约，且 controller 按需组装
 * 单一 endpoint 的响应体，无复用诉求）；Bean 名走默认（首字母小写类名），
 * 不与既有 bean 冲突（既有 service 均带 Ecosystem 前缀 / 复数）。</p>
 */
@Service
public class DataFreshnessService {

    private final PipelineRunDao pipelineRunDao;

    public DataFreshnessService(PipelineRunDao pipelineRunDao) {
        this.pipelineRunDao = pipelineRunDao;
    }

    /** 组装响应体：lastSucceededFinishedAt=ISO 字符串或 null + source=表名。 */
    public Map<String, Object> currentFreshness() {
        Optional<Instant> last = pipelineRunDao.lastSucceededFinishedAt();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("lastSucceededFinishedAt",
                last.map(Instant::toString).orElse(null));
        payload.put("source", "ecos_pipeline_run");
        return payload;
    }
}
