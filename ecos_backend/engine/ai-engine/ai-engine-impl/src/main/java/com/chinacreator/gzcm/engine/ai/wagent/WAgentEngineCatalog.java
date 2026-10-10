package com.chinacreator.gzcm.engine.ai.wagent;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Engine 同构映射与 W 域属主单源（详细设计-10 F10-02 / PRD-10 REQ-WAG-02，§十四 R-32=① 已批准）。
 *
 * <p>附件"五大 Engine" ↔ ECOS 实况服务（六引擎 + runtime 器 + workspace 场景层）的映射、
 * 每个 Engine 的**在位 REST 服务前缀**与属主 REQ 登记在本类；工具目录
 * （{@code ecos_wagent_tool_contract.engine}，V236）与 Policy Guard、Readiness 探测的
 * 目标端点校验一律引本类单源（禁散落 URL 字面量，R-43）。</p>
 *
 * <p>属主裁定（REQ-WAG-02 / R-32①）：</p>
 * <ul>
 *   <li>Decision / Option / Action 对象属主 = <b>ai-engine</b>（本册 W 域 Owner，新增对象承接附件 §8/§9）；</li>
 *   <li>cognitive 既有 {@code ecos_decision*}（V103/V151）保留，定性为附件 §16.2 的
 *       <b>Decision Basis（属 C）</b>——只读引用，不改表不改 API（只增不改）；</li>
 *   <li>security / runtime 为横切底座，不属任何 Engine 私有（卷 00/01 已定）；</li>
 *   <li>禁引入 {@code /api/v1/cognition} 前缀（X-18 → R-43，认知模型 API 正典 =
 *       cognitive-engine 的 {@code /api/v1/cognitive/*}）。</li>
 * </ul>
 *
 * <p>文档口径包名 {@code com.chinacreator.gzcm.ai.wagent} 与本包属同义（F10-01）。</p>
 *
 * <p>验收：{@code WAgentEngineCatalogTest#everyEngineMapsToLiveServicePrefix}（F10-02）。</p>
 */
public final class WAgentEngineCatalog {

    private WAgentEngineCatalog() {}

    /** 目录键：附件五大 Engine + 两横切底座。 */
    public enum EngineKey {
        DATA,
        ONTOLOGY,
        KNOWLEDGE,
        COGNITIVE,
        /** 附件 "Decision Engine" = ECOS ai-engine（W 域 Owner，铁律 §235 "Decision Action Orchestration"）。 */
        DECISION_AI,
        SECURITY,
        RUNTIME
    }

    /**
     * 单条 Engine 映射。
     *
     * @param key               目录键
     * @param attachmentName    附件 Engine 名
     * @param ecosService       ECOS 服务名（JAR 归属注释含部署端口，PRD REQ-WAG-02 表口径）
     * @param livePathPrefixes  在位 REST 服务前缀（只登记全仓实测在位者；横切门面型条目为空）
     * @param ownerDocs         属主卷/REQ 锚点
     * @param writeChannel      W 域触达该 Engine 的写通道口径
     * @param readOnlyFromW     W 域是否只读（Decision Basis 类引用面）
     */
    public record EngineEntry(EngineKey key,
                              String attachmentName,
                              String ecosService,
                              List<String> livePathPrefixes,
                              String ownerDocs,
                              String writeChannel,
                              boolean readOnlyFromW) {

        public EngineEntry {
            livePathPrefixes = Collections.unmodifiableList(livePathPrefixes);
        }

        /** 给定 REST 路径是否落在本 Engine 的在位前缀族内（前缀以 "/" 结尾保证段边界匹配）。 */
        public boolean ownsPath(String restPath) {
            if (restPath == null) {
                return false;
            }
            for (String prefix : livePathPrefixes) {
                if (restPath.startsWith(prefix)) {
                    return true;
                }
            }
            return false;
        }
    }

    // ── 映射名单源（前缀取自全仓 Controller @RequestMapping 实测，见各条注释）──────
    private static final Map<EngineKey, EngineEntry> ENTRIES = buildEntries();

    private static Map<EngineKey, EngineEntry> buildEntries() {
        Map<EngineKey, EngineEntry> m = new LinkedHashMap<>();
        // Data Engine：datanet JAR :18082（卷 02）。在位前缀族：facts(/api/v1/datanet)、
        // transform/schema(/api/v1/engine/data)、DQ 治理(/api/v1/dq)、管道(/api/v1/pipeline)。
        m.put(EngineKey.DATA, new EngineEntry(EngineKey.DATA, "Data Engine",
                "data-engine (datanet :18082)",
                List.of("/api/v1/datanet/", "/api/v1/engine/data/", "/api/v1/dq/", "/api/v1/pipeline/"),
                "卷 02 REQ-DATA-*；Readiness D 层探测 data.check_coverage/run_dq（F10-08）",
                "候选写回经属主 Engine REST 写端点（CandidateCommitPort，F10-23；Agent 不写对方表）",
                false));
        // Ontology Engine：buszhi JAR :18083（卷 03）。在位前缀族：/api/v1/ontology、/api/v1/engine/ontology。
        m.put(EngineKey.ONTOLOGY, new EngineEntry(EngineKey.ONTOLOGY, "Ontology Engine",
                "ontology-engine (buszhi :18083)",
                List.of("/api/v1/ontology/", "/api/v1/engine/ontology/"),
                "卷 03 REQ-ONT-*；口径主权在 ecos_ontology（Goal.caliber_version 只读引用，F10-07）",
                "候选（实体/关系/指标）经属主 Engine 写端点；事件复用 ONTOLOGY_PUBLISHED topic（§9.1.3，禁另立）",
                false));
        // Knowledge Engine：dccheng JAR :18086（卷 04）。在位前缀族：/api/v1/knowledge。
        m.put(EngineKey.KNOWLEDGE, new EngineEntry(EngineKey.KNOWLEDGE, "Knowledge Engine",
                "kb-engine (dccheng :18086)",
                List.of("/api/v1/knowledge/"),
                "卷 04 REQ-KB-*；Readiness K 层 know.select_profiles（F10-08）",
                "候选（知识项/假设/情景）经属主 Engine 写端点；KB 侧 kb_extract_candidate 以 CandidateAdapterPort 只读映射（F10-20）",
                false));
        // Cognition Engine：:18089（卷 05）。在位前缀族：/api/v1/cognitive（X-18 复验）、
        // /api/v1/engine/cognitive（runtime-monitor 引擎健康形态）。禁 cognition 前缀。
        m.put(EngineKey.COGNITIVE, new EngineEntry(EngineKey.COGNITIVE, "Cognition Engine",
                "cognitive-engine (:18089)",
                List.of("/api/v1/cognitive/", "/api/v1/engine/cognitive/"),
                "卷 05 REQ-COG-*；CM Registry(/api/v1/cognitive/models) 在位",
                "Situation 正式认知对象只经 cognitive-engine api 门面；ecos_decision* 定性 Decision Basis = 只读引用（R-32①）",
                true));
        // Decision Engine（W）：ai-engine（aiming JAR :18084），本册 W 域 Owner。
        // 新面只挂 /api/v1/wagent（WAgentApiPaths.BASE_PREFIX，§5.1 只增不改）。
        m.put(EngineKey.DECISION_AI, new EngineEntry(EngineKey.DECISION_AI, "Decision Engine（附件 §8/§9）",
                "ai-engine (aiming :18084)",
                List.of(WAgentApiPaths.BASE_PREFIX + "/"),
                "REQ-WAG-02/R-32①：Decision/Option/Action 属主唯一 = ai-engine（只增不改）",
                "本域即属主：写经 wagent 服务层 + wagent DAO（ecos_ai.ecos_wagent_*，ST07）",
                false));
        // security 横切：Policy Guard 五检查点调 POST /api/v1/security/policy-engine/evaluate
        // （默认 DENY 三态，F10-22；形态随 R-18 REST 裁定，禁同 JVM 反射）。
        m.put(EngineKey.SECURITY, new EngineEntry(EngineKey.SECURITY, "（附件无对应，Security/Policy 横切）",
                "security-engine（横切，不属任何 Engine 私有）",
                List.of("/api/v1/security/"),
                "卷 00/01；RLS/CLS 经其 REST（REQ-WAG-04）",
                "只调用不写库；security 不可用 = DENY（L1+ 拒绝）",
                true));
        // runtime 底座横切：LLM/存储/调度/监控/事件/Git 五器单通道（铁律 §2.5）。
        // W 域触达形态为**进程内门面/factory**（ITaskManagementService、llm-gateway、runtime-event…），
        // 引擎间健康面走 /api/v1/engine/* 前缀族（分散登记于各 Engine 条目），故本条 livePathPrefixes 为空 ⇒
        // isLiveServicePrefix 校验对门面型条目以「非 REST 直连」视为通过（F10-03 红线 2 的本意）。
        m.put(EngineKey.RUNTIME, new EngineEntry(EngineKey.RUNTIME, "（附件 runtime 器）",
                "runtime-access / llm-gateway / runtime-task / runtime-monitor / runtime-event（横切）",
                List.of(),
                "铁律 v2.0 §2.5 八条；调度只经 ITaskManagementService（F10-09/15）",
                "底座门面唯一通道；Agent 侧禁自建（NoDirectEngineTableAccessTest 同族）",
                false));
        return Collections.unmodifiableMap(m);
    }

    /** 全量目录（不可变）。 */
    public static List<EngineEntry> entries() {
        return List.copyOf(ENTRIES.values());
    }

    public static Optional<EngineEntry> entry(EngineKey key) {
        return Optional.ofNullable(ENTRIES.get(key));
    }

    /** {@code ecos_wagent_tool_contract.engine}（V236 VARCHAR(32)）落库代号单源 = 枚举名小写。 */
    public static String contractEngineCode(EngineKey key) {
        return key.name().toLowerCase(Locale.ROOT);
    }

    /** V236 注释口径的引擎代号集合（data/ontology/kb/cognitive/ai/security 六名 + workspace 场景层不参与工具面）。 */
    public static Set<String> contractEngineCodesForDdlCheck() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(
                Arrays.asList("data", "ontology", "kb", "cognitive", "ai", "security")));
    }

    /**
     * 逐 Engine 在位服务前缀校验（F10-02 验收基元）：
     * REST 路径必须命中且**只命中一个** Engine 的前缀族；门面型条目（空前缀，如 RUNTIME）不参与
     * REST 判定。禁 {@code /api/v1/cognition} 与 legacy {@code /api/v1/agent} 族出现在新契约里。
     *
     * @return 命中的 Engine 键；未命中或命中黑名单前缀返回 empty
     */
    public static Optional<EngineKey> resolveLiveServicePrefix(String restPath) {
        if (restPath == null || restPath.isBlank()) {
            return Optional.empty();
        }
        for (String banned : BANNED_FOREIGN_PREFIXES) {
            if (restPath.startsWith(banned)) {
                return Optional.empty();
            }
        }
        EngineKey hit = null;
        for (EngineEntry e : ENTRIES.values()) {
            if (e.ownsPath(restPath)) {
                if (hit != null) {
                    return Optional.empty(); // 前缀族重叠 = 单源被破坏，fail-loud
                }
                hit = e.key;
            }
        }
        return Optional.ofNullable(hit);
    }

    /** 该 REST 路径是否为「在位 Engine 服务前缀」可路由形态（校验方法，测试与契约登记共用）。 */
    public static boolean isLiveServicePrefix(String restPath) {
        return resolveLiveServicePrefix(restPath).isPresent();
    }

    /**
     * 禁入前缀黑名单（X-18/X-2 → R-43）：
     * {@code /api/v1/cognition}（三向重叠）与 legacy {@code /api/v1/agent} 族不得作新契约目标。
     */
    public static final List<String> BANNED_FOREIGN_PREFIXES = List.of(
            "/api/v1/cognition",
            "/api/v1/agent",   // legacy 编排面只登记不迁移（§5.1）
            "/api/agent"
    );
}
