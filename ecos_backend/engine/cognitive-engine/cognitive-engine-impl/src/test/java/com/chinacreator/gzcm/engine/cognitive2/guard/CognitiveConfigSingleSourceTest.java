package com.chinacreator.gzcm.engine.cognitive2.guard;

import com.chinacreator.gzcm.engine.cognitive2.service.CognitiveConfigQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §E.5 配置单源门禁（详设-05 E.5 末条 / 铁律 §十）：
 * 「{@code ecos.cognitive.enabled}、{@code ecos.cognitive.eval.trend_weight}、
 * {@code ecos.cognition.domain_alias}、{@code ecos.cognitive.demo} 一律经 sysman 配置门面消费，
 * 禁 {@code @Value} 散落默认值造成两态漂移」。
 *
 * <p>四道断言各自钉死一种漂移形态：</p>
 * <ol>
 *   <li><b>键字面量单源</b>：四枚键在认知引擎主源码内的<b>代码</b>出现点（注释已剥离）只准落在
 *       门面 {@link CognitiveConfigQueryService}；唯一登记豁免 = {@code Wave3DemoController} 的
 *       装配期 {@code @ConditionalOnProperty}（{@code matchIfMissing=false} ⇒ 与门面默认 false 同向，
 *       不构成两态）；</li>
 *   <li><b>禁 {@code @Value} 取这四枚键</b>：出现即 FAIL（默认值散落 = 代码态与库态漂移）；</li>
 *   <li><b>门面纯度</b>：门面只准委托 sysman {@code SysConfigService}，自身不得持 JdbcTemplate/SQL
 *       （否则即 X-31/W129 跨域写 sys_config 的复活形态）；</li>
 *   <li><b>别名表不改代码</b>：{@code DomainAliasResolver} 内不得内置别名对（F05-11 要点 2）。</li>
 * </ol>
 *
 * <p>另核验库侧种子面：V263 迁移脚本必须声明全部四枚键（缺行 ⇒ 门面只能吃代码常量默认值，
 * 运维改不动 = 另一种漂移）。脚本只落文件、未对 ecos-postgres 实跑（§14.4 未授权项）。</p>
 */
class CognitiveConfigSingleSourceTest {

    /** §E.5 冻结的四枚键（与文档字面量逐字对齐，含 {@code cognition} 这一非 {@code cognitive} 拼写）。 */
    private static final List<String> E5_KEYS = List.of(
            "ecos.cognitive.enabled",
            "ecos.cognitive.eval.trend_weight",
            "ecos.cognition.domain_alias",
            "ecos.cognitive.demo");

    /** 键字面量允许出现的代码位：门面本体 + 已登记的装配期豁免。 */
    private static final Set<String> KEY_ALLOWED_FILES = Set.of(
            "CognitiveConfigQueryService.java",
            "Wave3DemoController.java");

    /** {@code @Value("${key[:default]}")} 取键形态。 */
    private static final Pattern VALUE_ANNO = Pattern.compile("@Value\\s*\\(\\s*\"\\$\\{\\s*([^:}]+)");

