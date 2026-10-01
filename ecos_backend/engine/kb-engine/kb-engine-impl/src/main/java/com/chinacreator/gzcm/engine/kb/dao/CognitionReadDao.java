package com.chinacreator.gzcm.engine.kb.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 数据访问收口（R2-B2b kb 子包 / 架构铁律 §2.5 / IR06）：场景认知三态只读 JDBC 落点。
 * <p>SQL 原样搬运自原 {@code cognition.CognitionReadService#read}；读表真身由
 * {@code CognitionContractGuard} 常量传入（schema {@code ecos_cognitive.ecos_cognitive_*} 保留）。</p>
 */
@Repository
public class CognitionReadDao extends JdbcTemplateDaoSupport {

    /** 只读三态（status 白名单 IN + 可选 domain 过滤 + LIMIT）。 */
    public List<Map<String, Object>> read(String realTable, String[] validStatuses, String domain, int limit) {
        int lim = limit <= 0 ? 50 : Math.min(limit, 500);
        StringBuilder in = new StringBuilder();
        for (int i = 0; i < validStatuses.length; i++) {
            in.append(i == 0 ? "?" : ",?");
        }
        StringBuilder sql = new StringBuilder("SELECT * FROM ").append(realTable)
                .append(" WHERE status IN (").append(in).append(")");
        List<Object> args = new ArrayList<>();
        for (String v : validStatuses) {
            args.add(v);
        }
        if (domain != null && !domain.isBlank()) {
            sql.append(" AND domain = ?");
            args.add(domain.trim());
        }
        sql.append(" LIMIT ?");
        args.add(lim);
        return jdbcTemplate.queryForList(sql.toString(), args.toArray());
    }
}
