package com.chinacreator.gzcm.workspace.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * ObjectQL 查询 Service — 从 ObjectQLController 下沉的 JdbcTemplate 访问层。
 *
 * <p>职责：执行 entity 存在性校验与 ObjectQL 查询 SQL，保持与原 Controller 完全一致的 SQL 语义。</p>
 */
@Service
public class ObjectQLService {

    private static final Logger log = LoggerFactory.getLogger(ObjectQLService.class);

    private final JdbcTemplate jdbc;

    public ObjectQLService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 校验 link 目标实体是否在 ecos_ontology_entity 表中存在。
     * 返回记录数，调用方判断 ==0 即不存在。
     */
    public Integer countEntityByCode(String entityCode) {
        return jdbc.queryForObject(
            "SELECT COUNT(*) FROM ecos_ontology_entity WHERE code = ?",
            Integer.class, entityCode);
    }

    /**
     * 无参查询（params 为空时使用）。
     */
    public List<Map<String, Object>> queryForList(String sql) {
        return jdbc.queryForList(sql);
    }

    /**
     * 带参查询（params 非空时使用）。
     */
    public List<Map<String, Object>> queryForList(String sql, Object[] params) {
        return jdbc.queryForList(sql, params);
    }
}
