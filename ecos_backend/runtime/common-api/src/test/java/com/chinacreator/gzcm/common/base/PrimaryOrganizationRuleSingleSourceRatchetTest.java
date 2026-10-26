package com.chinacreator.gzcm.common.base;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chinacreator.gzcm.common.testscan.SourceCommentStripper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * 「唯一主机构」判据属主单源面棘轮门（#142/B4-3e-82，收口台账 <b>D-B4-99 b)</b> 属主读面腿）。
 *
 * <p><b>为什么存在</b>：同一条「主体的机构上下文」在仓内有两套互不相容的判据。
 * ① 策略面 {@code SubjectDepartmentResolutionService}（#142/B4-3e-80 建）要求主机构行
 * <b>恰好</b>一枚才写值，0 枚或多枚互异一律未解析；② 准入面 {@code ClearanceInterceptor}
 * 经 {@code ClearanceLookupDao.findOrgById} 的 {@code is_primary = '1' … LIMIT 1} 再取
 * {@code rows.get(0)}——登记 2 枚互异主机构的用户，它<b>必然</b>交付其中一枚，交回哪枚由
 * 数据库顺序决定。②把「事实不完备」谎报成「事实就是它」，机构级密级档因此可落在主体
 * 并不属于的机构上。#142/B4-3e-82 把判据本体上收为属主侧 {@code PrimaryOrganizationRule}，
 * 两侧同用并删除②那条取数腿。本门把这一形态钉成可复跑读数：没有它，下次有人「顺手」
 * 再写一条 {@code … is_primary = '1' LIMIT 1}、或把规则就地抄一份，只能靠人眼巡视发现——
 * 而这条链的两种失败形态（策略面拒／准入面放行）不会在同一次运行里同时现形。</p>
 *
 * <p><b>判据（本门口径，全部走 {@link SourceCommentStripper} 剥注释后的主源码面）</b>：
 * 扫描根＝{@code ecos_backend} 下所有 {@code src/main/java} 目录（{@code target/}、{@code archive/} 排除）。
 * 六条判据共用同一份面集（一次遍历，见 {@link #FACE}），面不可信时由
 * {@link #scanFaceIsTrustworthy()} 单独变红，不让「面读空」冒充「无违例」。</p>
 * <ol>
 *   <li><b>多重性裁决点单源</b>：{@code distinct.first()} 的出现文件集必须恰为规则本体一枚——
 *       「去重后集合 == 1 才取值」这件事在仓内只许写一次。选 {@code distinct.first()} 而非
 *       {@code "true".equalsIgnoreCase(} 做锚：后者经全仓实扫有 16 枚无关读数（噪声判据不可用），
 *       前者实扫唯一＝规则本体；</li>
 *   <li><b>消费方集合双向</b>：除规则本体外，引用 {@code PrimaryOrganizationRule} 的主源码文件
 *       必须恰为 {策略面, 准入面} 两枚。新消费方入面即红＝同期把「接入单源」与「另立判据」
 *       混做一处；某枚消费方消失同样红＝有人把委托拆回就地实现；</li>
 *   <li><b>死腿不得复活</b>：{@code findOrgById} 在剥注释后的主源码面读数必须为 0。
 *       判据作用在剥注释后的源码上不是细节而是本体——改前实测：不剥注释时该面 = 4 文件
 *       （三处 javadoc 历史提及 + 一处真实定义），真实源码会因此假红；</li>
 *   <li><b>SQL 取其一形态归零</b>：同一字符串字面量内同时出现 {@code is_primary = '1'} 与
 *       {@code LIMIT} 的读数必须为 0（{@code [^"]*} 不跨字面量边界，故 {@code UserController}
 *       的分段拼接串不被误计——这条排除是实测过的，不是推定）；</li>
 *   <li><b>Java 层取其一按名棘轮</b>：{@code orgs.get(0)} 面＝{@code Map<相对路径, 枚数>}
 *       与 {@link #BASELINE_TAKE_FIRST} 整张双向比对。现役唯一在册项＝{@code PIPImpl} 4 枚，
 *       它是 <b>D-B4-99 b) 余项</b>（改它等于改 {@code organizationId/Name/Code/Type} 四枚策略
 *       attribute 的取值来源，属裁决事项）——本门钉它的名而不顺手改它，也禁它长到新文件；</li>
 *   <li><b>两侧同源于属主门面</b>：准入面与策略面剥注释后都必须含
 *       {@code getUserOrganizationAffiliations}（跨域事实经属主 api 门面，铁律 #5），
 *       且准入面不得再直书对方表名 {@code td_user_organization}。</li>
 * </ol>
 *
 * <p><b>活性取证（能变红，不靠空面集自证）</b>：{@link #judgementsFireOnSyntheticRegression()}
 * 把上述判据同型跑在一段合成回退源码上（复活 {@code findOrgById}＋就地抄规则＋
 * SQL 取其一＋绕过门面直读表＋Java 层取其一），逐条断言判据<b>命中</b>；再用同一段夹具的
 * 「只在注释里提及」形态断言读数恒 0（证明剥注释这条前提成立）。没有这一条，
 * 「面集 == 空」与「判据失效」在绿光下不可分辨——本仓已有多枚门禁栽在此形。</p>
 *
 * <p><b>已声明盲区</b>：① 只钉 Java 主源码面；MyBatis XML 里的 {@code is_primary} 排序语义
 * 不在本门（{@code getUserOrganizationAffiliations} 的 SQL 由 sysman-impl 的
 * {@code OrganizationAffiliationReadTest} 钉）；② 规则本体的行为正确性属 sysman-impl 的
 * {@code PrimaryOrganizationRuleTest}，本门只管形态单源；③ 换掉变量名的取其一形态
 * （如 {@code list.get(0)}）不被 {@code orgs.get(0)} 判据捕获——它靠 ②④ 两条
 * 「单源 + SQL 形态」判据兜住，不靠标识符匹配；④ 不查已编译构件。</p>
 *
 * <p>验收：{@code mvn -o -f ecos_backend/pom.xml -pl runtime/common-api test}</p>
 */
@Timeout(180)
class PrimaryOrganizationRuleSingleSourceRatchetTest {

    /** 规则本体（判据唯一居住地的仓根相对路径）。 */
    private static final String RULE_FILE =
            "ecos_backend/services/sysman/impl/sysman-api/src/main/java/"
                    + "com/chinacreator/gzcm/sysman/iam/support/PrimaryOrganizationRule.java";

    /** 策略面消费方。 */
    private static final String CONSUMER_POLICY =
            "ecos_backend/engine/security-engine/security-engine-impl/src/main/java/"
                    + "com/chinacreator/gzcm/engine/security/decision/SubjectDepartmentResolutionService.java";

    /** 准入面消费方。 */
    private static final String CONSUMER_ADMISSION =
            "ecos_backend/services/sysman/impl/sysman-impl/src/main/java/"
                    + "com/chinacreator/gzcm/sysman/security/ClearanceInterceptor.java";

    /** 扫描面阈值：main 面实扫 java 文件数下限（读不到 ≠ 无违例）。 */
    private static final int MIN_SCANNED_MAIN_FILES = 2000;

    /** 「去重后恰好一枚」多重性裁决写法（只许出现在规则本体）。 */
    private static final Pattern MULTIPLICITY_JUDGE = Pattern.compile("distinct\\.first\\(\\)");

    /** 已删除的机构取数死腿名（剥注释后全主源码面必须为 0）。 */
    private static final Pattern DEAD_ORG_LOOKUP_LEG = Pattern.compile("findOrgById");

    /** 主机构行 SQL 取其一形态——{@code [^"]*} 保证不跨字符串字面量边界。 */
    private static final Pattern PRIMARY_LIMIT_IN_ONE_LITERAL =
            Pattern.compile("is_primary\\s*=\\s*'1'[^\"']*LIMIT");

    /** 列表取其一（Java 层），按文件计枚数。 */
    private static final Pattern TAKE_FIRST_OF_ORG_LIST = Pattern.compile("\\borgs\\.get\\(0\\)");

    /** 属主 api 门面取数方法名（两侧同源的取数锚）。 */
    private static final String OWNER_FACADE_READ = "getUserOrganizationAffiliations";

    /**
     * Java 层取其一基线（#142/B4-3e-82 建门批实跑读数＝<b>1 文件 / 4 枚</b>）。
     * <p>{@code PIPImpl} 的 4 枚同处一个 {@code if (!orgs.isEmpty())} 块内，把四枚策略
     * attribute 全绑在「数据库交回的第一行」上。它是台账 D-B4-99 b) <b>余项</b>：
     * 收口它要同时裁决「organizationId/Name/Code/Type 的取值来源」，本批不扩面。
     * 双向：长到别的文件＝新违例直红；这 4 枚消失＝基线陈旧，收口批须同批下调本表并留痕。</p>
     */
    private static final Map<String, Integer> BASELINE_TAKE_FIRST = new TreeMap<>(Map.of(
            "ecos_backend/engine/security-engine/security-engine-impl/src/main/java/"
                    + "com/chinacreator/gzcm/engine/security/policy/engine/impl/PIPImpl.java", 4));

    /** 剥注释后的主源码面（键＝仓根相对路径，值＝strip 后源码；一次性遍历，六条判据共用）。 */
    private static final class Face {
        final Map<String, String> files = new LinkedHashMap<>();
        int scannedFiles;
    }

    private static final Face FACE = loadFace();

    private static Face loadFace() {
        Face face = new Face();
        Path root = locateRepoRoot();
        List<Path> files = new ArrayList<>();
        try {
            collectJavaUnder(root.resolve("ecos_backend"), files);
            for (Path f : files) {
                String rel = root.relativize(f).toString().replace('\\', '/');
                face.files.put(rel,
                        SourceCommentStripper.strip(Files.readString(f, StandardCharsets.UTF_8)));
            }
        } catch (IOException e) {
            throw new IllegalStateException("扫描主源码面失败：" + e.getMessage(), e);
        }
        face.scannedFiles = files.size();
        return face;
    }

    // ── 0. 扫描面可信性 ─────────────────────────────

    @Test
    @DisplayName("扫描面可信：main 面文件数达阈值且规则本体在面内（面读空不等于无违例）")
    void scanFaceIsTrustworthy() {
        assertTrue(FACE.scannedFiles >= MIN_SCANNED_MAIN_FILES,
                "扫描面缩水：实扫 main 面 java 文件 " + FACE.scannedFiles + " < 阈值 "
                        + MIN_SCANNED_MAIN_FILES + "，判据不可信（读不到 ≠ 无违例）");
        assertTrue(FACE.files.containsKey(RULE_FILE),
                "规则本体不在扫描面内：" + RULE_FILE + "＝单源锚失效，本门全部判据退化为空集");
    }

    // ── 1. 多重性裁决点单源 ─────────────────────────

    @Test
    @DisplayName("「去重后恰好一枚主机构」的多重性裁决在仓内只许写一次（判据本体＝属主规则）")
    void multiplicityJudgeLivesOnlyInTheOwnerRule() {
        List<String> hits = filesMatching(MULTIPLICITY_JUDGE);
        assertEquals(List.of(RULE_FILE), hits,
                "唯一主机构多重性裁决点出现处 ≠ 1 枚：" + hits
                        + "。两套「主机构」判据并存＝准入面与策略面对同一主体给出不同答案"
                        + "（D-B4-99 b) 原缺陷形态）；改判据请只改 PrimaryOrganizationRule");
    }

    // ── 2. 消费方集合双向 ───────────────────────────

    @Test
    @DisplayName("规则的引用方恰为策略面＋准入面两枚：另立判据或拆回就地实现即红")
    void ruleConsumersAreTheTwoNamedSides() {
        List<String> consumers = filesMatching(Pattern.compile("PrimaryOrganizationRule"));
        List<String> others = consumers.stream().filter(f -> !f.equals(RULE_FILE)).sorted().toList();
        List<String> expected = java.util.stream.Stream.of(CONSUMER_POLICY, CONSUMER_ADMISSION)
                .sorted().toList();
        assertEquals(expected, others,
                "PrimaryOrganizationRule 消费方集合与基线不符：" + others
                        + "。新增文件＝把另一处判据接进本单源（须同步重设本基线并台账留痕）；"
                        + "某枚消失＝委托被拆回就地实现");
    }

    // ── 3. 死腿不复活 ───────────────────────────────

    @Test
    @DisplayName("findOrgById（is_primary LIMIT 1 + rows.get(0) 取数腿）在主源码面读数恒 0")
    void deadOrgLookupLegStaysDeleted() {
        List<String> hits = filesMatching(DEAD_ORG_LOOKUP_LEG);
        assertEquals(List.of(), hits,
                "已删除的机构取数死腿复活：" + hits
                        + "。该腿与属主单源判据不兼容（交回哪枚由数据库顺序决定）；"
                        + "取机构上下文走 IOrganizationService#" + OWNER_FACADE_READ
                        + " + PrimaryOrganizationRule");
    }

    @Test
    @DisplayName("同一字符串字面量内 is_primary='1' 与 LIMIT 并存的 SQL 形态读数恒 0")
    void primaryOrgTakeFirstSqlFaceIsZero() {
        List<String> hits = filesMatching(PRIMARY_LIMIT_IN_ONE_LITERAL);
        assertEquals(List.of(), hits,
                "机构主行 SQL 又写成「LIMIT 1 取其一」：" + hits
                        + "。这条形态绕开多重性判据，把「主机构不唯一」谎报成「就是这一枚」");
    }

    // ── 4. Java 层取其一按名棘轮 ────────────────────

    @Test
    @DisplayName("orgs.get(0) 面按文件/枚数双向锁定：唯一在册项＝PIPImpl 4 枚（D-B4-99 b) 余项）")
    void takeFirstOfOrgListIsRatchettedToTheRegisteredFile() {
        Map<String, Integer> face = new TreeMap<>();
        FACE.files.forEach((rel, src) -> {
            int n = count(TAKE_FIRST_OF_ORG_LIST, src);
            if (n > 0) {
                face.put(rel, n);
            }
        });
        int total = face.values().stream().mapToInt(Integer::intValue).sum();
        int baselineTotal = BASELINE_TAKE_FIRST.values().stream().mapToInt(Integer::intValue).sum();
        assertTrue(total <= baselineTotal,
                "取其一面上升：" + total + " > 基线 " + baselineTotal + "，面集=" + face
                        + "。新增「取列表第一条」＝又造一份不确定主机构读数（D-B4-99 b) 同族），"
                        + "请用 PrimaryOrganizationRule.uniquePrimaryOrg");
        assertEquals(BASELINE_TAKE_FIRST, face,
                "取其一面与基线不符。实跑面集=" + face + "；PIPImpl 4 枚消失＝该项已收口，"
                        + "收口批必须同批下调 BASELINE_TAKE_FIRST 并在台账留痕");
    }

    // ── 5. 两侧同源于属主门面 ───────────────────────

    @Test
    @DisplayName("准入面与策略面同源取数：都走属主门面，准入面不再直书对方表名")
    void bothSidesReadOrgThroughTheOwnerFacade() {
        String admission = FACE.files.get(CONSUMER_ADMISSION);
        assertTrue(admission != null, "准入面文件不在扫描面内：" + CONSUMER_ADMISSION);
        assertTrue(admission.contains(OWNER_FACADE_READ),
                "准入面不再经属主 api 门面取机构隶属＝铁律 #5 违例（跨域事实须经属主门面）");
        assertFalse(admission.contains("td_user_organization"),
                "准入面直书对方表名 td_user_organization＝本类自建取数面（D-B4-99 b) 原形态）；"
                        + "机构隶属的表属 sysman 属主，本类只该用门面交付的行集");

        String policy = FACE.files.get(CONSUMER_POLICY);
        assertTrue(policy != null && policy.contains(OWNER_FACADE_READ),
                "策略面取数腿须与准入面同源（同一枚属主门面方法），否则两侧行集口径可分叉");
    }

    // ── 6. 活性取证 ─────────────────────────────────

    @Test
    @DisplayName("合成回退夹具逐条命中判据：证明本门能变红，不是靠空面集自证")
    void judgementsFireOnSyntheticRegression() {
        String regressed = SourceCommentStripper.strip("""
                package fixture;
                // findOrgById 只在注释里提及的部分不该单独算命中，本夹具另有真实调用
                import com.chinacreator.gzcm.sysman.dao.ClearanceLookupDao;
                public class Fixture {
                    private static ClearanceLookupDao dao;
                    String orgId(String userId) {
                        return dao.findOrgById(userId).get(0).get("org_id").toString();
                    }
                    String judge(List<String> rows) {
                        TreeSet<String> distinct = new TreeSet<>(rows);
                        return distinct.first();
                    }
                    String sql = "SELECT org_id FROM td_user_organization WHERE user_id = ? AND is_primary = '1' LIMIT 1";
                    String first() {
                        List<Object> orgs = null;
                        return orgs.get(0).toString();
                    }
                }
                """);
        List<String> hit = new ArrayList<>();
        if (count(MULTIPLICITY_JUDGE, regressed) > 0) {
            hit.add("multiplicityJudge");
        }
        if (count(DEAD_ORG_LOOKUP_LEG, regressed) > 0) {
            hit.add("deadLeg");
        }
        if (count(PRIMARY_LIMIT_IN_ONE_LITERAL, regressed) > 0) {
            hit.add("sqlTakeFirst");
        }
        if (count(TAKE_FIRST_OF_ORG_LIST, regressed) > 0) {
            hit.add("javaTakeFirst");
        }
        if (regressed.contains("td_user_organization") && !regressed.contains(OWNER_FACADE_READ)) {
            hit.add("rawTableRead");
        }
        assertEquals(List.of("deadLeg", "javaTakeFirst", "multiplicityJudge", "rawTableRead", "sqlTakeFirst"),
                hit.stream().sorted().toList(),
                "合成回退源码未被判据全量命中（缺项=" + hit + "）＝判据失效，此时绿光与假绿不可分辨");

        // 反向对照：注释里的历史提及不算复活（否则本门会在真实源码上假红——改前实测 4 文件）
        String commentedOnly = SourceCommentStripper.strip("""
                public class Fixture {
                    // 见 findOrgById 的历史说明
                    /** distinct.first() 与 is_primary = '1' LIMIT 1 只写在 javadoc 里 */
                    void keep() { }
                }
                """);
        assertEquals(0, count(DEAD_ORG_LOOKUP_LEG, commentedOnly),
                "剥注释失效：注释读数被计入面（本门判据的前提不成立）");
        assertEquals(0, count(MULTIPLICITY_JUDGE, commentedOnly),
                "剥注释失效：注释读数被计入面");
        assertEquals(0, count(PRIMARY_LIMIT_IN_ONE_LITERAL, commentedOnly),
                "剥注释失效：注释读数被计入面");
    }

    // ── 扫描实现 ────────────────────────────────────

    private static List<String> filesMatching(Pattern pattern) {
        List<String> out = new ArrayList<>();
        FACE.files.forEach((rel, src) -> {
            if (pattern.matcher(src).find()) {
                out.add(rel);
            }
        });
        return out.stream().sorted().toList();
    }

    private static int count(Pattern pattern, String src) {
        int n = 0;
        Matcher m = pattern.matcher(src);
        while (m.find()) {
            n++;
        }
        return n;
    }

    private static Path locateRepoRoot() {
        Path cur = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (Path p = cur; p != null; p = p.getParent()) {
            if (Files.isDirectory(p.resolve("ecos_backend"))) {
                return p;
            }
        }
        throw new IllegalStateException("无法从 user.dir=" + cur + " 向上定位仓根（缺 ecos_backend/）");
    }

    private static void collectJavaUnder(Path dir, List<Path> out) throws IOException {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (java.util.stream.Stream<Path> walk = Files.walk(dir)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    .map(p -> p.toAbsolutePath().normalize())
                    .filter(p -> {
                        String s = p.toString().replace('\\', '/');
                        return s.contains("/src/main/java/")
                                && !s.contains("/target/")
                                && !s.contains("/archive/");
                    })
                    .sorted()
                    .forEach(out::add);
        }
    }
}
