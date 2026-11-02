package com.chinacreator.gzcm.engine.ontology.service;

import com.chinacreator.gzcm.engine.ontology.testscan.SourceCommentStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 本体规则域「{@code rule_carrier}」改名单源面门禁（#147S，收口《ECOS补充详细设计说明书-R7至R11-2026-10-16.md》
 * §2.1 I-4／§2.4 C-3 与 §3.1 DoD 的 <b>DC-2</b>「{@code carrier}（本体规则域）残留 · 跨文档 grep · 命中 0」）。
 *
 * <p><b>差距形态（本批实跑取证，非推断）</b>：DB 列／DTO／VO／DAO 早已按 I-4 改为
 * {@code rule_carrier}/{@code ruleCarrier}（与数据域 {@code carrier_ref} 消歧），但
 * {@code OntologyRuleService} 仍留两处裸名——① 创建日志的标签 {@code carrier=%{}}／②
 * {@code validateDualDimension} 内的局部变量 {@code carrier}。二者都不是契约面（wire 零影响），
 * 但 DC-2 的判据是<b>字面量零容差</b>：留一枚裸名，「统一改名已完成」就只能是局部真话。</p>
 *
 * <p><b>本门钉三件事</b>：</p>
 * <ol>
 *   <li>ontology-engine（api + impl）{@code src/main/java} 全部文件剥注释后，正则
 *       {@code \bcarrier\b}（大小写敏感）<b>命中 0</b>——词边界判据天然放过合规形态：
 *       {@code rule_carrier}／{@code ruleCarrier} 中 {@code carrier} 前后均为单词字符（{@code _} 也是），
 *       不构成边界，故不会被误计（这也是本腿不写成「先替换 rule_ 再 grep」的原因：那种口径要维护例外表）；</li>
 *   <li><b>属主回读</b>：DAO 的 SQL 列名、DTO 的驼峰属性、V300 DDL 的 {@code ADD COLUMN rule_carrier}
 *       三处锚点在位——防止「两侧一起删干净所以零命中」的假绿（DC-2 要的是<b>改名生效</b>，不是改名消失）；</li>
 *   <li><b>判据能变红</b>：{@code @TempDir} 合成夹具证明裸名被计、合规形态不被计，不往仓库塞违例文件。</li>
 * </ol>
 *
 * <p><b>面外口径（如实登记，禁被下游读成全仓零命中）</b>：{@code \bcarrier\b} 只判<b>本体规则域</b>。
 * 数据湖分层「层载体 carrier」（详设原册 §附录 载体 carrier、写通道 {@code layer+carrier}）与认知对象目录
 * 契约键 {@code {cClass,carrier,readOnlyResolvePath}}、前端航班样例列名 {@code carrier} 属另外三个域的同名词，
 * 不在 I-4 改名范围，也不得被本腿扫掉。文档面同口径：需求合并册 R10 句中的 {@code carrier} 已按
 * §2.9.5④「历史面加勘定注」两档口径就地标注作废指向，历史台账行保留不改字。</p>
 */
class OntologyRuleCarrierNamingGateTest {

    /** 裸名判据（大小写敏感 + 词边界）：合规的 rule_carrier / ruleCarrier / carrier_ref 均不构成边界命中。 */
    private static final Pattern BARE_CARRIER = Pattern.compile("\\bcarrier\\b");

    /** 扫描根相对仓根的位置（api + impl 两模块的 main 面）。 */
    private static final String ONTOLOGY_ENGINE_ROOT = "ecos_backend/engine/ontology-engine";

    /** 假空防护：本模块 main 面 java 文件数下限（2026-11-02 实测 236，只增不减）。 */
    private static final int MIN_SCANNED_ONTOLOGY_MAIN_FILES = 200;

    private static final String DAO_REL =
            ONTOLOGY_ENGINE_ROOT + "/ontology-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ontology/dao/OntologyRuleDao.java";
    private static final String SAVE_DTO_REL =
            ONTOLOGY_ENGINE_ROOT + "/ontology-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ontology/dto/OntologyRuleSaveDTO.java";
    private static final String V300_DDL_REL =
            "docs/50-db_script/02-flyway-migration/gateway/V300__ontology_rule_git_cols_and_ecos_git_repo.sql";

