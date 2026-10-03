package com.chinacreator.gzcm.engine.data;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 详细设计-02 §7.1 遗留债务「基线棘轮」门禁（C38 / C43 / C45 / C48）。
 *
 * <p>这些改造项在《详细设计-02》里被明确分级到 <b>M1/M2</b>（跨引擎表名限定 / Service 层
 * JdbcTemplate 解耦 / 网关经 datanet 间接 / DDL MC 红线），代码面属多模块批量重构，
 * 2026-10-03 时点仍存在<b>存量债务</b>。本测试以"棘轮（ratchet）"守门：</p>
 * <ul>
 *   <li><b>断言语义</b>：违规计数 <b>≤ 锁定的存量基线</b>。允许<b>只减不增</b>——
 *       任何一次整改使计数下降即通过；任何一次<b>新增同类违规</b>即 FAIL（红），
 *       从而阻止"越改越糟"，同时不阻塞本分册 M0/M1 已落地的特性（不制造红灯）。</li>
 *   <li>基线数值为 2026-10-03 实测（见各用例常量注释），须与本仓当前源码一致；
 *       改动相关源码使基线失真时，同批复测并把基线下调（只允许下调）。</li>
 * </ul>
 *
 * <p>本测试是源码文本供给（非 ArchUnit 类扫），与 {@link RlsInjectionGuardArchTest}
 * 同一族：复用其 {@link RlsInjectionGuardArchTest#stripComments} 剔除注释防误报。</p>
 */
class LegacyDebtBaselineRatchetArchTest {

    /** W49 基线：data-engine-impl 内裸（未 schema 限定）SQL 引用 {@code td_datasource} 的文件数（ST07/E.1 R-1）。 */
    static final int W49_BASELINE_BARE_TDDATASOURCE_FILES = 5;
    /** W54 基线：data-engine-impl {@code *service*} 包内 field 注入 {@code JdbcTemplate} 的文件数（DR 三层/M2）。 */
    static final int W54_BASELINE_JDBCTEMPLATE_SERVICE_FILES = 32;
    /** W56 基线：gateway 内直接引用 DQ 域表 {@code ecos_dq_issue}/{@code ecos_dq_rule} 的文件数（IR01/M1）。 */
    static final int W56_BASELINE_GATEWAY_DQ_TABLE_FILES = 2;
    /** W59 基线：迁移脚本单源目录内使用 {@code gen_random_uuid()}（MC01 红线）的 .sql 文件数（M1）。 */
    static final int W59_BASELINE_GEN_RANDOM_UUID_FILES = 11;

    private static final Pattern BARE_TDDATASOURCE =
            Pattern.compile("\\b(FROM|JOIN|INTO)\\s+td_datasource\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern JDBC_FIELD =
            Pattern.compile("\\bprivate\\s+(final\\s+)?JdbcTemplate\\b");
    private static final Pattern GATEWAY_DQ_TABLE =
            Pattern.compile("\\becos_dq_(issue|rule)\\b");
    private static final Pattern GEN_RANDOM_UUID = Pattern.compile("gen_random_uuid\\s*\\(");

    // ─────────────────────────── W49 表名限定 ─────────────────────────

    @Test
    @DisplayName("W49 QualifiedTableName — 裸 td_datasource 引用 只减不增（基线 " + W49_BASELINE_BARE_TDDATASOURCE_FILES + "）")
    void w49_bareTdDatasource_ratchet() {
        Path src = moduleSrcMain();
        List<Path> offenders = javaFiles(src);
        int hits = 0;
        List<String> detail = new ArrayList<>();
        for (Path p : offenders) {
            if (BARE_TDDATASOURCE.matcher(strip(p)).find()) { // comment-stripped; SQL literals survive
                hits++;
                detail.add(rel(p, src));
            }
        }
        ratchet("W49 裸 td_datasource（应 schema 限定 public.）", hits,
                W49_BASELINE_BARE_TDDATASOURCE_FILES, detail);
    }

    // ─────────────────────────── W54 Service JdbcTemplate ─────────────

    @Test
    @DisplayName("W54 NoJdbcTemplateInService — *service* 包 field 注入 JdbcTemplate 只减不增（基线 "
            + W54_BASELINE_JDBCTEMPLATE_SERVICE_FILES + "）")
    void w54_jdbcTemplateInService_ratchet() {
        Path src = moduleSrcMain();
        int hits = 0;
        List<String> detail = new ArrayList<>();
        for (Path p : javaFiles(src)) {
            String pstr = p.toString().replace('\\', '/');
            if (!pstr.contains("/service/")) {
                continue;
            }
            if (JDBC_FIELD.matcher(pseudoCode(p)).find()) {
                hits++;
                detail.add(rel(p, src));
            }
        }
        ratchet("W54 *service* 包 JdbcTemplate 字段注入（应收敛 Mapper/DAO）", hits,
                W54_BASELINE_JDBCTEMPLATE_SERVICE_FILES, detail);
    }

    // ─────────────────────────── W56 网关域表直查 ─────────────────────

    @Test
    @DisplayName("W56 GatewayNoDomainTable — gateway 直查 DQ 域表 只减不增（基线 " + W56_BASELINE_GATEWAY_DQ_TABLE_FILES + "）")
    void w56_gatewayDqTable_ratchet() {
        Path gw = backendRoot().resolve("gateway/src/main/java");
        List<Path> offenders = javaFiles(gw);
        assertTrue(!offenders.isEmpty(), "gateway/src/main/java 未找到 .java，路径异常: " + gw);
        int hits = 0;
        List<String> detail = new ArrayList<>();
        for (Path p : offenders) {
            if (GATEWAY_DQ_TABLE.matcher(strip(p)).find()) {
                hits++;
                detail.add(rel(p, gw));
            }
        }
        ratchet("W56 gateway 直查 ecos_dq_issue/ecos_dq_rule（应经 datanet 契约）", hits,
                W56_BASELINE_GATEWAY_DQ_TABLE_FILES, detail);
    }

    // ─────────────────────────── W59 DDL gen_random_uuid ─────────────

    @Test
    @DisplayName("W59 DdlCompliance — 迁移脚本 gen_random_uuid（MC01 红线）只减不增（基线 "
            + W59_BASELINE_GEN_RANDOM_UUID_FILES + "）")
    void w59_genRandomUuid_ratchet() {
        Path dir = backendRoot().resolve("gateway/src/main/resources/db/migration");
        assertTrue(Files.isDirectory(dir), "迁移单源目录不存在: " + dir);
        List<String> detail = new ArrayList<>();
        int hits = 0;
        List<Path> sqls;
        try (Stream<Path> walk = Files.list(dir)) {
            sqls = walk.filter(p -> p.toString().endsWith(".sql")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        assertTrue(!sqls.isEmpty(), "迁移目录为空，路径异常: " + dir);
        for (Path p : sqls) {
            String text = read(p);
            if (GEN_RANDOM_UUID.matcher(text).find()) {
                hits++;
                detail.add(p.getFileName().toString());
            }
        }
        ratchet("W59 迁移脚本 gen_random_uuid（MC01：应用侧 UUID，DDL 禁 gen_random_uuid）", hits,
                W59_BASELINE_GEN_RANDOM_UUID_FILES, detail);
    }

    // ─────────────────────────── 公共辅助 ─────────────────────────────

    /** 顾atchet 判定：计数只减不增；新增即红（附当前明细）。 */
    private static void ratchet(String label, int current, int baseline, List<String> detail) {
        assertFalse(current > baseline,
                String.format("[%s] 债务回升：当前 %d > 基线 %d（只允许减少；新增明细）:\n  %s",
                        label, current, baseline, String.join("\n  ", detail)));
    }

    private static Path moduleSrcMain() {
        Path src = RlsInjectionGuardArchTest.resolveModuleSrcMainJava();
        assertNotNull(src, "data-engine-impl/src/main/java 不可解析");
        return src;
    }

    /** gateway / data-engine 共同的 ecos_backend 工程根（由本模块 src/main/java 逐级上溯）。 */
    private static Path backendRoot() {
        Path r = moduleSrcMain();
        // src/main/java → .../data-engine-impl → .../data-engine → engine → ecos_backend
        for (int i = 0; i < 6; i++) {
            r = r.getParent();
        }
        assertNotNull(r, "无法上溯到 ecos_backend 工程根");
        assertFalse(r.getFileName() == null, "engines root 解析为空");
        return r;
    }

    private static java.util.List<Path> javaFiles(Path root) {
        assertTrue(Files.isDirectory(root), "非目录（路径异常）: " + root);
        List<Path> out = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            out = walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out;
    }

    private static String read(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 注释剔除后的"代码"视图（合法字符串字面量保留 —— 但 SQL 表名/字段声明都在字面量前缀里）。 */
    private static String strip(Path p) {
        return RlsInjectionGuardArchTest.stripComments(read(p));
    }

    /**
     * W54 判定用"伪代码"视图：field 声明 {@code private (final) JdbcTemplate x;} 在字符串字面量之外的
     * 真实字段区。用去字符串字面量的粗扫被 stripComments 过度剔除会漏，故这里用原始源判字段声明
     * （字段声明不出现在 SQL 字符串里，误报风险极低）。
     */
    private static String pseudoCode(Path p) {
        return read(p);
    }

    private static String rel(Path file, Path root) {
        return file.toString().replace(root.toString(), "").replaceFirst("^[\\\\/]+", "").replace('\\', '/');
    }
}
