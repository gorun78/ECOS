package com.chinacreator.gzcm.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * #146aj/#144 门禁批 - Mapper/运行期 SQL 方言面全源门禁（裁决 D-#144A-2 指定载体
 * {@code SqlDialectDerivationTest} 的落地实现）。
 *
 * <p><b>差距来源</b>：差距文档 篇6 7.7 节「kb/cognitive 多处 jsonb 强转、ILIKE 均无人守护」；
 * TOP 风险行点名 {@code HypothesisDao} 的 {@code ?::jsonb}、{@code DecisionDao} 的
 * {@code ILIKE}「属 Mapper/运行期 SQL 面，判据归本类（与 DDL 面分源）」。既有载体两枚都不覆盖
 * 本面：{@link Mc03TimeFormTest} 判 DDL 脚本文本面；cognitive 侧 CognitiveSqlDialectTest
 * （F05-13 M0）把扫描面白名单收窄到 CogScope 十余枚类，legacy 全域收敛被其 javadoc 自登记为
 * M1 项。按 R9 补本类。</p>
 *
 * <p><b>判定面与 DDL 面互不引用计数</b>（D-#144A-2）：只判生产源树 -
 * 任意层级 src/main/java 下的 java 源（含 MyBatis 注解内嵌 SQL 与注解内嵌 sql.xml）与
 * 任意层级 src/main/resources 下的 xml；不判 DDL 单源目录（归 Mc03/DdlShape 门）、
 * 不判 test 面（test 的 SQL 是夹具）、不判 archive 与 target 目录。</p>
 *
 * <p><b>Java 侧口径</b>：Java 的方法引用与 SQL cast 同形（全仓实扫方法引用 40+ 枚/类），
 * 故 cast 判据只字认字符串字面量与文本块里的 {@code ::}；代码区的一律不参。字面量面经
 * 「剥注释 + 保留字面量本体」两段清洗（与 ErrorCodeMachineCodeSingleSourceTest 同法，
 * 本仓惯例不自引 test-jar，自带实现）。</p>
 *
 * <p><b>XML 侧口径</b>：剥 XML 注释后全文判（EcosDataLineageMapper.xml 的 ILIKE 只存在于
 * XML 注释内，必须剥，否则把文档当违例）。</p>
 *
 * <p><b>现处置形态实测（2026-10-30，本批取证，主源树）</b>：全仓 databaseId 零使用
 * 表示 MC03 要求的 databaseId 方言片段或 Adapter 层不存在，故下列八族全部按
 * 「未处置违例存量」登记，判据 = <b>文件级名录双向相等</b>（出现新文件即红；
 * 已登记文件被收口也必须同批收缩名录；同文件内增减也红）：</p>
 * <pre>
 *   A_SQL_CAST  CANNED_CASTS 类型 token 裸强转（Java 字面量/文本块；XML 全文）
 *   C_UPSERT    ON CONFLICT + 伴生 EXCLUDED 计数合计（upsert）
 *   B_ILIKE     大小写不敏感 LIKE（MC03 点名，改 lower() LIKE）
 *   D_RETURNING 裸 RETURNING（无 databaseId 分支）
 *   E_GEN_UUID  gen_random_uuid()
 *   F_JSON_FB   指针解析 / jsonb 函数（dry-run 实测有命中面，判据自检活）
 *   G_REGCLASS  to_regclass()（dry-run 实测有命中面，同纪律）
 *   H_DIST_ON   DISTINCT ON
 * </pre>
 * <b>名录值是实测基线（棘轮，只收紧不放宽）</b>：收口某枚违例（如 ILIKE 改
 * {@code lower() LIKE}）后同批改本名录；新增任何一枚都先红再裁决，禁把新违例直接
 * 补进名录（用门禁给自己放行）。
 */
@Timeout(10)
@DisplayName("#146aj/SqlDialectDerivation：运行期 SQL 方言面（Java 字面量+文本块 / Mapper XML）全源防控门")
class SqlDialectDerivationTest {

    // 类型 token 闭集（CANNED_CASTS）：全仓实扫字符串字面量里出现过的全部取消。
    // 先例值证据：jsonb/text/bigint/date/numeric/interval/int8/bytea/timestamp/varchar
    // （2026-10-30 grep 词频统计）；闭集外的类型（如 citext / uuid）出现即红，
    // 裁决后再扩集（扩集与登记同批）。
    private static final Pattern CANNED_CASTS = Pattern.compile(
            "(?i)::(jsonb|text|bigint|date|numeric|interval|int8|bytea|timestamp|varchar)\\b");

