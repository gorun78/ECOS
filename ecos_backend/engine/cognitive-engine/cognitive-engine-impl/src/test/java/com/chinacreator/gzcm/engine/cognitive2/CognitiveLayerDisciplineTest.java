package com.chinacreator.gzcm.engine.cognitive2;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 分册05 §B-1 / 铁律 §0.6 / 后端开发规范 离线架构护栏。
 *
 * <p>认知引擎侧持久化现走 JdbcTemplate 通道（Mapper 0 命中，文档"零 Mapper"侧成立）；
 * 本护栏锁两维：</p>
 * <ol>
 *   <li><b>JdbcTemplate imports 只减不增（baseline 14 文件，2026-10-04 实测）</b>——Mapper 是
 *       认知域违项，非防御面，因此本测试只锁 JdbcTemplate 面的<b>文件数</b>不上升；</li>
 *   <li><b>认知主源码<b>零</b> {@code @Mapper} / {@code BaseMapper<}（认知域 B-1
 *       "零新增业务事实表 · 零 Mapper"纪律）。</li>
 * </ol>
 */
final class CognitiveLayerDisciplineTest {

    private static final Pattern JDBCTEMPLATE_IMPORT =
        Pattern.compile("import\\s+org\\.springframework\\.jdbc\\.core\\.JdbcTemplate\\b");
    private static final Pattern MAPPER_ANNOTATION =
        Pattern.compile("@Mapper\\b");
    private static final Pattern BASEMAPPER_EXTENDS =
        Pattern.compile("\\bBaseMapper\\s*<");

    @Test
    @DisplayName("JdbcTemplate 使用文件数 只减不增（baseline 14，2026-10-04 实测；认知侧 JdbcTemplate 属已知 legacy 通道路径）")
    void jdbcTemplateFilesRatchetFreeze() throws IOException {
        List<Path> files = CognitiveDocPaths.cognitiveMainJava();
        long hits = files.stream().filter(f -> {
            try {
                String body = java.nio.file.Files.readString(f, java.nio.charset.StandardCharsets.UTF_8);
                return JDBCTEMPLATE_IMPORT.matcher(body).find();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }).count();
        assertTrue(hits <= 14,
            "JdbcTemplate import 文件数 %d > 14（超出 2026-10-04 基线；Mapper 通道禁止新引入新文件）"
                .formatted(hits));
    }

    @Test
    @DisplayName("认知主源码零 @Mapper / BaseMapper<（B-1 零 Mapper 纪律；新引入即红）")
    void noMapperInCognitive() throws IOException {
        List<Path> files = CognitiveDocPaths.cognitiveMainJava();
        int mapperAnno = CognitiveDocPaths.countHits(files, MAPPER_ANNOTATION);
        int baseMapper = CognitiveDocPaths.countHits(files, BASEMAPPER_EXTENDS);
        assertTrue(mapperAnno == 0, "认知引擎出现 @Mapper（B-1 零 Mapper 纪律违）");
        assertTrue(baseMapper == 0, "认知引擎出现 BaseMapper<> 继承（铁律 §0.6 认知不新增业务事实表侧漏）");
    }
}
