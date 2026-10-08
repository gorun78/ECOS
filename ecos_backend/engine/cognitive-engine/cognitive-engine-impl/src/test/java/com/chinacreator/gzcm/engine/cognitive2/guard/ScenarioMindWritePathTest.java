package com.chinacreator.gzcm.engine.cognitive2.guard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 设计-05 F05-02 / W116 冻结验收：legacy {@code ecos_scenario_mind}（V146）库内不存在且
 * 不重建 legacy 名 — 全仓生产源码禁止任何 DML（INSERT/UPDATE/DELETE）指向该表（源码断言）。
 * 场景↔Mind 绑定唯一写通道 = cognitive 注册门面 → {@code ecos_cognitive.ecos_cognitive_scenario_mind}（V188）。
 */
class ScenarioMindWritePathTest {

    private static final Pattern LEGACY_MIND_DML = Pattern.compile(
            "(?i)\\b(INSERT\\s+INTO|UPDATE|DELETE\\s+FROM)\\s+(?:\\w+\\.)?ecos_scenario_mind\\b");

    @Test
    @DisplayName("F05-02/W116：全仓无 DML 写 legacy ecos_scenario_mind（源码断言）")
    void noInsertIntoDroppedLegacyMindTable() throws IOException {
        List<String> offenders = new ArrayList<>();
        List<Path> roots = List.of(
                CogScope.REPO.resolve("ecos_backend/workspace"),
                CogScope.REPO.resolve("ecos_backend/engine"),
                CogScope.REPO.resolve("ecos_backend/services"),
                CogScope.REPO.resolve("ecos_backend/gateway"),
                CogScope.REPO.resolve("ecos_backend/runtime"));
        for (Path root : roots) {
            if (Files.notExists(root)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(root)) {
                List<Path> files = walk
                        .filter(p -> p.toString().endsWith(".java"))
                        .filter(p -> {
                            String s = p.toString().replace('\\', '/');
                            return s.contains("/src/main/") && !s.contains("/target/") && !s.contains("/archive/");
                        })
                        .toList();
                for (Path f : files) {
                    Matcher m = LEGACY_MIND_DML.matcher(Files.readString(f));
                    if (m.find()) {
                        offenders.add(root.getParent().relativize(f).toString().replace('\\', '/')
                                + " @" + CogScope.lineOf(Files.readString(f), m.start()));
                    }
                }
            }
        }
        assertTrue(offenders.isEmpty(),
                "W116 停写违例：以下生产源码仍含 legacy ecos_scenario_mind DML → " + offenders);
    }
}
