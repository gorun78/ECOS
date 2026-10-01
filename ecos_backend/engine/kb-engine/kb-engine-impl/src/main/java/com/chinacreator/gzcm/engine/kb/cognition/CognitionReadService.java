package com.chinacreator.gzcm.engine.kb.cognition;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 场景认知三态只读读取 — 详细设计-04 F04-08 / REQ-KB-04。
 *
 * <p>kb 对场景侧<b>只读</b>开放 hypothesis/belief/mind。读表真身见 {@link CognitionContractGuard}
 * 常量映射（{@code ecos_cognitive.ecos_cognitive_*}）。认知三态属 <b>认知引擎独立持久</b>（控制域外），
 * standard 档可能不存在对应表 → 读取命中 {@code DataAccessException} 时<b>返回空列表而非 404/500</b>
 *（认知数据"场景侧不可用"是合法空态，不是 kb_nav_* 那种结构性表缺失）。
 *
 * <p>写路径一律不在此：写必须经 cognitive 引擎，kb 侧写代理会被 {@link CognitionContractGuard#assertReadOnly()}
 * 拒绝（405 ECOS-KB-070）。
 */
@Service
public class CognitionReadService {

    private static final Logger log = LoggerFactory.getLogger(CognitionReadService.class);

    private final JdbcTemplate jdbcTemplate;
    private final CognitionContractGuard guard;

    public CognitionReadService(JdbcTemplate jdbcTemplate, CognitionContractGuard guard) {
        this.jdbcTemplate = jdbcTemplate;
        this.guard = guard;
    }

    /** 只读 hypothesis 三态（status 白名单内），domain 过滤，空/表缺失 → 空列表。 */
    public List<Map<String, Object>> hypotheses(String domain, int limit) {
        return read(CognitionContractGuard.HYPOTHESIS_REAL_TABLE, domain, limit);
    }

    /** 只读 belief 三态。 */
    public List<Map<String, Object>> beliefs(String domain, int limit) {
        return read(CognitionContractGuard.BELIEF_REAL_TABLE, domain, limit);
    }

    /** 只读 mind 三态。 */
    public List<Map<String, Object>> minds(String domain, int limit) {
        return read(CognitionContractGuard.MIND_REAL_TABLE, domain, limit);
    }

    private List<Map<String, Object>> read(String realTable, String domain, int limit) {
        if (jdbcTemplate == null) {
            return new ArrayList<>();
        }
        int lim = limit <= 0 ? 50 : Math.min(limit, 500);
        String[] valid = HypothesisStatus.validStatusCodes();
        StringBuilder in = new StringBuilder();
        for (int i = 0; i < valid.length; i++) {
            in.append(i == 0 ? "?" : ",?");
        }
        StringBuilder sql = new StringBuilder("SELECT * FROM ").append(realTable)
                .append(" WHERE status IN (").append(in).append(")");
        List<Object> args = new ArrayList<>();
        for (String v : valid) {
            args.add(v);
        }
        if (domain != null && !domain.isBlank()) {
            sql.append(" AND domain = ?");
            args.add(domain.trim());
        }
        sql.append(" LIMIT ?");
        args.add(lim);
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), args.toArray());
            return rows == null ? new ArrayList<>() : rows;
        } catch (org.springframework.dao.DataAccessException e) {
            // 认知三态表可能不存在（standard 档 / 场景未启用）→ 合法空态，非结构性表缺失
            log.debug("认知三态读取降级为空 table={} domain={}: {}", realTable, domain, e.getMessage());
            return new ArrayList<>();
        }
    }
}
