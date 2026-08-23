package com.chinacreator.gzcm.engine.ai.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 自然语言查询 (NLQ) 服务 — 从 NLQController 下沉的 JdbcTemplate 访问层。
 *
 * <p>负责执行 ObjectQLParser 解析后生成的 SQL。</p>
 */
@Service
public class NLQService {

    private final JdbcTemplate jdbc;

    public NLQService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 执行 ObjectQL 解析后的 SQL 查询。
     *
     * @param sql    SQL 语句
     * @param params 参数列表（可为空）
     * @return 查询结果行
     */
    public List<Map<String, Object>> queryForList(String sql, List<Object> params) {
        if (params == null || params.isEmpty()) {
            return jdbc.queryForList(sql);
        }
        return jdbc.queryForList(sql, params.toArray());
    }
}
