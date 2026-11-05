package com.chinacreator.gzcm.gateway.arch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 缓存配置「归口单源」棘轮门（DoD DC-3；#147Z-2 落地）。
 *
 * <p><b>本门为什么存在</b>：DC-3 的原判据（#147BN）是「services/engine 各 {@code application*.yml}
 * 中 {@code ecos.cache} 零命中」。这条判据有两处致命盲区——① 只扫 {@code application*.yml}，
 * 而实存自建缓存键的 {@code ontology-defaults.yml} 根本不在扫描面内；② 只盯底座前缀
 * {@code ecos.cache}，于是引擎自建的 {@code ontology.cache.* / dw.cache.* / security.*.cache.ttl-ms}
 * 一律「看不见即满足」。本门把判据换成<b>按键形态扫全资源面 + 全 main 源面</b>，存量逐条登记豁免并写明
 * 清退方向，之后<b>只减不增</b>。</p>
 *
 * <p><b>三条面</b>：A 资源面（{@code src/main/resources/**&#47;*.{yml,yaml,properties}}，不限文件名）；
 * B 源面（{@code src/main/java/**&#47;*.java} 的字符串字面量，剥注释）；
 * C DDL 播种面（{@code docs/50-db_script/02-flyway-migration/gateway/*.sql} 的引号键）。
 * 底座属主键（{@code ecos.cache.*}）与归口外键分开钉：前者零容差（A/B 面只许出现在 2 枚属主文件，
 * C 面只许落在 V306 一枚种子脚本的 4 枚 config_key），后者走 {@link #LEGACY_FACES} 白名单棘轮。</p>
 *
 * <p><b>键形态判据</b>：剥掉 {@code ${...}} 包装后，须匹配 {@code [A-Za-z0-9_.-]+} 且含 {@code .}、
 * {@code _} 或 {@code -}、含 {@code cache}（忽略大小写）并含时效/开关词（{@code ttl|second|minute|enabled|_ms|-ms|delay}）。
 * 显式管辖外：驼峰 JSON 载荷字段名（如元数据刷新策略的 {@code cacheTtlMinutes}）——那是业务载荷键不是配置键，
 * 由 {@link #keyShapeRuleExcludesCamelCasePayloadKeys()} 钉住这一口径，防止日后有人把判据扩到业务 JSON 上
 * 造成误红，或把驼峰配置键塞进来造成漏判。</p>
 *
 * <p><b>红了怎么处置</b>：新增的缓存配置键<b>一律不许加白名单</b>——把该键改为经
 * {@code SysConfigCacheSettingsSource}（{@code sys_config config_group=cache}）+
 * {@code RuntimeCacheManager} 读取，或直接删键。清退一条存量就把 {@link #LEGACY_FACES} 里对应条目删掉
 * （只减不增）。共享工作树里若红差来自他人未提交面，只做归因，禁代改、禁放宽。</p>
 *
 * @author ECOS Gateway #147Z-2/DC-3
 */
class CacheConfigSingleSourceGateTest {

    /** 底座前缀：缓存底座的唯一配置命名空间。 */
    private static final String BASE_PREFIX = "ecos.cache.";

    /** 时效/开关词（判据的第二半）。 */
    private static final Pattern VALUE_TOKEN = Pattern.compile("(ttl|second|minute|enabled|_ms|-ms|delay)", Pattern.CASE_INSENSITIVE);

    /** 合法配置键形态：点分或下划线，无空格。 */
    private static final Pattern KEY_SHAPE = Pattern.compile("[A-Za-z0-9_.\\-]+");

    /** 资源面命中下限（防扫描根漂移把门变成空转）。#147EU 清退 ontology-defaults 后 HEAD 实测 = 8。 */
    private static final int MIN_RESOURCE_HITS = 8;
    /** main java 文件扫描下限。#147Z-2 HEAD 实测 = 2483 枚。 */
    private static final int MIN_MAIN_JAVA_FILES = 2000;
    /** 底座属主去重命中数（只减不增）。#147Z-2 HEAD 实测 = 9。 */
    private static final int BASE_OWNER_HITS = 9;
    /** 底座键在 DDL 单源里的唯一种子脚本。 */
    private static final String BASE_SEED_SCRIPT = "docs/50-db_script/02-flyway-migration/gateway/V306__cache_config_group_seed.sql";

    /** 底座键的 DDL 播种集（sysman 字典里 {@code config_group=cache} 的四枚 config_key）。 */
    private static final Set<String> BASE_SEED_KEYS = Set.of(
            "ecos.cache.enabled",
            "ecos.cache.default_ttl_seconds",
            "ecos.cache.max_stale_seconds",
            "ecos.cache.redis_enabled");

    /** 底座属主文件：{@code ecos.cache.*} 键只允许在这两枚代码文件里出现。 */
    private static final Set<String> BASE_OWNER_FILES = Set.of(
            "ecos_backend/services/sysman/impl/sysman-impl/src/main/java/com/chinacreator/gzcm/sysman/cache/SysConfigCacheSettingsSource.java",
            "ecos_backend/runtime/runtime-core/src/main/java/com/chinacreator/gzcm/runtime/core/cache/outbox/CacheInvalidationRelay.java");

    private static final String MYBATIS_REASON =
            "MyBatis 二级缓存开关（ORM 框架自身能力，非 R8 缓存底座配置面）。豁免条件=8 枚同值 true；"
                    + "任一分叉或新增第 9 枚即红，须按「底座归口 runtime」重新裁决。";

    private static final String SEED_ID_REASON =
            "sys_config 种子行的主键 id（cfg-* 命名），形态撞上判据但语义不是 config_key。豁免=行 id 不改名；"
                    + "改名即红，强制复核该组种子。";

    /**
     * 归口外缓存配置键存量白名单（棘轮，只减不增）：{@code 面|相对路径|键 → 登记理由}。
     * <p>逐条 HEAD 实测（#147Z-2，2026-11-04），非推算。清退方向写在理由里。</p>
     */
    private static final Map<String, String> LEGACY_FACES = legacyFaces();

    private static Map<String, String> legacyFaces() {
        Map<String, String> m = new LinkedHashMap<>();
        // —— 配置字典播种：引擎自建缓存 TTL（live dev PG 实测 4 行 active，见 DoD DC-3 回写）——
        m.put("B|ecos_backend/engine/ontology-engine/ontology-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ontology/controller/OntologyConfigController.java|ontology.cache.ttl_seconds",
                "DC-3 违例存量：本体引擎自建缓存 TTL 字典键。清退=删键并改读底座 default_ttl_seconds。");
        m.put("B|ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/controller/DataEngineConfigController.java|dw.cache.ttl_seconds",
                "DC-3 违例存量：数据引擎自建缓存 TTL。清退同上。");
        m.put("B|ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/controller/DataEngineConfigController.java|dw.lineage.cache_ttl_minutes",
                "DC-3 违例存量：血缘缓存 TTL。清退同上。");
        m.put("B|ecos_backend/services/sysman/impl/sysman-impl/src/main/java/com/chinacreator/gzcm/sysman/config/service/impl/SysConfigService.java|dw.cache.ttl_seconds",
                "DC-3 违例存量：sysman 字典种子里的同一键（与上两条同族）。清退=随引擎侧一并删行。");
        m.put("B|ecos_backend/services/sysman/impl/sysman-impl/src/main/java/com/chinacreator/gzcm/sysman/config/service/impl/SysConfigService.java|dw.lineage.cache_ttl_minutes",
                "DC-3 违例存量：同上。");
        // —— 自建缓存直读环境变量/系统属性：已清退（DC-3 #147EP 删除 security 两个
        //    内存缓存的 System property/环境变量 TTL 自读，改由装配方注入；该链未承流，
        //    无运行时消费方受影响，白名单同步 −4）——
        // —— ontology-defaults.yml 的 cache.ttl_seconds：已清退（DC-3 #147EU）。该文件
        //    全仓无 spring.config.import / @PropertySource / 属主绑定，键本身零读取方；
        //    删除后引擎缓存 TTL 不存在第二来源，白名单同步 −1、资源面下限 9 → 8）——
        // —— 幻影键：已清退（DC-3 #147EM 删除网关 `profile.cache-enabled/cache-ttl-minutes`，
        //    全仓无 profile 前缀绑定、属主默认值在 LLMGatewayProperties(llm.*)，删后白名单同步 −2）——
        // —— ORM 二级缓存开关：框架自身开关，不属 R8 知识缓存底座作用域，但必须同值同批 ——
        m.put("A|ecos_backend/gateway/src/main/resources/application.yml|mybatis.configuration.cache-enabled|true", MYBATIS_REASON);
        m.put("A|ecos_backend/services/aiming/src/main/resources/application.yml|mybatis.configuration.cache-enabled|true", MYBATIS_REASON);
        m.put("A|ecos_backend/services/buszhi/src/main/resources/application.yml|mybatis.configuration.cache-enabled|true", MYBATIS_REASON);
        m.put("A|ecos_backend/services/datanet/src/main/resources/application.yml|mybatis.configuration.cache-enabled|true", MYBATIS_REASON);
        m.put("A|ecos_backend/services/dccheng/src/main/resources/application.yml|mybatis.configuration.cache-enabled|true", MYBATIS_REASON);
        m.put("A|ecos_backend/services/sysman/src/main/resources/application.yml|mybatis.configuration.cache-enabled|true", MYBATIS_REASON);
        m.put("A|ecos_backend/services/sysman/impl/sysman-boot/src/main/resources/application.yml|mybatis.configuration.cache-enabled|true", MYBATIS_REASON);
        m.put("A|ecos_backend/workspace/workspace-service/src/main/resources/application.yml|mybatis.configuration.cache-enabled|true", MYBATIS_REASON);
        // —— DDL 播种面：自建缓存键（config_key 值）——
        m.put("C|docs/50-db_script/02-flyway-migration/gateway/V13__ecos_sys_config.sql|cache_ttl_seconds",
                "DC-3 违例存量：g1-data-foundation 组的自建缓存 TTL 种子（live 实测 1 行 active）。清退=停写后删行。");
        m.put("C|docs/50-db_script/02-flyway-migration/gateway/V13__ecos_sys_config.sql|cfg-g1-cache-ttl",
                SEED_ID_REASON);
        // —— DDL 播种面：底座键（属主=V306，由 baseCacheKeysOnDdlFaceLiveOnlyInSeedScript 单独钉）——
        m.put("C|docs/50-db_script/02-flyway-migration/gateway/V306__cache_config_group_seed.sql|cfg-cache-enabled",
                SEED_ID_REASON);
        m.put("C|docs/50-db_script/02-flyway-migration/gateway/V306__cache_config_group_seed.sql|cfg-cache-default-ttl",
                SEED_ID_REASON);
        m.put("C|docs/50-db_script/02-flyway-migration/gateway/V306__cache_config_group_seed.sql|cfg-cache-redis-enabled",
                SEED_ID_REASON);
        // 注：V306 的第 4 枚行 id 是 cfg-cache-max-stale，形态上缺时效/开关词（stale 不在 VALUE_TOKEN 内）
        // ⇒ 天然不落判据、无需白名单。这是「键形态判据会漏掉语义违例」的自觉样本，别把它当误报清掉。
        return m;
    }

    @Test
    @DisplayName("底座键 ecos.cache.* 的供给面零容差：只许在两枚属主文件内")
    void baseCacheKeysAreOwnedOnlyByBaseFiles() throws IOException {
        Faces faces = scan(repoRoot());
        Set<String> owners = new LinkedHashSet<>();
        Set<String> baseIds = new LinkedHashSet<>();
        for (Hit h : faces.all()) {
            // C 面的底座键是「配置字典播种」不是「代码读取面」，由
            // baseCacheKeysOnDdlFaceLiveOnlyInSeedScript 单独零容差钉；本腿只管 A/B（代码与配置供给面）。
            if (h.bareKey().startsWith(BASE_PREFIX) && !"C".equals(h.face)) {
                owners.add(h.file);
                baseIds.add(h.id());
            }
        }
        assertEquals(BASE_OWNER_FILES, owners,
                "底座配置键漂出了属主文件：" + owners + "（属主集见 BASE_OWNER_FILES；新增读取面必须扩 sysman 字典，禁另立前缀）");
        assertEquals(BASE_OWNER_HITS, baseIds.size(),
                "底座键去重命中数变了：" + baseIds.size() + " ≠ 基线 " + BASE_OWNER_HITS
                        + "（增减都要求同批改 DoD DC-3 回写并留证据；计数按 面|文件|键 去重，同键多次出现不重复计）");
    }

    @Test
    @DisplayName("底座键的 DDL 播种面零容差：只许落在 V306 一枚脚本、只许四枚键")
    void baseCacheKeysOnDdlFaceLiveOnlyInSeedScript() throws IOException {
        Faces faces = scan(repoRoot());
        Set<String> files = new LinkedHashSet<>();
        Set<String> keys = new LinkedHashSet<>();
        for (Hit h : faces.all()) {
            if ("C".equals(h.face) && h.bareKey().startsWith(BASE_PREFIX)) {
                files.add(h.file);
                keys.add(h.bareKey());
            }
        }
        assertEquals(Set.of(BASE_SEED_SCRIPT), files,
                "底座键在 DDL 单源里出现了第二枚脚本：" + files + "（禁分域另立播种；扩配置段请改 " + BASE_SEED_SCRIPT + "）");
        assertEquals(BASE_SEED_KEYS, keys,
                "底座播种键集变了：" + keys + " ≠ 基线 " + BASE_SEED_KEYS + "（新增=同批改 SysConfigCacheSettingsSource 读取腿，删键=同批改 DoD DC-3 回写）");
    }

    @Test
    @DisplayName("归口外缓存配置键面棘轮：存量逐条登记，新增即红")
    void nonBaseCacheConfigKeysDoNotGrow() throws IOException {
        Faces faces = scan(repoRoot());
        List<String> unregistered = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Hit h : faces.all()) {
            if (h.bareKey().startsWith(BASE_PREFIX)) {
                continue;
            }
            seen.add(h.id());
        }
        for (String id : seen) {
            if (!LEGACY_FACES.containsKey(id)) {
                unregistered.add(id);
            }
        }
        assertTrue(unregistered.isEmpty(),
                "新增自建缓存配置键（DC-3 违例）。禁加白名单——改经 SysConfigCacheSettingsSource + RuntimeCacheManager 读取："
                        + "\n" + String.join("\n", unregistered));
        for (String id : LEGACY_FACES.keySet()) {
            assertTrue(seen.contains(id),
                    "白名单里有一条已不存在（" + id + "）：说明确实清退了，请同批删掉该条并把 DoD DC-3 回写的存量计数改小——"
                            + "陈旧豁免=假绿）。");
        }
    }

    @Test
    @DisplayName("扫描面有效：资源面覆盖非 application*.yml，源面文件数不下退")
    void scanSurfaceCoversTheBlindSpot() throws IOException {
        Faces faces = scan(repoRoot());
        assertTrue(faces.mainJavaFiles >= MIN_MAIN_JAVA_FILES,
                "main 源扫描面退化：" + faces.mainJavaFiles + " < 下限 " + MIN_MAIN_JAVA_FILES + "（扫描根漂了？）");
        assertTrue(faces.resourceHits() >= MIN_RESOURCE_HITS,
                "资源面命中退化：" + faces.resourceHits() + " < 下限 " + MIN_RESOURCE_HITS
                        + "（若确已清退，同批下调白名单与本下限）");
        assertTrue(faces.resourceFiles.contains("ecos_backend/engine/ontology-engine/ontology-engine-impl/src/main/resources/ontology-defaults.yml"),
                "资源面盲区复发：ontology-defaults.yml 不在扫描面内——原判据只扫 application*.yml 正是因此漏掉自建缓存键");
    }

    @Test
    @DisplayName("变异自证：新引擎自建缓存键必红（临时目录跑同一算法）")
    void mutationProvesTheGateCanGoRed(@TempDir Path tmp) throws IOException {
        // 夹具必须落在扫描根 ecos_backend/ 之下——扫描根漂移会让本腿静默空转（这正是要防的假绿之一）
        Path yml = tmp.resolve("ecos_backend/svc/src/main/resources/engine-defaults.yml");
        Files.createDirectories(yml.getParent());
        Files.writeString(yml, "engine:\n  cache:\n    ttl_seconds: 60\n", StandardCharsets.UTF_8);
        Path java = tmp.resolve("ecos_backend/svc/src/main/java/demo/Foo.java");
        Files.createDirectories(java.getParent());
        Files.writeString(java, """
                package demo;
                /** 注释里的 demo.cache.ttl_seconds 不算，剥注释后必须消失。 */
                public class Foo {
                    // 行注释里的 legacy.cache.ttl_ms 也不算
                    static final String K = "demo.cache.ttl_seconds";
                    static final String PAYLOAD = "cacheTtlMinutes";
                }
                """, StandardCharsets.UTF_8);

        Faces faces = scan(tmp);
        Set<String> ids = new LinkedHashSet<>();
        for (Hit h : faces.all()) {
            ids.add(h.id());
        }
        assertTrue(ids.contains("A|ecos_backend/svc/src/main/resources/engine-defaults.yml|engine.cache.ttl_seconds|60"),
                "资源面没能吃到非 application*.yml 的自建缓存键（判据失效）：" + ids);
        assertTrue(ids.contains("B|ecos_backend/svc/src/main/java/demo/Foo.java|demo.cache.ttl_seconds"),
                "源面没能吃到字符串字面量里的自建缓存键：" + ids);
        assertTrue(ids.stream().noneMatch(s -> s.contains("legacy.cache.ttl_ms")),
                "行注释没被剥掉：注释里的键被计入会造成误红");
        assertTrue(ids.stream().noneMatch(s -> s.contains("demo.cache.ttl_seconds\"")),
                "Javadoc 没被剥掉（同上，误红源）");
        assertTrue(ids.stream().noneMatch(s -> s.endsWith("|cacheTtlMinutes")),
                "驼峰业务 JSON 载荷键被错误纳入管辖（应只认点分/下划线配置键）：" + ids);
    }

    @Test
    @DisplayName("键形态判据自证：接受/拒绝样例")
    void keyShapeRuleExcludesCamelCasePayloadKeys() {
        assertTrue(isCacheConfigKey("dw.cache.ttl_seconds"));
        assertTrue(isCacheConfigKey("ecos.cache.default_ttl_seconds"));
        assertTrue(isCacheConfigKey("security.policy.decision.cache.ttl-ms"));
        assertTrue(isCacheConfigKey("cache_ttl_seconds"));
        assertTrue(!isCacheConfigKey("cacheTtlMinutes"), "驼峰载荷键不属配置键形态");
        assertTrue(!isCacheConfigKey("is_cache_hit, caller_id"), "含逗号的 SQL 片段不属配置键形态");
        assertTrue(!isCacheConfigKey("cache"), "无时效/开关词的裸 cache 不算配置键");
        assertTrue(!isCacheConfigKey("ecos.cache.env"), "无时效/开关词：底座环境段键由属主面前缀腿单独管");
    }

    // ================= 扫描实现 =================

    private record Hit(String face, String file, String key, String value) {
        String bareKey() {
            String k = key;
            if (k.startsWith("${")) {
                k = k.substring(2);
                k = k.substring(0, Math.min(indexOrEnd(k, ':'), indexOrEnd(k, '}')));
            }
            return k;
        }

        String id() {
            return "B".equals(face) || "C".equals(face) ? face + "|" + file + "|" + key : face + "|" + file + "|" + key + "|" + value;
        }
    }

    private static final class Faces {
        final List<Hit> hits = new ArrayList<>();
        final Set<String> resourceFiles = new LinkedHashSet<>();
        int mainJavaFiles;
        int resourceHits() {
            return (int) hits.stream().filter(h -> "A".equals(h.face)).count();
        }

        List<Hit> all() {
            return hits;
        }
    }

    private static Faces scan(Path root) throws IOException {
        Faces f = new Faces();
        scanResources(root, f);
        scanJava(root, f);
        scanSqlSeed(root, f);
        return f;
    }

    private static void scanResources(Path root, Faces f) throws IOException {
        Path base = root.resolve("ecos_backend");
        if (!Files.isDirectory(base)) {
            return;
        }
        for (Path p : walk(base)) {
            String rel = rel(root, p);
            if (!rel.matches(".*/src/main/resources/.*\\.(yml|yaml|properties)$")) {
                continue;
            }
            f.resourceFiles.add(rel);
            List<String> lines = Files.readAllLines(p, StandardCharsets.UTF_8);
            List<int[]> indents = new ArrayList<>();
            List<String> keys = new ArrayList<>();
            for (String raw : lines) {
                if (raw.isBlank() || raw.trim().startsWith("#")) {
                    continue;
                }
                int indent = raw.length() - raw.stripLeading().length();
                String body = raw.trim();
                while (!indents.isEmpty() && indents.get(indents.size() - 1)[0] >= indent) {
                    indents.remove(indents.size() - 1);
                    keys.remove(keys.size() - 1);
                }
                Matcher m = Pattern.compile("([A-Za-z0-9_.\\-]+)\\s*:\\s*(.*)$").matcher(body);
                if (!m.find()) {
                    continue;
                }
                String key = m.group(1);
                String value = m.group(2).replaceAll("\\s+#.*$", "").trim();
                if (value.isEmpty()) {
                    indents.add(new int[] { indent });
                    keys.add(key);
                    continue;
                }
                String prefix = String.join(".", keys);
                String dotted = prefix.isEmpty() ? key : prefix + "." + key;
                if (isCacheConfigKey(dotted)) {
                    f.hits.add(new Hit("A", rel, dotted, value));
                }
            }
        }
    }

    private static void scanJava(Path root, Faces f) throws IOException {
        Path base = root.resolve("ecos_backend");
        if (!Files.isDirectory(base)) {
            return;
        }
        for (Path p : walk(base)) {
            String rel = rel(root, p);
            if (!rel.matches(".*/src/main/java/.*\\.java$")) {
                continue;
            }
            f.mainJavaFiles++;
            String text = stripComments(Files.readString(p, StandardCharsets.UTF_8));
            Matcher m = Pattern.compile("\"([^\"\\n]{2,90})\"").matcher(text);
            while (m.find()) {
                String lit = m.group(1);
                if (isCacheConfigKey(lit)) {
                    // 白名单按字面量原文记（含 ${...} 包装形态）；裸键形态由 bareKey() 供属主腿判前缀
                    f.hits.add(new Hit("B", rel, lit, ""));
                }
            }
        }
    }

    private static void scanSqlSeed(Path root, Faces f) throws IOException {
        Path base = root.resolve("docs/50-db_script/02-flyway-migration/gateway");
        if (!Files.isDirectory(base)) {
            return;
        }
        for (Path p : walk(base)) {
            String rel = rel(root, p);
            if (!rel.endsWith(".sql")) {
                continue;
            }
            Matcher m = Pattern.compile("'([^'\\n]{2,90})'").matcher(Files.readString(p, StandardCharsets.UTF_8));
            while (m.find()) {
                String lit = m.group(1);
                if (isCacheConfigKey(lit)) {
                    f.hits.add(new Hit("C", rel, lit, ""));
                }
            }
        }
    }

    /** 配置键形态判据（见类注释）。 */
    private static boolean isCacheConfigKey(String raw) {
        if (raw == null || raw.isEmpty()) {
            return false;
        }
        String k = raw;
        if (k.startsWith("${")) {
            k = k.substring(2);
            int colon = indexOrEnd(k, ':');
            int brace = indexOrEnd(k, '}');
            k = k.substring(0, Math.min(colon, brace));
        }
        if (!KEY_SHAPE.matcher(k).matches()) {
            return false;
        }
        // 分隔符认 . _ - 三种：cfg-* 命名的种子行 id 形态上就是横杠，漏掉会造成「白名单塞不进」的假红
        if (k.indexOf('.') < 0 && k.indexOf('_') < 0 && k.indexOf('-') < 0) {
            return false;
        }
        return k.toLowerCase(Locale.ROOT).contains("cache") && VALUE_TOKEN.matcher(k).find();
    }

    private static int indexOrEnd(String s, char c) {
        int i = s.indexOf(c);
        return i < 0 ? s.length() : i;
    }

    /** 剥 // 与块注释（与仓扫门族同法：注释里的键面不计，避免误红）。 */
    private static String stripComments(String src) {
        StringBuilder out = new StringBuilder(src.length());
        boolean inBlock = false;
        boolean inStr = false;
        boolean inChar = false;
        char prev = 0;
        for (int i = 0; i < src.length(); i++) {
            char c = src.charAt(i);
            char n = i + 1 < src.length() ? src.charAt(i + 1) : 0;
            if (inBlock) {
                if (c == '*' && n == '/') {
                    inBlock = false;
                    i++;
                } else if (c == '\n') {
                    out.append('\n');
                }
                prev = 0;
                continue;
            }
            if (inStr) {
                out.append(c);
                if (c == '"' && prev != '\\') {
                    inStr = false;
                }
                prev = c;
                continue;
            }
            if (inChar) {
                out.append(c);
                if (c == '\'' && prev != '\\') {
                    inChar = false;
                }
                prev = c;
                continue;
            }
            if (c == '"') {
                inStr = true;
                out.append(c);
                prev = c;
                continue;
            }
            if (c == '\'') {
                inChar = true;
                out.append(c);
                prev = c;
                continue;
            }
            if (c == '/' && n == '/') {
                while (i < src.length() && src.charAt(i) != '\n') {
                    i++;
                }
                out.append('\n');
                prev = 0;
                continue;
            }
            if (c == '/' && n == '*') {
                inBlock = true;
                i++;
                prev = 0;
                continue;
            }
            out.append(c);
            prev = c;
        }
        return out.toString();
    }

    private static List<Path> walk(Path dir) throws IOException {
        try (Stream<Path> s = Files.walk(dir)) {
            return s.filter(Files::isRegularFile)
                    .filter(p -> {
                        String t = p.toString().replace('\\', '/');
                        return !t.contains("/target/") && !t.contains("/archive/") && !t.contains("/node_modules/");
                    })
                    .toList();
        }
    }

    private static String rel(Path root, Path p) {
        return root.relativize(p).toString().replace('\\', '/');
    }

    /** Surefire CWD 漂移：向上探测同时含 ecos_backend/ 与 ecos-docker/ 的仓库根。 */
    private static Path repoRoot() {
        Path cur = Paths.get("").toAbsolutePath().normalize();
        for (int i = 0; i < 8 && cur != null; i++) {
            if (Files.isDirectory(cur.resolve("ecos_backend")) && Files.isDirectory(cur.resolve("ecos-docker"))) {
                return cur;
            }
            cur = cur.getParent();
        }
        throw new IllegalStateException("未找到仓库根，CWD=" + Paths.get("").toAbsolutePath());
    }
}
