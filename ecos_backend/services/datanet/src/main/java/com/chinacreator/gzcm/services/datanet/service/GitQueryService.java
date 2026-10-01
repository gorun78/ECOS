package com.chinacreator.gzcm.services.datanet.service;

import com.chinacreator.gzcm.services.datanet.dao.GitQueryDao;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * GitQueryService — Git 查询门面（P3-A 从 gateway 迁至 datanet。PMO-E2 下沉的 JdbcTemplate 访问层）。
 *
 * <p>R2-B5 数据访问收口（架构铁律 §2.5 #1 / IR06）：本层不再直持 JDBC，全部数据访问下沉到
 * {@link GitQueryDao}（extends runtime 数据底座 {@code JdbcTemplateDaoSupport}）；本服务仅作
 * thin façade，保留原 public API，逐方法原样委托。</p>
 *
 * @author ecos-factory
 * @since PMO-49 P3-A
 */
@Service
public class GitQueryService {

    private final GitQueryDao dao;

    public GitQueryService(GitQueryDao dao) {
        this.dao = dao;
    }

    public List<Map<String, Object>> queryForList(String sql, Object... args) {
        return dao.queryForList(sql, args);
    }

    public List<Map<String, Object>> queryForList(String sql) {
        return dao.queryForList(sql);
    }

    public <T> T queryForObject(String sql, Class<T> type, Object... args) {
        return dao.queryForObject(sql, type, args);
    }
}
