package com.chinacreator.gzcm.engine.data;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W50 / D-8 / C39（详细设计-02 E.1 ST07 Authorized Subschema Inventory，M1）——
 * 单源迁移目录下 <b>DML/DDL 动词后 schema-限定表引用</b> 的 schema 集合枚举，
 * 以"<b>越出 ST07 5+1 的 schema 数</b>（只减不增，基线 11）"作为棘轮守护。
 *
 * <p>ST07（AGENTS.md）授权五引擎 schema + 主控制 schema + 业务域落 <code>ecos_dw</code>：</p>
 * <pre>
 *   授权（7 个，ST07 允许当前 DDL 引用）：
 *     public      （现主控制 schema，目标 ecos_control）
 *     ecos_data / ecos_ontology / ecos_knowledge / ecos_ai / ecos_cognitive
 *     ecos_dw     （业务域五层 Doris∨CH 之前的 PG 落库）
 * </pre>
 *
 * <p><b>越出 5+1 的实查基线（2026-10-03 C39 上线日实测 = 11）</b>：
 * <code>ecos_dq / ecos_agent / ecos_workflow / ecos_rule / ecos_pipeline /
 * ecos_object / ecos_mission / ecos_identity / ecos_config /
 * ecos_catalog / ecos_audit</code>。正好是 D-8 点名的 "ecos_dq 等 11
 * 个 schema 越出 ST07 5+1" 存量名册（<b>只停写不迁移</b> —— 与 AGENTS.md
 * knownLegacy 口径一致，本护栏锁<b>增殖</b>而非要求立即迁走存量）。</p>
 *
 * <p><b>枚举口径</b>（与 {@link BareTableNameRatchetArchTest} / {@link DdlComplianceLintTest} /
 * {@link MigrationBasenameSingleRootArchTest} 同风格：行级正则、纯文件读取）：</p>
 * <ul>
 *   <li><b>动词 anchor 集</b>：{@code CREATE (UNIQUE|INDEX|TABLE|VIEW|MATERIALIZED VIEW|SEQUENCE|SCHEMA)} /
 *       {@code ALTER (TABLE|INDEX|SEQUENCE|SCHEMA|DATABASE|VIEW)} /
 *       {@code DROP (TABLE|INDEX|SEQUENCE|SCHEMA|VIEW|MATERIALIZED VIEW|FUNCTION|DATABASE)} /
 *       {@code INSERT INTO} / {@code UPDATE} / {@code DELETE FROM} /
 *       {@code CREATE OR REPLACE (FUNCTION|VIEW|TRIGGER|MATERIALIZED VIEW)} /
 *       {@code TRUNCATE (TABLE)}；</li>
 *   <li>动词后可选 {@code IF NOT EXISTS}/{@code ONLY}/{@code CASCADE}；</li>
 *   <li>紧接 <code>identifier.identifier</code>（第一段 = schema，仅小写字母/数字/下划线，防 Java 类名混入）；</li>
 *   <li>剔除 {@code --} 行尾注释后匹配。</li>
 * </ul>
 *
 * <p><b>棘轮语义</b>：越出 5+1 的 <b>distinct schema 数</b> ≤ 基线 11 → 绿；
 * &gt; 11 → 红（新增越出 schema 判红并附明细）。只减不增与
 * {@link DdlComplianceLintTest} 同语义：合规改造（存量迁入已授权 schema）
 * 天然放行；同名的 CREATE DDL 被清理后 basename 不再出现也天然放行。</p>
 *
 * <p><b>白线</b>：{@code ecos_dq} 必须在越出集合中（2026-10-03 实查既有越出 schema），
 * 防止若未来 SQL 语法演化（如新增多行 CREATE 复合块、反引号、双引号 schema 名）
 * 使正则退化到"全空、0 命中"而静默判绿。基线 0 触发后立即能感知正则退化。</p>
 *
 * <p><b>不触库、不 Spring、不联网</b>（纯文件读取 + 行级正则），
 * 与 E.5 lint ②的 {@link DdlComplianceLintTest} 从不同维度同守铁律 §3.1 schema 归口纪律：
 * ② 锁 <b>列类型</b> DR04/MC01 违规，本类锁 <b>schema 归属</b> 越出 5+1 的
 * <b>集合规模</b>（不枚举存量名册本身，避免与具体迁移脚本 V 号硬绑导致基线频繁变动）。</p>
 */
@DisplayName("W50/D-8/C39 单源 DDL 越出 ST07 5+1 schema 数 只减不增（基线 11，白线 ecos_dq）")
class SchemaInventoryGateTest {

    /**
     * 基线（2026-10-03 20:35 实查）：单源迁移目录内、DML/DDL 动词后 schema-限定引用命中的
     * <b>越出 ST07 5+1 授权的 distinct schema 数</b>。痕：ecos_dq / ecos_agent /
     * ecos_workflow / ecos_rule / ecos_pipeline / ecos_object / ecos_mission /
     * ecos_identity / ecos_config / ecos_catalog / ecos_audit。
     */
    static final int BASELINE_OUT_OF_ST07_SCHEMAS = 11;

    /** ST07（5+1）授权：五引擎 schema + 主控制 public + 业务域 ecos_dw。 */
    static final Set<String> ST07_AUTHORIZED =
            Set.of("public", "ecos_data", "ecos_ontology", "ecos_knowledge", "ecos_ai",
                    "ecos_cognitive", "ecos_dw");

