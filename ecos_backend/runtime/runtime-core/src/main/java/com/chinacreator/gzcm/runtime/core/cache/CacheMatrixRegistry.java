package com.chinacreator.gzcm.runtime.core.cache;

import java.util.List;

/**
 * §2.9.8① 十四类缓存对象一致性矩阵的<b>逐类定案</b>（#147AL）。
 *
 * <p><b>为什么要有这张表</b>：矩阵是"建议基线"，落地时每一类必须二选一并留依据——
 * 要么已接线（给出缓存域与接线位置），要么显式不缓存（给出理由）。只有"逐类有状态、有依据"
 * 才能防止把「底座已建」宣称为「矩阵已落地」，也防止后来者凭印象重复接线。</p>
 *
 * <p><b>口径</b>：本表只是决策登记，不承载行为；行为证据在各接线点的门禁测试
 * （{@code *CacheGateTest}）。权威数据源仍在业务 DB / 已发布 Git 版本 / 领域服务，
 * 缓存一律只是加速副本（§2.9.8①）。</p>
 *
 * @author ECOS Runtime #147AL
 */
public final class CacheMatrixRegistry {

    /** 缓存接线状态。 */
    public enum Status {
        /** 已接入 {@link RuntimeCacheManager}（读面带缓存 + 写面失效）。 */
        WIRED,
        /** 显式不缓存（理由见 rationale；严禁"先空着"）。 */
        NOT_CACHED
    }

    /**
     * 一类缓存对象。
     *
     * @param index     矩阵序号（1~14）
     * @param object    对象名（与 §2.9.8① 矩阵同词）
     * @param authority 权威数据源
     * @param status    接线状态
     * @param domain    缓存域（WIRED 必填；NOT_CACHED 为 null）
     * @param wiring    接线位置（类名 + 读/写面；NOT_CACHED 为 null）
     * @param rationale 依据（WIRED = 策略要点；NOT_CACHED = 不缓存理由）
     */
    public record Entry(int index, String object, String authority, Status status,
                        String domain, String wiring, String rationale) {
    }

    private static final List<Entry> ENTRIES = List.of(
            new Entry(1, "机构组织", "业务 DB（sysman）", Status.WIRED, "sysman",
                    "OrganizationServiceImpl#getUserAffiliations + assign/remove/setPrimary 失效",
                    "读多写少；按用户+租户维度成键，归属变更后立即失效"),
            new Entry(2, "用户", "业务 DB（sysman）", Status.WIRED, "sysman",
                    "UserServiceImpl 基本档案投影 + 8 处写面失效",
                    "只缓存基本档案白名单投影，凭据/密级字段不入缓存"),
            new Entry(3, "角色权限", "业务 DB + security-engine", Status.WIRED, "sysman",
                    "PermissionCheckServiceImpl/PermissionCacheServiceImpl + PEP 读侧 + DENY 执法侧",
                    "仅加速层；撤权后按最大陈旧时间失效，命中仍经 PEP 裁决，Redis 故障 fail-closed"),
            new Entry(4, "接入元数据", "业务 DB + 已发布 Git 版本", Status.WIRED, "data-engine-metadata",
                    "MetadataCollectionService#getResources/getAllResources + 采集后失效",
                    "读多写少；采集/写入后按数据源或整域失效"),
            new Entry(5, "Pipeline", "业务 DB + 已发布 Git 版本", Status.WIRED, "data-engine-pipeline",
                    "PipelineServiceImpl#getDefinition/listDefinitions + 增删改后失效",
                    "定义体缓存；发布=commit 后失效旧快照（Git 版本指针仍为权威）"),
            new Entry(6, "质量规则", "业务 DB", Status.WIRED, "data-engine-dq-rule",
                    "DqRuleLifecycleServiceImpl#listVersions/getVersion + approve 后失效",
                    "版本快照只增不删；approve 追加新版本即失效清单"),
            new Entry(7, "本体映射", "业务 DB + 已发布 Git 版本", Status.WIRED, "ontology",
                    "OntologyService#listOntologiesVO/listAllRelationshipsVO/getRelationshipGraphVO + 写后失效",
                    "关系清单与图谱同源同寿命；本体发布以 Git 版本为准"),
            new Entry(8, "知识库索引", "领域服务（kb-engine 图谱 + pgvector）", Status.WIRED, "kb-search",
                    "KnowledgeRetrievalServiceImpl#searchArticles + 建条目/改分类后失效",
                    "仅检索结果短 TTL（30s）；键为规范化查询摘要，原始查询串不入键，高基数查询不无界缓存"),
            new Entry(9, "认知资产模型", "业务 DB（ecos_cognitive）", Status.WIRED, "cognitive-model",
                    "ModelRegistryService#listModels/getModel + 注册/冻结/门禁/发布后整域失效",
                    "版本行不可变；写路径行级 ID 与 model_id 非一一对应故整域失效"),
            new Entry(10, "心智模型", "业务 DB（ecos_cognitive）", Status.WIRED, "cognitive-belief",
                    "CognitiveBeliefService#getLatest/list/getDetail + 注册/证据更新/覆写后整域失效",
                    "写入整域失效并经 Outbox 广播 ecos:cache:invalidate，其他节点同步清理"),
            new Entry(11, "Agent 协同", "业务 DB + 已发布 Git（未接线）", Status.NOT_CACHED, null, null,
                    "不缓存：候选态须先持久化、状态类强一致；发布链未落地前一律 not_available，缓存只会掩盖该状态"),
            new Entry(12, "统计派生", "未确认单一权威源", Status.NOT_CACHED, null, null,
                    "不缓存：权威源未确认（待确认后评估），此前任何缓存都会把派生值伪装成事实"),
            new Entry(13, "任务状态", "领域服务 runtime-task", Status.NOT_CACHED, null, null,
                    "不缓存：状态写入频繁且点读成本低，缓存与 runtime 权威表互备的风险高于收益（§2.9.8① 允许按现状裁剪建议基线）"),
            new Entry(14, "审计记录", "security-engine 哈希链 + Kafka ecos.audit", Status.NOT_CACHED, null, null,
                    "不缓存：禁止缓存可篡改副本；只读镜像尚未建立，建镜像前须做哈希校验"));

    private CacheMatrixRegistry() {
    }

    public static List<Entry> entries() {
        return ENTRIES;
    }
}
