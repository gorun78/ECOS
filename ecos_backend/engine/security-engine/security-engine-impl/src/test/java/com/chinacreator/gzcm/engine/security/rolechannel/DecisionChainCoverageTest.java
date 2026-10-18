package com.chinacreator.gzcm.engine.security.rolechannel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * oracle 用例的<b>回放通道覆盖率钉</b>（#142/B4-3d-3，D-B4-20 收口 + 删直连通道的反面证据）。
 *
 * <p><b>本批的实质变化</b>：带 {@code resource.dataScope} 的三例（{@code proj_ai_p1} /
 * {@code proj_ai_p2} / {@code dept_ai}）从「护栏源直连回放」改成「三通道唯一入口回放」，
 * 于是 {@code /decision} 覆盖的 oracle 例数由 <b>9 → 12</b>。单靠 {@code OpaProjOwnerTest}
 * 变绿不能证明这件事 —— 必须钉"每一个 {@code /decision} 可表达形态的例，确实有一条用例
 * 经唯一入口回放它"，否则会出现"方法改了但某例没人回放"的空转型绿。</p>
 *
 * <p><b>怎么钉</b>：扫本模块 {@code src/test/java} 里的
 * {@code replayViaDecisionChain("<caseName>")} 调用点（正则容忍换行/缩进），
 * 与 oracle 单源的形态分区做集合比对；同时确认三个不可表达形态（带
 * {@code input.environment}）的例在 ai 侧消费者用例里仍被引用，
 * 防止"删了直连通道 ⇒ 该例从此无人回放"这种静默降覆盖。
 * {@code B4-3d-11} 新增的 {@code ai_tool_no_department_declared} 属于第三例，
 * 分区随之由 2→3，总例数 14→15（可表达侧仍为 12，故覆盖率钉的分子不变）。</p>
 *
 * <p><b>本批实测到的门禁有效性问题</b>：首跑即抓到 {@code norole_ai} 报「无人回放」。
 * 它其实在 {@code OpaDenyTest} 里跑过，只是用例名由 {@code "norole_" + purpose} 拼接而来 ——
 * 静态比对看不见拼接名。判定为<b>真问题而非假阳</b>并双侧修：调用点改字面量三条具名用例，
 * 本类另加一枚「调用点形态钉」禁止拼接名回潮（否则改名或漏一格都不会变红）。</p>
 *
 * <p>扫描根解析 0 文件即失败（禁把"读不到"当"无缺漏"）。</p>
 */
class DecisionChainCoverageTest {

    @Test
    @DisplayName("形态分区 — 12 例 /decision 可表达、3 例带 environment 不可表达")
    void oraclePartitionIsExactlyTwelvePlusThree() throws IOException {
        List<OpaOracleReplaySupport.OracleCase> all = OpaOracleReplaySupport.cases();
        assertEquals(15, all.size(), "oracle 单源例数变了 ⇒ 本覆盖率钉的基线须同批重算并登记");

        List<String> shaped = new ArrayList<>();
        List<String> unshaped = new ArrayList<>();
        for (OpaOracleReplaySupport.OracleCase c : all) {
            (c.decisionChainShaped() ? shaped : unshaped).add(c.name());
        }
        assertEquals(12, shaped.size(), "走唯一入口的例数应为 9→12（B4-3d-3 +3 例声明域）");
        assertEquals(unshaped, List.of("ai_channel_inferred", "ai_channel_no_department",
                        "ai_tool_no_department_declared").stream()
                        .filter(unshaped::contains).toList(),
                "不可表达形态只能是 AI 通道三例（带 input.environment，/decision 不发该段）");
        assertEquals(3, unshaped.size());
    }

    @Test
    @DisplayName("覆盖率 — 每个 /decision 可表达例都有一条经唯一入口回放的用例")
    void everyDecisionShapedCaseIsReplayedViaUniqueEntry() throws IOException {
        List<String> names = OpaOracleReplaySupport.cases().stream()
                .filter(OpaOracleReplaySupport.OracleCase::decisionChainShaped)
                .map(OpaOracleReplaySupport.OracleCase::name)
                .toList();

        String blob = codeBlob();

        List<String> missing = new ArrayList<>();
        for (String name : names) {
            if (!Pattern.compile("replayViaDecisionChain\\(\\s*\"" + Pattern.quote(name) + "\"")
                    .matcher(blob).find()) {
                missing.add(name);
            }
        }
        assertTrue(missing.isEmpty(),
                "以下 /decision 形态例无人经唯一入口回放（删直连通道后的静默降覆盖）: " + missing);
    }

