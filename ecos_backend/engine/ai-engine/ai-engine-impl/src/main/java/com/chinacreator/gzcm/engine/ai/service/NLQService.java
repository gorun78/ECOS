package com.chinacreator.gzcm.engine.ai.service;

import com.chinacreator.gzcm.engine.ai.dao.NLQDao;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 自然语言查询 (NLQ) 服务 — 从 NLQController 下沉的访问层。
 *
 * <p>负责执行 ObjectQLParser 解析后生成的 SQL。R2-B4-ai 收口：SQL 落点下沉至 {@link NLQDao}。</p>
 */
@Service
public class NLQService {

    private final NLQDao dao;

    public NLQService(NLQDao dao) {
        this.dao = dao;
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
            return dao.queryForList(sql);
        }
        return dao.queryForList(sql, params.toArray());
    }

    public List<Map<String, Object>> queryForList(String sql) {
        return dao.queryForList(sql);
    }

    public List<Map<String, Object>> queryForList(String sql, Object[] params) {
        return dao.queryForList(sql, params);
    }
}
