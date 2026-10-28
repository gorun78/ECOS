package com.chinacreator.gzcm.runtime.llm.gateway;

import com.chinacreator.gzcm.runtime.llm.config.LLMGatewayProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #146x 附录 A 第 28 条供数腿验收：{@link LLMGateway#modelCatalog()} 是模型目录的
 * 只读展开单源（与 {@link LLMGateway#modelContextLimit(String)} 同表同口径）。
 *
 * <p>存在的理由：契约 #17 要"整张目录"，而既有读面只有"按键查"。本方法把同一张
 * {@code llm.models.catalog} 按登记序展开，<b>不</b>引入第二套目录、<b>不</b>补任何
 * 能力/成本/版本列（那些列在 {@link LLMGatewayProperties.Model} 上不存在，由消费方
 * 以 {@code null + *Applicable=false} 如实自证）。空列表 = 读数不可得，消费方必须显式
 * 501，禁折叠成 200＋空 items（X-22）。</p>
 */
@DisplayName("#146x LLMGateway#modelCatalog：目录展开单源、序与可用性如实")
class ModelCatalogReadTest {

    @Test
    @DisplayName("出厂目录按登记序展开：2 行、declared=true、上限为目录真值")
    void factoryCatalogExpandsInDeclaredOrder() {
        LLMGatewayImpl gateway = new LLMGatewayImpl();
        setProperties(gateway, new LLMGatewayProperties());

        List<LLMGateway.ModelLimit> rows = gateway.modelCatalog();

        assertEquals(2, rows.size(), "出厂目录 = LLMGatewayProperties.Models.defaultCatalog() 的行数");
        assertEquals("deepseek-chat", rows.get(0).model(), "必须保持 LinkedHashMap 登记序，禁排序改写目录语义");
        assertEquals("deepseek-reasoner", rows.get(1).model());
        for (LLMGateway.ModelLimit row : rows) {
            assertTrue(row.declared(), "出厂行是目录显式登记，不是 default-context-limit 兜底");
            assertTrue(row.available());
            assertEquals(64_000, row.contextLimit());
        }
    }

    @Test
    @DisplayName("展开面与按键查同值：同一模型两腿读数不得分叉")
    void catalogAgreesWithPerKeyLookup() {
        LLMGatewayImpl gateway = new LLMGatewayImpl();
        setProperties(gateway, new LLMGatewayProperties());

        LLMGateway.ModelLimit byKey = gateway.modelContextLimit("deepseek-chat");
        LLMGateway.ModelLimit byScan = gateway.modelCatalog().stream()
                .filter(r -> "deepseek-chat".equals(r.model())).findFirst().orElseThrow();

        assertEquals(byKey.contextLimit(), byScan.contextLimit());
        assertEquals(byKey.declared(), byScan.declared());
        assertEquals(byKey.available(), byScan.available());
    }

    @Test
    @DisplayName("属性未装配 → 空列表（消费方据此显式 501，禁兜底常量）")
    void missingPropertiesYieldsEmptyList() {
        assertTrue(new LLMGatewayImpl().modelCatalog().isEmpty(),
                "LLMGatewayProperties 未装配时不得凭空造目录");
    }

    @Test
    @DisplayName("运维把目录覆盖成空 → 空列表；上限 <=0 的登记行仍出行但 available=false")
    void operatorOverridesAreEchoedHonestly() {
        LLMGatewayProperties props = new LLMGatewayProperties();

        props.getModels().setCatalog(new LinkedHashMap<>());
        LLMGatewayImpl gateway = new LLMGatewayImpl();
        setProperties(gateway, props);
        assertTrue(gateway.modelCatalog().isEmpty(), "空目录不得被出厂值回填");

        Map<String, LLMGatewayProperties.Model> catalog = new LinkedHashMap<>();
        catalog.put("declared-but-unmeasured", model(0));
        catalog.put("measured", model(32_000));
        props.getModels().setCatalog(catalog);

        List<LLMGateway.ModelLimit> rows = gateway.modelCatalog();
        assertEquals(2, rows.size(), "登记行如实计数，禁因读数不可用而丢行");
        assertEquals("declared-but-unmeasured", rows.get(0).model());
        assertTrue(rows.get(0).declared(), "确实登记过 ⇒ declared=true");
        assertFalse(rows.get(0).available(), "上限 <=0 即不可用，消费方禁把它当真实窗口");
        assertTrue(rows.get(1).available());
        assertEquals(32_000, rows.get(1).contextLimit());
    }

    private static LLMGatewayProperties.Model model(int contextLimit) {
        LLMGatewayProperties.Model m = new LLMGatewayProperties.Model();
        m.setContextLimit(contextLimit);
        return m;
    }

    private static void setProperties(LLMGatewayImpl gateway, LLMGatewayProperties properties) {
        try {
            Field field = LLMGatewayImpl.class.getDeclaredField("llmGatewayProperties");
            field.setAccessible(true);
            field.set(gateway, properties);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("llmGatewayProperties 字段注入失败（字段改名须同步本夹具）: "
                    + e.getMessage(), e);
        }
    }
}
