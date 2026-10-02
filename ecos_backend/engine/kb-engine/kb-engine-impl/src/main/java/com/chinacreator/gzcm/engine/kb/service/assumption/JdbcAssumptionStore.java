package com.chinacreator.gzcm.engine.kb.service.assumption;

import com.chinacreator.gzcm.engine.kb.dao.KbAssumptionStoreDao;

import java.util.List;

/**
 * {@link KbAssumptionStore} 的 JDBC 实现（schema 限定 {@code ecos_knowledge.ecos_kb_assumption}，
 * 所有条件参数化；金额/值列 NUMERIC，时间列 TIMESTAMP 于 SQL 端 epoch 转换）。
 *
 * <p>R2-B2b 数据访问收口（架构铁律 §2.5 / IR06）：raw-SQL 全部下沉至
 * {@link KbAssumptionStoreDao}，本类仅保留 store 端口职责（findLatestByKey/findById 的
 * head-or-null 语义在 Store 层维持，public 签名不变）。</p>
 *
 * <p>由 {@link com.chinacreator.gzcm.engine.kb.service.KbProfileAssumptionConfig} 显式
 * {@code @Bean} 装配，不再标 {@code @Repository}——避免与 @Bean 定义双注册导致
 * kbAssumptionStore 有 2 个候选 bean。</p>
 */
public class JdbcAssumptionStore implements KbAssumptionStore {

    private final KbAssumptionStoreDao dao;

    public JdbcAssumptionStore(KbAssumptionStoreDao dao) {
        this.dao = dao;
    }

    @Override
    public List<KbAssumptionDO> query(String scenarioType, String metricCode, String status) {
        return dao.query(scenarioType, metricCode, status);
    }

    @Override
    public KbAssumptionDO findLatestByKey(String assumptionKey) {
        List<KbAssumptionDO> rows = dao.findLatestByKeyRows(assumptionKey);
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Override
    public KbAssumptionDO findById(String id) {
        List<KbAssumptionDO> rows = dao.findByIdRows(id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Override
    public void insert(KbAssumptionDO row) {
        dao.insert(row);
    }
}
