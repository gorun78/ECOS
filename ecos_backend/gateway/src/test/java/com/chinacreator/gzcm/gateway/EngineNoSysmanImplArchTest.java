package com.chinacreator.gzcm.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * B7 反证 —— 引擎 main 禁 import sysman.*.impl（D-10 引擎门面断链收口）。
 *
 * <p>架构铁律 §2.1：引擎层依赖方向 = {@code engine-impl → engine-api → common-api}，
 * 禁 engine-impl 反向依赖 sysman.{...}.impl 具体类（服务层实现）。引擎读配置走
 * sysman-api 门面（{@code ISysConfigService} / {@code IConfigService}）。</p>
 *
 * <p>选「源码 Pattern 扫描」而非 ArchUnit 字节码断言的原因一致（见
 * {@link SelfScheduleArchTest}）：sysman-impl 具体类往返类名可能被字节码优化改写，
 * 源码 import 行是稳定可判据；同时对 6 个 engine-impl main 域重叠扫描，
 * 一条断言 = 该条铁律的"新增即红"棘轮。</p>
 *
 * <p>覆盖域：security / data / ontology / kb / cognitive / ai 六 engine-impl main；
 * 断言：{@code import com.chinacreator.gzcm.sysman..impl..;} 命中 = 0。</p>
 *
 * <p>验收：{@code mvn -f ecos_backend/pom.xml -pl gateway -am test -Dtest='EngineNoSysmanImplArchTest'}。
 * 依赖前置：无（源码扫描，不依赖测试 classpath）。
 */
class EngineNoSysmanImplArchTest {

    private static final Pattern SYSMAN_IMPL_IMPORT =
            Pattern.compile("^import\\s+com\\.chinacreator\\.gzcm\\.sysman\\..*\\.impl\\.[\\w$]+\\s*;$");

    private static final List<String> ENGINE_IMPL_MAIN_REL = List.of(
            "engine/security-engine/security-engine-impl/src/main/java",
            "engine/data-engine/data-engine-impl/src/main/java",
            "engine/ontology-engine/ontology-engine-impl/src/main/java",
            "engine/kb-engine/kb-engine-impl/src/main/java",
            "engine/cognitive-engine/cognitive-engine-impl/src/main/java",
            "engine/ai-engine/ai-engine-impl/src/main/java");

    @Test
    @DisplayName("D-10：6 个 engine-impl main 域 0 处 import sysman.*.impl（配置走 sysman-api 门面）")
    void engineMainMustNotImportSysmanImpl() throws IOException {
        Path root = resolveBackendRoot();
        int hit = 0;
        for (String rel : ENGINE_IMPL_MAIN_REL) {
            Path base = root.resolve(rel);
            if (!Files.isDirectory(base)) {
                throw new IllegalStateException("engine-impl main 目录缺失：" + base);
            }
            try (Stream<Path> walk = Files.walk(base)) {
                for (Path p : walk.filter(Files::isRegularFile)
                        .filter(f -> f.toString().endsWith(".java"))
                        .toArray(Path[]::new)) {
                    String source = Files.readString(p);
                    for (String line : source.split("\r?\n")) {
                        String t = stripLineComment(line).strip();
                        if (t.startsWith("//") || t.startsWith("*")) {
                            continue;
                        }
                        if (SYSMAN_IMPL_IMPORT.matcher(t).matches()) {
                            hit++;
                            System.err.println("[D-10 命中] " + p + "  ->  " + t);
                        }
                    }
                }
            }
        }
        Assertions.assertEquals(0, hit,
                "引擎 main 存在 import sysman.*.impl 具体类（D-10 引擎门面断链回潮）：命中 " + hit + " 处，"
                        + " 请改走 sysman-api 门面（ISysConfigService / IConfigService）。");
        System.out.println("[EngineNoSysmanImplArch] 6 engine-impl main × sysman.*.impl 交叉 = " + hit);
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