    @Test
    @DisplayName("DC-2：本体规则域 main 面裸名 carrier 剥注释后零命中（词边界判据）")
    void ruleDomainNeverUsesBareCarrierName() throws IOException {
        Path root = locateRepoRoot();
        List<Path> javaFiles = new ArrayList<>();
        collectJavaMainUnder(root.resolve(ONTOLOGY_ENGINE_ROOT), javaFiles);
        assertTrue(javaFiles.size() >= MIN_SCANNED_ONTOLOGY_MAIN_FILES,
                "扫描面缩水：ontology-engine main 面实扫 java 文件 " + javaFiles.size()
                        + " < 下限 " + MIN_SCANNED_ONTOLOGY_MAIN_FILES + "，零命中不可信（读不到 ≠ 无违例）");

        List<String> hits = new ArrayList<>();
        for (Path f : javaFiles) {
            String stripped = SourceCommentStripper.strip(Files.readString(f, StandardCharsets.UTF_8));
            Matcher m = BARE_CARRIER.matcher(stripped);
            while (m.find()) {
                hits.add(relativize(root, f) + " → " + m.group());
            }
        }
        assertEquals(List.of(), hits,
                "本体规则域复现裸名 carrier（§2.1 I-4／C-3：该域唯一名 = rule_carrier／ruleCarrier，"
                        + "与数据域 carrier_ref 消歧；DoD DC-2 判据＝命中 0）。命中清单=" + hits);
    }

    @Test
    @DisplayName("改名生效回读：DAO 列名／DTO 属性／V300 DDL 三处 rule_carrier 锚点在位（防两侧都删的假绿）")
    void renamedOwnerAnchorsAreStillLive() throws IOException {
        Path root = locateRepoRoot();

        String dao = SourceCommentStripper.strip(readRequired(root, DAO_REL));
        assertTrue(dao.contains("rule_carrier"),
                "OntologyRuleDao 不再出现 rule_carrier 列名：改名属主面已迁走，本门须同批改判据，禁静默失配");

        String dto = SourceCommentStripper.strip(readRequired(root, SAVE_DTO_REL));
        assertTrue(dto.contains("ruleCarrier"),
                "OntologyRuleSaveDTO 不再出现 ruleCarrier 属性：契约面（I-3 值域校验的入参）已断");

        String ddl = Files.readString(root.resolve(V300_DDL_REL), StandardCharsets.UTF_8);
        assertTrue(ddl.contains("rule_carrier"),
                "V300 迁移脚本不再增 rule_carrier 列：DS-1 五列目标态的载体面被改走，须同批重裁本门");
    }

    @Test
    @DisplayName("合成夹具：裸名被计、rule_carrier/ruleCarrier/carrier_ref 不被计（判据能变红）")
    void fixtureProvesWordBoundaryJudgementCanTurnRed(@TempDir Path tmp) throws IOException {
        Path bad = tmp.resolve("BadRule.java");
        Files.writeString(bad, """
                class BadRule {
                    private static final String C = "carrier";
                    void m() { String carrier = "x"; }
                }
                """, StandardCharsets.UTF_8);
        String badStripped = SourceCommentStripper.strip(Files.readString(bad, StandardCharsets.UTF_8));
        assertTrue(BARE_CARRIER.matcher(badStripped).find(),
                "夹具裸名未被计 ⇒ 判据已退化（零命中是假绿）");

        Path good = tmp.resolve("GoodRule.java");
        Files.writeString(good, """
                class GoodRule {
                    private static final String COL = "rule_carrier";
                    private String ruleCarrier;
                    private String carrierRef;
                    // 注释里写 carrier 也不该计：本门先剥注释
                }
                """, StandardCharsets.UTF_8);
        String goodStripped = SourceCommentStripper.strip(Files.readString(good, StandardCharsets.UTF_8));
        assertFalse(BARE_CARRIER.matcher(goodStripped).find(),
                "合规形态被误计（rule_carrier／ruleCarrier／carrier_ref／注释）⇒ 判据过宽，会逼改码人删证据");

        // carrier_ref 必须确实在词边界判据外面：数据域同名词不受本门管辖
        assertFalse(BARE_CARRIER.matcher("carrier_ref").find());
    }

    // ─────────────────────────── helpers ───────────────────────────

    private static void collectJavaMainUnder(Path dir, List<Path> out) throws IOException {
        if (!Files.isDirectory(dir)) {
            fail("扫描根不存在：" + dir + "（扫描根漂移＝判据失明，不是零命中）");
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.filter(p -> p.toString().replace('\\', '/').contains("/src/main/java/"))
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(Files::isRegularFile)
                    .forEach(out::add);
        }
    }

    private static String relativize(Path root, Path f) {
        return root.relativize(f).toString().replace('\\', '/');
    }

    private static String readRequired(Path root, String rel) throws IOException {
        Path p = root.resolve(rel);
        if (!Files.isRegularFile(p)) {
            fail("属主锚点文件缺失：" + rel + "（改名属主面消失＝本门判据须同批改，禁以零命中放行）");
        }
        return Files.readString(p, StandardCharsets.UTF_8);
    }

    /** 从测试类目录向上定位仓根（含 ecos_backend 与 docs 两个子目录的那一层）。 */
    private static Path locateRepoRoot() {
        Path p = Paths.get("").toAbsolutePath();
        while (p != null) {
            if (Files.isDirectory(p.resolve("ecos_backend")) && Files.isDirectory(p.resolve("docs"))) {
                return p;
            }
            p = p.getParent();
        }
        fail("未能定位仓根（扫描根解析失败 ≠ 无违例）");
        return null;
    }
}