    private static final Pattern ON_CONFLICT = Pattern.compile("\\bON\\s+CONFLICT\\b");
    private static final Pattern EXCLUDED = Pattern.compile("\\bEXCLUDED\\b");
    private static final Pattern ILIKE = Pattern.compile("\\bILIKE\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern RETURNING = Pattern.compile("\\bRETURNING\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern GEN_UUID = Pattern.compile("gen_random_uuid\\s*\\(", Pattern.CASE_INSENSITIVE);
    private static final Pattern JSON_FB = Pattern.compile("->>|jsonb_[a-z0-9_]+\\s*\\(");
    private static final Pattern REGCLASS = Pattern.compile("to_regclass\\s*\\(", Pattern.CASE_INSENSITIVE);
    private static final Pattern DIST_ON = Pattern.compile("\\bDISTINCT\\s+ON\\b", Pattern.CASE_INSENSITIVE);

    /** 八族规则名（顺序即断言遍历顺序）。 */
    private static final List<String> RULE_NAMES =
            List.of("A_SQL_CAST", "C_UPSERT", "B_ILIKE", "D_RETURNING",
                    "E_GEN_UUID", "F_JSON_FB", "G_REGCLASS", "H_DIST_ON");

    // 文件级名录（双向相等；键 = repo-relative 路径，斜杠型；值 = 该文件命中数）。
    // 2026-10-30 dry-run diff 全量回读回填：Java 面百文件存量登记为棘轮基线；
    // G_REGCLASS 族（MindAssetDao/MindUpdateDao/DqHealthCheckDao/DqRuleProbeDao 等）经本门
    // 点名，其中 MindAssetDao/MindUpdateDao 恰为 R4 Mind 面（台账 v1.0.172 句法点名处）。
    /** Java 生产源树内嵌 SQL（MyBatis 注解 / JdbcTemplate 常量 / text block 字面量）。 */
    static final Map<String, Map<String, Integer>> JAVA_PIN = build(RULE_NAMES, Map.ofEntries(
            Map.entry("ecos_backend/engine/ai-engine/ai-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ai/agent/mesh/knowledge/repository/KnowledgeNodeRepository.java", Map.of( "A_SQL_CAST", 2,  "B_ILIKE", 2,  "F_JSON_FB", 1)),
            Map.entry("ecos_backend/engine/ai-engine/ai-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ai/agent/mesh/repository/AgentRegistryRepository.java", Map.of( "A_SQL_CAST", 4)),
            Map.entry("ecos_backend/engine/ai-engine/ai-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ai/agent/mesh/repository/MissionRepository.java", Map.of( "A_SQL_CAST", 2)),
            Map.entry("ecos_backend/engine/ai-engine/ai-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ai/agent/mesh/repository/MissionTaskRepository.java", Map.of( "A_SQL_CAST", 2)),
            Map.entry("ecos_backend/engine/ai-engine/ai-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ai/controller/DiagnosticAgentController.java", Map.of( "A_SQL_CAST", 1)),
            Map.entry("ecos_backend/engine/ai-engine/ai-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ai/dao/AgentMetricsDao.java", Map.of( "A_SQL_CAST", 3)),
            Map.entry("ecos_backend/engine/ai-engine/ai-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ai/dao/AgentSessionDao.java", Map.of( "A_SQL_CAST", 10)),
            Map.entry("ecos_backend/engine/ai-engine/ai-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ai/dao/DataSeedDao.java", Map.of( "A_SQL_CAST", 3)),
            Map.entry("ecos_backend/engine/ai-engine/ai-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ai/dao/DiagnosticAgentDao.java", Map.of( "A_SQL_CAST", 1)),
            Map.entry("ecos_backend/engine/ai-engine/ai-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ai/dao/GuardrailsPolicyDao.java", Map.of( "A_SQL_CAST", 1)),
            Map.entry("ecos_backend/engine/cognitive-engine/cognitive-engine-impl/src/main/java/com/chinacreator/gzcm/engine/cognitive2/dao/BeliefDao.java", Map.of( "A_SQL_CAST", 1)),
            // 台账行 456/434 指名违例点：HypothesisDao 的 ?::jsonb 强转、DecisionDao 的 ILIKE>=2（首腿前种子已登记，值经 dry-run 对账一致）。
            Map.entry("ecos_backend/engine/cognitive-engine/cognitive-engine-impl/src/main/java/com/chinacreator/gzcm/engine/cognitive2/dao/HypothesisDao.java", Map.of("A_SQL_CAST", 1)),
            Map.entry("ecos_backend/engine/cognitive-engine/cognitive-engine-impl/src/main/java/com/chinacreator/gzcm/engine/cognitive2/dao/DecisionDao.java", Map.of("B_ILIKE", 2)),
            Map.entry("ecos_backend/engine/cognitive-engine/cognitive-engine-impl/src/main/java/com/chinacreator/gzcm/engine/cognitive2/dao/CognitivePipelineDao.java", Map.of( "A_SQL_CAST", 2)),
            Map.entry("ecos_backend/engine/cognitive-engine/cognitive-engine-impl/src/main/java/com/chinacreator/gzcm/engine/cognitive2/dao/EvidenceDao.java", Map.of( "A_SQL_CAST", 3,  "F_JSON_FB", 1)),
            Map.entry("ecos_backend/engine/cognitive-engine/cognitive-engine-impl/src/main/java/com/chinacreator/gzcm/engine/cognitive2/dao/InvalidationDao.java", Map.of( "A_SQL_CAST", 1)),
            Map.entry("ecos_backend/engine/cognitive-engine/cognitive-engine-impl/src/main/java/com/chinacreator/gzcm/engine/cognitive2/dao/MindAssetDao.java", Map.of( "G_REGCLASS", 2)),
            Map.entry("ecos_backend/engine/cognitive-engine/cognitive-engine-impl/src/main/java/com/chinacreator/gzcm/engine/cognitive2/dao/MindUpdateDao.java", Map.of( "G_REGCLASS", 2)),
            Map.entry("ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/dao/AssetDao.java", Map.of( "A_SQL_CAST", 18,  "B_ILIKE", 5)),
            Map.entry("ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/dao/CatalogDao.java", Map.of( "B_ILIKE", 6)),
            Map.entry("ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/dao/DataLineageDao.java", Map.of( "A_SQL_CAST", 5,  "C_UPSERT", 2,  "F_JSON_FB", 2)),
            Map.entry("ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/dao/DqRuleIssueDao.java", Map.of( "A_SQL_CAST", 2,  "F_JSON_FB", 1)),
            Map.entry("ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/dao/PipelineDefinitionDao.java", Map.of( "A_SQL_CAST", 5)),
            Map.entry("ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/dao/PipelineExecutionDao.java", Map.of( "A_SQL_CAST", 1,  "C_UPSERT", 12)),
            Map.entry("ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/dao/PipelineFunctionDao.java", Map.of( "A_SQL_CAST", 3,  "C_UPSERT", 4,  "B_ILIKE", 2)),
            Map.entry("ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/dao/PipelineGitDao.java", Map.of( "A_SQL_CAST", 3,  "C_UPSERT", 2)),
            Map.entry("ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/dao/PipelineRunDao.java", Map.of( "A_SQL_CAST", 5)),
            Map.entry("ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/dao/PipelineTaskDao.java", Map.of( "A_SQL_CAST", 3,  "C_UPSERT", 2)),
            Map.entry("ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/dao/QualityDao.java", Map.of( "A_SQL_CAST", 3)),
            Map.entry("ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/dao/QueryExecutionDao.java", Map.of( "A_SQL_CAST", 2)),
            Map.entry("ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/dao/SchemaDetectionDao.java", Map.of( "A_SQL_CAST", 2,  "H_DIST_ON", 1)),
            Map.entry("ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/dao/UdfDao.java", Map.of( "A_SQL_CAST", 2)),
            Map.entry("ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/governance/dao/DataGovernanceCandidateDao.java", Map.of( "A_SQL_CAST", 1)),
            Map.entry("ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/quality/dao/DqAlertDao.java", Map.of( "A_SQL_CAST", 5)),
            Map.entry("ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/quality/dao/DqAutoRepairDao.java", Map.of( "A_SQL_CAST", 1)),
            Map.entry("ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/quality/dao/DqHealthCheckDao.java", Map.of( "A_SQL_CAST", 1,  "G_REGCLASS", 1)),
            Map.entry("ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/quality/dao/DqKnowledgeSinkDao.java", Map.of( "A_SQL_CAST", 2)),
            Map.entry("ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/quality/dao/DqRuleLifecycleDao.java", Map.of( "A_SQL_CAST", 2)),
            Map.entry("ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/quality/dao/DqScheduleDao.java", Map.of( "A_SQL_CAST", 3)),
            Map.entry("ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/quality/dao/DqScoreDao.java", Map.of( "A_SQL_CAST", 3,  "C_UPSERT", 4)),
            Map.entry("ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/quality/dao/DqWorkOrderDao.java", Map.of( "A_SQL_CAST", 2)),
            Map.entry("ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/quality/mapper/DqRuleMapper.java", Map.of( "A_SQL_CAST", 2)),
            Map.entry("ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/quality/mapper/DqScheduleMapper.java", Map.of( "A_SQL_CAST", 3,  "C_UPSERT", 1)),
            Map.entry("ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/quality/probe/DqRuleProbeDao.java", Map.of( "A_SQL_CAST", 1,  "G_REGCLASS", 1)),
            Map.entry("ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/quality/probe/DqRuleProbeExecutorImpl.java", Map.of( "A_SQL_CAST", 6)),
            Map.entry("ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/quality/service/DqWorkOrderServiceImpl.java", Map.of( "B_ILIKE", 2)),
            Map.entry("ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/service/PipelineExecutionEngine.java", Map.of( "C_UPSERT", 1)),
            Map.entry("ecos_backend/engine/kb-engine/kb-engine-impl/src/main/java/com/chinacreator/gzcm/engine/kb/dao/ComplianceRuleDao.java", Map.of( "A_SQL_CAST", 1)),
            Map.entry("ecos_backend/engine/kb-engine/kb-engine-impl/src/main/java/com/chinacreator/gzcm/engine/kb/dao/EntityLinkerDao.java", Map.of( "B_ILIKE", 1,  "F_JSON_FB", 1)),
            Map.entry("ecos_backend/engine/kb-engine/kb-engine-impl/src/main/java/com/chinacreator/gzcm/engine/kb/dao/KBExtractScheduleDao.java", Map.of( "A_SQL_CAST", 3,  "C_UPSERT", 7,  "D_RETURNING", 1)),
            Map.entry("ecos_backend/engine/kb-engine/kb-engine-impl/src/main/java/com/chinacreator/gzcm/engine/kb/dao/KbAssumptionStoreDao.java", Map.of( "C_UPSERT", 1)),
            Map.entry("ecos_backend/engine/kb-engine/kb-engine-impl/src/main/java/com/chinacreator/gzcm/engine/kb/dao/KbEntityInstanceExtractionServiceDao.java", Map.of( "A_SQL_CAST", 2,  "C_UPSERT", 21,  "D_RETURNING", 1,  "H_DIST_ON", 1)),
            Map.entry("ecos_backend/engine/kb-engine/kb-engine-impl/src/main/java/com/chinacreator/gzcm/engine/kb/dao/KbOntologySnapshotDao.java", Map.of( "A_SQL_CAST", 2,  "C_UPSERT", 5)),
            Map.entry("ecos_backend/engine/kb-engine/kb-engine-impl/src/main/java/com/chinacreator/gzcm/engine/kb/dao/KbProfileStoreDao.java", Map.of( "C_UPSERT", 1)),
            Map.entry("ecos_backend/engine/kb-engine/kb-engine-impl/src/main/java/com/chinacreator/gzcm/engine/kb/dao/KgMapperDao.java", Map.of( "F_JSON_FB", 4,  "H_DIST_ON", 1)),
            Map.entry("ecos_backend/engine/kb-engine/kb-engine-impl/src/main/java/com/chinacreator/gzcm/engine/kb/dao/KgSyncLogDao.java", Map.of( "H_DIST_ON", 1)),
            Map.entry("ecos_backend/engine/kb-engine/kb-engine-impl/src/main/java/com/chinacreator/gzcm/engine/kb/dao/NavTaxonDao.java", Map.of( "A_SQL_CAST", 5,  "E_GEN_UUID", 2)),
            Map.entry("ecos_backend/engine/kb-engine/kb-engine-impl/src/main/java/com/chinacreator/gzcm/engine/kb/dao/ProfileDao.java", Map.of( "C_UPSERT", 1)),
            Map.entry("ecos_backend/engine/kb-engine/kb-engine-impl/src/main/java/com/chinacreator/gzcm/engine/kb/doc/mapper/KbDocChunkMapper.java", Map.of( "C_UPSERT", 8)),
            Map.entry("ecos_backend/engine/kb-engine/kb-engine-impl/src/main/java/com/chinacreator/gzcm/engine/kb/doc/mapper/KbDocMapper.java", Map.of( "C_UPSERT", 10)),
            Map.entry("ecos_backend/engine/kb-engine/kb-engine-impl/src/main/java/com/chinacreator/gzcm/engine/kb/repo/QueryEmbeddingHelper.java", Map.of( "B_ILIKE", 2)),
            Map.entry("ecos_backend/engine/kb-engine/kb-engine-impl/src/main/java/com/chinacreator/gzcm/engine/kb/repository/ComplianceRuleMapper.java", Map.of( "A_SQL_CAST", 16)),
            Map.entry("ecos_backend/engine/kb-engine/kb-engine-impl/src/main/java/com/chinacreator/gzcm/engine/kb/repository/ExpertRuleMapper.java", Map.of( "A_SQL_CAST", 6)),
            Map.entry("ecos_backend/engine/kb-engine/kb-engine-impl/src/main/java/com/chinacreator/gzcm/engine/kb/repository/KnowledgeArticleMapper.java", Map.of( "B_ILIKE", 2)),
            Map.entry("ecos_backend/engine/kb-engine/kb-engine-impl/src/main/java/com/chinacreator/gzcm/engine/kb/repository/KnowledgeEmbeddingMapper.java", Map.of( "C_UPSERT", 15,  "B_ILIKE", 1)),
            Map.entry("ecos_backend/engine/kb-engine/kb-engine-impl/src/main/java/com/chinacreator/gzcm/engine/kb/repository/KnowledgeNodeMapper.java", Map.of( "B_ILIKE", 1)),
            Map.entry("ecos_backend/engine/kb-engine/kb-engine-impl/src/main/java/com/chinacreator/gzcm/engine/kb/service/ExtractionSourceLoader.java", Map.of( "D_RETURNING", 1)),
            Map.entry("ecos_backend/engine/ontology-engine/ontology-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ontology/controller/OntologyCompatController.java", Map.of( "D_RETURNING", 1)),
            Map.entry("ecos_backend/engine/ontology-engine/ontology-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ontology/controller/OntologyMappingController.java", Map.of( "A_SQL_CAST", 2)),
            Map.entry("ecos_backend/engine/ontology-engine/ontology-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ontology/dao/ActionTypeDao.java", Map.of( "A_SQL_CAST", 1)),
            Map.entry("ecos_backend/engine/ontology-engine/ontology-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ontology/dao/AutoDiscoverDao.java", Map.of( "A_SQL_CAST", 2,  "C_UPSERT", 1)),
            Map.entry("ecos_backend/engine/ontology-engine/ontology-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ontology/dao/GlossaryDao.java", Map.of( "A_SQL_CAST", 6,  "B_ILIKE", 3,  "D_RETURNING", 1)),
            Map.entry("ecos_backend/engine/ontology-engine/ontology-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ontology/dao/GlossaryRelationDao.java", Map.of( "D_RETURNING", 1)),
            Map.entry("ecos_backend/engine/ontology-engine/ontology-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ontology/dao/LineageEventDao.java", Map.of( "A_SQL_CAST", 4)),
            Map.entry("ecos_backend/engine/ontology-engine/ontology-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ontology/dao/OntologyDao.java", Map.of( "A_SQL_CAST", 1)),
            Map.entry("ecos_backend/engine/ontology-engine/ontology-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ontology/dao/OntologyDomainDao.java", Map.of( "B_ILIKE", 3)),
            Map.entry("ecos_backend/engine/ontology-engine/ontology-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ontology/dao/OntologyMappingDao.java", Map.of( "A_SQL_CAST", 2)),
            Map.entry("ecos_backend/engine/ontology-engine/ontology-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ontology/dao/OntologyVersionDao.java", Map.of( "A_SQL_CAST", 2)),
            Map.entry("ecos_backend/engine/ontology-engine/ontology-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ontology/dao/PgGraphDao.java", Map.of( "A_SQL_CAST", 2,  "C_UPSERT", 5)),
            Map.entry("ecos_backend/engine/ontology-engine/ontology-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ontology/service/PgGraphService.java", Map.of( "D_RETURNING", 1)),
            Map.entry("ecos_backend/engine/security-engine/security-engine-impl/src/main/java/com/chinacreator/gzcm/engine/security/dao/AuditDao.java", Map.of( "A_SQL_CAST", 3,  "C_UPSERT", 1)),
            Map.entry("ecos_backend/gateway/src/main/java/com/chinacreator/gzcm/gateway/dao/QuotaDao.java", Map.of( "A_SQL_CAST", 2,  "C_UPSERT", 1,  "D_RETURNING", 1)),
            Map.entry("ecos_backend/gateway/src/main/java/com/chinacreator/gzcm/gateway/dao/SpanDao.java", Map.of( "C_UPSERT", 1)),
            Map.entry("ecos_backend/gateway/src/main/java/com/chinacreator/gzcm/gateway/dao/TelemetryDao.java", Map.of( "A_SQL_CAST", 3,  "H_DIST_ON", 1)),
            Map.entry("ecos_backend/gateway/src/main/java/com/chinacreator/gzcm/gateway/dao/UsageAggregateDao.java", Map.of( "A_SQL_CAST", 6,  "C_UPSERT", 4)),
            Map.entry("ecos_backend/runtime/common-api/src/main/java/com/chinacreator/gzcm/common/dao/ObjectRuntimeDao.java", Map.of( "A_SQL_CAST", 3,  "H_DIST_ON", 1)),
            Map.entry("ecos_backend/runtime/runtime-monitor/src/main/java/com/chinacreator/gzcm/runtime/core/monitor/server/warn/dao/WarnLogDao.java", Map.of( "C_UPSERT", 1)),
            Map.entry("ecos_backend/runtime/runtime-task/src/main/java/com/chinacreator/gzcm/runtime/core/task/persistence/dao/TaskPersistenceDao.java", Map.of( "C_UPSERT", 38)),
            Map.entry("ecos_backend/services/agent-service/src/main/java/com/chinacreator/gzcm/services/agent/dao/AgentEvolutionDao.java", Map.of( "A_SQL_CAST", 3)),
            Map.entry("ecos_backend/services/datanet/src/main/java/com/chinacreator/gzcm/services/datanet/controller/DqDashboardController.java", Map.of( "A_SQL_CAST", 1)),
            Map.entry("ecos_backend/services/sysman/impl/sysman-boot/src/main/java/com/chinacreator/gzcm/sysman/boot/dao/AuthDao.java", Map.of( "D_RETURNING", 1)),
            Map.entry("ecos_backend/services/sysman/impl/sysman-impl/src/main/java/com/chinacreator/gzcm/sysman/controller/EntityTableMappingController.java", Map.of( "A_SQL_CAST", 1)),
            Map.entry("ecos_backend/services/sysman/impl/sysman-impl/src/main/java/com/chinacreator/gzcm/sysman/controller/UserController.java", Map.of( "B_ILIKE", 2)),
            Map.entry("ecos_backend/services/sysman/impl/sysman-impl/src/main/java/com/chinacreator/gzcm/sysman/dao/AuditLogDao.java", Map.of( "D_RETURNING", 1)),
            Map.entry("ecos_backend/services/sysman/impl/sysman-impl/src/main/java/com/chinacreator/gzcm/sysman/dao/BlacklistDao.java", Map.of( "C_UPSERT", 2)),
            Map.entry("ecos_backend/services/sysman/impl/sysman-impl/src/main/java/com/chinacreator/gzcm/sysman/dao/DictTableDao.java", Map.of( "B_ILIKE", 3)),
            Map.entry("ecos_backend/services/sysman/impl/sysman-impl/src/main/java/com/chinacreator/gzcm/sysman/dao/EntityTableMappingQueryDao.java", Map.of( "A_SQL_CAST", 1)),
            Map.entry("ecos_backend/services/sysman/impl/sysman-impl/src/main/java/com/chinacreator/gzcm/sysman/dao/SecurityProfileQueryDao.java", Map.of( "C_UPSERT", 2)),
            Map.entry("ecos_backend/services/sysman/impl/sysman-impl/src/main/java/com/chinacreator/gzcm/sysman/dao/SysConfigDao.java", Map.of( "C_UPSERT", 2,  "E_GEN_UUID", 2)),
            Map.entry("ecos_backend/workspace/workspace-impl/src/main/java/com/chinacreator/gzcm/workspace/controller/ObjectController.java", Map.of( "B_ILIKE", 4)),
            Map.entry("ecos_backend/workspace/workspace-impl/src/main/java/com/chinacreator/gzcm/workspace/controller/ObjectQLParser.java", Map.of( "A_SQL_CAST", 2,  "F_JSON_FB", 9)),
            Map.entry("ecos_backend/workspace/workspace-impl/src/main/java/com/chinacreator/gzcm/workspace/dao/WorkspaceDataDao.java", Map.of( "A_SQL_CAST", 8,  "C_UPSERT", 3)),
            Map.entry("ecos_backend/workspace/workspace-impl/src/main/java/com/chinacreator/gzcm/workspace/scenario/ScenarioService.java", Map.of( "A_SQL_CAST", 1))
    ));

    /** Mapper XML 主源树。 */
    static final Map<String, Map<String, Integer>> XML_PIN = build(RULE_NAMES, Map.ofEntries(
            // 台账行 4391 点名：EcosDataLineage 4 枚 ON CONFLICT（其 ILIKE 在注释内不参）＋ A_SQL_CAST 4 枚。
            Map.entry("ecos_backend/engine/data-engine/data-engine-impl/src/main/resources/mapper/EcosDataLineageMapper.xml",
                    Map.of("A_SQL_CAST", 4, "C_UPSERT", 4)),
            Map.entry("ecos_backend/engine/data-engine/data-engine-impl/src/main/resources/mapper/DataSourceMapper.xml",
                    Map.of("A_SQL_CAST", 2)),
            Map.entry("ecos_backend/engine/data-engine/data-engine-impl/src/main/resources/mapper/DataResourceMapper.xml",
                    Map.of("A_SQL_CAST", 1, "B_ILIKE", 3))
    ));
    private static Map<String, Map<String, Integer>> build(List<String> ruleNames, Map<String, Map<String, Integer>> raw) {
        Map<String, Map<String, Integer>> out = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Integer>> e : raw.entrySet()) {
            Map<String, Integer> filled = new LinkedHashMap<>();
            for (String r : ruleNames) {
                filled.put(r, e.getValue().getOrDefault(r, 0));
            }
            out.put(e.getKey(), filled);
        }
        return out;
    }

