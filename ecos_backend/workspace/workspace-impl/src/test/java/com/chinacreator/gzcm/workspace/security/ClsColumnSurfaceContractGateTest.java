package com.chinacreator.gzcm.workspace.security;

import com.chinacreator.gzcm.workspace.testscan.SourceCommentStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CLS「列面读数」跨模块键名单源门禁（#142/B4-3d-1，差距 D-B4-25）。
 *
 * <p><b>为什么必须有源码级门禁</b>：D-B4-25 的缺陷本质是<b>键名漂移</b>——生产者
 * {@code ColumnLevelSecurityServiceImpl} 发的是 {@code visibleColumns}，三个消费者
 * （workspace / ontology / ai）都按 legacy 契约册的 {@code allowedColumns} 读数，
 * 取到 {@code null} 后返回空集＝列裁剪恒 DENY。两侧分处不同 Maven 模块、编译期零耦合，
 * 单测层又普遍把 client 整体 mock 掉，因此任何一侧再改键名都不会红。本门禁做四件编译做不到的事：</p>
 * <ol>
 *   <li>生产者确实写权威键（{@link #producerEmitsTheAuthoritativeColumnSurfaceKey()}）；</li>
 *   <li>三消费者都委托 {@code ColumnSurfaceContract.read}，且不再自己读键面
 *       （{@link #consumersDelegateColumnSurfaceToSingleSource()} +
 *       {@link #consumersNeverReimplementTheKeyLookup()}）——
 *       这一条同时封掉"把三处字面量各自改对＝制造三套真值"的回退路径；</li>
 *   <li>别名键字面量在全后端生产源码内只有单源一处
 *       （{@link #legacyAliasLiteralIsSingleSourced()}）；</li>
 *   <li>调用 {@code /security/cls/columns} 的生产者侧消费面清单不缩
 *       （{@link #columnSurfaceCallersAreCoveredByTheContract()}，D-B4-24 的教训：
 *       已知缺陷清单不等于穷举，第四消费者必须被同一条规则覆盖）。</li>
 * </ol>
 *
 * <p>反假绿：每个用例的扫描根解析到 0 个文件即判失败（禁"无文件＝无违例"的绿）；
 * 具名文件缺失同样直接失败（{@link #repoFile} 找不到即抛）。</p>
 */
@DisplayName("CLS 列面读数：权威键单源 + 三消费者委托同一读取面 + 别名字面量零复制")
class ClsColumnSurfaceContractGateTest {

    private static final String CONTRACT_SRC =
            "ecos_backend/runtime/common-api/src/main/java/"
                    + "com/chinacreator/gzcm/common/security/cls/ColumnSurfaceContract.java";
    private static final String PRODUCER_SRC =
            "ecos_backend/engine/security-engine/security-engine-impl/src/main/java/"
                    + "com/chinacreator/gzcm/engine/security/service/ColumnLevelSecurityServiceImpl.java";

    /** 三个消费者的 CLS 读数位（相对仓根，只增不减）。 */
    private static final List<String> CONSUMER_CLS_SOURCES = List.of(
            "ecos_backend/workspace/workspace-impl/src/main/java/"
                    + "com/chinacreator/gzcm/workspace/security/WorkspaceSecurityEngineClient.java",
            "ecos_backend/engine/ontology-engine/ontology-engine-impl/src/main/java/"
                    + "com/chinacreator/gzcm/engine/ontology/security/SecurityEngineClient.java",
            "ecos_backend/engine/ai-engine/ai-engine-impl/src/main/java/"
                    + "com/chinacreator/gzcm/engine/ai/security/AiSecurityEngineClient.java");

    /** 消费者主源码树：扫描面完整性断言的具名根（防扫描面悄悄缩回已知清单）。 */
    private static final List<String> CONSUMER_MAIN_TREES = List.of(
            "ecos_backend/workspace/workspace-impl/src/main/java",
            "ecos_backend/engine/ontology-engine/ontology-engine-impl/src/main/java",
            "ecos_backend/engine/ai-engine/ai-engine-impl/src/main/java");

    /** 全后端生产源码扫描根。 */
    private static final String WHOLE_BACKEND_MAIN_ROOT = "ecos_backend";

    /** 契约 import 的完整形态（单源委托的可观测证据）。 */
    private static final String CONTRACT_IMPORT =
            "import com.chinacreator.gzcm.common.security.cls.ColumnSurfaceContract;";

    // ═══════════════ 1. 生产者侧：权威键在位且未被别名替代 ═══════════════

    @Test
    @DisplayName("生产者写权威键 visibleColumns，且不写 legacy 别名键")
    void producerEmitsTheAuthoritativeColumnSurfaceKey() throws IOException {
        String src = stripComments(readRepoFile(PRODUCER_SRC));

        assertTrue(src.contains("\"visibleColumns\""),
                "生产者不再写 visibleColumns ⇒ 三消费者按单源读到的将是漂移态（本门禁的对照面失效）");
        assertTrue(src.contains("\"blockedColumns\""),
                "被隐藏列面是消费方展示/审计依据，生产者侧不得静默丢失");
        assertFalse(src.contains("\"allowedColumns\""),
                "生产者改写 legacy 别名键＝把 D-B4-25 反向复发（读侧兜底别名会被当权威键用）");
    }

    // ═══════════════ 2. 消费者侧：委托单源，禁自建键面判定 ═══════════════

    @Test
    @DisplayName("三消费者均 import 并调用 ColumnSurfaceContract.read（缺一即红）")
    void consumersDelegateColumnSurfaceToSingleSource() throws IOException {
        for (String rel : CONSUMER_CLS_SOURCES) {
            String src = stripComments(readRepoFile(rel));
            assertTrue(src.contains(CONTRACT_IMPORT),
                    rel + " 未 import 单源契约（改回本地字面量判定＝制造第二套真值）");
            assertTrue(src.contains("ColumnSurfaceContract.read("),
                    rel + " 未调用单源读取入口 ColumnSurfaceContract.read(");
        }
    }

    @Test
    @DisplayName("三消费者不再自己读列面键（get(\"visibleColumns\")/get(\"allowedColumns\") 零残留）")
    void consumersNeverReimplementTheKeyLookup() throws IOException {
        List<String> violations = new ArrayList<>();
        for (String rel : CONSUMER_CLS_SOURCES) {
            String src = stripComments(readRepoFile(rel));
            for (String key : List.of("visibleColumns", "blockedColumns", "allowedColumns")) {
                if (src.contains("get(\"" + key + "\")")) {
                    violations.add(rel + " 直接读键 " + key);
                }
            }
        }
        assertTrue(violations.isEmpty(), "列面键的读取只允许发生在单源契约内：" + violations);
    }

    @Test
    @DisplayName("漂移态必须显式处理：三消费者都带 drifted() 分支，禁静默空集冒充策略裁决")
    void consumersHandleDriftStateExplicitly() throws IOException {
        for (String rel : CONSUMER_CLS_SOURCES) {
            String src = stripComments(readRepoFile(rel));
            assertTrue(src.contains(".drifted()"),
                    rel + " 未对列面漂移分态（把漂移折成普通空集＝缺陷复发时无法在审计里区分）");
        }
    }

    // ═══════════════ 3. 别名键字面量在全后端只有一处 ═══════════════

    @Test
    @DisplayName("全后端生产源码内 legacy 别名字面量：单源持有 + 其余零复制 + 扫描面非空")
    void legacyAliasLiteralIsSingleSourced() throws IOException {
        ScanResult scan = scanBackendMainSources();
        assertTrue(scan.scanned > 0, "扫描面解析到 0 个源文件＝假绿，直接判失败");
        for (String tree : CONSUMER_MAIN_TREES) {
            assertTrue(scan.perTree.getOrDefault(tree, 0) > 0,
                    "消费者树 " + tree + " 在本次扫描面内为空（扫描面不得缩回已知清单）");
        }

        List<String> holders = scan.aliasLiteralHolders;
        assertTrue(holders.isEmpty(),
                "除单源契约外，任何生产源出现 legacy 别名字面量都是第二套真值：实际=" + holders);
        assertTrue(scan.contractHoldsAlias,
                "单源契约必须仍持有别名兜底字面量（删掉即停写侧旧应答无兼容，本门禁的对照面失效）");
    }

    @Test
    @DisplayName("调用 /security/cls/columns 的生产者侧消费面清单不缩，且新调用方必须挂契约")
    void columnSurfaceCallersAreCoveredByTheContract() throws IOException {
        ScanResult scan = scanBackendMainSources();
        assertTrue(scan.callerHolders.containsAll(CONSUMER_CLS_SOURCES)
                        && scan.callerHolders.size() == CONSUMER_CLS_SOURCES.size(),
                "调用列面端点的主源码应恰为具名三消费者，实际=" + scan.callerHolders
                        + "（新增消费者必须同时被单源契约覆盖并登记进本清单）");
        assertTrue(scan.contractLessCallers.isEmpty(),
                scan.contractLessCallers + " 调用列面端点却未委托单源契约（新消费面必须挂契约，禁本地读数）");
    }

    @Test
    @DisplayName("单源契约只依赖 JDK（common-api 不引业务模块，铁律 enforcer 面）")
    void singleSourceDependsOnJdkOnly() throws IOException {
        String src = stripComments(readRepoFile(CONTRACT_SRC));

        assertFalse(src.contains("import com.chinacreator.gzcm.engine."),
                "契约层不得反向依赖引擎实现（构建环）");
        assertFalse(src.contains("import com.chinacreator.gzcm.workspace."),
                "契约层不得反向依赖 workspace（构建环）");
        assertFalse(src.contains("import org.springframework."),
                "列面读取契约是纯数据形态判定，禁绑 Spring（消费侧三套 Spring 版本形态不一）");
    }

    // ═══════════════ 辅助 ═══════════════

    /** 全后端主源码一次遍历的累计结果：扫描数、每棵树文件数、别名持有者、调用面清单。 */
    private static final class ScanResult {
        int scanned;
        final Map<String, Integer> perTree = new LinkedHashMap<>();
        final List<String> aliasLiteralHolders = new ArrayList<>();
        final List<String> callerHolders = new ArrayList<>();
        final List<String> contractLessCallers = new ArrayList<>();
        boolean contractHoldsAlias;
    }

    private static ScanResult scanBackendMainSources() throws IOException {
        ScanResult out = new ScanResult();
        Path root = repoFile(WHOLE_BACKEND_MAIN_ROOT);

        Files.walkFileTree(root, new java.nio.file.SimpleFileVisitor<Path>() {
            @Override
            public java.nio.file.FileVisitResult preVisitDirectory(
                    Path dir, java.nio.file.attribute.BasicFileAttributes attrs) {
                String name = dir.getFileName() == null ? "" : dir.getFileName().toString();
                // target/＝构建产物（复制过去的源码不是生产事实）；archive/＝停写遗留；.git/＝库内部
                if ("target".equals(name) || "archive".equals(name) || ".git".equals(name)) {
                    return java.nio.file.FileVisitResult.SKIP_SUBTREE;
                }
                return java.nio.file.FileVisitResult.CONTINUE;
            }

            @Override
            public java.nio.file.FileVisitResult visitFile(
                    Path file, java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                String normalized = file.toString().replace('\\', '/');
                if (!(normalized.endsWith(".java") && normalized.contains("/src/main/java/"))) {
                    return java.nio.file.FileVisitResult.CONTINUE;
                }
                out.scanned++;
                for (String tree : CONSUMER_MAIN_TREES) {
                    if (normalized.contains("/" + tree + "/")) {
                        out.perTree.merge(tree, 1, Integer::sum);
                    }
                }
                String repoRelative = normalized.substring(indexOfBackendMarker(normalized));
                String code = stripComments(Files.readString(file, StandardCharsets.UTF_8));

                if (repoRelative.equals(CONTRACT_SRC)) {
                    // 单源本身合法持有别名字面量（兜底分支），不参与违例判定，但要单独确认它在位
                    if (code.contains("\"allowedColumns\"")) out.contractHoldsAlias = true;
                    if (code.contains("/security/cls/columns")) out.callerHolders.add(repoRelative);
                    return java.nio.file.FileVisitResult.CONTINUE;
                }
                if (code.contains("\"allowedColumns\"")) {
                    out.aliasLiteralHolders.add(repoRelative);
                }
                if (code.contains("/security/cls/columns")) {
                    out.callerHolders.add(repoRelative);
                    if (!code.contains("ColumnSurfaceContract.read(")) {
                        out.contractLessCallers.add(repoRelative);
                    }
                }
                return java.nio.file.FileVisitResult.CONTINUE;
            }
        });
        return out;
    }

    /** 从 {@code .../ecos_backend/...} 截出仓根相对路径，保证与具名清单可直接比对。 */
    private static int indexOfBackendMarker(String normalized) {
        int idx = normalized.indexOf(WHOLE_BACKEND_MAIN_ROOT + "/");
        if (idx < 0) {
            throw new IllegalStateException("扫描根异常（未取得 ecos_backend/ 前缀）: " + normalized);
        }
        return idx;
    }

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
        return SourceCommentStripper.strip(src);
    }
}
