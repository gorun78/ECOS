package com.chinacreator.gzcm.engine.kb.service.profile;

import java.util.List;
import java.util.Map;

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

    /**
     * 画像存量两计（只读，一次查询，WAgent Readiness {@code know.sample_size} 供数）：
     * 命中行数 + {@code sample_count} 合计。
     *
     * @param status     画像状态过滤（DRAFT/PUBLISHED/SUPERSEDED），空/null 不过滤
     * @param metricCode 指标编码过滤，空/null 不过滤
     * @param domains    域过滤集（逗号拆分后逐枚 trim），空/全空不过滤；实现侧按 {@code IN (?,...)} 参数化展开
     * @return {@code Map}，常量键 {@code profiles}(int) + {@code sampleSum}(long)（无命中行时两计为 0，禁 null）
     */
    Map<String, Object> profileStock(String status, String metricCode, List<String> domains);
}