    // ==================== 门禁腿 ====================

    @Test
    @DisplayName("Java 侧：各族文件级名录双向相等（新增文件即红；已登记文件被收口也必须同批收缩）")
    void javaSurfacePinnedProjectLevel() throws IOException {
        assertPinned(JAVA_PIN, scanJava(),
                "Java 运行期 SQL 方言面（字面量+文本块）文件级名录必须双向相等。"
                        + "收口 = 同批改名录（禁单向裁剪）；新增 = 红线回潮须先裁决。");
    }

    @Test
    @DisplayName("XML 侧：同一判据（剥 XML 注释后）文件级名录双向相等")
    void xmlSurfacePinnedProjectLevel() throws IOException {
        assertPinned(XML_PIN, scanXml(),
                "Mapper XML 方言面文件级名录必须双向相等（EcosDataLineage 是台账行 4391 指名点）。");
    }

    @Test
    @DisplayName("反向对照：台账指名违例点必须被本门看见（防谓词空转把零命中当真绿）")
    void designatedOffendersMustBeSeen() throws IOException {
        Map<String, Map<String, Integer>> js = scanJava();
        Map<String, Map<String, Integer>> xm = scanXml();

        // 台账行 456：HypothesisDao 的 ?::jsonb
        assertTrue(js.getOrDefault("ecos_backend/engine/cognitive-engine/cognitive-engine-impl/src/main/java/com/chinacreator/gzcm/engine/cognitive2/dao/HypothesisDao.java", Map.of())
                        .getOrDefault("A_SQL_CAST", 0) >= 1,
                "HypothesisDao 的 ?::jsonb 必须被本门看见（检不到 = A_SQL_CAST 判据或剥离面退化）");

        // 台账行 434：DecisionDao 的 ILIKE>=2
        assertTrue(js.getOrDefault("ecos_backend/engine/cognitive-engine/cognitive-engine-impl/src/main/java/com/chinacreator/gzcm/engine/cognitive2/dao/DecisionDao.java", Map.of())
                        .getOrDefault("B_ILIKE", 0) >= 2,
                "DecisionDao 的 ILIKE(>=2) 必须被本门看见（检不到 = ILIKE 判据或剥离面退化）");

        // 台账行 4391：EcosDataLineageMapper.xml 的 4 枚 ON CONFLICT（其 ILIKE 只存注释内，剥注释后不参）
        assertTrue(xm.getOrDefault("ecos_backend/engine/data-engine/data-engine-impl/src/main/resources/mapper/EcosDataLineageMapper.xml", Map.of())
                        .getOrDefault("C_UPSERT", 0) >= 4,
                "EcosDataLineageMapper 的 ON CONFLICT(>=4) 必须被本门看见（检不到 = ON_CONFLICT 判据或 XML 注释剥面退化）");

        // 台账行 456 补点：本门 XML 全文 A_SQL_CAST 判据腿（EcosDataLineage 同文 4 枚 ::jsonb 强转）
        assertTrue(xm.getOrDefault("ecos_backend/engine/data-engine/data-engine-impl/src/main/resources/mapper/EcosDataLineageMapper.xml", Map.of())
                        .getOrDefault("A_SQL_CAST", 0) >= 1,
                "EcosDataLineageMapper 的 XML 全文 ::强转(>=1) 必须被本门看见（检不到 = A_SQL_CAST 对 XML 全文面退化）");
    }

