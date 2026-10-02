package com.chinacreator.gzcm.engine.kb.service.profile;

import com.chinacreator.gzcm.engine.kb.dao.KbProfileStoreDao;

import java.util.List;

/**
 * {@link KbProfileStore} 的 JDBC 实现（schema 限定 {@code ecos_knowledge.ecos_kb_profile}，
 * 所有条件参数化防注入；金额/统计量列 NUMERIC，时间列 TIMESTAMP 于 SQL 端 epoch 转换）。
 *
 * <p>R2-B2b 数据访问收口（架构铁律 §2.5 / IR06）：raw-SQL 全部下沉至
 * {@link KbProfileStoreDao}，本类仅保留 store 端口职责：</p>
 * <ul>
 *   <li>{@code query} — 透传 DAO（动态 WHERE 在 DAO 内维持 byte-for-byte）</li>
 *   <li>{@code findLatestByKey} / {@code findById} — {@code List → head-or-null} 语义在 Store 层
 *       维持（public 签名不变）</li>
 *   <li>{@code insert} — 透传 DAO</li>
 * </ul>
 *
 * <p>由 {@link com.chinacreator.gzcm.engine.kb.service.KbProfileAssumptionConfig} 显式
 * {@code @Bean} 装配，不再标 {@code @Repository}——避免与 @Bean 定义双注册。</p>
 */
public class JdbcProfileStore implements KbProfileStore {

    private final KbProfileStoreDao dao;

    public JdbcProfileStore(KbProfileStoreDao dao) {
        this.dao = dao;
    }

    @Override
    public List<KbProfileDO> query(String metricCode, String status, String domain, String profileKeyPrefix) {
        return dao.query(metricCode, status, domain, profileKeyPrefix);
    }

    @Override
    public KbProfileDO findLatestByKey(String profileKey) {
        List<KbProfileDO> rows = dao.findLatestByKeyRows(profileKey);
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Override
    public KbProfileDO findById(String id) {
        List<KbProfileDO> rows = dao.findByIdRows(id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Override
    public void insert(KbProfileDO row) {
        dao.insert(row);
    }
}
