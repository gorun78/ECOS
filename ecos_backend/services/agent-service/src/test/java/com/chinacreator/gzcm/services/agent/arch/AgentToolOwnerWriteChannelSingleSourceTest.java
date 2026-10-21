package com.chinacreator.gzcm.services.agent.arch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * agent 工具面<b>属主写通道与结论词表单源</b>门禁（#142/B4-3e-22，登记并收口差距 <b>D-B4-49 余项
 * 的进程内写腿子项</b>）。
 *
 * <p><b>本批解除的事实（HEAD 实测，非推断）</b>：{@code TriggerPipelineTool} 与
 * {@code RunQualityCheckTool} 此前各在 agent 进程里执行一条 {@code jdbcTemplate.update("INSERT INTO ...")}
 * ——前者写 data-engine 属主的真实运行台账 {@code ecos_pipeline_run}（违反铁律约束 5：写通道与台账归属主），
 * 后者写一张 <b>DDL 单源里根本不存在的幽灵表</b> {@code ecos_quality_check}（{@code CREATE TABLE ... ecos_quality_check}
 * 在 {@code docs/50-db_script/02-flyway-migration/} 全量 {@code V*.sql} 中实扫 0 枚），
 * 两支都<b>无条件</b>回 {@code status="triggered"}。于是属主的只读冲突（409）、已下线（410）、
 * 拒绝（403）、护栏不可用（503）四种不同事实在机读面上全长成「已触发」＝假执行面。</p>
 *
 * <p><b>本门钉六件事，缺一件都留得下复发路径</b>：
 * ①<b>工具面零 DML</b>——{@code tools/} 与 {@code AgentToolDao} 都不许再出现
 * {@code INSERT INTO}/{@code UPDATE ... SET}/{@code DELETE FROM} 或 {@code jdbcTemplate.update(}
 * （只禁 {@code tools/} 则写腿可以退回 DAO 里再插；只禁 DAO 则可以在工具里直接拼；两头都要钉）；
 * ②<b>两支写腿指向 V55 种子登记的属主端点</b>——路径常量与
 * {@code V55__ecos_tool_seed.sql} 里 {@code tool-006}/{@code tool-007} 的 {@code api_path} 逐字符对齐
 * （依据与 #142/B4-3e-16 对 {@code QueryPhysicalTable} 的裁决同形：种子才是本域端点的在册单源，
 * 自认一条「更合理的路径」不算收口）；③<b>词表单源</b>——状态码分派（{@code case 401/403/409/410/503}）
 * 只许出现在 {@code Outcome.java} 内，别处出现即第二套词表；
 * ④<b>传输骨架单源</b>——{@code HttpStatusCodeException} 的持有者清单钉死为
 * 「骨架 {@code OwnerPostLeg.java} ＋ 在册遗留一腿 {@code QueryPhysicalTableTool.java}」，
 * <b>新增即红、下降也红</b>（下降说明在册腿被改坏，或有人并骨后没随批下调基线）；
 * ⑤<b>写腿必须经基址单源与凭证单源</b>——两支都要含 {@code dataBaseUrl()} 与 {@code OwnerPostLeg.post(}，
 * 且不得再 {@code import} {@code AgentToolDao}（回归即进程内执行面复活）；
 * ⑥<b>{@code OWNER_CHANNEL_CLOSED} 是必需态</b>——{@code Outcome} 声明该常量，且 {@code 409/410}
 * 两条映射都在源码里（少了它就只能把「属主关了这个面」报成 403 或 500）。</p>
 *
 * <p><b>判据一律先剥注释</b>（D-B4-40 的教训）：本域 javadoc 大量「照登收口前形态」，
 * 里面就带 {@code INSERT INTO}／{@code jdbcTemplate.update(}／{@code case 403} 这些字面量
 * （实测 {@code TriggerPipelineTool} 第 20 行、{@code RunQualityCheckTool} 第 21 行、
 * {@code QueryPhysicalTableTool} 第 28 行），不剥注释的门会恒红而被人关掉，等于没有门。
 * 剥注释器必须<b>引号感知</b>：朴素实现会把字符串里的 {@code //}（如 URL）当行注释起点，
 * 把后半行真代码一起吃掉＝反向假绿。两向都由
 * {@link #stripperHandlesBothBlindSpots()} 的合成夹具正/反钉死，夹具只在内存里、不落仓。</p>
 *
 * <p>扫描面为空或显著少于在册文件数时 fail-loud，禁把「没扫到」读成「过门」。</p>
 */
class AgentToolOwnerWriteChannelSingleSourceTest {

    /** DML 字面量形态（出现在<b>代码</b>里即进程内写面）。 */
    private static final Pattern DML_LITERAL = Pattern.compile(
            "\"\\s*(?:[Ii][Nn][Ss][Ee][Rr][Tt]\\s+[Ii][Nn][Tt][Oo]|[Dd][Ee][Ll][Ee][Tt][Ee]\\s+[Ff][Rr][Oo][Mm]|[Uu][Pp][Dd][Aa][Tt][Ee]\\s+\\w+\\s+[Ss][Ee][Tt])");

    /** 状态码分派形态——只许在词表单源文件内出现。 */
    private static final Pattern STATUS_CASE = Pattern.compile("\\bcase\\s+(?:40[139]|410|503)\\b");

    /** 写腿的 DAO 依赖（回归即进程内执行面复活）。 */
    private static final Pattern DAO_IMPORT = Pattern.compile(
            "\\bimport\\s+[\\w.]*AgentToolDao\\s*;");

    /** 种子里的 {@code {id}} 形态路径段 → 代码里的 {@code %s} 形态。 */
    private static final Pattern SEED_PLACEHOLDER = Pattern.compile("\\{[^{}]*}");

    /** 词表单源文件（{@link Pattern} 判据对它放行）。 */
    private static final String VOCABULARY_SOURCE = "Outcome.java";

    /** 传输骨架文件（{@code HttpStatusCodeException} 的合法持有者之一）。 */
    private static final String SKELETON_SOURCE = "OwnerPostLeg.java";

    /** 在册遗留一腿（#142/B4-3e-16 收口，自带同形骨架、文案被契约测试钉死，本批不并骨）。 */
    private static final Set<String> REGISTERED_LEGACY_STATUS_HOLDERS = Set.of("QueryPhysicalTableTool.java");

    /** 本批收口的两支写腿 → 其在 V55 种子里登记的 {@code tool_code}。 */
    private static final List<WriteLeg> WRITE_LEGS = List.of(
            new WriteLeg("TriggerPipelineTool.java", "PATH_TASK_RUN",
                    "/api/v1/engine/data/pipeline/tasks/{id}/run", "tool-007", "TriggerPipeline"),
            new WriteLeg("RunQualityCheckTool.java", "PATH_RULE_EVALUATE",
                    "/api/v1/engine/data/quality/rules/{id}/evaluate", "tool-006", "RunQualityCheck"));

    /** 一支写腿的在册登记。 */
    private record WriteLeg(String fileName, String pathConstant, String seedPathTemplate,
                            String seedToolId, String toolCode) {
    }

    /** {@code tools/} 目录源码文件数下限（实测 23；显著低于即扫描面异常）。 */
    private static final int MIN_TOOL_FILES = 20;

    // ── ① 工具面零 DML ────────────────────────────────

    @Test
    @Timeout(value = 60)
    @DisplayName("D-B4-49 余项写腿：tools/ 与 AgentToolDao 均零 DML 字面量、零 jdbcTemplate.update")
    void noInProcessWriteSurfaceRemainsOnToolFace() throws IOException {
        List<String> hits = new ArrayList<>();
        for (Path p : toolSources()) {
            String code = stripComments(Files.readString(p, StandardCharsets.UTF_8));
            String name = p.getFileName().toString();
            Matcher m = DML_LITERAL.matcher(code);
            while (m.find()) {
                hits.add(name + ": " + m.group());
            }
            if (code.contains("jdbcTemplate.update(") || code.contains("jdbcTemplate.execute(")) {
                hits.add(name + ": jdbcTemplate 写方法调用");
            }
        }
        String toolDao = Files.readString(toolDaoSource(), StandardCharsets.UTF_8);
        Matcher daoDml = DML_LITERAL.matcher(stripComments(toolDao));
        while (daoDml.find()) {
            hits.add("AgentToolDao.java: " + daoDml.group());
        }
        if (stripComments(toolDao).contains("jdbcTemplate.update(")) {
            hits.add("AgentToolDao.java: jdbcTemplate.update(");
        }
        assertEquals(List.of(), hits,
                "agent 工具面又出现进程内写腿 ⇒ D-B4-49 余项复发：变更必须经属主引擎端点（写通道与台账归属主，铁律约束 5）");
    }

    // ── ② 写腿指向种子登记的属主端点 ──────────────────

    @Test
    @Timeout(value = 60)
    @DisplayName("两支写腿路径常量与 V55 种子的 api_path 逐字符对齐（种子是端点在册单源）")
    void writeLegPathsMatchTheSeededOwnerEndpoints() throws IOException {
        String seed = Files.readString(seedSource(), StandardCharsets.UTF_8);
        for (WriteLeg leg : WRITE_LEGS) {
            String declared = declaredPathConstant(
                    Files.readString(toolSource(leg.fileName()), StandardCharsets.UTF_8), leg.pathConstant());
            assertTrue(declared != null, leg.fileName() + " 缺少常量 " + leg.pathConstant()
                    + "：写腿路径必须是在册常量，不许散在调用点里");
            String seeded = seededPath(seed, leg.seedToolId(), leg.toolCode());
            assertEquals(SEED_PLACEHOLDER.matcher(seeded).replaceAll("{}"),
                    SEED_PLACEHOLDER.matcher(declared).replaceAll("{}"),
                    leg.fileName() + " 的端点与 V55 种子登记不符 ⇒ 要么改指了没登记的幻影路径，"
                            + "要么种子需同批订正（依据同 #142/B4-3e-16）");
            // 占位符形态本身也是判据：可达性门（AgentToolPathReachabilityTest）按花括号归一模板变量，
            // 写成 String.format 的 %s 形态会被它判成幻影路径 ⇒ 本批首跑即因此变红，故在此钉死形态
            assertTrue(declared.contains("{") && declared.contains("}"),
                    leg.fileName() + " 的路径占位符不是 {var} 形态（实测 " + declared
                            + "）⇒ 与属主 @PathVariable 不同形，且会让可达性门把它读成幻影路径");
            assertFalse(declared.contains("%s"),
                    leg.fileName() + " 用 %s 拼路径 ⇒ 可达性门的模板归一失效（D-B4-47 假幻影）");
        }
    }

    // ── ③④ 词表与传输骨架单源 ─────────────────────────

    @Test
    @Timeout(value = 60)
    @DisplayName("状态码分派只许出现在 Outcome.java（第二套词表即折叠的起点）")
    void statusDispatchLivesOnlyInTheVocabularySource() throws IOException {
        List<String> hits = new ArrayList<>();
        for (Path p : toolSources()) {
            String name = p.getFileName().toString();
            if (VOCABULARY_SOURCE.equals(name)) {
                continue;
            }
            Matcher m = STATUS_CASE.matcher(stripComments(Files.readString(p, StandardCharsets.UTF_8)));
            while (m.find()) {
                hits.add(name + ": " + m.group());
            }
        }
        assertEquals(List.of(), hits,
                "tools/ 里出现了第二套状态分派 ⇒ 该腿必然把 409/410/503 中的一个折成别的态；分派只经 Outcome");
    }

    @Test
    @Timeout(value = 60)
    @DisplayName("HttpStatusCodeException 持有者＝骨架＋在册遗留一腿：新增即红、下降也红")
    void statusExceptionHoldersMatchTheRegisteredInventory() throws IOException {
        Set<String> holders = new TreeSet<>();
        for (Path p : toolSources()) {
            if (stripComments(Files.readString(p, StandardCharsets.UTF_8)).contains("HttpStatusCodeException")) {
                holders.add(p.getFileName().toString());
            }
        }
        Set<String> expected = new TreeSet<>(REGISTERED_LEGACY_STATUS_HOLDERS);
        expected.add(SKELETON_SOURCE);
        assertEquals(expected, holders,
                "非骨架的出向腿又各自抓状态异常 ⇒ 传输分派回到腿里。在册遗留一腿并骨后须同批下调本基线，"
                        + "故清单下降同样变红（防'静默改坏'与'基线未随批更新'两种走法）");
        assertTrue(holders.contains(VOCABULARY_SOURCE) == false,
                "词表单源不该持有传输异常：Outcome 只做映射，抓异常属骨架职责");
    }

    // ── ⑤ 写腿的出向形态 ──────────────────────────────

    @Test
    @Timeout(value = 60)
    @DisplayName("两支写腿经 dataBaseUrl() 取基址、经 OwnerPostLeg 发请求、不再依赖 AgentToolDao")
    void writeLegsGoThroughTheSingleSourcedOwnerChannel() throws IOException {
        for (WriteLeg leg : WRITE_LEGS) {
            String code = stripComments(Files.readString(toolSource(leg.fileName()), StandardCharsets.UTF_8));
            assertTrue(code.contains("dataBaseUrl()"),
                    leg.fileName() + " 未经基址单源解析器 ⇒ 切流后打到已下线端口（D-B4-46 形态）");
            assertTrue(code.contains("OwnerPostLeg.post("),
                    leg.fileName() + " 未走传输骨架 ⇒ 凭证中继/异常归型回到腿里各写一遍");
            assertTrue(code.contains("Outcome.ACCEPTED"),
                    leg.fileName() + " 的成功出口未指定写腿终态 ⇒ 会沿用读腿词表，把受理报成取数");
            assertFalse(DAO_IMPORT.matcher(code).find(),
                    leg.fileName() + " 又 import 了 AgentToolDao ⇒ 进程内执行面复活");
            assertTrue(code.contains("safePathSegment"),
                    leg.fileName() + " 的路径段入参未过形状判据 ⇒ LLM 给的 id 能改写请求落点");
        }
    }

    // ── ⑥ OWNER_CHANNEL_CLOSED 必需 ───────────────────

    @Test
    @Timeout(value = 60)
    @DisplayName("Outcome 声明 OWNER_CHANNEL_CLOSED 并同时接住 409/410（属主关闭 ≠ 无权限 ≠ 故障）")
    void ownerClosedChannelIsAMandatoryState() throws IOException {
        String code = stripComments(Files.readString(toolSource(VOCABULARY_SOURCE), StandardCharsets.UTF_8));
        assertTrue(code.contains("OWNER_CHANNEL_CLOSED"),
                "词表里没有 OWNER_CHANNEL_CLOSED ⇒ 只能把属主按裁决下线的变更面报成 403（诱导申请权限）"
                        + "或 500（诱导重试）；本批两支写腿在真环境的结论正是 409/410");
        long closedMappings = STATUS_CASE.matcher(code).results()
                .filter(m -> m.group().equals("case 409") || m.group().equals("case 410"))
                .count();
        assertTrue(closedMappings >= 2,
                "409/410 必须在状态行与信封两条来源上都落到 OWNER_CHANNEL_CLOSED（实测命中 " + closedMappings + " 处）");
    }

    // ── 判据自身的取证：能变红 + 剥注释器两个盲区 ─────

    @Test
    @DisplayName("判据可红：DML/状态分派/DAO import 三种违例形态都能被抓住，且注释形态不误伤")
    void judgementsFailLoudOnRealViolationsButNotOnComments() {
        String violating = """
                class X {
                    void a() { jdbcTemplate.update("INSERT INTO ecos_pipeline_run (id) VALUES (?)"); }
                    Outcome b(int s) { return switch (s) { case 403 -> Outcome.DENIED; default -> null; }; }
                }
                """;
        assertEquals(1, count(DML_LITERAL, violating), "夹具未命中 ⇒ DML 判据本身是坏的（恒绿门）");
        assertEquals(1, count(STATUS_CASE, violating), "夹具未命中 ⇒ 状态分派判据本身是坏的");
        assertTrue(DAO_IMPORT.matcher("import com.chinacreator.gzcm.services.agent.dao.AgentToolDao;").find(),
                "DAO import 判据本身是坏的");

        String commentedOut = """
                class X {
                    // jdbcTemplate.update("INSERT INTO ecos_pipeline_run (id) VALUES (?)")
                    /* case 403 -> DENIED; */
                    /** INSERT INTO ecos_quality_check 系收口前形态 */
                    String url = "http://svc/teapot//x"; // 引号内的 // 不得把后半行吃掉
                    String tail = "KEEP_ME";
                }
                """;
        String stripped = stripComments(commentedOut);
        assertEquals(0, count(DML_LITERAL, stripped), "注释里的收口前形态被当成真代码 ⇒ 门恒红，最终会被人关掉");
        assertEquals(0, count(STATUS_CASE, stripped), "同上：javadoc/行注释不许触发状态分派判据");
        assertTrue(stripped.contains("KEEP_ME"),
                "字符串里的 // 被当成了注释起点 ⇒ 后半行真代码被吃掉（D-B4-40 的反向假绿）");
    }

    // ── 工具方法 ──────────────────────────────────────

    private static int count(Pattern p, String s) {
        return (int) p.matcher(s).results().count();
    }

    /** 从源码里取出 {@code static final String <name> = "..."} 的字面量值。 */
    private static String declaredPathConstant(String code, String constantName) {
        Matcher m = Pattern.compile("String\\s+" + Pattern.quote(constantName)
                + "\\s*=\\s*\"([^\"]+)\"").matcher(stripComments(code));
        return m.find() ? m.group(1) : null;
    }

    /** 从 V55 种子里取出某条工具的 {@code api_path}（第七列）。 */
    private static String seededPath(String seed, String toolId, String toolCode) {
        for (String line : seed.split("\n")) {
            if (line.contains("'" + toolId + "'") && line.contains("'" + toolCode + "'")) {
                Matcher m = Pattern.compile("'(/api/[^']*)'").matcher(line);
                List<String> paths = new ArrayList<>();
                while (m.find()) {
                    paths.add(m.group(1));
                }
                assertFalse(paths.isEmpty(), "种子行里没解析出 api_path: " + toolId);
                assertEquals(1, paths.size(), "种子行里出现多个 /api/ 字面量，取值不确定: " + toolId + " → " + paths);
                return paths.get(0);
            }
        }
        throw new AssertionError("V55 种子未登记 " + toolId + "/" + toolCode
                + " —— 该写腿在册端点的唯一依据消失，须先裁决归属再改代码");
    }

    /**
     * 引号感知的剥注释器（本域无 common-api 的 test-jar 依赖，故在门内自带一份；
     * 与 {@code SourceCommentStripper} 同判据：字符串与字符字面量内的斜杠组合不当注释起点）。
     *
     * <p><b>注</b>：本段说明刻意不写块注释的闭合符号序列（javadoc 自己就会被它截断，
     * 这正是 D-B4-40 那类「判据写进注释反而改变注释边界」的坑）。</p>
     *
     * <p>被剥掉的区间以空格替代，保持 token 边界：否则被注释分开的两个标识符会粘成一个，造出假符号。</p>
     */
    private static String stripComments(String src) {
        StringBuilder out = new StringBuilder(src.length());
        int i = 0;
        char quote = 0;
        while (i < src.length()) {
            char c = src.charAt(i);
            if (quote != 0) {
                out.append(c);
                if (c == '\\' && i + 1 < src.length()) {
                    out.append(src.charAt(i + 1));
                    i += 2;
                    continue;
                }
                if (c == quote) {
                    quote = 0;
                }
                i++;
                continue;
            }
            if (c == '"' || c == '\'') {
                quote = c;
                out.append(c);
                i++;
                continue;
            }
            if (c == '/' && i + 1 < src.length() && src.charAt(i + 1) == '/') {
                while (i < src.length() && src.charAt(i) != '\n') {
                    i++;
                }
                out.append(' ');
                continue;
            }
            if (c == '/' && i + 1 < src.length() && src.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < src.length() && !(src.charAt(i) == '*' && src.charAt(i + 1) == '/')) {
                    i++;
                }
                i = Math.min(i + 2, src.length());
                out.append(' ');
                continue;
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    /** 列出 {@code tools/} 下全部源码（含非 {@code *Tool.java} 的单源件），扫描面过小即 fail-loud。 */
    private static List<Path> toolSources() throws IOException {
        Path dir = toolsDir();
        List<Path> files = new ArrayList<>();
        try (Stream<Path> walk = Files.list(dir)) {
            walk.filter(f -> f.toString().endsWith(".java")).forEach(files::add);
        }
        assertTrue(files.size() >= MIN_TOOL_FILES,
                "tools/ 源码扫描面异常（实扫 " + files.size() + " 个 < 下限 " + MIN_TOOL_FILES
                        + "），门禁不得对空扫描面放行: " + dir);
        return files;
    }

    private static Path toolSource(String fileName) throws IOException {
        Path p = toolsDir().resolve(fileName);
        assertTrue(Files.isRegularFile(p), "在册单源件缺失: " + p);
        return p;
    }

    private static Path toolDaoSource() {
        Path p = ecosBackend().resolve(
                "services/agent-service/src/main/java/com/chinacreator/gzcm/services/agent/dao/AgentToolDao.java");
        assertTrue(Files.isRegularFile(p), "AgentToolDao 源码不存在: " + p);
        return p;
    }

    private static Path seedSource() {
        Path p = repoRoot().resolve("docs/50-db_script/02-flyway-migration/gateway/V55__ecos_tool_seed.sql");
        assertTrue(Files.isRegularFile(p), "V55 工具种子不存在，写腿端点的在册依据消失: " + p);
        return p;
    }

    private static Path toolsDir() {
        Path tools = ecosBackend().resolve(
                "services/agent-service/src/main/java/com/chinacreator/gzcm/services/agent/runtime/toolrouter/tools");
        assertTrue(Files.isDirectory(tools), "工具源码目录不存在: " + tools);
        return tools;
    }

    /** agent-service 模块工作目录上溯到 {@code ecos_backend}。 */
    private static Path ecosBackend() {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (dir != null && !Files.isDirectory(dir.resolve("services/agent-service"))) {
            dir = dir.getParent();
        }
        assertTrue(dir != null, "上溯未找到 ecos_backend 根");
        return dir;
    }

    /** 仓库根（须同时含 {@code ecos_backend} 与 {@code docs}，防止锚到上层或下层目录）。 */
    private static Path repoRoot() {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (dir != null && !(Files.isDirectory(dir.resolve("ecos_backend")) && Files.isDirectory(dir.resolve("docs")))) {
            dir = dir.getParent();
        }
        assertTrue(dir != null, "上溯未找到仓库根（需同时含 ecos_backend 与 docs）");
        return dir;
    }
}
