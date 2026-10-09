package com.chinacreator.gzcm.engine.ai.arch;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 护栏合规源码守门（详细设计-06 F06-04 要点 1 · REQ-AI-01 §1.1 · 铁律 §2.4-3/6/7）。
 *
 * <p>F06-04 要点 1 原文："本地正则只允许作为'降级前的粗筛'且<b>粗筛结果不得作为放行依据</b>"。
 * 验收形态为源码级扫描（grep 计数门禁风味，非纯 bytecode）：</p>
 * <ol>
 *   <li>{@link #noLocalRegexUsedAsPassDecision()} —— 全 {@code engine/ai} 源码树扫描
 *       "正则匹配结果直接决定放行"的固定语式：同一文件内，出现 {@code .matches()} /
 *       {@code .find()} 的行起、向后 12 行窗口中存在代码行
 *       {@code return (true|TRUE|PASS|ALLOW)}（注释行与 Javadoc 行不计）。
 *       当前全树实测 <b>0 命中</b>（2026-10-09 预检），故规则保持<b>全树范围</b>而非收窄到
 *       护栏相关类（GuardrailsServiceImpl / SecurityCheckerNode / AgentToolPolicyGate /
 *       ToolObligationApplier / ToolExecutorService）—— 范围即设计文档所述 "ai-engine 内"。</li>
 *   <li>{@link #guardrailDecisionComesFromSecurity()} —— GuardrailsServiceImpl 的裁决
 *       必须出自 security（含 {@code securityClient.evaluateData(}）；粗筛正则清单
 *       （{@code COARSE_SPANS}）允许存在，但该类不得出现 {@code kafkaTemplate} /
 *       {@code new RestTemplate}（F06-03/F06-17 底座收口：事件走 runtime-event、HTTP 走 runtime-access）。</li>
 *   <li>{@link #noLocalMaskingAlgorithmClasses()} —— {@code ..engine.ai..} 源码树内
 *       不得存在本地脱敏算法类（文件名 {@code *Mask*} / {@code *Desensit*} / {@code *Anonymiz*}，
 *       大小写不敏感，0 命中）。脱敏/行过滤只在 security 侧（铁律 §2.4-1/2/3），
 *       obligations 消费见 F06-02 要点 2（分册验收 {@code ObligationsApplyTest#engineDoesNotSelfImplementMasking}
 *       的 grep 计数形态同批）。</li>
 * </ol>
 *
 * <p>源码根解析：cwd = 模块目录（{@code src/main/java/...}）或 ecos_backend / 仓库根
 * （{@code engine/ai-engine/ai-engine-impl/src/main/java/...} 及带 {@code ecos_backend/} 前缀），
 * 并自 user.dir 向上回溯祖先前缀逐一尝试；解析失败时<b>直接 fail</b>（禁静默跳过）。</p>
 */
class GuardrailComplianceArchTest {

    /** .matches()/.find() 之后判定"正则决定放行"的窗口行数（设计冻结值 12）。 */
    private static final int PASS_DECISION_WINDOW = 12;

    /** 放行语式：代码行内 return 直接给出 true/TRUE/PASS/ALLOW。 */
    private static final Pattern PASS_RETURN_IDIOM =
            Pattern.compile("\\breturn\\s+(true|TRUE|PASS|ALLOW)\\b");

    /** 本地脱敏算法类文件名黑名单形态（大小写不敏感）。 */
    private static final Pattern LOCAL_MASKING_FILE_NAME =
            Pattern.compile("(?i).*(mask|desensit|anonymiz).*\\.java");

    /** 源码根候选（相对 cwd / 各祖先目录），模块目录 → ecos_backend → 仓库根。 */
    private static final String[] SOURCE_ROOT_CANDIDATES = {
            "src/main/java/com/chinacreator/gzcm/engine/ai",
            "engine/ai-engine/ai-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ai",
            "ecos_backend/engine/ai-engine/ai-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ai",
    };

    // ─── a. 粗筛正则不得作为放行依据 ─────────────────────────────────────

    @Test
    void noLocalRegexUsedAsPassDecision() throws IOException {
        Path root = resolveSourceRoot();
        List<String> violations = new ArrayList<>();
        for (Path file : javaSources(root)) {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                String anchor = lines.get(i);
                if (isCommentLine(anchor)) {
                    continue;
                }
                if (!anchor.contains(".matches()") && !anchor.contains(".find()")) {
                    continue;
                }
                int end = Math.min(lines.size() - 1, i + PASS_DECISION_WINDOW);
                for (int j = i; j <= end; j++) {
                    String candidate = lines.get(j);
                    if (isCommentLine(candidate)) {
                        continue;
                    }
                    if (PASS_RETURN_IDIOM.matcher(candidate).find()) {
                        violations.add(root.relativize(file) + ":" + (j + 1) + ": "
                                + candidate.trim()
                                + "（正则判定行 " + anchor.trim() + " @line " + (i + 1) + "）");
                    }
                }
            }
        }
        if (!violations.isEmpty()) {
            throw new AssertionError(
                    "F06-04 要点 1 违规：本地正则匹配结果被直接用作放行依据（粗筛结果不得放行，"
                            + "裁决必须出自 security-engine）。\n命中清单（file:line）:\n  "
                            + String.join("\n  ", violations));
        }
    }

    // ─── b. 裁决出处 = security；底座收口 ───────────────────────────────

    @Test
    void guardrailDecisionComesFromSecurity() throws IOException {
        Path impl = findSourceFile(resolveSourceRoot(), "GuardrailsServiceImpl.java");
        String src = Files.readString(impl, StandardCharsets.UTF_8);
        List<String> failures = new ArrayList<>();
        if (!src.contains("securityClient.evaluateData(")) {
            failures.add("GuardrailsServiceImpl 缺少 securityClient.evaluateData( —— "
                    + "护栏裁决未走 security-engine（F06-04 要点 1：废本地正则冒充护栏）");
        }
        if (src.contains("kafkaTemplate")) {
            failures.add("GuardrailsServiceImpl 出现 kafkaTemplate —— 事件出口必须收 "
                    + "runtime-event EventBusService（F06-03 / 铁律 §2.5-7 禁自建 KafkaTemplate）");
        }
        if (src.contains("new RestTemplate")) {
            failures.add("GuardrailsServiceImpl 出现 new RestTemplate —— HTTP 出口必须收 "
                    + "runtime-access（F06-17 要点 2 / 铁律 §2.5-1）");
        }
        if (!failures.isEmpty()) {
            throw new AssertionError("F06-04/F06-03/F06-17 违规：\n  " + String.join("\n  ", failures));
        }
    }

    // ─── c. 禁本地脱敏算法类 ────────────────────────────────────────────

    @Test
    void noLocalMaskingAlgorithmClasses() throws IOException {
        Path root = resolveSourceRoot();
        List<String> hits = new ArrayList<>();
        for (Path file : javaSources(root)) {
            if (LOCAL_MASKING_FILE_NAME.matcher(file.getFileName().toString()).matches()) {
                hits.add(root.relativize(file).toString());
            }
        }
        if (!hits.isEmpty()) {
            throw new AssertionError("F06-04/§2.4-1 违规：..engine.ai.. 出现本地脱敏/掩码/匿名化算法类"
                    + "（脱敏只在 security 侧，引擎只传结果集）。\n命中文件:\n  "
                    + String.join("\n  ", hits));
        }
    }

    // ─── helpers ────────────────────────────────────────────────────────

    /**
     * 解析 ai-engine 生产源码根：从 user.dir 起逐级向上，对每一目录尝试
     * {@link #SOURCE_ROOT_CANDIDATES}；全部失败则 fail（禁静默跳过导致门禁空转）。
     */
    private static Path resolveSourceRoot() {
        Path start = Paths.get("").toAbsolutePath().normalize();
        for (Path dir = start; dir != null; dir = dir.getParent()) {
            for (String candidate : SOURCE_ROOT_CANDIDATES) {
                Path resolved = dir.resolve(candidate);
                if (Files.isDirectory(resolved)) {
                    return resolved;
                }
            }
        }
        throw new AssertionError("无法定位 ai-engine 源码根（cwd = " + start
                + "，候选 = " + String.join(" | ", SOURCE_ROOT_CANDIDATES)
                + "，已向上回溯至文件系统根）。F06-04 源码门禁不可静默跳过——请检查运行目录/工作树完整性");
    }

    private static List<Path> javaSources(Path root) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    .sorted()
                    .toList();
        }
    }

    private static Path findSourceFile(Path root, String fileName) throws IOException {
        Optional<Path> found = javaSources(root).stream()
                .filter(p -> p.getFileName().toString().equals(fileName))
                .findFirst();
        return found.orElseThrow(() -> new AssertionError(
                "F06-04 门禁依赖的源码文件不存在：" + fileName + "（源码根 = " + root + "）—— "
                        + "护栏实现被移除/改名属形态漂移，须连同本门禁一起复核"));
    }

    /** 注释行（单行注释 / Javadoc 续行 / 块注释起始）不参与放行语式判定，防 "@return true=…" 误报。 */
    private static boolean isCommentLine(String line) {
        String trimmed = line.trim();
        return trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*");
    }
}
