package com.chinacreator.gzcm.engine.ai.dao;

import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

/**
 * 数据访问收口（R2-B4-ai / 架构铁律 §2.5 #1 / IR06）：Prompt 编译器的 JDBC 落点。
 * <p>SQL 原样搬运自原 {@code service.PromptCompilerServiceImpl}，只搬不改语义；
 * 上下文拼装逻辑保留在 Service 层。</p>
 */
@Repository
public class PromptCompilerDao extends JdbcTemplateDaoSupport {

    public List<Map<String, Object>> searchTableNames(String pattern, int topK) {
        return jdbcTemplate.queryForList(
            "SELECT table_name, table_comment FROM information_schema.tables " +
            "WHERE table_schema = 'public' AND table_name LIKE ? LIMIT ?",
            pattern, topK);
    }
}
