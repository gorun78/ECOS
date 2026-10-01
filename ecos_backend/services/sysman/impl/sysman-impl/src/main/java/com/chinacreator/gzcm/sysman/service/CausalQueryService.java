package com.chinacreator.gzcm.sysman.service;

import com.chinacreator.gzcm.sysman.dao.CausalQueryDao;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * CausalQueryService — Causal 查询服务（PMO-E2: 从 CausalController 下沉的 JdbcTemplate 访问层）。
 * <p>铁律 §2.5：数据访问下沉到 DAO（{@link CausalQueryDao}）。
 */
@Service
public class CausalQueryService {

    private final CausalQueryDao dao;

    public CausalQueryService(CausalQueryDao dao) {
        this.dao = dao;
    }

    public Integer ping() {
        return dao.ping();
    }

    public List<Map<String, Object>> listNodes() {
        return dao.listNodes();
    }

    public List<Map<String, Object>> listEdges() {
        return dao.listEdges();
    }
}
