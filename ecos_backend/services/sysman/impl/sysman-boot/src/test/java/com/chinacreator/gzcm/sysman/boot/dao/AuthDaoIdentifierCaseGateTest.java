package com.chinacreator.gzcm.sysman.boot.dao;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 缺陷 D87 门禁：登录链路里的 SQL 不得使用双引号全大写标识符。
 *
 * <p>PostgreSQL 的双引号标识符<b>大小写敏感</b>，而 DDL 单源建的是不加引号的小写列
 * （{@code tenant_id}/{@code username}）。旧写法 {@code SELECT "TENANT_ID" FROM TD_USER WHERE "USERNAME" = ?}
 * 在真实库上必报 {@code column "TENANT_ID" does not exist}，异常又被登录端 catch 成"无租户"，
 * 于是所有用户都被兜到默认租户 {@code tenant-a}，租户隔离形同虚设且没有任何报错。</p>
 */
class AuthDaoIdentifierCaseGateTest {

    /** 源码里的双引号全大写标识符（列名形态，≥3 字符，避免误判短缩写）。 */
    private static final Pattern QUOTED_UPPER = Pattern.compile("\"([A-Z][A-Z0-9]*_[A-Z0-9_]+)\"");

    @Test
    @DisplayName("AuthDao 的 SQL 字面量不得再出现双引号全大写列名")
    void noQuotedUppercaseIdentifiers() throws Exception {
        String raw = Files.readString(
                Path.of("src/main/java/com/chinacreator/gzcm/sysman/boot/dao/AuthDao.java"),
                StandardCharsets.UTF_8);
        // 只判定 Java 字符串字面量（SQL 本体）：注释里的反例与文档不受影响
        Matcher literal = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(raw);
        StringBuilder offenders = new StringBuilder();
        while (literal.find()) {
            Matcher m = QUOTED_UPPER.matcher(literal.group(1).replace("\\\"", "\""));
            while (m.find()) {
                offenders.append(m.group(1)).append(' ');
            }
        }
        assertFalse(offenders.length() > 0,
                "登录链路 SQL 出现双引号全大写标识符（真实 PG 上必报列不存在）: " + offenders);
    }

    @Test
    @DisplayName("探针自检：正则确实能命中缺陷写法（否则本门永绿）")
    void gateRegexMatchesLegacyForm() {
        assertTrue(QUOTED_UPPER.matcher(
                "SELECT \"TENANT_ID\" FROM TD_USER WHERE \"USERNAME\" = ?").find(),
                "门禁正则必须能命中旧写法");
        assertFalse(QUOTED_UPPER.matcher(
                "SELECT tenant_id FROM TD_USER WHERE username = ?").find(),
                "门禁不得对正确写法误报");
    }
}
