package com.chinacreator.gzcm.sysman.service;

import com.chinacreator.gzcm.sysman.dao.WorldModelGraphQueryDao;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * WorldModelGraphQueryService — WorldModelGraph 查询服务（PMO-E2: 从 WorldModelGraphController 下沉的 JdbcTemplate 访问层）。
 * <p>铁律 §2.5：数据访问下沉到 DAO（{@link WorldModelGraphQueryDao}）。
 */
@Service
public class WorldModelGraphQueryService {

    private final WorldModelGraphQueryDao dao;

    public WorldModelGraphQueryService(WorldModelGraphQueryDao dao) {
        this.dao = dao;
    }

    public Integer ping() {
        return dao.ping();
    }

    public List<Map<String, Object>> listGoals() {
        return dao.listGoals();
    }

    public List<Map<String, Object>> listCausalGraphNodes() {
        return dao.listCausalGraphNodes();
    }

    public List<Map<String, Object>> listCausalGraphEdges() {
        return dao.listCausalGraphEdges();
    }
}
