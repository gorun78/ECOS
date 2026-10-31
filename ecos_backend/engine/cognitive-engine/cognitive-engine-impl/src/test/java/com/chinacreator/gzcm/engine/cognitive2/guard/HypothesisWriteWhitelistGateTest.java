package com.chinacreator.gzcm.engine.cognitive2.guard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 台账 §8.2.3「假设写侧四终态冻结违例」+ TOP 风险 #2「写侧枚举冻结被自家 DAO 打破」销账
 * （#146bd，台账 v1.0.229）——假设 status 写面白名单形态门禁（纯源码/DDL 扫描，零 DB 依赖）。
 *
 * <p>背景：V191 建表时 DEFAULT 'VALID' 与 HypothesisDao INSERT 字面量 'VALID' 同违 R-15 ①
 * 四终态词表（PROPOSED/EVIDENCED/BELIEVED/REFUTED，词表单源 {@code HypothesisStatus}）；
 * 旧值 VALID/INVALIDATED/ARCHIVED 仅 read 侧 {@code fromDbValue} 只读别名，禁新写入。
 * 本批收口面：V299 把 DDL 默认值归一 'PROPOSED'；HypothesisDao.insert 字面量同改。</p>
 *
 * <p>断言面：</p>
 * <ol>
 *   <li><b>写面白名单零容差</b>：HypothesisDao 的 INSERT 字面量 status 只认 'PROPOSED'
 *       （新假设注册语义起点），旧值 'VALID'/'INVALIDATED'/'ARCHIVED' 作写值零命中
 *       （判据先剥注释——Javadoc/注释里的旧值示例不是违例）；</li>
 *   <li><b>V299 DDL 形态冻结</b>：只 ALTER 默认值为 'PROPOSED' 落权威载体
 *       {@code ecos_cognitive.ecos_cognitive_hypothesis}，禁改类型 / NOT NULL / 加删列；</li>
 *   <li><b>V191 已发布脚本网位冻结</b>：V191 marker 行
 *       {@code status VARCHAR(16) NOT NULL DEFAULT 'VALID'} 保留原样——
 *       已发布迁移脚本网**不改**（V299 才是后续单源 ALTER 覆盖位置）；
 *       「历史生效默认值被新脚本 ALTER 覆盖」的语义由 case② 承担了。</li>
 * </ol>
 *
 * <p>变异探针（交付时实跑取证后复原）：① Dao INSERT 字面量改回 'VALID' → case①红；
 * ② V299 改回 'VALID'/改类型/加删列 → case②红；
 * ③ 有人去修 V191 把 DEFAULT 改回 'PROPOSED'（违"已发布迁移不改"）→ case③红；
 * ④ HypothesisStatus 增/删一枚四终态值 → case④红。</p>
 */
class HypothesisWriteWhitelistGateTest {

    private static final Path REPO = CogScope.REPO;
    private static final Path MIGRATIONS = REPO.resolve("docs/50-db_script/02-flyway-migration/gateway");
    private static final Path V191 = MIGRATIONS.resolve("V191__cognitive_converge_belief_hypo_evidence.sql");
    private static final Path V299 = MIGRATIONS.resolve("V299__cognitive_hypothesis_status_default_proposed.sql");
    private static final Path DAO = CogScope.MAIN.resolve("com/chinacreator/gzcm/engine/cognitive2/dao/HypothesisDao.java");
    private static final Path STATUS = REPO.resolve(
            "ecos_backend/engine/cognitive-engine/cognitive-engine-api/src/main/java"
                    + "/com/chinacreator/gzcm/engine/cognitive/api/HypothesisStatus.java");

    private static final String CARRIER = "ecos_cognitive.ecos_cognitive_hypothesis";

    // ────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("case① 写面白名单：HypothesisDao INSERT 只认 'PROPOSED' 字面量，旧值 VALID/INVALIDATED/ARCHIVED 作写值零命中")
    void daoInsertWriteFaceIsFourTerminalWhitelist() throws IOException {
        String dao = CogScope.stripComments(Files.readString(DAO));
        assertTrue(Pattern.compile("INSERT\\s+INTO\\s+ecos_cognitive_hypothesis[\\s\\S]*?'PROPOSED'")
                        .matcher(dao).find(),
                "HypothesisDao INSERT 缺四终态字面量 'PROPOSED'（新假设注册语义起点；R-15 ①）");
        assertFalse(dao.contains("'VALID'"),
                "HypothesisDao 写面出现旧值 'VALID'（仅 read 侧别名，禁新写入）");
        assertFalse(dao.contains("'INVALIDATED'"),
                "HypothesisDao 写面出现旧值 'INVALIDATED'（仅 read 侧别名，禁新写入）");
        assertFalse(dao.contains("'ARCHIVED'"),
                "HypothesisDao 写面出现旧值 'ARCHIVED'（仅 read 侧别名，禁新写入）");
    }

    @Test
    @DisplayName("case② V299 DDL 形态冻结：只 ALTER 默认值 'PROPOSED' 落权威载体，禁改类型/NOT NULL/加删列")
    void v299DdlShapeIsFrozen() throws IOException {
        assertTrue(Files.isRegularFile(V299), "V299 单源文件缺失：" + V299);
        String ddl = CogScope.stripComments(Files.readString(V299));
        assertTrue(ddl.contains("ALTER TABLE " + CARRIER + "\n    ALTER COLUMN status SET DEFAULT 'PROPOSED'"),
                "V299 缺权威载体 status 默认值 'PROPOSED' 归一");
        // 只判 DDL 骨架行（ALTER/COLUMN/SET/DEFAULT）像，注释/COMMENT 正文描述骨架
        // 的字样不触发（形状面：本批禁的骨架动作只能出现在可执行 DDL 语句内）。
        for (String line : ddl.lines().filter(l -> l.trim().startsWith("ALTER")
                || l.trim().startsWith("SET")).toList()) {
            String low = line.toLowerCase();
            assertFalse(low.contains("not null"),
                    "V299 骨架行触碰 NOT NULL 约束（本批只改默认值词表，禁动约束）：" + line);
            assertTrue(!Pattern.compile("(?i)\\b(drop|add)\\s+column\\b|\\bset\\s+data\\s+type\\b")
                            .matcher(line).find(),
                    "V299 骨架行有加/删列或改类型动作（IR03 只加不删骨架；本批只动默认值）：" + line);
        }
    }

    @Test
    @DisplayName("case③ V191 已发布脚本网位冻结：marker 行保留原样（已发布迁移不可回改；V299 才是后续单源 ALTER 覆盖位置）")
    void v191PublishedMarkerIsFrozen() throws IOException {
        assertTrue(Files.isRegularFile(V191), "V191 单源文件缺失：" + V191);
        String ddl = CogScope.stripComments(Files.readString(V191));
        assertTrue(ddl.contains("status           VARCHAR(16)  NOT NULL DEFAULT 'VALID'"),
                "V191 已发布脚本网 marker 行被改（DB 铁律：已发布迁移不可回改；本批语义收口走 V299 单源 ALTER 覆盖，不是回改 V191）");
    }

    @Test
    @DisplayName("case④ 词表锚点：HypothesisStatus 枚举精确 = PROPOSED/EVIDENCED/BELIEVED/REFUTED 四值零增删")
    void vocabularySingleSourceIsFrozen() throws IOException {
        String src = CogScope.stripComments(Files.readString(STATUS));
        assertTrue(src.contains("PROPOSED, EVIDENCED, BELIEVED, REFUTED;"),
                "词表单源 HypothesisStatus 四终态值面漂移（R-15 ① 冻结，禁增删）");
    }

}