    @Test
    @DisplayName("谓词自检：八族判据都必须能判非法串，且不判合法串（恒 0 命中 = 谓词死的假绿形态）")
    void predicatesBiteOnSyntheticStrings() {
        // A 族：cast 闭集
        assertTrue(count(CANNED_CASTS, "?::jsonb") == 1, "A: ::jsonb 非法串未命中");
        assertTrue(count(CANNED_CASTS, "CAST(x AS jsonb) vs ?::JSONB") >= 1, "A: 大写 JSONB 未命中");
        assertEquals(0, count(CANNED_CASTS, "map(this::someMethod)"), "A: Java 方法引用必须不计");
        assertEquals(0, count(CANNED_CASTS, "list.add(x)::add"), "A: ::add 不在闭集，不参（防误判）");

        // C 族：ON CONFLICT + EXCLUDED
        assertTrue(count(ON_CONFLICT, "INSERT ... ON CONFLICT (id) DO UPDATE") == 1);
        assertTrue(count(EXCLUDED, "SET x = EXCLUDED.x") == 1);

        // B 族：ILIKE
        assertTrue(count(ILIKE, "name ILIKE 'x'") == 1);
        assertTrue(count(ILIKE, "NAME ilike ?") == 1, "ILIKE 大小写不敏感判死");

        // D 族：RETURNING
        assertTrue(count(RETURNING, "INSERT ... RETURNING id") == 1);
        assertEquals(0, count(RETURNING, "returnValue(returningFlag=1)"), "D: 词内嵌入的 returning 不参（词边界）");

        // E 族：gen_random_uuid
        assertTrue(count(GEN_UUID, "DEFAULT gen_random_uuid()") == 1);
        assertEquals(0, count(GEN_UUID, "gen_random_uuid"), "E: 缺括号不参");

        // F/G/H 族
        assertTrue(count(JSON_FB, "payload->>'name'") == 1, "F: 指针操作符未命中");
        assertTrue(count(REGCLASS, "WHERE to_regclass(oid) IS NOT NULL") == 1, "G: to_regclass 未命中");
        assertTrue(count(DIST_ON, "SELECT DISTINCT ON (a) a, b FROM t") == 1, "H: DISTINCT ON 未命中");
    }

