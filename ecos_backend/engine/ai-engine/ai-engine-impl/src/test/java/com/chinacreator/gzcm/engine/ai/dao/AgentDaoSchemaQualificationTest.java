package com.chinacreator.gzcm.engine.ai.dao;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D70 门禁：{@code ecos_agent} 必须带 schema 限定。
 *
 * <p>存在理由：{@code ecos_agent} 在库里<b>同名存在两份</b>——
 * {@code ecos_control.ecos_agent}（V4 建表时未带 schema 前缀，落进 search_path 首位的
 * 主控制 schema，活体 4 行数据在它这里）与 {@code ecos_ai.ecos_agent}
 * （06_ecos_ai.sql 标注「旧版」，0 行，多一列 {@code tenant_id}）。
 * 裸表名的解析结果完全取决于连接的 {@code search_path} 顺序：现网首位是
 * {@code ecos_control} 所以"碰巧"读对；一旦次序变化，同一份代码静默切到另一张空表
 * ——列表变空、写入进错表、无任何报错。数据库访问规范 §七 通用规则第 1 条要求
 * 「表名必须带 schema 限定，未限定的裸表名 = FAIL」。</p>
 *
 * <p><b>本门禁只钉这一个双表同名的具体面</b>，不扫全仓 224 处存量裸表名（那是系统性
 * 技术债，需单独排期）；故只对已知会双解析的表 {@code ecos_agent} 断言。</p>
 */
class AgentDaoSchemaQualificationTest {

    /** 会双解析的表名（同名存在多份 schema）。新增成员须同步复核两个 SQL 宿主。 */
    private static final List<String> AMBIGUOUS_TABLES = List.of("ecos_agent");

    /** 承载该表 SQL 的源文件（相对 ai-engine-impl/src/main/java）。 */
    private static final List<String> SQL_HOSTS = List.of(
            "com/chinacreator/gzcm/engine/ai/dao/AgentDao.java",
            "com/chinacreator/gzcm/engine/ai/controller/DiagnosticAgentController.java");

    /** 裸表名形态：FROM/INTO/UPDATE 之后紧跟表名、且表名前不带 {@code schema.}。 */
    private static final Pattern BARE_REF = Pattern.compile(
            "(?i)\\b(?:FROM|INTO|UPDATE)\\s+(?!\\w+\\.)ecos_agent\\b");

    @Test
    @DisplayName("ecos_agent 的所有 DML/DQL 位点都带 ecos_control. 限定，无裸表名")
    void allEcosAgentReferencesAreSchemaQualified() throws IOException {
        List<String> violations = new ArrayList<>();
        for (String host : SQL_HOSTS) {
            Path src = Path.of("src/main/java", host);
            assertTrue(Files.exists(src), "源文件不存在（测试工作目录应为 ai-engine-impl）: " + src.toAbsolutePath());
            String text = Files.readString(src, StandardCharsets.UTF_8);
            Matcher m = BARE_REF.matcher(text);
            while (m.find()) {
                int line = lineOf(text, m.start());
                violations.add(src.getFileName() + ":" + line + " → " + m.group().replaceAll("\\s+", " "));
            }
        }
        assertTrue(violations.isEmpty(),
                "发现 ecos_agent 裸表名（schema 未限定，search_path 次序变化即静默切表）：" + violations);
    }

    @Test
    @DisplayName("反向探针：门禁正则会命中裸写法、不命中限定写法（防正则写错恒绿）")
    void patternDetectsBareFormAndIgnoresQualifiedForm() {
        for (String bare : List.of(
                "SELECT * FROM ecos_agent WHERE id = ?",
                "INSERT INTO ecos_agent (id) VALUES (?)",
                "UPDATE ecos_agent SET name = ?",
                "DELETE FROM ecos_agent WHERE id = ?")) {
            assertTrue(BARE_REF.matcher(bare).find(), "应命中裸写法: " + bare);
        }
        for (String qualified : List.of(
                "SELECT * FROM ecos_control.ecos_agent WHERE id = ?",
                "INSERT INTO ecos_control.ecos_agent (id) VALUES (?)",
                "UPDATE ecos_control.ecos_agent SET name = ?",
                "DELETE FROM ecos_control.ecos_agent WHERE id = ?",
                "SELECT * FROM ecos_ai.ecos_agent")) {
            assertTrue(!BARE_REF.matcher(qualified).find(), "不应命中限定写法: " + qualified);
        }
    }

    private static int lineOf(String text, int offset) {
        int line = 1;
        for (int i = 0; i < offset && i < text.length(); i++) {
            if (text.charAt(i) == '\n') line++;
        }
        return line;
    }
}
