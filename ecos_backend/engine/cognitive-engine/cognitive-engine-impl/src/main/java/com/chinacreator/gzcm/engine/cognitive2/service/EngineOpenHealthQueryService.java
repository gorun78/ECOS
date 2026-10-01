package com.chinacreator.gzcm.engine.cognitive2.service;

import com.chinacreator.gzcm.engine.cognitive2.dao.EngineHealthDao;
import org.springframework.stereotype.Service;

/**
 * cognitive 引擎 Open Health 探活查询服务 — DB 探活 + pipeline 计数。
 *
 * <p>供 {@code CognitiveEngineOpenHealthController} 调用：三条 COUNT / SELECT 1 SQL
 * 全部收敛在 Service 层（架构铁律 §3.6：Controller 不得直接访问数据库）。
 * SQL 落点已收口至 {@link EngineHealthDao}（架构铁律 §2.5 #1 / IR06，B2-b）。</p>
 *
 * <p>异常语义：查询抛出的 {@code DataAccessException} 原样上浮，由调用方 Controller
 * 分段 try-catch 并降级 VO 字段，与既有健康检查行为一致。</p>
 */
@Service
public class EngineOpenHealthQueryService {

    private final EngineHealthDao engineHealthDao;

    public EngineOpenHealthQueryService(EngineHealthDao engineHealthDao) {
        this.engineHealthDao = engineHealthDao;
    }

    /**
     * PG (sys_man) 探活：{@code SELECT 1}，成功返回 1。
     */
    public Long probeDb() {
        return engineHealthDao.probeDb();
    }

    /**
     * {@code kb_cognitive_pipeline} 未删除行数（is_deleted=0）。
     */
    public Long countPipelines() {
        return engineHealthDao.countPipelines();
    }

    /**
     * {@code kb_cognitive_pipeline} 中 status='ACTIVE' 且未删除的行数。
     */
    public Long countActivePipelines() {
        return engineHealthDao.countActivePipelines();
    }
}
