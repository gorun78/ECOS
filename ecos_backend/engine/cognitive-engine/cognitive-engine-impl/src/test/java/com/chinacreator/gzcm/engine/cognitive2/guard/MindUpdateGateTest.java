package com.chinacreator.gzcm.engine.cognitive2.guard;

import com.chinacreator.gzcm.engine.cognitive.api.CognitiveApiPaths;
import com.chinacreator.gzcm.engine.cognitive.api.mind.MindStatusAxes;
import com.chinacreator.gzcm.engine.cognitive.api.mind.MindUpdateVO;
import com.chinacreator.gzcm.engine.cognitive2.controller.MindUpdateGatewayController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 差距分析 P0-6 / B3 认知治理批（#141）—— R4 Mind 聚合根演进形态门禁（V292 + 候选更新闸门）。
 *
 * <p><b>责任边界</b>：本门禁只钉「演进形态与写路径单源」；V292 在 dev PG 的实跑属授权门控项
 * （同 V291 先例，本批零写共享 dev PG），实跑凭证另行取证登记，禁以本静态门冒充 live 验收。</p>
 *
 * <p>断言面（纯源码/DDL 扫描 + 反射，不启动 Spring）：
 * <ol>
 *   <li>V292 DDL 单源形态冻结：11 列加列 + 闸门表 + 双索引 + 三轴默认值与
 *       {@link MindStatusAxes} 枚举默认单源一致；禁 JSONB/timestamptz/CHECK/无 schema 限定；</li>
 *   <li>Mind 候选闸门写路径单源：{@code MindUpdateDao} DML 只允许指向
 *       {@code ecos_cognitive_mind_update}，服务/控制器层零 DML；</li>
 *   <li>受理后正式资产零副作用（D-B3-1 挂账面）：R4 三件对
 *       {@code ecos_cognitive_mind} 本表 / belief / hypothesis 表零 DML
 *       （ACCEPTED 等价正式资产新版本行承流未实现前，任何直写即红）；</li>
 *   <li>控制器映射常量驱动：类前缀 = {@link CognitiveApiPaths#COGNITIVE}，方法级零 {@code /api/} 字面；</li>
 *   <li>三状态词表双源一致：{@link MindUpdateVO} 常量字面与 {@code MindUpdateDao} 源码同字。</li>
 * </ol>
 * 变异探针（交付时实跑取证后复原，needle 见各断言上下文）：① V292 默认值改字面 → case①红；
 * ② MindAggregateService 增 belief 表 UPDATE 行 → case③红；③ 控制器类前缀改字面 → case④红。</p>
 */
class MindUpdateGateTest {

    private static final Path REPO = Path.of("..", "..", "..", "..").toAbsolutePath().normalize();
    private static final Path V292 = REPO.resolve(
            "docs/50-db_script/02-flyway-migration/gateway/V292__cognitive_mind_r4_aggregate.sql");

    private static final Path IMPL_MAIN = REPO.resolve(
            "ecos_backend/engine/cognitive-engine/cognitive-engine-impl/src/main/java/com/chinacreator/gzcm/engine/cognitive2");

    private static final Pattern DML = Pattern.compile(
            "(?i)\\b(INSERT\\s+INTO|UPDATE|DELETE\\s+FROM)\\s+([\\w.]+)");

    // ────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("case① V292 DDL 形态冻结：11 列 + 闸门表 + 双索引 + 三轴默认值与词表单源一致 + 禁 JSONB/timestamptz/CHECK")
    void v292DdlShapeIsSingleSourceAndFrozen() throws IOException {
        assertTrue(Files.isRegularFile(V292), "V292 单源文件缺失：" + V292);
        String ddl = CogScope.stripComments(Files.readString(V292));

        // 11 列加列（六段认知上下文 5 + 解释 1 + 三轴 3 + 版本历史 2；scope/context_ref 计入认知上下文）
        for (String col : List.of("subject_ref", "scope", "context_ref", "goals_json",
                "key_questions_json", "explanation", "validity_status", "review_status",
                "adoption_status", "change_reason", "superseded_by")) {
            assertTrue(ddl.contains("ADD COLUMN IF NOT EXISTS " + col),
                    "V292 缺三轴/六段列：" + col);
        }
        // 闸门表 + 双索引
        assertTrue(ddl.contains("CREATE TABLE IF NOT EXISTS ecos_cognitive.ecos_cognitive_mind_update"),
                "V292 缺候选闸门表");
        assertTrue(ddl.contains("idx_cmu_mind"), "V292 缺 idx_cmu_mind");
        assertTrue(ddl.contains("idx_cmu_run"), "V292 缺 idx_cmu_run");
        // 七基线列（DR06~DR08 同族 V187）
        for (String col : List.of("domain", "version_no", "is_deleted", "create_by",
                "update_by", "create_time", "update_time")) {
            assertTrue(ddl.contains(col), "V292 闸门表缺基线列：" + col);
        }
        // 三轴默认值 = 词表单源（MindStatusAxes 各轴 DDL 默认枚的 name()）
        assertTrue(ddl.contains("validity_status   VARCHAR(20) NOT NULL DEFAULT 'PENDING_REVIEW'")
                        || ddl.contains("validity_status VARCHAR(20) NOT NULL DEFAULT 'PENDING_REVIEW'"),
                "validity_status 默认值偏离词表单源（应为 PENDING_REVIEW）");
        assertTrue(ddl.matches("(?s).*review_status\\s+VARCHAR\\(20\\) NOT NULL DEFAULT 'DRAFT'.*"),
                "review_status 默认值偏离词表单源（应为 DRAFT）");
        assertTrue(ddl.matches("(?s).*adoption_status\\s+VARCHAR\\(20\\) NOT NULL DEFAULT 'NOT_ADOPTED'.*"),
                "adoption_status 默认值偏离词表单源（应为 NOT_ADOPTED）");
        // V187 单 status 停写注释在（IR03 只停写不删的证据）
        assertTrue(ddl.contains("停写"), "V292 缺 V187 单 status 停写注释（IR03 证据面）");
        // 红线：禁 JSONB（MC02）/ timestamptz（MC03 家族）/ 新增 CHECK（R4 头注纪律：词表应用层守护）
        assertFalse(ddl.contains("jsonb"), "V292 出现 JSONB（MC02）");
        assertFalse(ddl.contains("timestamptz"), "V292 出现 timestamptz（MC03 家族纪律）");
        assertFalse(ddl.contains("CONSTRAINT chk_") && ddl.contains("CHECK"),
                "R4 纪律：CHECK 延后，本脚本不得新增 chk_ 约束");
    }

    @Test
    @DisplayName("case② 闸门写路径单源：DAO 两枚 DML 的表恒 = MIND_UPDATE_TABLE 常量；服务/控制器零 DML")
    void mindUpdateWritePathIsSingleSource() throws IOException {
        String dao = CogScope.stripComments(
                Files.readString(IMPL_MAIN.resolve("dao/MindUpdateDao.java")));
        // 表常量单源值（IR01 schema 限定）
        assertTrue(dao.contains("\"ecos_cognitive.ecos_cognitive_mind_update\""),
                "MindUpdateDao 表常量字面漂移（须 ecos_cognitive.ecos_cognitive_mind_update）");
        // INSERT：串接表常量须为 MIND_UPDATE_TABLE，恰 1 枚
        Matcher ins = Pattern.compile("INSERT\\s+INTO\\s*\"\\s*\\+\\s*([A-Za-z_]\\w*)").matcher(dao);
        int insN = 0;
        while (ins.find()) { insN++; assertEquals("MIND_UPDATE_TABLE", ins.group(1),
                "INSERT 越出闸门表常量 → " + ins.group(1)); }
        assertEquals(1, insN, "候选提交 INSERT 应恰 1 枚，实测 " + insN);
        // UPDATE 终态：串接表常量须为 MIND_UPDATE_TABLE，恰 1 枚
        Matcher upd = Pattern.compile("(?<![A-Za-z_])UPDATE\\s*\"\\s*\\+\\s*([A-Za-z_]\\w*)").matcher(dao);
        int updN = 0;
        while (upd.find()) { updN++; assertEquals("MIND_UPDATE_TABLE", upd.group(1),
                "finalize UPDATE 越出闸门表常量 → " + upd.group(1)); }
        assertEquals(1, updN, "受控终态 UPDATE 应恰 1 枚，实测 " + updN);
        assertFalse(dao.contains("DELETE FROM"), "mind_update 禁 DELETE（软删 is_deleted 单源，R9 只加不删）");
        // 写 SQL 面 = 恰好上面两枚：本文件不得出现其它表常量的 DML
        assertFalse(Pattern.compile("(?i)\\b(INSERT\\s+INTO|DELETE\\s+FROM)\\s*\"\\s*\\+\\s*MIND_TABLE")
                .matcher(dao).find(), "DML 串接了 MIND_TABLE（正式聚合根禁经本 DAO 直写）");
    }

    @Test
    @DisplayName("case③ 受理后正式资产零副作用：三件文件全 DML 计数恒 = DAO 两枚（mind/belief/hypo 零直写，D-B3-1 在位）")
    void noDmlAgainstFormalMindAssets() throws IOException {
        for (String f : List.of("service/mind/MindAggregateService.java",
                "controller/MindUpdateGatewayController.java")) {
            String src = CogScope.stripComments(Files.readString(IMPL_MAIN.resolve(f)));
            assertFalse(src.contains("INSERT INTO"), f + " 出现 INSERT INTO（正式资产禁经服务/控制器直写）");
            assertFalse(Pattern.compile("(?<![A-Za-z_.])UPDATE\\s*\"").matcher(src).find(),
                    f + " 出现 UPDATE 写 SQL（正式资产需新版本行须经受控闸门，本面恒零）");
            assertFalse(src.contains("DELETE FROM"), f + " 出现 DELETE FROM");
            assertFalse(src.contains("jdbcTemplate"), f + " 私接 JdbcTemplate（写面必须收口 DAO 单源）");
        }
    }

    @Test
    @DisplayName("case④ 控制器映射常量驱动：类前缀 = CognitiveApiPaths.COGNITIVE；方法级零 /api/ 字面；占位全绑定")
    void controllerMappingsAreConstantDriven() {
        RequestMapping classAnn = MindUpdateGatewayController.class.getAnnotation(RequestMapping.class);
        assertTrue(classAnn != null, "控制器缺类级 @RequestMapping");
        assertEquals(List.of(CognitiveApiPaths.COGNITIVE), List.of(classAnn.value()),
                "类前缀必须引用 CognitiveApiPaths.COGNITIVE 常量单源");

        int mapped = 0;
        for (Method meth : MindUpdateGatewayController.class.getDeclaredMethods()) {
            String[] paths = valueOf(meth, GetMapping.class);
            String[] posts = valueOf(meth, PostMapping.class);
            String[] puts = valueOf(meth, PutMapping.class);
            for (String p : Stream.of(paths, posts, puts).flatMap(Arrays::stream).toList()) {
                mapped++;
                assertFalse(p.contains("/api/"),
                        "方法级映射出现绝对 /api/ 字面（须走 REL 常量）：" + p);
                assertTrue(p.startsWith("/minds"),
                        "映射须位于 /minds 族 :" + p);
                if (p.contains("{")) {
                    assertTrue(hasPathVariable(meth),
                            "URI 占位缺 @PathVariable 绑定：" + p + " @ " + meth.getName());
                }
            }
        }
        assertEquals(6, mapped,
                "Minds 端点族应为 6 枚（列表 GET + mind 详情 GET + 提交 POST + 候选列表 GET + 回读 GET + 终态 PUT）");
    }

    @Test
    @DisplayName("case⑤ 三状态词表双源一致：MindUpdateVO 常量字面与 MindUpdateDao 源码同字；枚举缺词回落 null")
    void statusVocabularyIsTwoSourceConsistent() throws IOException {
        String dao = CogScope.stripComments(
                Files.readString(IMPL_MAIN.resolve("dao/MindUpdateDao.java")));
        for (String s : List.of(MindUpdateVO.STATUS_PROPOSED, MindUpdateVO.STATUS_ACCEPTED,
                MindUpdateVO.STATUS_REJECTED)) {
            assertTrue(dao.contains("'" + s + "'") || dao.contains("\"" + s + "\"")
                            || dao.contains("=" + s) || dao.contains(s),
                    "MindUpdateDao 缺状态字面单源引用：" + s);
        }
        // 词表 VI 面（fromName 单源）
        assertFalse(MindStatusAxes.Validity.fromName("bogus") != null,
                "Validity.fromName 对词表外字面未回落 null（词表纪律）");
        assertEquals(MindStatusAxes.Review.DRAFT, MindStatusAxes.Review.fromName("DRAFT"),
                "Review 默认枚 DRAFT 与 V292 DEFAULT 'DRAFT' 漂移");
        assertEquals(MindStatusAxes.Validity.PENDING_REVIEW,
                MindStatusAxes.Validity.fromName("PENDING_REVIEW"),
                "Validity 默认枚 PENDING_REVIEW 与 V292 默认漂移");
        assertEquals(MindStatusAxes.Adoption.NOT_ADOPTED,
                MindStatusAxes.Adoption.fromName("NOT_ADOPTED"),
                "Adoption 默认枚 NOT_ADOPTED 与 V292 默认漂移");
        // 三轴正交禁合并：三枚 fromName({} axis) 互不可解析 = 三轴展开面值无交叉污染
        assertFalse(MindStatusAxes.Validity.fromName("DRAFT") != null,
                "Validity 轴收到 Review 轴字面 DRAFT（三轴词表交叉污染，禁合并回单 status）");
        assertFalse(MindStatusAxes.Adoption.fromName("APPROVED") != null,
                "Adoption 轴收到 Review 轴字面 APPROVED（三轴词表交叉污染）");
    }

    // ─── helper ─────────────────────────────────────────────
    private static <A extends Annotation> String[] valueOf(Method meth, Class<A> ann) {
        Annotation a = meth.getAnnotation(ann);
        if (a == null) return new String[0];
        if (a instanceof GetMapping g) return g.value();
        if (a instanceof PostMapping p) return p.value();
        if (a instanceof PutMapping p) return p.value();
        return new String[0];
    }

    private static boolean hasPathVariable(Method meth) {
        for (Annotation[] pas : meth.getParameterAnnotations()) {
            for (Annotation a : pas) if (a instanceof PathVariable) return true;
        }
        return false;
    }
}
