package com.chinacreator.gzcm.workspace.config;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Method;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.xml.MappingJackson2XmlHttpMessageConverter;
import org.springframework.web.client.RestTemplate;

/**
 * D150 回归门：{@code WorkspaceRestClientConfig} 产出的 RestTemplate 不得携带
 * {@link MappingJackson2XmlHttpMessageConverter}。
 *
 * <p>实证背景（2026-11-16 live）：知识工作台「引擎配置」写面恒报 {@code updated:0}，
 * 真因是 RestTemplate 默认注册 XML 转换器 ⇒ 出站 Accept 拼进 {@code application/xml}
 * ⇒ 网关内容协商回 {@code application/xml} ⇒ XML 转换器抢答、用 XmlMapper 反序列化
 * 无类型 Map，把信封数字叶子（{@code code}/{@code updated}）全部字符串化 ⇒
 * {@code extractUpdated} 的 {@code instanceof Number} 恒 false。本测试钉住
 * 「RestTemplate 不含 XML 转换器」这一修复不变量，防回潮。
 */
class WorkspaceRestClientConfigXmlConverterTest {

    @Test
    @DisplayName("集中工厂产出的 RestTemplate 不含 XML 转换器（D150 根因不变量）")
    void restTemplateMustNotCarryXmlConverter() throws Exception {
        Method m = WorkspaceRestClientConfig.class.getDeclaredMethod(
                "restTemplate", String.class, int.class);
        m.setAccessible(true);
        RestTemplate template = (RestTemplate) m.invoke(null, "http://localhost:1", 1500);

        boolean hasXml = template.getMessageConverters().stream()
                .anyMatch(c -> c instanceof MappingJackson2XmlHttpMessageConverter);
        assertFalse(hasXml,
                "D150：workspace RestTemplate 仍注册 MappingJackson2XmlHttpMessageConverter，"
                        + "会导致 Accept 含 application/xml、网关回 XML、数字信封被字符串化，"
                        + "KnowledgeConfigAggregator 写面上报恒 0");
    }
}