    /**
     * 动词 anchor + 可选条件词 + schema.table。
     * case-insensitive；仅捕获第一段 identifier 表字符（小写字母/数字/下划线）：
     * Java 类名（含大写）不匹配、驼峰单词不匹配，从而与 VerseCode 等应用标识符解耦。
     */
    private static final Pattern SCHEMA_QUALIFIED_REF = Pattern.compile(
            "(?i)(?:CREATE\\s+(?:OR\\s+REPLACE\\s+)?(?:UNIQUE\\s+)?(?:INDEX|MATERIALIZED\\s+VIEW|INDEX\\s+CONCURRENTLY|TABLE(?:\\s+IF\\s+NOT\\s+EXISTS)?|VIEW(?:\\s+IF\\s+NOT\\s+EXISTS)?|FUNCTION|TRIGGER|SCHEMA(?:\\s+IF\\s+NOT\\s+EXISTS)?|SEQUENCE(?:\\s+IF\\s+NOT\\s+EXISTS)?)|ALTER\\s+(?:TABLE|INDEX|SEQUENCE|SCHEMA|DATABASE|VIEW|MATERIALIZED\\s+VIEW)(?:\\s+IF\\s+EXISTS)?(?:\\s+ONLY)?|DROP\\s+(?:TABLE|INDEX|SEQUENCE|SCHEMA|VIEW|MATERIALIZED\\s+VIEW|FUNCTION|DATABASE)(?:\\s+(?:CASCADE|RESTRICT))?(?:\\s+IF\\s+EXISTS)?|INSERT\\s+INTO|UPDATE|DELETE\\s+FROM|TRUNCATE(?:\\s+TABLE)?)\\s+"
                    + "([a-z][a-z0-9_]*)\\s*\\.\\s*[a-z][a-z0-9_]*",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    @Test
    @DisplayName("W50/C39 ST07 5+1 — 越出授权的 distinct schema 数 只减不增（基线 " + BASELINE_OUT_OF_ST07_SCHEMAS + "）")
    void st07_inventoryRatchet() throws Exception {
        Set<String> outOfSet = scanOutOfAuthorizedSchemas();
        List<String> detail = outOfSet.stream().sorted().collect(Collectors.toList());
        assertFalse(outOfSet.size() > BASELINE_OUT_OF_ST07_SCHEMAS,
                String.format("[W50/C39 ST07 5+1] 越出授权的 schema 数回升：当前 %d > 基线 %d（只允许减少；越出集合：%s）",
                        outOfSet.size(), BASELINE_OUT_OF_ST07_SCHEMAS, String.join(", ", detail)));
    }

    @Test
    @DisplayName("W50/C39 白线 — ecos_dq 应仍在越出集合（防正则退化到 0 命中假绿）")
    void whitelistEcosDqStillPresent() throws Exception {
        Set<String> outOfSet = scanOutOfAuthorizedSchemas();
        assertTrue(outOfSet.contains("ecos_dq"),
                "白线破裂：ecos_dq 应仍在越出 ST07 5+1 集合（V166/V171 已授权单源目录仍在用于 linchpin 存 DDL），"
                        + "当前越出集合 = " + new TreeSet<>(outOfSet)
                        + " ；如果基线 0，视为正则退化需回滚 SCHEMA_QUALIFIED_REF 的动词 anchor。");
    }

    /**
     * 扫描单源目录（与 {@link DdlComplianceLintTest} 同根定位）内所有 *.sql
     * 的动词-锚定 schema.table 引用，剔除 ST07 授权后返回 distinct 越出 schema 集合。
     */
    private static Set<String> scanOutOfAuthorizedSchemas() throws IOException {
        Set<String> outOfSet = new LinkedHashSet<>();
        Path dir = backendRoot().resolve("gateway/src/main/resources/db/migration");
        assertTrue(Files.isDirectory(dir), "迁移单源目录不存在: " + dir);
        List<Path> sqls;
        try (Stream<Path> walk = Files.list(dir)) {
            sqls = walk.filter(p -> p.toString().endsWith(".sql")).sorted().collect(Collectors.toList());
        }
        assertFalse(sqls.isEmpty(), "迁移目录为空，路径异常: " + dir);
        for (Path p : sqls) {
            String content = stripLineComments(read(p));
            Matcher m = SCHEMA_QUALIFIED_REF.matcher(content);
            while (m.find()) {
                String schema = m.group(1).toLowerCase(Locale.ROOT);
                if (!ST07_AUTHORIZED.contains(schema)) {
                    outOfSet.add(schema);
                }
            }
        }
        return outOfSet;
    }

    /** 逐行剔除 {@code --} 行尾注释（防注释里出现的 schema 名被计入）。 */
    private static String stripLineComments(String src) {
        StringBuilder sb = new StringBuilder(src.length());
        for (String line : src.split("\\R", -1)) {
            int idx = line.indexOf("--");
            sb.append(idx >= 0 ? line.substring(0, idx) : line).append('\n');
        }
        return sb.toString();
    }

    private static Path backendRoot() {
        Path r = RlsInjectionGuardArchTest.resolveModuleSrcMainJava();
        // src/main/java → data-engine-impl → data-engine → engine → ecos_backend
        for (int i = 0; i < 6; i++) {
            r = r.getParent();
        }
        assertNotNull(r, "无法上溯到 ecos_backend 工程根");
        return r;
    }

    private static String read(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