    @Test
    @DisplayName("E.5-1 四枚键字面量在认知主源码内只准出现在门面（+Wave3Demo 装配期豁免）")
    void keyLiteralsHaveSingleSource() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path p : javaSources()) {
            String code = CogScope.stripComments(readUtf8(p));
            String fileName = p.getFileName().toString();
            for (String key : E5_KEYS) {
                int idx = code.indexOf(key);
                while (idx >= 0) {
                    if (!KEY_ALLOWED_FILES.contains(fileName)) {
                        violations.add(fileName + ":" + CogScope.lineOf(code, idx) + " 取键 " + key);
                    }
                    idx = code.indexOf(key, idx + key.length());
                }
            }
        }
        assertTrue(violations.isEmpty(),
                "§E.5 键字面量散落（应只经 CognitiveConfigQueryService 门面常量）：" + violations);
    }

    @Test
    @DisplayName("E.5-2 认知主源码禁对这四枚键用 @Value（禁默认值散落造成两态漂移）")
    void noValueInjectionForE5Keys() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path p : javaSources()) {
            String code = CogScope.stripComments(readUtf8(p));
            Matcher m = VALUE_ANNO.matcher(code);
            while (m.find()) {
                String key = m.group(1).trim();
                if (E5_KEYS.contains(key)) {
                    violations.add(p.getFileName() + ":" + CogScope.lineOf(code, m.start()) + " @Value " + key);
                }
            }
        }
        assertTrue(violations.isEmpty(), "§E.5 禁 @Value 取门面键：" + violations);
    }

    @Test
    @DisplayName("E.5-3 门面本体只委托 SysConfigService：零 SQL / 零 JdbcTemplate（X-31/W129 不复辟）")
    void facadeDelegatesOnlyToSysmanGate() throws IOException {
        Path facade = CogScope.MAIN.resolve(
                "com/chinacreator/gzcm/engine/cognitive2/service/CognitiveConfigQueryService.java");
        assertTrue(Files.isRegularFile(facade), "门面文件缺失：" + facade);
        String code = CogScope.stripComments(readUtf8(facade));
        for (String forbidden : List.of("JdbcTemplate", "SELECT ", "INSERT INTO", "UPDATE ", "DELETE FROM")) {
            assertFalse(code.contains(forbidden), "门面出现「" + forbidden + "」= 自持数据访问，违 §E.5/F05-13 收口");
        }
        assertTrue(code.contains("SysConfigService"), "门面必须委托 sysman sanctioned 配置门面 SysConfigService");
    }

    @Test
    @DisplayName("E.5-4 四枚键的字面量常量与文档冻结值逐字相等（防拼写漂移，含 cognition/cognitive 混写风险面）")
    void facadeKeyConstantsMatchDoc() throws IOException {
        Path facade = CogScope.MAIN.resolve(
                "com/chinacreator/gzcm/engine/cognitive2/service/CognitiveConfigQueryService.java");
        String code = CogScope.stripComments(readUtf8(facade));
        for (String key : E5_KEYS) {
            assertTrue(code.contains("\"" + key + "\""), "门面缺少冻结键常量：" + key);
        }
        // 运行期默认值与库侧种子行同向（两态漂移的第三种形态：代码默认 true 而库内 false）
        assertEquals(1.0d, CognitiveConfigQueryService.TREND_WEIGHT_DEFAULT, 0.0d,
                "PRD-05 §1.1 规格 1：本期 trend_weight 恒 1");
    }

    @Test
    @DisplayName("E.5-5 别名表落配置：DomainAliasResolver 内禁内置别名对（F05-11 要点 2「新增别名不改代码」）")
    void aliasResolverCarriesNoBuiltInTable() throws IOException {
        Path resolver = CogScope.MAIN.resolve(
                "com/chinacreator/gzcm/engine/cognitive2/service/scenario/DomainAliasResolver.java");
        assertTrue(Files.isRegularFile(resolver), "解析器缺失：" + resolver);
        String code = CogScope.stripComments(readUtf8(resolver));
        assertFalse(code.contains("Map.of("), "解析器内置别名表 = 代码/配置两态漂移");
        assertFalse(code.contains("经营") || code.contains("运营"), "解析器不得内置中文别名对");
        assertTrue(code.contains("domainAliases()"), "解析器必须经门面 domainAliases() 取表");
    }

    @Test
    @DisplayName("E.5-6 库侧种子面：V263 声明全部四枚键（只落文件，实跑属 §14.4 未授权项 ⇒ 该半项「未执行」）")
    void seedMigrationDeclaresAllFourKeys() throws IOException {
        Path seed = CogScope.REPO.resolve(
                "docs/50-db_script/02-flyway-migration/gateway/V263__cognitive_sys_config_seed.sql");
        assertTrue(Files.isRegularFile(seed), "配置种子脚本缺失（四枚键无可改面）：" + seed);
        String sql = readUtf8(seed);
        for (String key : E5_KEYS) {
            assertTrue(sql.contains("'" + key + "'"), "V263 未声明键：" + key);
        }
        assertTrue(sql.contains("config_group"), "V263 必须落单表分组（铁律 §十）");
        assertTrue(sql.contains("ON CONFLICT"), "V263 必须幂等（IR02 人工重跑面禁报错）");
        // 未授权实跑：本门禁只判脚本文件面（键/分组/幂等形态），库内行数与生效值实测 = 未执行
    }

    // ── 夹具 ─────────────────────────────────────────────────────────

    /** 认知引擎全量主源码（含 /config/ 包：装配类同样禁取这四枚键）。 */
    private static List<Path> javaSources() throws IOException {
        try (Stream<Path> walk = Files.walk(CogScope.MAIN)) {
            List<Path> out = new ArrayList<>();
            walk.filter(p -> p.toString().endsWith(".java")).forEach(out::add);
            assertFalse(out.isEmpty(), "主源码目录为空，门禁无从扫描：" + CogScope.MAIN);
            return out;
        }
    }

    /** 宽松 UTF-8 读（存量非 UTF-8 源不得让门禁假崩）。 */
    private static String readUtf8(Path p) throws IOException {
        return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
    }
}