    // ==================== 扫描与工具 ====================

    /** Java 侧：所有主源树 .java + 内嵌 -sql.xml，剥注释后取字面量+文本块内容。 */
    static Map<String, Map<String, Integer>> scanJava() throws IOException {
        Map<String, Map<String, Integer>> out = new LinkedHashMap<>();
        for (Path f : walkDir("java")) {
            String s = f.toString().replace('\\', '/');
            if (!s.contains("/src/main/java/") || !(s.endsWith(".java") || s.endsWith("-sql.xml"))) {
                continue;
            }
            if (s.contains("/archive/") || s.contains("/target/")) {
                continue;
            }
            String rel = s.substring(s.indexOf("ecos_backend"));
            String text = stripJavaComments(read(f));
            StringBuilder sf = new StringBuilder();
            for (String r : extractStringLiterals(text)) {
                sf.append(r).append('\u0001');
            }
            Map<String, Integer> counts = countAll(sf.toString());
            if (!counts.isEmpty()) {
                out.put(rel, counts);
            }
        }
        return out;
    }

    /** XML 侧：主资源树所有 .xml，剥注释后全文判（XML 注释里的违例不参）。 */
    static Map<String, Map<String, Integer>> scanXml() throws IOException {
        Map<String, Map<String, Integer>> out = new LinkedHashMap<>();
        for (Path f : walkDir("xml")) {
            String s = f.toString().replace('\\', '/');
            if (!s.contains("/src/main/resources/") || !s.endsWith(".xml")) {
                continue;
            }
            if (s.contains("/archive/") || s.contains("/target/")) {
                continue;
            }
            String rel = s.substring(s.indexOf("ecos_backend"));
            String text = stripXmlComments(read(f));
            Map<String, Integer> counts = countAll(text);
            if (!counts.isEmpty()) {
                out.put(rel, counts);
            }
        }
        return out;
    }

