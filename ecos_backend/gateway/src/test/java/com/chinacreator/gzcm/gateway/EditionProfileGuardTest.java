package com.chinacreator.gzcm.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 详细设计-00 F00-13 — 三档发布装配门禁（standard/enterprise/ultimate）。
 *
 * <p>断言"依赖裁剪 + {@code spring.profiles.active} 生效 + 能力开关"三处一致：
 * <ol>
 *   <li>gateway 三档 yml 的能力开关对齐矩阵：
 *       <ul>
 *         <li>standard：{@code ecos.graph.provider=pg}、{@code features.knowledge-graph=false}（无 Neo4j/Doris bean）</li>
 *         <li>enterprise：{@code ecos.graph.provider=neo4j}、{@code knowledge-graph=true}、{@code terra.storage.provider=minio}</li>
 *         <li>ultimate：{@code ecos.graph.provider=neo4j} + {@code terra.analytics.provider=doris}（列存）+ {@code knowledge-graph=true}</li>
 *       </ul></li>
 *   <li>{@code Neo4jConfig}（runtime-access 图库 Driver 唯一装配源）必须
 *       {@code @ConditionalOnProperty(name="ecos.graph.provider", havingValue="neo4j")}
 *       + {@code @ConditionalOnClass(Driver)}——即 standard 档 provider=pg 时
 *       <b>不创建 Neo4j bean</b>（本测试以注解常量静态化断言该装配语义，
 *       避免启动真实 Spring context + Neo4j）。</li>
 *   <li>dccheng 的 Neo4j 不能当无条件硬装配：{@code terra.neo4j.enabled} 默认 {@code false}，
 *       {@code uri} 走 {@code ${NEO4J_URI:...}} 占位（ST02: standard 禁实启 Neo4j）。</li>
 * </ol>
 *
 * <p>验收：{@code mvn -pl gateway -am test -Dtest='EditionProfileGuardTest'}</p>
 */
class EditionProfileGuardTest {

    @Test
    @DisplayName("1. standard 档：graph/analytics 走 PG，知识图谱能力关")
    void standardProfileIsPgOnly() {
        Map<String, Object> y = loadGateway("application-standard.yml");
        assertEquals("standard", readPath(y, "ecos.edition"));
        assertEquals("pg", readPath(y, "ecos.graph.provider"));
        assertEquals("pg", readPath(y, "ecos.analytics.provider"));
        assertEquals("pg", readPath(y, "ecos.storage.provider"));
        assertEquals(Boolean.FALSE, readPath(y, "ecos.features.knowledge-graph"));
        assertEquals(Boolean.FALSE, readPath(y, "ecos.features.multi-tenant"));
    }

    @Test
    @DisplayName("2. enterprise 档：graph=neo4j + 知识图谱开 + MinIO 存储")
    void enterpriseProfileUsesNeo4j() {
        Map<String, Object> y = loadGateway("application-enterprise.yml");
        assertEquals("enterprise", readPath(y, "ecos.edition"));
        assertEquals("neo4j", readPath(y, "ecos.graph.provider"));
        assertEquals(Boolean.TRUE, readPath(y, "ecos.features.knowledge-graph"));
        assertEquals("minio", readPath(y, "ecos.storage.provider"));
    }

    @Test
    @DisplayName("3. ultimate 档：graph=neo4j + 列存 doris + 知识图谱开")
    void ultimateProfileUsesNeo4jAndDoris() {
        Map<String, Object> y = loadGateway("application-ultimate.yml");
        assertEquals("ultimate", readPath(y, "ecos.edition"));
        assertEquals("neo4j", readPath(y, "ecos.graph.provider"));
        assertEquals("doris", readPath(y, "ecos.analytics.provider"));
        assertEquals(Boolean.TRUE, readPath(y, "ecos.features.knowledge-graph"));
    }

    @Test
    @DisplayName("4. Neo4jConfig 条件装配：仅 provider=neo4j 且 classpath 有 Driver 时激活")
    void neo4jConfigIsProfileConditional() throws ClassNotFoundException {
        Class<?> cfg = Class.forName("com.chinacreator.gzcm.runtime.access.config.Neo4jConfig");
        ConditionalOnProperty onProp = cfg.getAnnotation(ConditionalOnProperty.class);
        assertNotNull(onProp, "Neo4jConfig 必须 @ConditionalOnProperty（否则 standard 也建 neo4j bean）");
        assertTrue(onProp.name().length >= 1
                        && onProp.name()[0].contains("ecos.graph.provider"),
                "应锚定 ecos.graph.provider，实际: " + String.join(",", onProp.name()));
        assertEquals("neo4j", onProp.havingValue(), "仅在 provider=neo4j 时装配 Neo4j");

        ConditionalOnClass onClass = cfg.getAnnotation(ConditionalOnClass.class);
        assertNotNull(onClass, "Neo4jConfig 必须 @ConditionalOnClass(Driver) 防标准档缺 driver 崩");
        boolean hasDriver = false;
        for (Class<?> c : onClass.value()) {
            if (org.neo4j.driver.Driver.class.getName().equals(c.getName())) hasDriver = true;
        }
        assertTrue(hasDriver, "Neo4jConfig 必须 @ConditionalOnClass(org.neo4j.driver.Driver)");
    }

    @Test
    @DisplayName("5. dccheng Neo4j 默认关闭（ECOS_NEO4J_ENABLED 缺省 false）+ uri 占位")
    void dcchengNeo4jDefaultOff() throws IOException {
        Path dccheng = Path.of(resolveBackendRoot(),
                "services/dccheng/src/main/resources/application.yml");
        assertTrue(Files.exists(dccheng), "未找到 dccheng application.yml");
        String content = Files.readString(dccheng);
        // eco.neo4j.enabled 默认 false（standard 禁实启 Neo4j，ST02）
        assertTrue(content.contains("enabled: ${ECOS_NEO4J_ENABLED:false}"),
                "dccheng Neo4j 必须默认关闭（${ECOS_NEO4J_ENABLED:false}）");
        // uri 走占位符
        assertTrue(content.contains("${NEO4J_URI"),
                "dccheng Neo4j uri 应走 ${NEO4J_URI:...} 占位符");
    }

    // ── yml 读取工具 ──────────────────────────────────────────────────

    private static Map<String, Object> loadGateway(String fileName) {
        try (InputStream in = EditionProfileGuardTest.class.getClassLoader()
                .getResourceAsStream(fileName)) {
            assertNotNull(in, "gateway classpath 缺少 " + fileName);
            return new org.yaml.snakeyaml.Yaml().load(in);
        } catch (IOException e) {
            throw new IllegalStateException("读 yml 失败: " + fileName, e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Object readPath(Map<String, Object> root, String dotted) {
        String[] parts = dotted.split("\\.");
        Object cur = root;
        for (String p : parts) {
            if (cur instanceof Map) {
                cur = ((Map<String, Object>) cur).get(p);
            } else {
                return null;
            }
        }
        return cur;
    }

    private static String resolveBackendRoot() {
        Path cur = Paths.get("").toAbsolutePath();
        while (cur != null) {
            if (Files.exists(cur.resolve("pom.xml")) && Files.exists(cur.resolve("gateway"))) {
                return cur.toString();
            }
            cur = cur.getParent();
        }
        return Paths.get("..").toAbsolutePath().normalize().toString();
    }
}
