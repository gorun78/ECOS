package com.chinacreator.gzcm.runtime.core.database.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SQL 配置文件方言分派门（#147AF/D）。
 *
 * <p><b>缺陷实况</b>：加载器原先只看扩展名——`.xml` 一律走 iBATIS 解析。而本仓同时存在
 * `<properties><property name=…>` 形态的 `*-sql.xml`（如 sysman 的 `UserRole-sql.xml`），
 * 被 iBATIS 解析器读成空表 ⇒ 运行期报 `SQL not found in config: …#listByUserId`。
 * 现按**根元素**分派，本门钉住两种形态的判定。</p>
 *
 * @author ECOS Runtime #147AF
 */
class SqlConfigFormatDispatchTest {

    private static ByteArrayInputStream xml(String body) {
        return new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("根元素 <properties> ⇒ 走属性方言（修本缺陷的关键分支）")
    void propertiesRootIsDetected() {
        assertTrue(SystemDatabaseAccessImpl.isPropertiesRootXml(xml(
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                        + "<properties><property name=\"listByUserId\"><![CDATA[SELECT 1]]></property></properties>")));
    }

    @Test
    @DisplayName("iBATIS 形态（<sqlMap>/<mapper>）仍走 iBATIS 解析")
    void ibatisRootsAreNotMistaken() {
        assertFalse(SystemDatabaseAccessImpl.isPropertiesRootXml(xml(
                "<sqlMap namespace=\"x\"><select id=\"a\">SELECT 1</select></sqlMap>")));
        assertFalse(SystemDatabaseAccessImpl.isPropertiesRootXml(xml(
                "<mapper namespace=\"x\"><select id=\"a\">SELECT 1</select></mapper>")));
    }

    @Test
    @DisplayName("非法 XML 不抛（按 iBATIS 处理，后续解析给出明确错误）")
    void malformedXmlFallsBack() {
        assertFalse(SystemDatabaseAccessImpl.isPropertiesRootXml(xml("not xml")));
    }
}
