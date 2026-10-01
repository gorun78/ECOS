package com.chinacreator.gzcm.services.datanet.service;

import com.chinacreator.gzcm.services.datanet.dao.DqDashboardDao;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * DqDashboardService — DQ 看板查询门面（P3-A 从 gateway 迁至 datanet。PMO-E2 下沉的 JdbcTemplate 访问层）。
 *
 * <p>R2-B5 数据访问收口（架构铁律 §2.5 #1 / IR06）：本层不再直持 JDBC，全部数据访问下沉到
 * {@link DqDashboardDao}（extends runtime 数据底座 {@code JdbcTemplateDaoSupport}）；本服务仅作
 * thin façade，保留原 public API，逐方法原样委托。</p>
 *
 * @author ecos-factory
 * @since PMO-49 P3-A
 */
@Service
public class DqDashboardService {

    private final DqDashboardDao dao;

    public DqDashboardService(DqDashboardDao dao) {
        this.dao = dao;
    }

    public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
        return dao.query(sql, rowMapper, args);
    }

    public <T> List<T> query(String sql, RowMapper<T> rowMapper) {
        return dao.query(sql, rowMapper);
    }

    public <T> T queryForObject(String sql, Class<T> type, Object... args) {
        return dao.queryForObject(sql, type, args);
    }

    public int update(String sql, Object... args) {
        return dao.update(sql, args);
    }
}
