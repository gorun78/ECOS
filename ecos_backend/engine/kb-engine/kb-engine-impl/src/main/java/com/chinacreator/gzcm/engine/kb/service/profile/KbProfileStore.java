package com.chinacreator.gzcm.engine.kb.service.profile;

import java.util.List;

/**
 * 历史画像存储端口（解耦生产 JdbcTemplate 实现与单测内存实现）。
 *
 * <p>生产实现走 {@code ecos_knowledge.ecos_kb_profile}（MyBatis/注解或 JdbcTemplate，schema 限定）；
 * 单测用内存 fake 覆盖状态机与退化逻辑（禁 {@code @SpringBootTest}）。
 */
public interface KbProfileStore {

    /** 任一画像 list 查询（可按 metric/status/domain/维度前缀过滤，过滤条件在实现侧参数化，禁字符串拼接）。 */
    List<KbProfileDO> query(String metricCode, String status, String domain, String profileKeyPrefix);

    /** 取同 profileKey 的最新版本行（按 profile_version 语义比较，取最高）。 */
    KbProfileDO findLatestByKey(String profileKey);

    /** 取既有的某 key+version 行（发布/拒改判定用）。 */
    KbProfileDO findById(String id);

    /** 插入新画像行（版本化新增，绝不 UPDATE 已发布行 — I-6）。 */
    void insert(KbProfileDO row);
}
