package com.chinacreator.gzcm.engine.kb.service.assumption;

import java.util.List;

/**
 * 情景假设存储端口（解耦生产 JdbcTemplate 实现与单测内存实现）。
 *
 * <p>生产实现走 {@code ecos_knowledge.ecos_kb_assumption}（schema 限定）；
 * 单测用内存 fake 覆盖状态机与审批/引用语义（禁 {@code @SpringBootTest}）。
 */
public interface KbAssumptionStore {

    /** 按 scenarioType / metricCode / status 过滤（条件实现侧参数化，禁字符串拼接）。 */
    List<KbAssumptionDO> query(String scenarioType, String metricCode, String status);

    /** 取同 key 的最新版本行（发布/新版本判定用）。 */
    KbAssumptionDO findLatestByKey(String assumptionKey);

    /** 取某 id 行（状态机 / 拒改判定用）。 */
    KbAssumptionDO findById(String id);

    /** 插入新版本假设行（版本化新增，绝不 UPDATE 已 ACTIVE 行 — I-6）。 */
    void insert(KbAssumptionDO row);
}