    @Test
    @DisplayName("绕行通道已删除；带声明域的三例全部落在唯一入口侧")
    void scopeBearingCasesAreNotReplayedDirectly() throws IOException {
        for (var m : OpaOracleReplaySupport.class.getDeclaredMethods()) {
            assertFalse("replayViaGuardrailDirect".equals(m.getName()),
                    "绕行通道回来了 ⇒ 唯一入口不再是三通道验收的唯一路径（D-B4-20 复发）");
        }
        List<String> scoped = OpaOracleReplaySupport.cases().stream()
                .filter(OpaOracleReplaySupport.OracleCase::hasResourceScope)
                .filter(OpaOracleReplaySupport.OracleCase::decisionChainShaped)
                .map(OpaOracleReplaySupport.OracleCase::name)
                .sorted()
                .toList();
        assertEquals(List.of("dept_ai", "proj_ai_p1", "proj_ai_p2"), scoped,
                "声明域的三例必须都能经唯一入口表达（否则 D-B4-20 未收口）");
        // 第四例 ai_tool_no_department_declared 也带 resource.dataScope，但它是生产工具形态
        // （带 environment.channel=AGENT），按 D-B4-30 之前的现状不走 /decision —— 有意不入本清单。
    }

    @Test
    @DisplayName("调用点形态 — 回放用例名必须是字面量（拼接名让一格失去可静态定位的属主）")
    void caseNamesAtCallSitesAreLiterals() throws IOException {
        String blob = codeBlob();

        // 非字面量：首参直接是标识符。声明位 {@code public static ChainReplay replayViaDecisionChain(String caseName)}
        // 前面是空格不是点，故用「点号限定」把匹配面收在调用点上。
        assertFalse(Pattern.compile("\\.\\s*replayViaDecisionChain\\(\\s*[^\"\\s]").matcher(blob).find(),
                "存在首参非字面量的回放调用点");
        // 拼接名：字面量后紧跟 +（本批前的 "norole_" + purpose 形态）
        assertFalse(Pattern.compile("\\.\\s*replayViaDecisionChain\\(\\s*\"[^\"]*\"\\s*\\+")
                .matcher(blob).find(),
                "存在拼接用例名的回放调用点 ⇒ oracle 单源里某一格失去可静态定位的属主，"
                        + "该格改名或漏测时上一枚覆盖率钉不会变红");
    }

    /**
     * 本模块测试源码的<b>代码位</b>文本（先剥行注释与 javadoc 再匹配）。
     *
     * <p>剥注释不是宽松化：本类的两条形态钉都要防的违例形态（拼接名／变量名）只可能出现在代码位；
     * 反之，类注释里为解释缺陷而<b>提及</b> {@code replayViaDecisionChain(...)} 的说明文字
     * 会被点号限定误判成调用点。判据与 {@code RlsExecutionContractGateTest} 同批建立的
     * 「代码位字面量必见／注释提及必放」口径一致。</p>
     */
    private static String codeBlob() throws IOException {
        Path dir = testSourceRoot();
        List<String> sources;
        try (var walk = Files.walk(dir)) {
            sources = walk.filter(p -> p.getFileName().toString().endsWith(".java"))
                    .map(p -> {
                        try {
                            return stripComments(Files.readString(p, StandardCharsets.UTF_8));
                        } catch (IOException e) {
                            throw new IllegalStateException("测试源码不可读: " + p, e);
                        }
                    })
                    .toList();
        }
        assertFalse(sources.isEmpty(), "测试源码扫描根解析到 0 文件 —— 不得当成「无违例」: " + dir);
        String blob = String.join("\n", sources);
        assertTrue(blob.contains("replayViaDecisionChain"),
                "剥注释后代码位里连方法名都没有 ⇒ 扫描根错位，两条形态钉同时失效");
        return blob;
    }

    /** 剥 {@code /* ... *}{@code /}（含 javadoc）与 {@code //} 行注释；只用于本类扫描面。 */
    private static String stripComments(String src) {
        return src.replaceAll("(?s)/\\*.*?\\*/", " ")
                .replaceAll("(?m)//[^\\n]*", " ");
    }

    /** 本模块测试源码根（与 {@link #everyDecisionShapedCaseIsReplayedViaUniqueEntry()} 同根）。 */
    private static Path testSourceRoot() throws IOException {
        return OpaOracleReplaySupport.repoFile("ecos_backend/pom.xml").getParent()
                .resolve("engine/security-engine/security-engine-impl/src/test/java")
                .toRealPath();
    }

    @Test
    @DisplayName("三个 environment 形态例仍被 ai 侧消费者用例引用（降覆盖防护）")
    void environmentShapedCasesStayCoveredOnAiSide() throws IOException {
        String aiTest = Files.readString(OpaOracleReplaySupport.repoFile(
                "ecos_backend/engine/ai-engine/ai-engine-impl/src/test/java/"
                        + "com/chinacreator/gzcm/engine/ai/security/AiGuardrailVerdictFromOracleTest.java"),
                StandardCharsets.UTF_8);
        assertTrue(aiTest.contains("ai_channel_inferred"), "ai_channel_inferred 失去属主用例");
        assertTrue(aiTest.contains("ai_channel_no_department"), "ai_channel_no_department 失去属主用例");
        // #142/B4-3d-11：这一格是 D-B4-29 唯一能被真引擎证伪的形态（生产工具载荷 + 主体域已解析），
        // 只挂在 ai 侧消费者用例上 —— 属主一旦被删，矩阵会静默退回"没人证明 p1 域内可达"。
        assertTrue(aiTest.contains("ai_tool_no_department_declared"),
                "ai_tool_no_department_declared 失去属主用例");
    }
}
