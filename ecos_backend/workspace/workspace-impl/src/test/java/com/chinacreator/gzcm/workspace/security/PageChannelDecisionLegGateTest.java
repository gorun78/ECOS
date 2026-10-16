package com.chinacreator.gzcm.workspace.security;

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
 * 页面通道裁决腿与通道词单源的跨模块静态门禁（#142/B4-3d-2，差距 D-B4-15 / D-B4-16 / D-B4-27）。
 *
 * <p><b>本门禁防的是三件编译期与单测期都看不见的事</b>：</p>
 * <ol>
 *   <li><b>第二条裁决腿复发</b>（D-B4-15）：同一个主体在同一张表上的列面，此前由
 *       {@code /cls/columns} 与 {@code /decision} 两个端点各答一次。「绕过其中一条在架构上
 *       不可表达」的前提是先只有一条 —— 单测里把 client mock 掉时，多一条腿照样绿
 *       （D-B4-25 的遮蔽机制正是如此，见 {@code WorkspaceClsColumnSurfaceTest} 类注释）；</li>
 *   <li><b>列面读数回到第三种叫法</b>（D-B4-16）：{@code bundle.columns} 曾只有
 *       {@code mode}（恒 "allow"，零信息）/{@code names}，消费者自行 {@code get("names")}
 *       就等于在 workspace 侧再造一套列面真值；四态（策略拒绝 / 宇宙未解析 / 源异常 / 键面漂移）
 *       一旦被折成一条日志，运维与调用方就分不清 403 与 503（§4.5.4「不可用 ≠ 无权限」）；</li>
 *   <li><b>通道词字面量复制</b>（D-B4-27）：入口把认不出的 purpose 静默当 {@code page}
 *       是 fail-open 伪装成成功，而消费侧内联 {@code "export"} 会让"改了词表没人知道"
 *       成为常态。词表唯一持有者必须是 {@code DecisionChannelContract}。</li>
 * </ol>
 *
 * <p>与 {@code ClsColumnSurfaceContractGateTest}（B4-3d-1，键面单源）分工：那条门钉
 * <em>列面键名</em>的单源，本条门钉 <em>裁决腿数量、状态分面与通道词</em>的单源，
 * 二者刻意不重复扫描同一断言（门禁也要单源）。</p>
 *
 * <p><b>反假绿</b>：每个扫描根解析到 0 个文件即判失败；具名文件缺失由 {@link #repoFile}
 * 直接抛错；对照面（生产者写权威键、契约持有三词、客户端仍声明 legacy 出口）与违例面同批断言，
 * 使"因为扫不到所以通过"不可能成立。每条违例面都有对应的可变红探针（见类尾注释）。</p>
 */
@DisplayName("页面通道：/decision 单腿 + 列面四态不折叠 + 通道词唯一持有者")
class PageChannelDecisionLegGateTest {

    // ── 具名源（相对仓根；缺失即抛，禁"文件不在＝无需校验"） ──
    private static final String ABAC_SRC =
            "ecos_backend/workspace/workspace-impl/src/main/java/"
                    + "com/chinacreator/gzcm/workspace/security/AbacQueryFilter.java";
    private static final String WORKSPACE_CLIENT_SRC =
            "ecos_backend/workspace/workspace-impl/src/main/java/"
                    + "com/chinacreator/gzcm/workspace/security/WorkspaceSecurityEngineClient.java";
    private static final String PRODUCER_CONTROLLER_SRC =
            "ecos_backend/engine/security-engine/security-engine-impl/src/main/java/"
                    + "com/chinacreator/gzcm/engine/security/controller/DecisionController.java";
    private static final String CHANNEL_CONTRACT_SRC =
            "ecos_backend/runtime/common-api/src/main/java/"
                    + "com/chinacreator/gzcm/common/security/decision/DecisionChannelContract.java";
    private static final String COLUMN_CONTRACT_SRC =
            "ecos_backend/runtime/common-api/src/main/java/"
                    + "com/chinacreator/gzcm/common/security/cls/ColumnSurfaceContract.java";

    private static final String WORKSPACE_MAIN_TREE =
            "ecos_backend/workspace/workspace-impl/src/main/java";
    private static final String WHOLE_BACKEND_MAIN_ROOT = "ecos_backend";

    /** 裁决端点的主源码持有者基线（只增不减；新增持有者必须同批登记并说明理由）。 */
    private static final List<String> DECISION_ENDPOINT_HOLDERS = List.of(
            PRODUCER_CONTROLLER_SRC, WORKSPACE_CLIENT_SRC);

    /** 页面通道列裁剪的五条分立理由（日志面在代码里，不经注释剥除即可见）。 */
    private static final List<String> FOUR_STATE_LOG_FACES = List.of(
            "结果行无列键，未发起裁决",
            "页面通道裁决束缺失，非策略结论",
            "bundle 列面键面漂移，非策略结论",
            "列全集未解析，裁决未判定列面",
            "security-engine 明确拒绝");

    /** 列面键面（含 legacy 别名与第三种叫法）：workspace 侧禁直读。 */
    private static final List<String> FORBIDDEN_DIRECT_READS = List.of(
            "get(\"names\")", "get(\"mode\")",
            "get(\"visibleColumns\")", "get(\"blockedColumns\")",
            "get(\"allowedColumns\")", "get(\"clsDenyReason\")",
            "get(\"columnUniverseSource\")", "get(\"columnUniverseResolved\")");

    // ═══════════════ 1. 单腿：页面通道只经 /decision ═══════════════

    @Test
    @DisplayName("filterColumns 只经 /decision 的 page 通道，且不再另调 /cls/columns")
    void pageChannelColumnTrimIsASingleDecisionLeg() throws IOException {
        String src = stripComments(readRepoFile(ABAC_SRC));

        assertTrue(src.contains("securityEngineClient.decide("),
                "页面通道列裁剪未走唯一裁决入口＝仍在分散面上（D-B4-15 未收口）");
        assertTrue(src.contains("DecisionChannelContract.PURPOSE_PAGE"),
                "通道词必须取单源常量，不得内联");
        assertFalse(src.contains("allowedColumns("),
                "列面出现第二条裁决腿（/cls/columns）＝同一主体同一张表由两端点各答一次");
        assertFalse(src.contains("cls/columns"),
                "页面通道源码树内不得再出现列面端点路径");
    }

    @Test
    @DisplayName("allowedColumns 的持有者恰为客户端声明处（调用面为零，legacy 出口只停写不删）")
    void allowedColumnsCallSitesAreSealedToTheLegacyClient() throws IOException {
        ScanResult scan = scanWorkspaceMainSources();
        assertTrue(scan.scanned > 0, "workspace 主源码扫描面为 0＝假绿，直接判失败");

        assertTrue(scan.allowedColumnsHolders.size() == 1
                        && scan.allowedColumnsHolders.get(0).equals(WORKSPACE_CLIENT_SRC),
                "allowedColumns 只允许作为客户端的 legacy 契约面存在（退役判定归 B4-3d 后续子批），"
                        + "任何新增调用点必须走 /decision，实际持有者=" + scan.allowedColumnsHolders);
        String client = stripComments(readRepoFile(WORKSPACE_CLIENT_SRC));
        assertTrue(client.contains("public List<String> allowedColumns("),
                "对照面失效：客户端 legacy 出口已被删除，本门禁的持有者清单需与退役登记同批改");
    }

    @Test
    @DisplayName("/api/v1/security/decision 的主源码持有者清单不缩，workspace 侧 HTTP 触点唯一")
    void decisionEndpointHoldersAreSealedAndNeverShrink() throws IOException {
        ScanResult scan = scanBackendMainSources();
        assertTrue(scan.scanned > 0, "全后端主源码扫描面为 0＝假绿，直接判失败");

        assertTrue(scan.decisionEndpointHolders.containsAll(DECISION_ENDPOINT_HOLDERS),
                "裁决端点两侧（生产者 Controller + workspace 消费者）必须都在清单内，"
                        + "实际=" + scan.decisionEndpointHolders);
        assertTrue(scan.decisionEndpointHolders.size() >= DECISION_ENDPOINT_HOLDERS.size()
                        && scan.workspaceDecisionHolders.size() == 1
                        && scan.workspaceDecisionHolders.get(0).equals(WORKSPACE_CLIENT_SRC),
                "workspace 侧「谁能调 security-engine」的清单必须保持单点，实际="
                        + scan.workspaceDecisionHolders);
    }

    // ═══════════════ 2. 四态不折叠 + 读数单源 ═══════════════

    @Test
    @DisplayName("列面读数委托 ColumnSurfaceContract，四态各有一条可区分的 DENY 理由")
    void columnSurfaceStatesAreReportedDistinctly() throws IOException {
        String src = stripComments(readRepoFile(ABAC_SRC));

        assertTrue(src.contains("import com.chinacreator.gzcm.common.security.cls.ColumnSurfaceContract;"),
                "未 import 单源契约＝改回本地键面判定（制造第二套真值）");
        assertTrue(src.contains("ColumnSurfaceContract.read("),
                "列面读数必须经单源入口 ColumnSurfaceContract.read(");
        assertTrue(src.contains(".drifted()"), "缺漂移态分面＝漂移被折成普通空集（缺陷复发时无法在审计里区分）");
        assertTrue(src.contains("universeUnresolved("), "缺宇宙未解析分面＝调用方少传参数被报成用户无权限");
        assertTrue(src.contains("denyReasonOf("), "拒绝理由未取单源＝workspace 侧自编解释");

        for (String face : FOUR_STATE_LOG_FACES) {
            assertTrue(src.contains(face), "四态折叠：日志面缺少「" + face + "」——"
                    + "五态（含本地空宇宙）必须各带不同理由，禁折叠成一条「无可见列」");
        }
    }

    @Test
    @DisplayName("workspace 主源码零处直读列面键（names/mode/visibleColumns/…）")
    void workspaceNeverReadsBundleColumnKeysDirectly() throws IOException {
        ScanResult scan = scanWorkspaceMainSources();
        assertTrue(scan.scanned > 0, "workspace 主源码扫描面为 0＝假绿，直接判失败");
        assertTrue(scan.directReadViolations.isEmpty(),
                "列面键的读取只允许发生在单源契约内（workspace 直读＝第三种叫法复发），实际="
                        + scan.directReadViolations);
    }

    // ═══════════════ 3. 通道词唯一持有者 ═══════════════

    @Test
    @DisplayName("引用通道词表的文件一律用常量，全后端主源码的裸通道词只属于单源契约")
    void channelWordsAreHeldByTheContractAlone() throws IOException {
        String contract = stripComments(readRepoFile(CHANNEL_CONTRACT_SRC));
        for (String word : List.of("page", "export", "ai")) {
            assertTrue(contract.contains("\"" + word + "\""),
                    "对照面失效：单源契约不再持有 " + word + "，本用例的违例判定失去基准");
        }

        ScanResult scan = scanBackendMainSources();
        assertTrue(scan.scanned > 0, "全后端主源码扫描面为 0＝假绿，直接判失败");
        assertTrue(scan.rawChannelWordHolders.isEmpty(),
                "引用 DecisionChannelContract 的文件却内联通道词字面量＝第二套词面"
                        + "（词表改动时这些点不会红），实际=" + scan.rawChannelWordHolders);
    }

    @Test
    @DisplayName("入口解析三态在位：未识别 purpose 走 400，禁静默降级为 page")
    void entryParsingRejectsUnrecognizedChannelWords() throws IOException {
        String src = stripComments(readRepoFile(PRODUCER_CONTROLLER_SRC));

        assertTrue(src.contains("DecisionChannelContract.normalize("),
                "入口未用单源词表解析通道＝词表与入口各认各的（D-B4-27 复发）");
        assertTrue(src.contains("DecisionChannelContract.unrecognizedReason("),
                "未识别 purpose 的解释必须取自单源理由码，禁在 Controller 里另编一句");
        assertTrue(src.contains("resolvePurpose(rawPurpose)"),
                "入口解析未走统一方法＝三态判定散落在请求处理里");
        assertFalse(src.contains("import com.chinacreator.gzcm.workspace."),
                "契约/引擎层不得反向依赖 workspace（构建环）");
    }

    // ═══════════════ 辅助 ═══════════════

    /** 扫描累计结果：文件数、各类违例/持有者清单。 */
    private static final class ScanResult {
        int scanned;
        final Map<String, Integer> perTree = new LinkedHashMap<>();
        final List<String> allowedColumnsHolders = new ArrayList<>();
        final List<String> directReadViolations = new ArrayList<>();
        final List<String> decisionEndpointHolders = new ArrayList<>();
        final List<String> workspaceDecisionHolders = new ArrayList<>();
        final List<String> rawChannelWordHolders = new ArrayList<>();
    }

    private static ScanResult scanWorkspaceMainSources() throws IOException {
        ScanResult out = scan(WORKSPACE_MAIN_TREE, false);
        assertTrue(out.perTree.getOrDefault(WORKSPACE_MAIN_TREE, 0) > 0,
                "扫描根 " + WORKSPACE_MAIN_TREE + " 解析到 0 个源文件＝假绿");
        return out;
    }

    private static ScanResult scanBackendMainSources() throws IOException {
        return scan(WHOLE_BACKEND_MAIN_ROOT, true);
    }

    /**
     * @param wholeBackend true 时做跨模块面（裁决端点持有者 + 裸通道词），
     *                     false 时只扫 workspace 树（legacy 调用面 + 直读键面）
     */
    private static ScanResult scan(String rootRel, boolean wholeBackend) throws IOException {
        ScanResult out = new ScanResult();
        Path root = repoFile(rootRel);

        Files.walkFileTree(root, new java.nio.file.SimpleFileVisitor<Path>() {
            @Override
            public java.nio.file.FileVisitResult preVisitDirectory(
                    Path dir, java.nio.file.attribute.BasicFileAttributes attrs) {
                String name = dir.getFileName() == null ? "" : dir.getFileName().toString();
                // target/＝构建产物副本；archive/＝停写遗留；.git/＝库内部
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
                if (normalized.contains("/" + WORKSPACE_MAIN_TREE + "/")) {
                    out.perTree.merge(WORKSPACE_MAIN_TREE, 1, Integer::sum);
                }
                String repoRelative = normalized.substring(indexOfBackendMarker(normalized));
                String code = stripComments(Files.readString(file, StandardCharsets.UTF_8));

                if (code.contains("allowedColumns(")) {
                    out.allowedColumnsHolders.add(repoRelative);
                }
                for (String read : FORBIDDEN_DIRECT_READS) {
                    if (code.contains(read)) {
                        out.directReadViolations.add(repoRelative + " 直读 " + read);
                    }
                }
                if (!wholeBackend) return java.nio.file.FileVisitResult.CONTINUE;

                if (code.contains("/api/v1/security/decision")) {
                    out.decisionEndpointHolders.add(repoRelative);
                    if (repoRelative.startsWith(WORKSPACE_MAIN_TREE)) {
                        out.workspaceDecisionHolders.add(repoRelative);
                    }
                }
                if (!repoRelative.equals(CHANNEL_CONTRACT_SRC)
                        && code.contains("DecisionChannelContract")) {
                    for (String word : List.of("page", "export", "ai")) {
                        if (code.contains("\"" + word + "\"")) {
                            out.rawChannelWordHolders.add(repoRelative + " 内联 \"" + word + "\"");
                        }
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

    /** 剥块注释与行注释（与既有跨模块门禁同口径，避免注释里的示例字面量造成假命中）。 */
    private static String stripComments(String src) {
        return src.replaceAll("/\\*[^*]*\\*+(?:[^/*][^*]*\\*+)*/", " ")
                .replaceAll("//[^\\r\\n]*", " ");
    }
}

/*
 * 可变红探针（每条违例面各一次，逐条翻面、逐条还原，禁一次翻多面）：
 *   P1 在 AbacQueryFilter#filterColumns 内插入 `if (false) securityEngineClient.allowedColumns(entityCode, allColumns);`
 *      → 用例 1「第二条裁决腿」+ 用例 2「持有者清单恰为客户端」同时红（还原后二者复绿）。
 *   P2 把任一 FOUR_STATE_LOG_FACES 的理由串改成与其它条相同 → 用例「四态各有一条可区分的 DENY 理由」红。
 *   P3 在 ScenarioReadGuard 里把 PURPOSE_EXPORT 换成内联 "export" → 用例「裸通道词只属于单源契约」红。
 * 本批实测记录见差距文档 v1.0.19 行与 BUILD 日志；探针语句严禁入库（提交前 grep MUTATION 必须零残留）。
 */
