package com.chinacreator.gzcm.gateway.arch;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 规范体系一致性门禁（治理建议 §三 第 2 件）。
 *
 * <p>守住一条真实不变量：<b>规范索引（路由表）与规范正文必须自洽</b>。
 * 索引里的版本号、红线条数、文件路径都是手抄副本，任何一处与正文分叉
 * 都会让触发路由指错口径。本测试把"手抄必然漂移"变成提交即红。</p>
 *
 * <p>四类检查：① 索引版本 == 文件头版本；② 索引声明的红线条数 == 正文枚举相加；
 * ③ 索引与触发文件引用的规则路径必须存在；④ 规范总行数只降不升（ratchet）。</p>
 *
 * <p>边界：只校验索引/触发文件与正文头部的自洽性，不校验规范内容对错，
 * 也不复述任何业务数字（版本号一律从正文头部现读）。</p>
 */
@DisplayName("规范索引与正文一致性门禁")
class RulesIndexConsistencyGateTest {

    /** 索引路由表。 */
    private static final String INDEX_REL = ".qoder/rules/00-规范索引.md";
    /** 后端触发文件（引用了铁律与后端/数据库规范路径）。 */
    private static final String BACKEND_TRIGGER_REL = ".qoder/rules/后端规范触发.md";
    /** 数据库/文档触发文件。 */
    private static final String DB_DOC_TRIGGER_REL = ".qoder/rules/数据库与文档规范触发.md";

    /**
     * 规范总行数上限（ratchet，只降不升）。
     * 基线 = 治理执行时实测 2289 行 + 新增内容余量，收口后应随瘦身逐步下调，
     * 任何新增叙事段落都会顶穿上限从而被迫走"先精简再新增"。
     */
    private static final int RULES_TOTAL_LINE_CEILING = 2350;

    /** 版本号：兼容 `v2.4` 与 `版本: 2.4 |` 两种写法。 */
    private static final Pattern VERSION_TOKEN = Pattern.compile("v?(\\d+(?:\\.\\d+)+)");

    /** 索引表格行：| 规范名 | 版本... | `路径` | 场景 | */
    private static final Pattern INDEX_ROW = Pattern.compile(
            "^\\|\\s*([^|]+?)\\s*\\|\\s*([^|]*?)\\s*\\|\\s*`([^`]+)`\\s*\\|");

    /** 正文红线条数声明：共 N 条。 */
    private static final Pattern REDLINE_COUNT = Pattern.compile("共\\s*(\\d+)\\s*条");

    /** 红线段枚举：IR01~06 + DR01~08 ... */
    private static final Pattern REDLINE_RANGE = Pattern.compile("([A-Z]{2})(\\d{2})~([A-Z]{2})?(\\d{2})");

    @Test
    @DisplayName("索引版本号与规范正文头部版本一致")
    void indexVersionsMatchFileHeaders() throws IOException {
        Path root = findRepoRoot();
        List<String> mismatches = new ArrayList<>();
        int checked = 0;

        for (String line : Files.readAllLines(root.resolve(INDEX_REL), StandardCharsets.UTF_8)) {
            Matcher row = INDEX_ROW.matcher(line);
            if (!row.find()) {
                continue;
            }
            String name = row.group(1).trim();
            String indexVersionCell = row.group(2).trim();
            String relPath = row.group(3).trim();

            Path ruleFile = root.resolve(relPath);
            if (!Files.isRegularFile(ruleFile)) {
                mismatches.add(name + "：索引声明的路径不存在 " + relPath);
                continue;
            }
            // 红线文件（禁任何 Agent 修改）不参与版本对账，索引写「红线文件」即视为已声明
            if (indexVersionCell.contains("红线文件")) {
                continue;
            }
            String indexVersion = firstVersion(indexVersionCell);
            String headerVersion = firstVersion(headerOf(ruleFile));
            if (indexVersion == null && headerVersion == null) {
                continue; // 双方都无版本声明 → 不判
            }
            checked++;
            if (indexVersion == null) {
                mismatches.add(name + "：索引版本格未填（正文 v" + headerVersion + "），"
                        + "索引须写明版本或显式标注「红线文件」");
            } else if (headerVersion == null) {
                mismatches.add(name + "：正文头部未写版本，索引为 v" + indexVersion);
            } else if (!indexVersion.equals(headerVersion)) {
                mismatches.add(name + "：索引 v" + indexVersion + " ≠ 正文 v" + headerVersion
                        + "（" + relPath + "）");
            }
        }

        assertTrue(checked > 0, "索引一行规范都没解析到，正则与索引格式已脱节：" + INDEX_REL);
        assertTrue(mismatches.isEmpty(),
                "规范索引与正文版本号分叉（手抄漂移，改版本时须同批改索引）：\n"
                        + String.join("\n", mismatches));
    }

