package com.chinacreator.gzcm.gateway;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.chinacreator.gzcm.common.event.KafkaTopics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * C.5.3 / W12（详细设计-00）— Kafka topic 单一事实源三方对账。
 *
 * <p>三个面必须对齐，任一漂移即红灯：
 * <ol>
 *   <li>{@link KafkaTopics} 常量集（runtime/common-api，反射枚举全部静态 String 常量）；</li>
 *   <li>{@code gateway/src/main/resources/kafka/topics.txt}（每行一个 topic，# 注释排除）；</li>
 *   <li>{@code ecos-docker/docker-compose.yml} kafka-init entrypoint 里
 *       {@code --topic ecos.*} 字面量集（部署启动面）。</li>
 * </ol>
 *
 * <p>断言：常量集 == topics.txt 集（双向差集为空，测试失败信息打印差集明细）；
 * topics.txt ⊇ compose 初始化面（部署面不得超集于代码单源，防"部署存在但代码
 * 不认"的孤儿 topic）。
 *
 * <p>运行：{@code mvn -pl gateway -am test -Dtest=KafkaTopicParityTest}
 */
public class KafkaTopicParityTest {

    private static final Pattern COMPOSE_TOPIC = Pattern.compile("--topic\\s+(ecos[\\w.\\-]+)");

    private static Set<String> constantTopics;
    private static Set<String> manifestTopics;
    private static Set<String> composeTopics;

    @BeforeAll
    static void load() throws IOException {
        constantTopics = kafkaTopicsConstants();
        manifestTopics = readTopicsTxt();
        composeTopics = readComposeTopics();
    }

    @Test
    @DisplayName("KafkaTopics 常量集 == topics.txt（双向差集为空）")
    void constantsEqualManifest() {
        assertFalse(constantTopics.isEmpty(), "KafkaTopics 常量枚举为空（反射失败？）");
        assertFalse(manifestTopics.isEmpty(), "topics.txt 为空（gateway/src/main/resources/kafka/topics.txt）");

        Set<String> onlyInCode = diff(constantTopics, manifestTopics);
        Set<String> onlyInFile = diff(manifestTopics, constantTopics);
        assertEquals(Set.of(), onlyInCode,
                "仅存在于 KafkaTopics 常量、topics.txt 缺失：需补行到 topics.txt（C.5.3 单源）");
        assertEquals(Set.of(), onlyInFile,
                "仅存在于 topics.txt、KafkaTopics 无此常量:需补常量或删行（C.5.3 单源）");
    }

    @Test
    @DisplayName("topics.txt ⊇ docker-compose kafka-init 初始化面（无孤儿 topic）")
    void manifestSupersetOfCompose() {
        assertFalse(composeTopics.isEmpty(),
                "docker-compose kafka-init 未提取到任何 ecos.* topic（compose 结构变更？）");
        Set<String> orphanInCompose = diff(composeTopics, manifestTopics);
        assertEquals(Set.of(), orphanInCompose,
                "compose kafka-init 初始化了 topics.txt/代码均不存在的 topic（孤儿部署面）");
    }

    @Test
    @DisplayName("topics.txt 无重复行（单源纪律：一个 topic 一行）")
    void manifestHasNoDuplicates() throws IOException {
        List<String> lines = rawTopicLines();
        long distinct = manifestTopics.size();
        assertEquals(lines.size(), distinct, "topics.txt 存在重复 topic 行");
    }

    // ── 读取器 ────────────────────────────────────────────────────

    /** 反射枚举 KafkaTopics 全部 public static final String 常量值 */
    private static Set<String> kafkaTopicsConstants() {
        Set<String> out = new LinkedHashSet<>();
        for (Field f : KafkaTopics.class.getFields()) {
            if (Modifier.isStatic(f.getModifiers()) && f.getType() == String.class) {
                try {
                    Object v = f.get(null);
                    if (v instanceof String s && !s.isBlank()) {
                        out.add(s);
                    }
                } catch (IllegalAccessException ignore) {
                    // 常量异常读不到即测试失败（LinkedHashSet 少一个，等值断言兜底）
                }
            }
        }
        return out;
    }

    private static Set<String> readTopicsTxt() throws IOException {
        try (InputStream is = KafkaTopicParityTest.class.getResourceAsStream("/kafka/topics.txt")) {
            assertNotNull(is, "classpath 缺 kafka/topics.txt（C.5.3 单源文件未入库）");
            Set<String> out = new LinkedHashSet<>();
            try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(is,
                    java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty() && !line.startsWith("#")) {
                        out.add(line);
                    }
                }
            }
            return out;
        }
    }

    private static List<String> rawTopicLines() throws IOException {
        try (InputStream is = KafkaTopicParityTest.class.getResourceAsStream("/kafka/topics.txt");
             var reader = new java.io.BufferedReader(new java.io.InputStreamReader(is,
                     java.nio.charset.StandardCharsets.UTF_8))) {
            List<String> lines = new java.util.ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    lines.add(line);
                }
            }
            return lines;
        }
    }

    /** 仓库根定位：gateway 为 cwd，向上找含 ecos_backend 目录的层级（同 GatewayRouteManifestTest 口径） */
    private static Path repoRoot() {
        Path cur = Paths.get("").toAbsolutePath();
        while (cur != null && !Files.isDirectory(cur.resolve("ecos_backend"))) {
            cur = cur.getParent();
        }
        assertNotNull(cur, "cwd 未定位到 ECOS 仓库根");
        return cur;
    }

    private static Set<String> readComposeTopics() throws IOException {
        Path compose = repoRoot().resolve("ecos-docker").resolve("docker-compose.yml");
        assertNotNull(compose, "未定位到 ecos-docker/docker-compose.yml: " + compose);
        Set<String> out = new LinkedHashSet<>();
        String content = Files.readString(compose, java.nio.charset.StandardCharsets.UTF_8);
        Matcher m = COMPOSE_TOPIC.matcher(content);
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }

    private static Set<String> diff(Set<String> left, Set<String> right) {
        return left.stream().filter(t -> !right.contains(t)).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }
}
