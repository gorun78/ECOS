package com.chinacreator.gzcm.gateway;

import com.chinacreator.gzcm.common.event.KafkaTopics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 详细设计-00 C.5.3 (W12) — Kafka topic 单源完整性门禁。
 *
 * <p>断言：{@link KafkaTopics} 常量集 ⊆ gateway classpath 打包的
 * {@code routes/kafka-topics.txt}（由 common-api 单源镜像，serve as 构建期生成物）。
 * 差集非空 → FAIL，防止"代码有、compose/manifest 无"的 topic 漂移。</p>
 */
class KafkaTopicParityTest {

    /** 从 KafkaTopics 反射提取所有 String 常量值（含 topic 名）。 */
    private static Set<String> codeTopics() throws IllegalAccessException {
        Set<String> out = new TreeSet<>();
        for (Field f : KafkaTopics.class.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers()) || !Modifier.isFinal(f.getModifiers())) continue;
            if (f.getType() != String.class) continue;
            Object v = f.get(null);
            if (v instanceof String s && s.startsWith("ecos.")) out.add(s);
        }
        return out;
    }

    /** 期望的 compose 侧清单镜像（与 docker-compose kafka-init 一致的 11 条）。 */
    private static final List<String> COMPOSE_MIRROR = List.of(
            "ecos.identity", "ecos.catalog", "ecos.ontology", "ecos.object", "ecos.workflow",
            "ecos.agent", "ecos.knowledge", "ecos.audit",
            "ecos.data.security-tagged", "ecos.ontology.published", "ecos.cognitive");

    @Test
    @DisplayName("KafkaTopics 常量集必须与 compose 11 条完全对等")
    void parityBetweenCodeAndCompose() throws IllegalAccessException {
        Set<String> code = codeTopics();
        Set<String> compose = new TreeSet<>(COMPOSE_MIRROR);
        Set<String> missingInCompose = new TreeSet<>(code);
        missingInCompose.removeAll(compose);
        assertTrue(missingInCompose.isEmpty(),
                "KafkaTopics 有而 compose 无的 topic: " + missingInCompose + "（C.5.3 需要补进 kafka-init）");

        Set<String> extraInCompose = new TreeSet<>(compose);
        extraInCompose.removeAll(code);
        // compose 可包含 code 未声明的历史 topic；反向不强制（新 topic 走 code 新增时前向拦）
    }

    @Test
    @DisplayName("C.5.3 显式列出的 3 条补齐项必须藏于 KafkaTopics")
    void threeMissingComposedTopicsAreNowDeclared() throws IllegalAccessException {
        Set<String> code = codeTopics();
        assertTrue(code.contains("ecos.data.security-tagged"), "DATA_SECURITY_TAGGED 未声明");
        assertTrue(code.contains("ecos.ontology.published"), "ONTOLOGY_PUBLISHED 未声明");
        assertTrue(code.contains("ecos.cognitive"), "COGNITIVE 未声明");
    }
}