    @Test
    @DisplayName("索引声明的红线条数与正文枚举相加一致")
    void indexRedlineCountMatchesEnumeration() throws IOException {
        Path root = findRepoRoot();
        String dbRuleRel = ".trae/rules/数据库访问规范.md";
        Path dbRule = root.resolve(dbRuleRel);
        assertTrue(Files.isRegularFile(dbRule), "缺数据库访问规范：" + dbRuleRel);

        // 取第一条带「共 N 条」的声明行，并要求该行自身含枚举（避免把变更块里的旧值当权威）
        String declaredLine = null;
        Integer declared = null;
        for (String line : Files.readAllLines(dbRule, StandardCharsets.UTF_8)) {
            if (declaredLine != null && !line.startsWith(">")) {
                break; // 头部元信息块结束
            }
            Matcher c = REDLINE_COUNT.matcher(line);
            if (c.find() && REDLINE_RANGE.matcher(line).find()) {
                declared = Integer.valueOf(c.group(1));
                declaredLine = line;
                break;
            }
        }
        assertTrue(declared != null,
                "数据库访问规范头部缺「共 N 条 + 区间枚举」同行的红线声明：" + dbRuleRel);

        int enumerated = 0;
        StringBuilder parts = new StringBuilder();
        Matcher range = REDLINE_RANGE.matcher(declaredLine);
        while (range.find()) {
            int from = Integer.parseInt(range.group(2));
            int to = Integer.parseInt(range.group(4));
            int span = to - from + 1;
            enumerated += span;
            parts.append(parts.length() == 0 ? "" : " + ").append(range.group(1))
                    .append(range.group(2)).append('~').append(range.group(4))
                    .append('=').append(span);
        }
        assertTrue(enumerated > 0, "声明行未解析到红线区间枚举（格式已变？）：" + declaredLine);

        assertTrue(declared == enumerated,
                "数据库访问规范红线条数自相矛盾：声明「共 " + declared + " 条」，"
                        + "但枚举相加 = " + enumerated + "（" + parts + "）");

        // 索引侧若也写了条数，必须同值
        Integer indexDeclared = firstInt(REDLINE_COUNT,
                Files.readString(root.resolve(INDEX_REL), StandardCharsets.UTF_8));
        if (indexDeclared != null) {
            assertTrue(indexDeclared == enumerated,
                    "索引声明的红线数 " + indexDeclared + " ≠ 正文枚举相加 " + enumerated);
        }
    }

    @Test
    @DisplayName("索引与触发文件引用的规则路径全部存在")
    void referencedRulePathsExist() throws IOException {
        Path root = findRepoRoot();
        List<String> missing = new ArrayList<>();

        for (String rel : List.of(INDEX_REL, BACKEND_TRIGGER_REL, DB_DOC_TRIGGER_REL)) {
            Path file = root.resolve(rel);
            assertTrue(Files.isRegularFile(file), "缺触发/索引文件：" + rel);
            String content = Files.readString(file, StandardCharsets.UTF_8);
            Matcher ref = Pattern.compile("`(\\.trae/rules/[^`]+\\.md)`").matcher(content);
            while (ref.find()) {
                String target = ref.group(1);
                if (!Files.isRegularFile(root.resolve(target))) {
                    missing.add(rel + " → " + target);
                }
            }
        }

        assertTrue(missing.isEmpty(),
                "触发/索引引用了不存在的规则文件（重命名或搬迁后未同批改引用）：\n"
                        + String.join("\n", missing));
    }

