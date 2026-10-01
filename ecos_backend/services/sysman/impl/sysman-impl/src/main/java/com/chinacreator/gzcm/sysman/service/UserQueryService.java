package com.chinacreator.gzcm.sysman.service;

import com.chinacreator.gzcm.sysman.dao.UserQueryDao;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * UserQueryService — User 查询服务（PMO-E2: 从 UserController 下沉的 JdbcTemplate 访问层）。
 * <p>铁律 §2.5：数据访问下沉到 DAO（{@link UserQueryDao}）。
 */
@Service
public class UserQueryService {

    private final UserQueryDao dao;

    public UserQueryService(UserQueryDao dao) {
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
    public int update(String sql, Object... args) {
        return dao.update(sql, args);
    }
}
