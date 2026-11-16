package com.chinacreator.gzcm.sysman.iam.dao.impl;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 缺陷 D88 门禁：建号 SQL 必须落 {@code real_name}。
 *
 * <p>列表与详情都从 {@code td_user.real_name} 读姓名（{@code queryUsers} 按名引用），
 * 而建号 SQL 漏了该列时：接口收下 {@code realName} ⇒ 库里永远是 NULL ⇒
 * 用户看到"填了姓名但列表空空"，且没有任何报错。</p>
 */
class UserInsertCoversDisplayedColumnsTest {

    private static String sqlBody(String statementId) throws Exception {
        String xml = Files.readString(
                Path.of("src/main/java/com/chinacreator/gzcm/sysman/iam/dao/impl/User-sql.xml"),
                StandardCharsets.UTF_8);
        int start = xml.indexOf("id=\"" + statementId + "\"");
        assertTrue(start > 0, "未找到语句 " + statementId);
        int end = xml.indexOf("</", start);
        return xml.substring(start, end);
    }

    @Test
    @DisplayName("insertUser 必须写入 real_name（否则建号填的姓名被静默丢弃）")
    void insertUserWritesRealName() throws Exception {
        String body = sqlBody("insertUser");
        assertTrue(body.contains("real_name"), "insertUser 缺 real_name 列: " + body);
        assertTrue(body.contains("#[realName]"), "insertUser 缺 realName 绑定: " + body);
    }

    @Test
    @DisplayName("探针自检：判定对缺列写法确实报红")
    void gateDetectsMissingColumn() throws Exception {
        String legacy = "INSERT INTO TD_USER (user_id, username, password, email) VALUES (#[userId])";
        assertTrue(!legacy.contains("real_name"), "门禁判据须能识别缺列写法");
    }
}