    /** 共同计数：八族判据对 text 各自计数（零命中不入库，保持名录紧凑）。 */
    private static Map<String, Integer> countAll(String text) {
        Map<String, Integer> m = new LinkedHashMap<>();
        putIfPositive(m, "A_SQL_CAST", count(CANNED_CASTS, text));
        putIfPositive(m, "C_UPSERT", count(ON_CONFLICT, text) + count(EXCLUDED, text));
        putIfPositive(m, "B_ILIKE", count(ILIKE, text));
        putIfPositive(m, "D_RETURNING", count(RETURNING, text));
        putIfPositive(m, "E_GEN_UUID", count(GEN_UUID, text));
        putIfPositive(m, "F_JSON_FB", count(JSON_FB, text));
        putIfPositive(m, "G_REGCLASS", count(REGCLASS, text));
        putIfPositive(m, "H_DIST_ON", count(DIST_ON, text));
        return m;
    }

    private static void putIfPositive(Map<String, Integer> m, String k, int v) {
        if (v > 0) {
            m.put(k, v);
        }
    }

    private static void assertPinned(Map<String, Map<String, Integer>> pin,
                                     Map<String, Map<String, Integer>> actual, String msg) {
        // 双向：pin 与 actual 的文件集合并后逐族比较；
        // 新文件缺席 pin = pin 侧视为全 0，出现命中即红（红线回潮）。
        TreeMap<String, Boolean> files = new TreeMap<>();
        pin.keySet().forEach(k -> files.put(k, Boolean.TRUE));
        actual.keySet().forEach(k -> files.putIfAbsent(k, Boolean.TRUE));
        StringBuilder sb = new StringBuilder();
        for (String key : files.keySet()) {
            Map<String, Integer> p = pin.getOrDefault(key, Map.of());
            Map<String, Integer> a = actual.getOrDefault(key, Map.of());
            for (String r : RULE_NAMES) {
                int pv = p.getOrDefault(r, 0);
                int av = a.getOrDefault(r, 0);
                if (pv != av) {
                    sb.append(String.format("  %s %s: pin=%d actual=%d%n", key, r, pv, av));
                }
            }
        }
        if (sb.length() > 0) {
            throw new AssertionError(msg + "\n差异：\n" + sb);
        }
    }

