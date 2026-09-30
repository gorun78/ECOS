package com.chinacreator.gzcm.engine.data.fact.dq;

import com.chinacreator.gzcm.engine.data.fact.FactType;
import com.chinacreator.gzcm.engine.data.fact.model.RowRejection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F02-07 红线架构守卫《NoSilentDefaultFillArchTest》— 禁"补默认值/补 0 以通过校验"。
 *
 * <p>设计依据《详细设计-02 数据域》§F02-07 红线「禁任何补默认值/补 0 以通过校验的代码
 * 路径（ArchTest 守）」+ PRD-02 §2.2：任一字段缺席一律按缺失进 F07/F02/F03 拦截，
 * 而非 {@code Map.putIfAbsent(field, 0)} 让校验"通过"。
 *
 * <p>守两条真实不变量：
 * <ol>
 *   <li><b>源码扫描</b>：DQ 门禁实现 {@link DqRowGateImpl}（行级校验真身）与门面
 *       {@code FactImportService} 的**比率/金额类列**（attributionRatio / realizationRate /
 *       amount / contractBase）不得出现 {@code put/putIfAbsent} 默认值填充——缺失必须
 *       被拒（F07/F02/F03），不得静默补齐。
 *       <b>口径校正（相对指令）</b>：指令列出的 {@code costCategory}/{@code currency}
 *       在 {@code FactImportService.hydrate} 以 {@code putIfAbsent} 预填，但那是 V167 DDL
 *       <b>NOT NULL 默认列的合法预填充</b>（Javadoc 明示「Service 侧同口径，避免 INSERT
 *       全值传 null」），与"补值以通过 DQ 校验"红线不同源；ratio/amount 类业务字段始终
 *       由 DQ 门禁承接缺失拒绝。故本守卫的行级红线锚定在 ratio/amount/base 列，
 *       costCategory/currency 的 DDL 默认预填不做"静默补值"违规判定。</li>
 *   <li><b>行为</b>：直接 {@code new DqRowGateImpl()}{@code checkRows(ATTRIBUTION,
 *       [仅 projectId 无 attributionRatio], ctx)} → 返回行含 {@code DQ-F07}（缺必填列）
 *       而非静默填 0；且入行 map 未被回填 {@code attributionRatio}（无静默填充副作用）。</li>
 * </ol>
 *
 * <p>W 行：详细设计-02 §F02-07 红线 / PRD-02 §2.2。
 *
 * @author ecos-p0-data-engine
 * @date 2026-09-30
 */
@DisplayName("F02-07 红线：禁补默认值/补 0 通过校验（ratio/amount 列禁 fill + 缺列走 F07 拒不收编）")
class NoSilentDefaultFillArchTest {

    /** ratio/amount/base 业务列的"静默填充"模式（put 或 putIfAbsent 给这些列赋默认值）。 */
    private static final Pattern RATIO_AMOUNT_FILL = Pattern.compile(
            "(?:put|putIfAbsent)\\s*\\(\\s*\"(?:attributionRatio|realizationRate|amount"
                    + "|contractBase|realizationRate)\"");

    private static final String GATE_IMPL_REL =
            "ecos_backend/engine/data-engine/data-engine-impl/src/main/java"
                    + "/com/chinacreator/gzcm/engine/data/fact/dq/DqRowGateImpl.java";
    private static final String FACT_SVC_REL =
            "ecos_backend/engine/data-engine/data-engine-impl/src/main/java"
                    + "/com/chinacreator/gzcm/engine/data/fact/service/FactImportService.java";

    @Test
    @DisplayName("DqRowGateImpl 对 ratio/amount/base 列无 put/putIfAbsent 静默填充")
    void dqGateImplNeverSilentlyFillsRatioAmount() throws IOException {
        Path gate = findRepoRoot().resolve(GATE_IMPL_REL);
        assertTrue(Files.isRegularFile(gate), "缺 DqRowGateImpl 源文件: " + gate);
        String src = Files.readString(gate, StandardCharsets.UTF_8);
        assertFalse(RATIO_AMOUNT_FILL.matcher(src).find(),
                "F02-07 红线：DqRowGate 行级门禁不得对 ratio/amount/base 列静默补默认值，"
                        + "缺列须按 F07/F02/F03 拒绝");
    }

    @Test
    @DisplayName("FactImportService 对 ratio/amount/base 列无 put/putIfAbsent 静默填充")
    void factImportNeverSilentlyFillsRatioAmount() throws IOException {
        Path svc = findRepoRoot().resolve(FACT_SVC_REL);
        assertTrue(Files.isRegularFile(svc), "缺 FactImportService 源文件: " + svc);
        String src = Files.readString(svc, StandardCharsets.UTF_8);
        assertFalse(RATIO_AMOUNT_FILL.matcher(src).find(),
                "F02-07 红线：导入门面不得对 ratio/amount/base 业务列静默补默认值"
                        + "（缺失须交 DqRowGate 拒收，禁止补 0 通过校验）");
    }

    @Test
    @DisplayName("行为：ATTRIBUTION 行缺 attributionRatio → DQ-F07 拒绝，且入行未被静默回填")
    void missingRatioRejectedNotFilled() {
        DqRowGateImpl gate = new DqRowGateImpl();
        // 仅 projectId、无 attributionRatio（ATTRIBUTION 必填列 = {projectId, attributionRatio}）
        Map<String, Object> row = new HashMap<>();
        row.put("projectId", "p-001");
        Map<String, Object> ctx = new HashMap<>();

        List<RowRejection> out = gate.checkRows(FactType.ATTRIBUTION, List.of(row), ctx);

        // 缺必填列 → 命中 F07（完整性），而非静默填 0 通过
        boolean f07 = out.stream().anyMatch(r -> r.ruleId().equals("DQ-F07"));
        assertTrue(f07, "缺 attributionRatio 必须命中 DQ-F07（缺必填列），实际 ruleIds: "
                + out.stream().map(RowRejection::ruleId).toList());
        // 且不得把该行收编为"通过"——至少一条拒绝
        assertFalse(out.isEmpty(), "缺必填列不得静默通过");
        // 关键红线：入行 map 未被回填 attributionRatio（无静默填充副作用）
        assertFalse(row.containsKey("attributionRatio"),
                "红线：门禁不得把缺失的 attributionRatio 静默回填到行上（补 0/补默认 = FAIL）");
        // 若命中 F07，field 应定位 attributionRatio
        RowRejection r7 = out.stream().filter(r -> r.ruleId().equals("DQ-F07")).findFirst().orElseThrow();
        assertEquals("attributionRatio", r7.field(), "F07 应定位缺失的必填列 attributionRatio");
    }

    static Path findRepoRoot() {
        Path dir = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        Path cursor = dir;
        while (cursor != null) {
            if (Files.isDirectory(cursor.resolve(".git")) || Files.exists(cursor.resolve(".git"))) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        cursor = dir;
        while (cursor != null) {
            if (Files.exists(cursor.resolve("pom.xml"))) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("无法从 user.dir=" + dir + " 定位仓库根（.git/pom.xml 均缺失）");
    }
}
