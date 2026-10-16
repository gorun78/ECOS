package com.chinacreator.gzcm.workspace.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RLS「执行契约」跨模块键名与形态门禁（#142/B4-3c-2，差距 D-B4-17）。
 *
 * <p><b>为什么必须有源码级门禁</b>：D-B4-17 的缺陷本质是<b>键名漂移</b>——生产者
 * {@code /rls/apply} 的应答里从来没有 {@code whereClause}，而三个消费者（workspace /
 * ontology / ai）都按这个不存在的键读数，取到 {@code null} 后返回空串＝"无行级限制"，
 * 于是页面/本体/AI 三通道的行过滤恒等于关闭。两侧分处不同 Maven 模块、编译期零耦合，
 * 任何一侧再改键名或退回"空串即放行"的写法，单测都发现不了。本门禁做四件编译做不到的事：</p>
 * <ol>
 *   <li>生产者渲染器确实把五个契约键写进应答（{@link #producerEmitsTheExecutionContractKeys()}）；</li>
 *   <li>{@code /rls/apply} 服务把渲染结果并进响应（{@link #rlsApplyResponseMergesRenderedContract()}）——
 *       堵住"渲染器在位但没人调用"这一类假实现；</li>
 *   <li>三消费者读同一个键名字面量，且必须<b>成对</b>消费（读片段者要么也读
 *       {@code whereParams}，要么显式对 {@code ?} 走拒绝分支），
 *       {@link #consumersConsumeTheContractAsAPairOrDeny()}；</li>
 *   <li>fail-open 形态在<b>全后端生产源码</b>内零残留（{@link #failOpenShapeIsNowhereInConsumers()}，
 *       扫 {@code ecos_backend/**\src\main\java}，排 target/archive；同时断言三棵具名消费者树
 *       各自在本次扫描面内非空，防"扫描面悄悄缩回已知清单"）。</li>
 * </ol>
 *
 * <p>反假绿：每个用例的扫描根若解析到 0 个文件即判失败（禁"无文件＝无违例"的绿），
 * 消费者文件缺失同样直接失败。</p>
 */
@DisplayName("RLS 执行契约：跨模块键名单源 + 成对消费 + fail-open 形态零残留")
class RlsExecutionContractGateTest {

    private static final String RENDERER_SRC =
            "ecos_backend/engine/security-engine/security-engine-impl/src/main/java/"
                    + "com/chinacreator/gzcm/engine/security/rls/RlsPredicateRenderer.java";
    private static final String RLS_APPLY_SRC =
            "ecos_backend/engine/security-engine/security-engine-impl/src/main/java/"
                    + "com/chinacreator/gzcm/engine/security/service/RowLevelSecurityServiceImpl.java";

    /** 三个消费者的 RLS 读数位（相对仓根）。 */
    private static final List<String> CONSUMER_RLS_SOURCES = List.of(
            "ecos_backend/workspace/workspace-impl/src/main/java/"
                    + "com/chinacreator/gzcm/workspace/security/RlsFilter.java",
            "ecos_backend/engine/ontology-engine/ontology-engine-impl/src/main/java/"
                    + "com/chinacreator/gzcm/engine/ontology/security/SecurityEngineClient.java",
            "ecos_backend/engine/ai-engine/ai-engine-impl/src/main/java/"
                    + "com/chinacreator/gzcm/engine/ai/security/AiSecurityEngineClient.java");

    /** 消费者主源码树：具名读数位的扫描根（只增不减）。 */
    private static final List<String> CONSUMER_MAIN_TREES = List.of(
            "ecos_backend/workspace/workspace-impl/src/main/java",
            "ecos_backend/engine/ontology-engine/ontology-engine-impl/src/main/java",
            "ecos_backend/engine/ai-engine/ai-engine-impl/src/main/java");

    /**
     * fail-open 形态的<b>全后端</b>扫描根（#142/B4-3c-2 加严，防"第四消费者"漏项）。
     *
     * <p>只扫三个已知树＝把缺陷清单当成穷举的断言：本批实测后端还有第四处 RLS 消费者
     * （{@code data-engine QueryController} 读 {@code predicate.predicateTemplate} 形态，
     * 已是 fail-closed），若它日后改读 {@code whereClause}，三树扫描就看不见。
     * 全后端扫描排除 {@code target/}（构建产物）与 {@code archive/}（停写遗留）。</p>
     */
    private static final String WHOLE_BACKEND_MAIN_ROOT = "ecos_backend";

    /** 执行契约五个键（与 {@code Rendered.toMap()} 一一对应）。 */
    private static final List<String> CONTRACT_KEYS =
            List.of("whereClause", "whereParams", "unresolvedBindings", "noPolicyMatched", "rlsDenyReason");

    /**
     * 「取到 null 就返回空串」的原始缺陷形态（#142/B4-3c 收口前的生产代码逐字形状）。
     *
     * <p>必须是<b>跨行窗口</b>而非单行正则：当年的写法是两行
     * （先 {@code Object whereObj = data.get("whereClause");}，后
     * {@code return whereObj == null ? "" : …;}），单行正则对真实缺陷恒不命中＝假门禁。</p>
     */
    private static final Pattern FAIL_OPEN_SHAPE = Pattern.compile(
            "get\\(\\s*\"whereClause\"\\s*\\)[\\s\\S]{0,160}\\?\\s*\"\"\\s*:");


    @Test
    void producerEmitsTheExecutionContractKeys() throws IOException {
        String src = stripComments(readRepoFile(RENDERER_SRC));

        // toMap() 是应答键的唯一出口：五个键都要在那里落字面量
        int toMapAt = src.indexOf("public Map<String, Object> toMap()");
        assertTrue(toMapAt >= 0, "Rendered.toMap() 不存在，应答键失去单源出口");
        String toMapBody = src.substring(toMapAt, src.indexOf('\n', src.indexOf("}", toMapAt)));
        for (String key : List.of("whereClause", "whereParams", "unresolvedBindings", "noPolicyMatched")) {
            assertTrue(toMapBody.contains("m.put(\"" + key + "\""),
                    "D-B4-17 违规: 渲染器未把契约键写进应答: " + key);
        }
        assertTrue(toMapBody.contains("m.put(\"rlsDenyReason\""),
                "拒绝原因必须随应答外发（禁消费者折叠成「无数据」）");
        // 拒绝/恒真片段常量值必须与消费侧同值，否则三态判定分叉
        assertTrue(src.contains("DENY_CLAUSE = \"1=0\""), "拒绝片段字面量漂移");
        assertTrue(src.contains("ALLOW_ALL_CLAUSE = \"1=1\""), "恒真片段字面量漂移");
    }

    @Test
    void rlsApplyResponseMergesRenderedContract() throws IOException {
        String src = stripComments(readRepoFile(RLS_APPLY_SRC));

        assertTrue(src.contains("RENDERER.render("),
                "D-B4-17 违规: /rls/apply 未调用渲染器（渲染器在位但无人调用＝假实现）");
        assertTrue(src.contains("result.putAll(rendered.toMap())"),
                "D-B4-17 违规: /rls/apply 应答未并入执行契约（whereClause/whereParams 不会出现在响应里）");
    }

    @Test
    void consumersConsumeTheContractAsAPairOrDeny() throws IOException {
        for (String rel : CONSUMER_RLS_SOURCES) {
            String src = stripComments(readRepoFile(rel));
            assertTrue(src.contains("\"whereClause\""),
                    "D-B4-17 违规: 消费者未读契约键 whereClause（键名漂移或读数位被删）: " + rel);
            boolean bindsParams = src.contains("\"whereParams\"");
            boolean refusesUnbindable = src.contains("indexOf('?')") || src.contains("'?'");
            assertTrue(bindsParams || refusesUnbindable,
                    "D-B4-17 违规: 消费者取片段却不处理参数面（要么成对读 whereParams，要么对含 ? 片段走拒绝）: " + rel);
        }
        // 无参数通道的两个引擎必须把「含 ? 片段」判为拒绝，不能拼进 SQL（IR05）
        for (String rel : List.of(CONSUMER_RLS_SOURCES.get(1), CONSUMER_RLS_SOURCES.get(2))) {
            String src = stripComments(readRepoFile(rel));
            assertTrue(src.contains("indexOf('?')"),
                    "该消费者无参数绑定通道，必须显式对含 ? 片段走 DENY: " + rel);
        }
    }

    @Test
    void failOpenShapeIsNowhereInConsumers() throws IOException {
        List<String> violations = new ArrayList<>();
        java.util.Map<String, Integer> perKnownTree = new java.util.LinkedHashMap<>();
        int[] scanned = new int[1];
        Path root = repoFile(WHOLE_BACKEND_MAIN_ROOT);

        Files.walkFileTree(root, new java.nio.file.SimpleFileVisitor<Path>() {
            @Override
            public java.nio.file.FileVisitResult preVisitDirectory(
                    Path dir, java.nio.file.attribute.BasicFileAttributes attrs) {
                String name = dir.getFileName() == null ? "" : dir.getFileName().toString();
                // target/＝构建产物（含复制过去的源码，命中它不是生产事实）；
                // archive/＝已停写的遗留面；.git/＝版本库内部对象。
                if ("target".equals(name) || "archive".equals(name) || ".git".equals(name)) {
                    return java.nio.file.FileVisitResult.SKIP_SUBTREE;
                }
                return java.nio.file.FileVisitResult.CONTINUE;
            }

            @Override
            public java.nio.file.FileVisitResult visitFile(
                    Path file, java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                String normalized = file.toString().replace('\\', '/');
                if (normalized.endsWith(".java") && normalized.contains("/src/main/java/")) {
                    scanned[0]++;
                    for (String tree : CONSUMER_MAIN_TREES) {
                        if (normalized.contains(tree)) {
                            perKnownTree.merge(tree, 1, Integer::sum);
                        }
                    }
                    String code = stripComments(Files.readString(file, StandardCharsets.UTF_8));
                    Matcher m = FAIL_OPEN_SHAPE.matcher(code);
                    if (m.find()) {
                        violations.add(root.relativize(file) + " ← " + m.group());
                    }
                }
                return java.nio.file.FileVisitResult.CONTINUE;
            }
        });

        assertTrue(scanned[0] > 0, "扫描根解析到 0 个 .java＝假绿，必须失败（实测扫描面: "
                + WHOLE_BACKEND_MAIN_ROOT + "）");
        // 已知三棵消费者树各自必须扫到文件：防"全后端扫描成功、但已知消费者被移出扫描面"
        // 这一类只增不减承诺被悄悄破掉的形态。
        for (String tree : CONSUMER_MAIN_TREES) {
            assertTrue(perKnownTree.getOrDefault(tree, 0) > 0,
                    "具名消费者树在本次扫描里为 0 个文件（树被移动/改名即门禁空转）: " + tree);
        }
        assertTrue(violations.isEmpty(),
                "D-B4-17 fail-open 形态回潮（取不到 whereClause 就当无过滤放行），"
                        + "扫描面=" + scanned[0] + " 个生产 .java: " + violations);
    }

    /**
     * 门禁自检（反假绿）：上一条用例之所以可信，是因为本模式对<b>缺陷原形</b>确实能变红。
     * 这里把收口前的生产代码逐字形状喂给同一个模式，断言命中——
     * 若有人把正则改成恒不命中的形态（"永远绿"的门禁），本用例先红。
     */
    @Test
    void gateItselfDetectsTheHistoricalFailOpenShape() {
        String preFixShape = stripComments("""
                Object whereObj = ((Map<String, Object>) resp.get("data")).get("whereClause");
                return whereObj == null ? "" : whereObj.toString();
                """);
        assertTrue(FAIL_OPEN_SHAPE.matcher(preFixShape).find(),
                "门禁模式对缺陷原形不命中＝假门禁，必须修模式而不是删用例");
        assertFalse(FAIL_OPEN_SHAPE.matcher(stripComments("""
                Object whereObj = data.get("whereClause");
                if (whereObj == null || String.valueOf(whereObj).isBlank()) {
                    return deny(DRIFT_ABSENT + " entity=" + entityCode, denyIfEmpty, unresolved);
                }
                """)).find(), "收口后的形状不得被误报（否则违例清单里全是噪声）");
    }

    @Test
    void workspaceChannelNeverRevertsToClauseOnlyApi() throws IOException {
        // 页面通道两个 Controller 必须用成对出口 buildRowFilter(；退回只取片段的
        // buildRowFilterCondition( 会让带 ? 的片段悬空（同一缺陷的另一半）
        List<String> pageControllers = List.of(
                "ecos_backend/workspace/workspace-impl/src/main/java/"
                        + "com/chinacreator/gzcm/workspace/controller/ObjectController.java",
                "ecos_backend/workspace/workspace-impl/src/main/java/"
                        + "com/chinacreator/gzcm/workspace/controller/ObjectQLController.java");
        for (String rel : pageControllers) {
            String src = stripComments(readRepoFile(rel));
            assertTrue(src.contains("buildRowFilter("), rel + " 未走成对下推出口 buildRowFilter(");
            assertFalse(src.contains("buildRowFilterCondition("),
                    rel + " 退回只取片段出口 buildRowFilterCondition(（带 ? 片段将无参数可绑）");
        }
    }

    // ═══════════════ 辅助 ═══════════════

    private static Path repoFile(String repoRelative) {
        Path cur = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 8 && cur != null; i++) {
            Path candidate = cur.resolve(repoRelative);
            if (Files.isRegularFile(candidate) || Files.isDirectory(candidate)) return candidate;
            cur = cur.getParent();
        }
        throw new IllegalStateException("未找到源文件/目录（跨模块契约的两侧必须都在，禁跳过校验）: "
                + repoRelative);
    }

    private static String readRepoFile(String repoRelative) throws IOException {
        return Files.readString(repoFile(repoRelative), StandardCharsets.UTF_8);
    }

    /** 剥块注释与行注释（与既有跨模块门禁同口径，避免注释里的示例字符串造成假命中）。 */
    private static String stripComments(String src) {
        return src.replaceAll("/\\*[^*]*\\*+(?:[^/*][^*]*\\*+)*/", " ")
                .replaceAll("//[^\\r\\n]*", " ");
    }
}