    // ==================== 剥注释 + 取字面量 ====================

    /** 剥 Java 行/块注释，保留字符串字面量与文本块内内容原样。 */
    static String stripJavaComments(String src) {
        StringBuilder out = new StringBuilder(src.length());
        int i = 0, n = src.length();
        while (i < n) {
            char c = src.charAt(i);
            char nx = i + 1 < n ? src.charAt(i + 1) : '\0';
            if (c == '/' && nx == '*') {
                int end = src.indexOf("*/", i + 2);
                int stop = end < 0 ? n : end + 2;
                for (int k = i; k < stop; k++) {
                    out.append(src.charAt(k) == '\n' ? '\n' : ' ');
                }
                i = stop;
                continue;
            }
            if (c == '/' && nx == '/') {
                int end = src.indexOf('\n', i);
                int stop = end < 0 ? n : end;
                for (int k = i; k < stop; k++) {
                    out.append(' ');
                }
                i = stop;
                continue;
            }
            if (c == '"') {
                // 文本块（三引号）优先探测
                if (i + 2 < n && src.charAt(i + 1) == '"' && src.charAt(i + 2) == '"') {
                    int end = i + 3;
                    while (end + 2 < n && !(src.charAt(end) == '"' && src.charAt(end + 1) == '"' && src.charAt(end + 2) == '"')) {
                        end++;
                    }
                    int stop = Math.min(end + 3, n);
                    out.append(src, i, stop);
                    i = stop;
                    continue;
                }
                out.append(c);
                i++;
                while (i < n) {
                    char d = src.charAt(i);
                    out.append(d);
                    i++;
                    if (d == '\\' && i < n) {
                        out.append(src.charAt(i));
                        i++;
                        continue;
                    }
                    if (d == '"') {
                        break;
                    }
                }
                continue;
            }
            if (c == '\'') {
                out.append(c);
                i++;
                while (i < n) {
                    char d = src.charAt(i);
                    out.append(d);
                    i++;
                    if (d == '\\' && i < n) {
                        out.append(src.charAt(i));
                        i++;
                        continue;
                    }
                    if (d == '\'') {
                        break;
                    }
                }
                continue;
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    /** 从剥注释后的 Java 源里提取所有字符串字面量 + 文本块内容（仅取引号内，不含引号）。 */
    static List<String> extractStringLiterals(String src) {
        List<String> out = new ArrayList<>();
        int i = 0, n = src.length();
        while (i < n) {
            char c = src.charAt(i);
            if (c != '"') {
                i++;
                continue;
            }
            if (i + 2 < n && src.charAt(i + 1) == '"' && src.charAt(i + 2) == '"') {
                int end = i + 3;
                while (end + 2 < n && !(src.charAt(end) == '"' && src.charAt(end + 1) == '"' && src.charAt(end + 2) == '"')) {
                    end++;
                }
                out.add(src.substring(i + 3, Math.min(end, n)));
                i = Math.min(end + 3, n);
                continue;
            }
            int end = i + 1;
            while (end < n) {
                char d = src.charAt(end);
                if (d == '\\') {
                    if (end + 1 < n) {
                        end += 2;
                    } else {
                        end++;
                    }
                    continue;
                }
                if (d == '"') {
                    break;
                }
                end++;
            }
            out.add(src.substring(i + 1, Math.min(end, n)));
            i = end + 1;
        }
        return out;
    }

    /** 剥 XML 注释（注释内的违例不参）。 */
    static String stripXmlComments(String src) {
        StringBuilder out = new StringBuilder(src.length());
        int i = 0;
        while (i < src.length()) {
            if (i + 3 < src.length()
                    && src.charAt(i) == '<' && src.charAt(i + 1) == '!'
                    && src.charAt(i + 2) == '-' && src.charAt(i + 3) == '-') {
                int end = src.indexOf("-->", i + 4);
                if (end < 0) {
                    break;
                }
                out.append(' ');
                i = end + 3;
            } else {
                out.append(src.charAt(i));
                i++;
            }
        }
        return out.toString();
    }

    // ==================== 文件遍历（仓根定位复用 DdlShapeScanner 口径） ====================

    /** 遍历 ecos_backend 生产源树。type="java" 或 "xml"。 */
    private static List<Path> walkDir(String type) throws IOException {
        Path root = DdlShapeScanner.repoRoot().resolve("ecos_backend");
        List<Path> out = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> type.equals("java") ? p.toString().endsWith(".java") || p.toString().endsWith("-sql.xml")
                            : p.toString().endsWith(".xml"))
                    .filter(p -> p.toString().replace('\\', '/').contains("/src/main/"))
                    .filter(p -> !p.toString().replace('\\', '/').contains("/target/"))
                    .filter(p -> !p.toString().replace('\\', '/').contains("/archive/"))
                    .sorted()
                    .forEach(out::add);
        }
        return out;
    }

    private static String read(Path f) throws IOException {
        return Files.readString(f, StandardCharsets.UTF_8);
    }

    private static int count(Pattern p, String s) {
        Matcher m = p.matcher(s);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }
}
