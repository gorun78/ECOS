package com.chinacreator.gzcm.gateway;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * F08-06 / B8b —— 业务侧禁自建 Kafka producer/consumer（架构铁律 §2.5；事件单出口 = runtime-event {@code EventBusService}）。
 *
 * <p>与 {@link NoScheduledAnnotationArchTest} 同族源码扫描护栏。覆盖 engine / services /
 * workspace / runtime，排除 runtime-event（Kafka 事件总线底座自身合法持有 {@code KafkaTemplate} /
 * {@code @KafkaListener}）。</p>
 *
 * <p><b>基线冻结（2026-10-07 B8 派工，live grep 实测生产 main = 0 真实自建）</b>：
 * 引擎侧全部已改走 {@code EventBusService}（PMO-74 H2-T4 收口），KafkaConsum/Producer 均
 * 为 javadoc 说明语（{@code {@code @KafkaListener(...)}} / {@code KafkaTemplate} 裸词），
 * 不构成本正则命中。判据取「注解/构造/openConnection 级」精确形态，javadoc 裸词不计：
 * <ul>
 *   <li>{@code @KafkaListener(} 注解注解形：</li>
 *   <li>{@code new KafkaTemplate<}（构造自建 producer）：</li>
 *   <li>{@code KafkaTemplate<String,\s*String>} 作为字段声明/构造参数注入（自持 client）。</li>
 * </ul>
 * 随「series 改造窗口」（EcosOntologyEventConsumer 等 @KafkaListener 接管计划）推进，
 * 命中应保持 0。当前护栏：**只减不增**（基线 0，任何新增即红）。</p>
 *
 * <p>验收：{@code mvn -f ecos_backend/pom.xml -pl gateway -am test -Dtest='NoSelfKafkaArchTest'}。</p>
 */
class NoSelfKafkaArchTest {

    /** 基线冻结 2026-10-07：0（引擎侧均已切 EventBusService；real 自建 = 0）。 */
    static final int BASELINE_SELF_KAFKA = 0;

    // 精确形态：构造 / 注解 / 类型字段注入（跳过 javadoc 裸词与 // 注释行）
    private static final List<Pattern> SELF_KAFKA = List.of(
            Pattern.compile("@KafkaListener\\s*\\("),
            Pattern.compile("new\\s+KafkaTemplate\\s*<"),
            Pattern.compile("KafkaTemplate\\s*<\\s*String\\s*,\\s*String\\s*>\\s+(private|final)\\s+[a-zA-Z]"));

    private static final List<String> TOP_LEVEL = List.of("engine", "services", "workspace", "runtime");
    private static final Pattern EXCLUDED_MODULE = Pattern.compile("/runtime-event/");

    @Test
    @DisplayName("B8b 业务侧真实自建 Kafka（注解/构造/自持 client）= 0")
    void businessSideMustNotSelfHoldKafka() throws IOException {
        Path root = resolveBackendRoot();
        int hit = 0;
        for (String top : TOP_LEVEL) {
            Path base = root.resolve(top);
            if (!Files.isDirectory(base)) {
                throw new IllegalStateException("Kafka 扫描根目录缺失：" + base);
            }
            try (Stream<Path> walk = Files.walk(base)) {
                for (Path p : walk.filter(Files::isRegularFile)
                        .filter(f -> f.toString().endsWith(".java"))
                        .filter(f -> f.toString().replaceAll("\\\\", "/").contains("/src/main/java/"))
                        .filter(f -> !EXCLUDED_MODULE.matcher(f.toString().replaceAll("\\\\", "/")).find())
                        .toArray(Path[]::new)) {
                    String source = Files.readString(p);
                    for (String raw : source.split("\r?\n")) {
                        String line = stripLineComment(raw).stripLeading();
                        if (line.startsWith("*")) {
                            continue; // javadoc
                        }
                        for (Pattern pat : SELF_KAFKA) {
                            if (pat.matcher(line).find()) {
                                hit++;
                                System.err.println("[B8b 命中] " + p + "  ->  " + raw.strip());
                                break;
                            }
                        }
                    }
                }
            }
        }
        Assertions.assertEquals(BASELINE_SELF_KAFKA, hit,
                "B8b 业务侧自建 Kafka 新增：实测 " + hit + "（铁律 §2.5 事件单出口 runtime-event EventBusService）");
        System.out.println("[NoSelfKafkaArch] " + TOP_LEVEL.size() + " 顶层域 main 真实自建 Kafka = " + hit);
    }

    private static Path resolveBackendRoot() {
        Path cur = Path.of("").toAbsolutePath();
        while (cur != null) {
            if (Files.isRegularFile(cur.resolve("pom.xml"))
                    && Files.isDirectory(cur.resolve("gateway"))) {
                return cur.normalize();
            }
            cur = cur.getParent();
        }
        throw new IllegalStateException("未能定位 ecos_backend 根（pom.xml + gateway/ 目录）");
    }

    private static String stripLineComment(String line) {
        int idx = line.indexOf("//");
        return idx >= 0 ? line.substring(0, idx) : line;
    }
}
