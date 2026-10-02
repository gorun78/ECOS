package com.chinacreator.gzcm.workspace.scenario;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 详细设计-07 F07-02 / C148 §8.1 点名 `nodeRatioFormulaIsGone`（设计原文挂
 * {@code ScenarioCompletenessServiceTest}；本测试独立成类，同包与他例紧邻 —— 类内判 FAIL 红线门禁）。
 * —— 铁律 §0.6.2 / REQ-PLT-13 的**判 FAIL 红线**：完整度**必须**是连边覆盖率
 * {@code |E_present ∩ E_req| / |E_req|}，**禁止**退化为"节点占比 / 绑定数量占比"
 * （PRD-01 §2.4 自带判词："完整度=绑定数量的实现 → 判 FAIL"）。
 *
 * <p><b>为什么是源码结构断言而非仅行为断言</b>：本类的行为用例（{@code fullChainYieldsCoverageOne} /
 * {@code missingMappingEdgeYieldsPointSixSeven} / {@code emptyScenarioIsZeroNotOneHundred} …）已在<b>数值</b>
 * 上证明当前算法正确 —— 但它们用当前夹具数据锁死当前公式，<b>失效前不会自动报警</b>：若日后有人把
 * {@code coverage} 改回 {@code totalObjects==0 ? 1.0 : present/total}（节点占比，X-23/X-24 旧实现），
 * 在"全绑定的演示数据"下可能照样产出通过值 → 行为用例全绿而公式已回退。正是本条判 FAIL 红线的盲区。
 * 故按设计点名补一条<b>源码级结构门禁</b>（同 {@code FormalBaselineReferenceTest#guardImplementedOnceNotTwice}
 * 反锚点同源手法）：断言计算点用的是"present 边类型命中 / 必需边分母"，且类内<b>永不出现</b>节点计数式
 * 分母（{@code totalObjects} / {@code graph()} 节点数 / 字面量 {@code 1.0} 短路）。不引 ArchUnit，读源即可离线证伪。</p>
 *
 * <p><b>扫的是"代码"非注释</b>：先剥离块注释（含 Javadoc）与行注释再断言 —— Javadoc 里提及
 * "nodeRatioFormulaIsGone / 节点占比"这一验收名不算回退，只有进了代码体才算。</p>
 */
class NodeRatioFormulaGoneTest {

    /** 分母必须是"必需边集合"，不是节点数。正典形式（唯一允许），出现在 compute() 内。 */
    private static final String EDGE_BASED_NUMERATOR_DIVISOR = "presentTypes.size() / required.size()";

    /** 节点占比/绑定数占比回退的指纹 —— 命中任一即 §0.6.2 判 FAIL。 */
    private static final String[] NODE_RATIO_FINGERPRINTS = {
            "totalObjects",
            "getGraph",
            "graph(",
            "bindings.size",
            "bindings().size",
    };

    /** 空场景"恒 100%"的字面量短路（X-23 旧实现 `totalObjects == 0 ? 1.0`）—— code 内禁止。 */
    private static final String LITERAL_HUNDRED_SHORTCUT = "1.0";

    private static Path completenessServiceSource() {
        Path cur = Path.of("").toAbsolutePath().normalize();
        for (int i = 0; i < 8 && cur != null; i++) {
            Path cand = cur.resolve("src/main/java/com/chinacreator/gzcm/workspace/scenario/ScenarioCompletenessService.java");
            if (Files.exists(cand) && Files.exists(cur.resolve("pom.xml"))) {
                return cand;
            }
            cur = cur.getParent();
        }
        throw new AssertionError("无法定位 ScenarioCompletenessService.java（src/main/java + pom.xml）");
    }

    @Test
    @DisplayName("nodeRatioFormulaIsGone：完整度=连边覆盖率，类内无任何节点占比/绑定数分母回退")
    void nodeRatioFormulaIsGone() throws IOException {
        String code = stripComments(Files.readString(completenessServiceSource(), StandardCharsets.UTF_8));

        // 正典：计算点确实用 present 边类型命中数 / 必需边分母（连边覆盖率）
        assertTrue(code.contains(EDGE_BASED_NUMERATOR_DIVISOR),
                "F07-02 §0.6.2 红线：coverage 必须是 |E_present ∩ E_req| / |E_req| 连边覆盖率；"
                        + "类内未找到 present-types / required-edges 除法式");

        // 反锚点：节点占比/绑定数占比指纹一条都不许进代码体
        for (String fp : NODE_RATIO_FINGERPRINTS) {
            assertFalse(code.contains(fp),
                    "铁律 §0.6.2 / REQ-PLT-13 判 FAIL：完整度禁退化回节点占比/绑定数量占比 —— "
                            + "检测到指纹 `" + fp + "`（X-23/X-24 旧实现回退复辟）");
        }
        // 空场景恒 100% 的字面量短路（totalObjects==0 ? 1.0）不许复活
        assertFalse(code.contains(LITERAL_HUNDRED_SHORTCUT),
                "F07-02 要点 2：空场景必须 coverage=null + EMPTY_SCENARIO（禁 100%）—— "
                        + "类内检测到字面量 `1.0` 短路（X-23 `totalObjects==0 ? 1.0` 回退）");
    }

    /** 剥离块/行注释（本文件无字符串字面量含 /* 或 //，剥离安全）。 */
    private static String stripComments(String raw) {
        String noBlock = raw.replaceAll("(?s)/\\*.*?\\*/", " ");
        String noLine = noBlock.replaceAll("//[^\n]*", " ");
        return noLine;
    }
}
