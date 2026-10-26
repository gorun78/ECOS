package com.chinacreator.gzcm.engine.cognitive2.guard;

import com.chinacreator.gzcm.engine.cognitive.api.CognitiveErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F05-12 铁律守护接线（REQ-COG-05 五项逐条 → 构建期/测试期可执行断言）：
 * <ol>
 *   <li><b>推理结果不落盘</b> → {@code #diagnoseForecastSimulateLeaveRowCountUnchanged}
 *       （静态半区 = 禁写推理结果载体族；库侧调用前后 count 比对需实跑 ecos-postgres
 *       ⇒ 需求检视报告 §14.4 未授权项，记「未执行」并显式打印，禁静默当已验）；</li>
 *   <li><b>不新增业务事实表</b> → {@code #cognitiveDomainMayNotCreateTablesOutsideFour}
 *       （扫 DDL 单源目录 {@code docs/50-db_script/02-flyway-migration/gateway}，
 *       认知域表白名单外零容忍 + 白名单虚条目判 FAIL）；</li>
 *   <li><b>不引入规则引擎</b> → {@code CognitiveDependencyAuditTest#noRuleEngineOnClasspath}；</li>
 *   <li><b>禁 LLM 直接承担推理职责</b> → {@code CognitiveNoLlmNumbersTest#chainsContributionScoreNeverFromLlmResponse}；</li>
 *   <li><b>Context 不建表</b> → {@code #noCognitiveContextTable}（本类）。</li>
 * </ol>
 *
 * <p>另含契约面跨域写守护 {@code #diagnoseLeavesRowCountUnchanged}
 * （场景认知契约面 SQL 只访问认知属主 schema，不写 sysman / kb 前缀表 → C111、C112）；
 * 冻结名保留原文，语义 = 静态"无写口 ⇒ 行数不变"，同项 1 的库侧半区一样记「未执行」。
 * 范围 = {@code CogScope.inScope}（E1~E4 契约面 + mind/scenario DAO + world-model 桩）与
 * {@code CogScope.streamMainAll}（认知全域主源，豁免 {@code /config/}）；
 * legacy 全域收敛属 F05-13 M1（C111、C112 已登记）。</p>
 */
class CognitiveIronLawsTest {

    // SQL 语境限定：schema 限定引用 / SQL 关键字后的表名才判跨域，注释散文（"sysman 配置门面"）不误伤
    private static final Pattern FORBIDDEN_TABLE = Pattern.compile(
            "\\bsysman\\.\\w+|\\b(?:FROM|JOIN|UPDATE|INTO)\\s+(?:\\w+\\.)?(?:sys_config|sys_compliance_rule)\\b|\\bkbs?_\\w+\\b");
    private static final Pattern CONTEXT_TABLE = Pattern.compile(
            "cognitive_context\\w*", Pattern.CASE_INSENSITIVE);

    @Test
    @DisplayName("场景认知契约面禁跨域写 (sysman.*/kb.*/kb_*)（X-31/C112）")
    void diagnoseLeavesRowCountUnchanged() throws IOException {
        List<String> offenders = new ArrayList<>();
        try (var walk = CogScope.streamInScope()) {
            walk.forEach(p -> {
                try {
                    String src = Files.readString(p);
                    if (FORBIDDEN_TABLE.matcher(src).find()) offenders.add(p.toString());
                } catch (IOException ignored) {}
            });
        }
        assertTrue(offenders.isEmpty(),
                "场景认知契约面存在跨域表引用（X-31）：\n" + String.join("\n", offenders));
        // 断言 D.5 错误码常量仍在（新鲜复算复用，防误删）
        assertTrue(CognitiveErrorCode.E501_SCHEMA_NOT_READY.startsWith("ECOS-COG-"));
    }

    @Test
    @DisplayName("场景认知契约面 Context 不建表（禁 cognitive_context*）")
    void noCognitiveContextTable() throws IOException {
        List<String> offenders = new ArrayList<>();
        try (var walk = CogScope.streamInScope()) {
            walk.forEach(p -> {
                try {
                    String src = Files.readString(p);
                    if (CONTEXT_TABLE.matcher(src).find()) offenders.add(p.toString());
                } catch (IOException ignored) {}
            });
        }
        assertTrue(offenders.isEmpty(),
                "场景认知契约面出现 cognitive_context* 表引（Context 不建表）：\n"
                        + String.join("\n", offenders));
    }

    // ── F05-12 守护项 1「推理结果不落盘」+ 守护项 2「不新增业务事实表」──────────────

    /**
     * 推理结果载体族（diagnose/forecast/simulate 三端点的<b>输出</b>若落盘只会落在这族表名上）。
     *
     * <p><b>逐条排除的邻近物（不是放水，是语义边界）</b>：
     * <ul>
     *   <li>{@code ecos_cognitive_belief/hypothesis/evidence/model} —— 心智状态与模型资产，
     *       有各自的登记写口（{@code BeliefDao}/{@code HypothesisDao}/{@code EvidenceDao}/
     *       {@code ModelRegistryDao}），"推理结果"指端点输出而非状态资产；</li>
     *   <li>{@code ecos_cognitive_run_invalidation}（V192）—— run <b>失效登记</b>，记"哪次运行过期"，
     *       不记运行产物；</li>
     *   <li>{@code kb_cognitive_pipeline} —— V163 声明停写的 knownLegacy 收敛面（IR03 只停写不改名），
     *       归 {@code CognitiveCrossDomainTest} 的跨域写门禁管，不在本铁律族。</li>
     * </ul>
     */
    private static final Pattern REASONING_RESULT_WRITE = Pattern.compile(
            "(?i)\\b(?:INSERT\\s+INTO|UPDATE|DELETE\\s+FROM)\\s+(?:[a-z0-9_]+\\.)?"
                    + "(?:ecos_cognitive_run|ecos_reasoning\\w*|ecos_simulation_result\\w*"
                    + "|ecos_forecast_result\\w*|ecos_kb_reasoning\\w*|cog_[a-z0-9_]+)\\b");

    /** 确定性产物唯一提交通道字面量（详设-05 §8.1 表末行：产物只经 data-engine 写通道 REST 提交）。 */
    private static final String WRITE_CHANNEL_LITERAL = "/api/v1/datanet/write-channel";

    @Test
    @DisplayName("diagnose/forecast/simulate 三端点不留推理结果写口（静态半区）；库侧 count 比对记「未执行」")
    void diagnoseForecastSimulateLeaveRowCountUnchanged() throws IOException {
        List<String> offenders = new ArrayList<>();
        try (var walk = CogScope.streamMainAll()) {
            for (Path p : walk.toList()) {
                String src = CogScope.stripComments(Files.readString(p));
                var m = REASONING_RESULT_WRITE.matcher(src);
                while (m.find()) {
                    offenders.add(p.getFileName() + ":" + CogScope.lineOf(src, m.start())
                            + " :: " + m.group());
                }
            }
        }
        assertTrue(offenders.isEmpty(),
                "认知域出现推理结果落盘写口（违 REQ-COG-05 铁律「推理结果不落盘」）：\n"
                        + String.join("\n", offenders));

        // 正向面（防"零写口=没接线"的假绿）：唯一提交通道必须真实存在于写通道类，且全仓只出现一次。
        List<String> channels = new ArrayList<>();
        try (var walk = CogScope.streamMainAll()) {
            for (Path p : walk.toList()) {
                String src = CogScope.stripComments(Files.readString(p));
                if (src.contains(WRITE_CHANNEL_LITERAL)) {
                    channels.add(p.getFileName().toString());
                }
            }
        }
        assertTrue(channels.contains("ForecastArtifactWriteChannel.java"),
                "确定性产物写通道未被引用 ⇒ REQ-COG-09 规格 2 未接线（当前命中：" + channels + "）");
        assertTrue(channels.size() <= 2,
                "写通道路径字面量出现多源（应单源在写通道类，其余仅注释引用）：" + channels);

        System.out.println("[REQ-COG-05 未执行] 三端点调用前后 kb 三表 + 认知四表 SELECT count(*) 比对"
                + "需对 ecos-postgres 实跑 ⇒ 需求检视报告 §14.4 未授权项，本批只钉静态面。");
    }

    /** DDL 单源目录（铁律 v2.0 §3.1 权威落点，非 gateway/src/main/resources/db/migration）。 */
    private static final Path MIGRATION_DIR =
            CogScope.REPO.resolve("docs/50-db_script/02-flyway-migration/gateway");

    private static final Pattern CREATE_ON_COGNITIVE = Pattern.compile(
            "(?i)\\bCREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?(ecos_cognitive\\.[a-z0-9_]+)");

    /**
     * 已登记的认知域表集（12 张，逐张带创建脚本引用）。
     *
     * <p><b>与 PRD-05 措辞的偏差说明（不静默）</b>：PRD-05 §5 与 ARCH-07 写"cognitive 域仅 4 表"，
     * 而 §14.1（2026-09-29）R-13 ① 批准的 V187~V196 收敛批在 {@code ecos_cognitive} 并建了 11 张
     * （其中 {@code ecos_business_scenario}/{@code ecos_scenario_binding*} 属 R-13 ① 定性的
     * <b>错放镜像</b>，只停写不迁移，IR03）。本门禁因此按"实登白名单 + 新增必登记"执行，
     * 而非按"4 表"字面数判定 —— 冻结标识名 {@code cognitiveDomainMayNotCreateTablesOutsideFour}
     * 保留原文，语义 = <b>白名单外零容忍</b>。</p>
     *
     * <p>PRD-05 §6.1 规格 3 明文要求"扩表时必须同批改白名单"，故第 12 张（V267 结果评估表）
     * 的登记出处 = PRD-05 §七「需求增量登记（2026-10-10）」第 4 条 Outcome Evaluation
     * + 《认知域与智能域补充详细设计 v1.0》§9.3；它属<b>评估记录</b>（控制域治理态数据），
     * 不是确定性计算产物 —— 产物仍按 ADR-14/REQ-COG-09 落业务域 {@code ecos_dw}，
     * 本表只存 {@code calculation_refs_text} 引用（§9.3 末段铁律）。</p>
     */
    private static final List<String> REGISTERED_COGNITIVE_TABLES = List.of(
            "ecos_cognitive.ecos_cognitive_mind",            // V187
            "ecos_cognitive.ecos_cognitive_scenario_mind",   // V188
            "ecos_cognitive.ecos_cognitive_belief",          // V191（权威载体，V262 补 probability）
            "ecos_cognitive.ecos_cognitive_hypothesis",      // V191
            "ecos_cognitive.ecos_cognitive_evidence",        // V191
            "ecos_cognitive.ecos_cognitive_model",           // V192（CM 注册中心资产表）
            "ecos_cognitive.ecos_cognitive_run_invalidation",// V192
            "ecos_cognitive.ecos_cognitive_pipeline",        // V163 建 / V191·V192 幂等并建，V163 声明停写
            "ecos_cognitive.ecos_business_scenario",         // V193（错放镜像，只停写 IR03）
            "ecos_cognitive.ecos_scenario_binding",          // V193（错放镜像，只停写 IR03）
            "ecos_cognitive.ecos_scenario_binding_link",     // V193（错放镜像，只停写 IR03）
            "ecos_cognitive.ecos_cognitive_outcome_evaluation",      // V267 建 / V274 幂等补列（W→D 结果评估记录，补充设计 §9）
            "ecos_cognitive.ecos_cognitive_mind_update");            // V292 建（R4 Mind 候选更新闸门，差异分析 P0-6 / 详设 §8.4.2）

    @Test
    @DisplayName("认知域 DDL 白名单外零新表（REQ-COG-05 守护项 2，ArchUnit 面 = DDL 单源目录扫描）")
    void cognitiveDomainMayNotCreateTablesOutsideFour() throws IOException {
        assertTrue(Files.isDirectory(MIGRATION_DIR),
                "DDL 单源目录缺失，白名单门禁失去比较对象：" + MIGRATION_DIR);

        List<String> unregistered = new ArrayList<>();
        List<String> registeredSeen = new ArrayList<>();
        try (var walk = Files.walk(MIGRATION_DIR, 1)) {
            List<Path> sqls = walk.filter(f -> f.toString().endsWith(".sql")).toList();
            for (Path sql : sqls) {
                String code = stripSqlComments(Files.readString(sql));
                var m = CREATE_ON_COGNITIVE.matcher(code);
                while (m.find()) {
                    String table = m.group(1).toLowerCase();
                    if (REGISTERED_COGNITIVE_TABLES.contains(table)) {
                        registeredSeen.add(table);
                    } else {
                        unregistered.add(sql.getFileName() + " :: " + table);
                    }
                }
            }
        }
        assertTrue(unregistered.isEmpty(),
                "认知域出现白名单外的新表（须先在 PRD-05 §6.1 / 需求检视报告 §14.1 登记再入 DDL）：\n"
                        + String.join("\n", unregistered));
        // 白名单不得虚胖：登记而未实建的条目同样判 FAIL（防止"先占位后建表"绕过评审）
        List<String> phantom = new ArrayList<>(REGISTERED_COGNITIVE_TABLES);
        phantom.removeAll(registeredSeen);
        assertTrue(phantom.isEmpty(),
                "白名单存在 DDL 单源目录里查无此表的虚条目（应删登记）：" + phantom);
    }

    /** SQL 行注释剥离（{@code --} 到行尾），使头注里的历史性表名/形态描述不进入判定。 */
    private static String stripSqlComments(String sql) {
        StringBuilder sb = new StringBuilder(sql.length());
        for (String line : sql.split("\r?\n")) {
            int idx = line.indexOf("--");
            sb.append(idx < 0 ? line : line.substring(0, idx)).append('\n');
        }
        return sb.toString();
    }
}
