package com.chinacreator.gzcm.engine.ontology.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.chinacreator.gzcm.engine.ontology.dto.OntologySourceQuery;
import com.chinacreator.gzcm.engine.ontology.dto.OntologySourceVO;

/**
 * 本体对象来源聚合查询服务 — T16-2 强类型重构。
 *
 * <p>由 {@code OntologySourceController} 调用：
 * 把 {@code ecos_entity_table_mapping} 三表 JOIN 的 SQL 从 Controller 抽到 Service 层
 * （此前 Controller 直接持有 {@code JdbcTemplate} 内部拼接 SQL，违反"Controller 不写业务"铁律）。
 *
 * <p>旧 Map 签名保留：通过 {@link #listSourcesRaw} 暴露原始行（与 T16-1 既有
 * {@code controller 仍调 Service} 的兼容模式一致）；
 * 新 {@link #listSources} 返回 {@link OntologySourceVO} 强类型。
 */
@Service
public class OntologySourceService {

    private static final Logger log = LoggerFactory.getLogger(OntologySourceService.class);

    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 500;

    private final JdbcTemplate jdbc;

    public OntologySourceService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 强类型 VO 版（T16-2）。
     *
     * @param query 可选 domainCode + limit；limit 默认 {@value #DEFAULT_LIMIT}、最大 {@value #MAX_LIMIT}
     * @return 强类型来源列表
     */
    public List<OntologySourceVO> listSources(OntologySourceQuery query) {
        int lim = Math.max(1, Math.min(query.getLimit() != null ? query.getLimit() : DEFAULT_LIMIT, MAX_LIMIT));
        String domainCode = query.getDomainCode();
        boolean hasFilter = domainCode != null && !domainCode.isBlank();

        String sql = "SELECT "
            + "  e.id          AS \"ontologyId\", "
            + "  d.code        AS \"sourceDomain\", "
            + "  m.resource_name AS \"originManifest\", "
            + "  e.created_at  AS \"createdAt\", "
            + "FROM ecos_entity_table_mapping m "
            + "INNER JOIN ecos_ontology_entity e ON e.code = m.entity_code "
            + "INNER JOIN ecos_domain d ON d.id = e.domain_id AND d.code = m.domain_code";
        if (hasFilter) {
            sql += " WHERE m.domain_code = ?";
        }
        sql += " ORDER BY m.created_at DESC LIMIT ?";

        List<Map<String, Object>> rawRows = hasFilter
            ? jdbc.queryForList(sql, domainCode, lim)
            : jdbc.queryForList(sql, lim);

        List<OntologySourceVO> voList = new ArrayList<>(rawRows.size());
        for (Map<String, Object> r : rawRows) {
            OntologySourceVO vo = new OntologySourceVO();
            vo.setOntologyId(String.valueOf(r.getOrDefault("ontologyId", "")));
            vo.setSourceDomain(String.valueOf(r.getOrDefault("sourceDomain", "")));
            vo.setOriginManifest(String.valueOf(r.getOrDefault("originManifest", "")));
            Object createdAt = r.get("createdAt");
            vo.setCreatedAt(createdAt != null ? createdAt.toString() : null);
            voList.add(vo);
        }
        return voList;
    }

    /**
     * 兼容旧 Map 形态（C1 mock / 内部消费者兼容）。
     * <p>等价于 {@link #listSources} 但返回原始 JDBC 行（Map）。
     */
    public List<Map<String, Object>> listSourcesLegacy(int limit, String domainCode) {
        List<OntologySourceVO> voList = listSources(buildQuery(domainCode, limit));
        List<Map<String, Object>> raw = new ArrayList<>(voList.size());
        for (OntologySourceVO vo : voList) {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("ontologyId", vo.getOntologyId());
            m.put("sourceDomain", vo.getSourceDomain());
            m.put("originManifest", vo.getOriginManifest());
            m.put("createdAt", vo.getCreatedAt());
            raw.add(m);
        }
        return raw;
    }

    private static OntologySourceQuery buildQuery(String domainCode, int limit) {
        OntologySourceQuery q = new OntologySourceQuery();
        q.setDomainCode(domainCode);
        q.setLimit(limit);
        return q;
    }
}
