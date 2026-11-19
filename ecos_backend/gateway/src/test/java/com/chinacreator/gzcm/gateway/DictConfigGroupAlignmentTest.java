package com.chinacreator.gzcm.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 差距分析 D-1 门禁 — 字典 subsystem 与配置 config_group 同名对齐一致性。
 *
 * <p>背景（`docs/30-设计/ECOS系统配置现状与差距分析-2026-11-19.md` D-1）：
 * 铁律 §3.6 要求 {@code sys_dict.subsystem} 与所属引擎 {@code config_group} 同名对齐，
 * 空值 = 全局字典；但两侧真实值域一直靠人工维护、无门禁校验一致性。</p>
 *
 * <p>裁决（本批）：维持别名层终态、不双列化 {@code sys_config}；改为在门禁侧校验
 * <b>「sys_dict 里出现的 subsystem 值必须存在于 sys_config 的 config_group 值域中」</b>。
 * 空 subsystem（全局字典）放行。</p>
 *
 * <p>扫描面（铁律 v2.4 §3.1：活单源 ∪ 归档，漏任一侧即空集假绿）：
 * {@code docs/50-db_script/02-flyway-migration/gateway/} 活单源 +
 * {@code docs/90-归档/50-db_script/02-flyway-migration-legacy/gateway/} 历史归档。</p>
 *
 * <p>验收：{@code mvn -pl gateway test -Dtest='DictConfigGroupAlignmentTest'}。</p>
 */
class DictConfigGroupAlignmentTest {

    /** sys_config 的 config_group 合法值域（种子脚本实测 + 无表值缺省）。 */
    private static final Set<String> CONFIG_GROUPS = new TreeSet<>(Set.of(
            "global", "general", "security",
            "g1-data-foundation", "g2-biz-semantics", "g3-operations", "g4-agent",
            "data-engine", "ontology-engine", "ai-engine", "cognitive-engine",
            "cognitive", "knowledge", "fc.backtest", "cache", "llm", "event", "storage",
            "wagent.action_publish_target.adapter", "wagent.automation.max_level",
            "wagent.budget", "wagent.degrade.whitelist", "wagent.readiness.threshold",
            "wagent.toolset"
    ));

    /**
     * 合法例外：字典子系统名与配置组名语义等价但不逐字相同的登记项。
     * 仅在两侧确属同一业务域、且有既有契约依赖时登记（禁为过门禁批量放水）。
     */
    private static final Map<String, String> SUBSYSTEM_ALIASES = Map.of(
            // 字典侧子系统短名 → 配置侧组名
            "data", "data-engine",
            "ontology", "ontology-engine",
            "ai", "ai-engine",
            "kb", "knowledge"
    );

    /** INSERT INTO sys_dict (col1, col2, ...) VALUES (...)：捕获列名序，匹配 subsystem 列位置后取值。 */
    private static final Pattern INSERT_SYS_DICT = Pattern.compile(
            "INSERT\\s+INTO\\s+(?:public\\.)?sys_dict\\s*\\(([^)]*)\\)\\s*VALUES",
            Pattern.CASE_INSENSITIVE);

    @Test
    @DisplayName("sys_dict.subsystem 值必须命中 sys_config.config_group 值域（空=全局，放行）")
    void dictSubsystemAlignsWithConfigGroup() throws IOException {
        Set<String> offenders = new TreeSet<>();
        for (Path root : resolveScanRoots()) {
            if (!Files.exists(root)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(root)) {
                walk.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().endsWith(".sql"))
                        .forEach(p -> scanFile(p, offenders));
            }
        }
        assertTrue(offenders.isEmpty(),
                "D-1 违规：sys_dict.subsystem 引用了 sys_config 不存在的 config_group（须同名对齐或登记别名）:\n  "
                        + String.join("\n  ", offenders));
    }

    private static void scanFile(Path file, Set<String> offenders) {
        String content = read(file);
        Matcher m = INSERT_SYS_DICT.matcher(content);
        while (m.find()) {
            String[] cols = m.group(1).split(",");
            int subsystemIdx = -1;
            for (int i = 0; i < cols.length; i++) {
                if ("subsystem".equalsIgnoreCase(cols[i].trim())) {
                    subsystemIdx = i;
                    break;
                }
            }
            if (subsystemIdx < 0) {
                continue;   // 该 INSERT 不含 subsystem 列 ⇒ 全局字典，放行
            }
            // 抓取 VALUES 后的第一组括号内容
            int valuesStart = content.indexOf('(', m.end());
            if (valuesStart < 0) {
                continue;
            }
            int valuesEnd = findMatchingParen(content, valuesStart);
            if (valuesEnd < 0) {
                continue;
            }
            String row = content.substring(valuesStart + 1, valuesEnd);
            String[] vals = row.split(",");
            if (subsystemIdx >= vals.length) {
                continue;
            }
            String subsystem = unquote(vals[subsystemIdx]);
            if (subsystem.isEmpty() || "null".equalsIgnoreCase(subsystem)) {
                continue;   // 空 = 全局字典
            }
            String normalized = subsystem.toLowerCase();
            if (CONFIG_GROUPS.contains(normalized) || SUBSYSTEM_ALIASES.containsKey(normalized)) {
                continue;
            }
            offenders.add(file.getFileName() + " → subsystem='" + subsystem + "'");
        }
    }

    private static int findMatchingParen(String s, int open) {
        int depth = 0;
        for (int i = open; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\'') {
                i = skipString(s, i);
            } else if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            } else if (c == ';') {
                return -1;   // 括号未闭合就到语句尾 ⇒ 放弃本行
            }
        }
        return -1;
    }

    private static int skipString(String s, int quoteStart) {
        for (int i = quoteStart + 1; i < s.length(); i++) {
            if (s.charAt(i) == '\'') {
                if (i + 1 < s.length() && s.charAt(i + 1) == '\'') {
                    i++;   // 转义单引号
                } else {
                    return i;
                }
            }
        }
        return s.length() - 1;
    }

    private static String unquote(String v) {
        String t = v.trim();
        if (t.startsWith("'") && t.endsWith("'") && t.length() >= 2) {
            return t.substring(1, t.length() - 1).replace("''", "'");
        }
        return t;
    }

    private static Set<Path> resolveScanRoots() {
        Path repoRoot = resolveRepoRoot();
        return Set.of(
                repoRoot.resolve("docs/50-db_script/02-flyway-migration/gateway"),
                repoRoot.resolve("docs/90-归档/50-db_script/02-flyway-migration-legacy/gateway")
        );
    }

    private static Path resolveRepoRoot() {
        Path cur = Paths.get("").toAbsolutePath();
        while (cur != null) {
            if (Files.exists(cur.resolve("ecos_backend")) && Files.exists(cur.resolve("docs"))) {
                return cur;
            }
            cur = cur.getParent();
        }
        return Paths.get("..").toAbsolutePath().normalize();
    }

    private static String read(Path p) {
        try {
            return Files.readString(p);
        } catch (IOException e) {
            return "";
        }
    }
}