    @Test
    @DisplayName("规范总行数只降不升（ratchet）")
    void rulesCorpusSizeIsRatchetBounded() throws IOException {
        Path root = findRepoRoot();
        Path rulesDir = root.resolve(".trae/rules");
        assertTrue(Files.isDirectory(rulesDir), "缺规范目录：.trae/rules");

        long totalLines = 0;
        List<String> perFile = new ArrayList<>();
        try (var stream = Files.list(rulesDir)) {
            for (Path p : stream.filter(f -> f.getFileName().toString().endsWith(".md")).toList()) {
                long n = Files.readAllLines(p, StandardCharsets.UTF_8).size();
                totalLines += n;
                perFile.add(p.getFileName() + "=" + n);
            }
        }

        assertTrue(totalLines <= RULES_TOTAL_LINE_CEILING,
                "规范总行数 " + totalLines + " 超过上限 " + RULES_TOTAL_LINE_CEILING
                        + "（ratchet 只降不升；新增内容前先精简，把变更史迁 docs/40-实现/）\n"
                        + String.join(" ", perFile));
    }

    @Test
    @DisplayName("规范与触发文件不得复述已单源化的 DDL 版本钉值")
    void rulesDoNotRestateSingleSourcedDdlPins() throws IOException {
        Path root = findRepoRoot();
        List<String> offenders = new ArrayList<>();

        // DDL 版本钉值的唯一权威 = MigrationVersionUniquenessTest 常量。
        // 规范/触发文件里出现「常量名 = 数字」或「NEXT 数字」这类复述即红——
        // 复述必然随版本推进而漂移（历史实测：铁律写 NEXT 350 时真值已是 361）。
        Pattern pinRestatement = Pattern.compile(
                "(SCRIPT_COUNT_FLOOR|MAX_BASELINE_VERSION|NEXT_FREE_VERSION)\\s*[=:：]\\s*\\d+"
                        + "|(?:NEXT|MAX)\\s*[=:：]?\\s*3\\d{2}\\b");

        List<String> targets = new ArrayList<>(List.of(
                ".qoder/rules/00-规范索引.md",
                ".qoder/rules/后端规范触发.md",
                ".qoder/rules/数据库与文档规范触发.md",
                "AGENTS.md"));
        try (var stream = Files.list(root.resolve(".trae/rules"))) {
            for (Path p : stream.filter(f -> f.getFileName().toString().endsWith(".md")).toList()) {
                targets.add(".trae/rules/" + p.getFileName());
            }
        }

        for (String rel : targets) {
            Path f = root.resolve(rel);
            if (!Files.isRegularFile(f)) {
                continue;
            }
            int lineNo = 0;
            for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                lineNo++;
                if (pinRestatement.matcher(line).find()) {
                    offenders.add(rel + ":" + lineNo + " → " + line.trim());
                }
            }
        }

        assertTrue(offenders.isEmpty(),
                "以下位置复述了已单源化的 DDL 版本钉值（改引用常量名或脚本，勿写具体数字）：\n"
                        + String.join("\n", offenders));
    }

    private static String headerOf(Path ruleFile) throws IOException {
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (String line : Files.readAllLines(ruleFile, StandardCharsets.UTF_8)) {
            sb.append(line).append('\n');
            if (++n >= 15) {
                break;
            }
        }
        return sb.toString();
    }

    private static String firstVersion(String text) {
        if (text == null) {
            return null;
        }
        Matcher m = VERSION_TOKEN.matcher(text);
        return m.find() ? m.group(1) : null;
    }

    private static Integer firstInt(Pattern p, String text) {
        Matcher m = p.matcher(text);
        return m.find() ? Integer.valueOf(m.group(1)) : null;
    }

    static Path findRepoRoot() {
        Path cursor = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        Path start = cursor;
        while (cursor != null) {
            if (Files.exists(cursor.resolve(".git"))) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        cursor = start;
        while (cursor != null) {
            if (Files.exists(cursor.resolve("pom.xml"))) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("无法从 user.dir=" + start + " 定位仓库根（.git/pom.xml 均缺失）");
    }
}
