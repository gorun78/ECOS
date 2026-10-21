package com.chinacreator.gzcm.services.agent.arch;

import com.chinacreator.gzcm.services.agent.runtime.toolrouter.AgentToolEndpointResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * agent 工具腿<b>端点路径可达性</b>门禁（#142/B4-3e-9，登记差距 <b>D-B4-47</b>／<b>D-B4-48</b>）。
 *
 * <p><b>为什么基址收口必须同批钉路径</b>：把 {@code http://localhost:8080} 换成属主解析器
 * 只解决"打到哪台机器"，不解决"这台机器上有没有这个端点"。若某条腿的路径在全仓没有
 * 任何 Controller 服务，那么基址单源化之后它依然恒 404，而门禁看着比改前更"合规"——
 * 这正是本批要堵的假绿面。实测本域 9 条腿里曾有 <b>1 条幻影路径</b>（{@code ValidateMappingTool}
 * 的 {@code /api/v1/ecos/ontology-mappings}，全仓无 Controller），登记为 <b>D-B4-47</b>，
 * 已在 <b>#142/B4-3e-10 关账</b>：本体域存在语义对口的真实端点
 * {@code OntologyMappingController#validateMappings}＝{@code POST /api/v1/ontology/mappings/validate}
 * （入参 DTO 支持 mappingId／内联 datasetId+fieldMappings／entityCode／全量，与工具整包透传的
 * {@code params} 形态一致），故裁决为<b>改指既有端点</b>而非新增 API。幻影清单随之<b>清空</b>：
 * 此后任何工具声明无 Controller 服务的路径即红（清单只准为空，"补齐不删项"的齿由同一断言承担）。</p>
 *
 * <p><b>D-B4-48 的另一面</b>：{@link ServiceEndpointResolver} 的查表键是 route-manifest 的
 * {@code owner} 字段而不是 URL 前缀，所以 {@code /api/v1/ecos}、{@code /api/v1/engine/ontology}、
 * {@code /api/v1/policy-engine} 三族虽然分别由 ontology-engine-impl（属主 {@code buszhi}）与
 * security-engine-impl（属主 {@code sysman}）提供，manifest 里却一条都没有登记 ⇒ 族到属主的映射
 * 只存在于调用方的口头声明里。本门把"未登记前缀清单"钉成实测三条且只准缩短：补进 manifest 后
 * 清单变空、门禁翻红一次，逼 ledger 同步关账。</p>
 */
class AgentToolPathReachabilityTest {

    /** 工具源码里的路径常量声明形态（收口后基址与路径分离，路径必须以常量声明）。 */
    private static final Pattern TOOL_PATH_DECL = Pattern.compile(
            "\\bstatic final String (PATH_\\w+)\\s*=\\s*\"([^\"]+)\"");

    /** 类级前缀：{@code @RequestMapping("...")} 或 {@code @RequestMapping({"...", "..."})}。 */
    private static final Pattern CLASS_LEVEL_MAPPING = Pattern.compile(
            "@RequestMapping\\s*\\(\\s*\\{?\\s*\"([^\"]+)\"");

    /** 方法级映射：动词注解只取第一个字面量（与既有 route-rewrite-lint 口径一致）。 */
    private static final Pattern METHOD_LEVEL_MAPPING = Pattern.compile(
            "@(?:Get|Post|Put|Delete|Patch)Mapping\\s*\\(\\s*\\{?\\s*\"([^\"]+)\"");

    /** route-manifest 属主名抽取。 */
    private static final Pattern MANIFEST_OWNER = Pattern.compile("\"owner\"\\s*:\\s*\"([^\"]+)\"");

    /** route-manifest 前缀抽取。 */
    private static final Pattern MANIFEST_PREFIX = Pattern.compile("\"prefix\"\\s*:\\s*\"([^\"]+)\"");

    /**
     * D-B4-47 在册幻影路径清单：<b>#142/B4-3e-10 关账后为空</b>（原唯一一项
     * {@code /api/v1/ecos/ontology-mappings} 已改指 {@code /api/v1/ontology/mappings/validate}）。
     * 零容忍——任何工具声明无 Controller 服务的路径即红，包括退回上一条死路径。
     */
    private static final Set<String> KNOWN_PHANTOM_PATHS = Set.of();

    /**
     * D-B4-48 在册未登记前缀清单（实测 3 条）：工具族根在 route-manifest 里没有前缀条目，
     * 属主只能由调用方声明。只准缩短，补进 manifest 后须同步删项。
     */
    private static final Set<String> KNOWN_UNREGISTERED_PREFIXES = Set.of(
            "/api/v1/ecos",
            "/api/v1/engine/ontology",
            "/api/v1/policy-engine");

    /** 扫描面阈值守卫：全仓 Controller 路径数低于此值＝扫错了根，不得把空集读成"路径都不存在"或"都存在"。 */
    private static final int MIN_CONTROLLER_PATHS = 300;

    @Test
    @Timeout(value = 180)
    @DisplayName("工具路径必须真有 Controller 服务；幻影清单已关账为空（D-B4-47）")
    void toolPathsResolveToRealControllersUnlessRegisteredAsPhantom() throws IOException {
        Map<String, String> toolPaths = toolPathConstants();
        Set<String> controllerPaths = controllerPaths();
        assertTrue(controllerPaths.size() >= MIN_CONTROLLER_PATHS,
                "Controller 路径扫描面异常（实扫 " + controllerPaths.size() + " 条 < 阈值 "
                        + MIN_CONTROLLER_PATHS + "），本门禁不得对可疑扫描面判定可达性");

        Set<String> phantoms = new TreeSet<>();
        for (Map.Entry<String, String> e : toolPaths.entrySet()) {
            if (!controllerPaths.contains(normalize(e.getValue()))) {
                phantoms.add(e.getValue());
            }
        }
        assertEquals(KNOWN_PHANTOM_PATHS, phantoms,
                "agent 工具出现幻影路径：清单在册项已在 #142/B4-3e-10 关账为空，"
                        + "非空即等于又一条恒 404 的腿（D-B4-47 复发或新增）。实测=" + phantoms);
        assertTrue(toolPaths.size() >= 9,
                "工具路径常量扫描面异常（实扫 " + toolPaths.size() + " 条），不得对空清单判可达性");
    }

    @Test
    @Timeout(value = 60)
    @DisplayName("D-B4-47 关账形态钉死：校验腿走 mappings/validate，不得退回死路径")
    void validateMappingToolUsesTheRealValidationEndpoint() throws IOException {
        Map<String, String> toolPaths = toolPathConstants();
        assertTrue(toolPaths.containsKey("ValidateMappingTool.java#PATH_ONTOLOGY_MAPPINGS_VALIDATE"),
                "ValidateMappingTool 的路径常量不再是 PATH_ONTOLOGY_MAPPINGS_VALIDATE ⇒ "
                        + "D-B4-47 的关账形态被拆掉（删常量不等于修缺陷）");
        assertEquals("/api/v1/ontology/mappings/validate",
                toolPaths.get("ValidateMappingTool.java#PATH_ONTOLOGY_MAPPINGS_VALIDATE"),
                "映射校验腿未指向实测存在的 OntologyMappingController#validateMappings");
    }

    @Test
    @Timeout(value = 120)
    @DisplayName("工具族用到的属主必须真在 route-manifest 登记（否则基址恒解析为空）")
    void resolverOwnersAreRegisteredInManifest() throws IOException {
        Set<String> owners = manifestOwners();
        for (String owner : List.of(AgentToolEndpointResolver.SYSMAN_ROUTE_OWNER,
                AgentToolEndpointResolver.ONTOLOGY_ROUTE_OWNER)) {
            assertTrue(owners.contains(owner),
                    "解析器声明的属主 " + owner + " 不在 route-manifest 的 owner 集合 " + owners
                            + " 里 ⇒ 该族基址恒为空，工具腿会静默走『未发起调用』分支");
        }
    }

    @Test
    @Timeout(value = 120)
    @DisplayName("工具族用到的属主必须真在 route-manifest 登记，前缀缺口只准在册这三条（D-B4-48）")
    void unregisteredFamilyPrefixesAreRatcheted() throws IOException {
        Set<String> prefixes = manifestPrefixes();
        Set<String> unregistered = new TreeSet<>();
        for (String toolPath : toolPathConstants().values()) {
            String root = familyRoot(toolPath);
            boolean registered = prefixes.stream().anyMatch(root::startsWith);
            if (!registered) {
                unregistered.add(root);
            }
        }
        assertEquals(KNOWN_UNREGISTERED_PREFIXES, unregistered,
                "route-manifest 前缀登记缺口与 D-B4-48 在册清单不符（实测=" + unregistered
                        + "）。补齐一条就删一项并同步台账，别让族→属主映射继续只活在调用方注释里");
    }

    /**
     * 反向夹具：合成一个不存在的路径必须被判成幻影（证明判据不是恒真）。
     * 走同一条私有比较逻辑，不依赖真实源码。
     */
    @Test
    @DisplayName("合成幻影路径能变红（可达性判据非恒真）")
    void syntheticPhantomIsDetected() throws IOException {
        Set<String> controllerPaths = controllerPaths();
        assertTrue(!controllerPaths.contains(normalize("/api/v1/definitely/not/registered")),
                "合成幻影竟被判可达 ⇒ 可达性判据恒真，本门是假绿");
        assertTrue(controllerPaths.contains("/api/v1/ecos/ontologies"),
                "扫描器读出的真实路径集里找不到 /api/v1/ecos/ontologies ⇒ 解析器口径坏了");
    }

    // ---------- helpers ----------

    private static Map<String, String> toolPathConstants() throws IOException {
        Map<String, String> out = new LinkedHashMap<>();
        for (Path p : filesIn(toolsDir())) {
            Matcher m = TOOL_PATH_DECL.matcher(Files.readString(p, StandardCharsets.UTF_8));
            while (m.find()) {
                out.put(p.getFileName().toString() + "#" + m.group(1), m.group(2));
            }
        }
        return out;
    }

    /** 全仓主源码 Controller 的「类级前缀 + 方法级路径」全集（模板变量归一为 {@code {}}）。 */
    private static Set<String> controllerPaths() throws IOException {
        Set<String> out = new LinkedHashSet<>();
        Path backend = backendRoot();
        try (Stream<Path> walk = Files.walk(backend)) {
            List<Path> controllers = walk
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith("Controller.java"))
                    .filter(p -> !p.toString().replace('\\', '/').contains("/target/"))
                    .filter(p -> p.toString().replace('\\', '/').contains("/src/main/"))
                    .toList();
            for (Path c : controllers) {
                String code = Files.readString(c, StandardCharsets.UTF_8);
                String prefix = firstMatch(CLASS_LEVEL_MAPPING, code);
                if (prefix == null) {
                    continue;
                }
                Matcher m = METHOD_LEVEL_MAPPING.matcher(code);
                while (m.find()) {
                    out.add(normalize(join(prefix, m.group(1))));
                }
                // 类级前缀本身也可能就是一个可寻址端点（无方法级注解的极简形态不在此面）
                out.add(normalize(prefix));
            }
        }
        return out;
    }

    private static Set<String> manifestOwners() throws IOException {
        return literals(MANIFEST_OWNER);
    }

    private static Set<String> manifestPrefixes() throws IOException {
        return literals(MANIFEST_PREFIX);
    }

    private static Set<String> literals(Pattern pattern) throws IOException {
        String json = Files.readString(backendRoot()
                .resolve("runtime/runtime-access/src/main/resources/route-manifest.json"),
                StandardCharsets.UTF_8);
        Set<String> out = new LinkedHashSet<>();
        Matcher m = pattern.matcher(json);
        while (m.find()) {
            out.add(m.group(1));
        }
        assertTrue(!out.isEmpty(), "route-manifest 解析出空集合 ⇒ 判据不得对空面放行");
        return out;
    }

    /**
     * 族根＝前 3 段（{@code /api/v1/x}），{@code engine} 族取前 4 段（{@code /api/v1/engine/x}），
     * 与 route-manifest 现有前缀粒度一致。
     */
    private static String familyRoot(String path) {
        String[] seg = path.split("/");
        int take = "engine".equals(seg.length > 3 ? seg[3] : "") ? 4 : 3;
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < seg.length && i <= take; i++) {
            sb.append('/').append(seg[i]);
        }
        return sb.toString();
    }

    private static String join(String prefix, String suffix) {
        String p = prefix.endsWith("/") ? prefix.substring(0, prefix.length() - 1) : prefix;
        String s = suffix.startsWith("/") ? suffix : "/" + suffix;
        return p + s;
    }

    /** 归一：模板变量名不影响可寻址性，统一成 {@code {}}；顺带收掉重复斜杠。 */
    private static String normalize(String path) {
        return path.replaceAll("\\{[^}]*}", "{}").replaceAll("/{2,}", "/");
    }

    private static String firstMatch(Pattern p, String code) {
        Matcher m = p.matcher(code);
        return m.find() ? m.group(1) : null;
    }

    private static List<Path> filesIn(Path dir) throws IOException {
        List<Path> out = new ArrayList<>();
        try (Stream<Path> walk = Files.list(dir)) {
            walk.filter(f -> f.toString().endsWith(".java")).forEach(out::add);
        }
        return out;
    }

    private static Path toolsDir() {
        Path tools = backendRoot().resolve(
                "services/agent-service/src/main/java/com/chinacreator/gzcm/services/agent/runtime/toolrouter/tools");
        assertTrue(Files.isDirectory(tools), "工具源码目录不存在: " + tools);
        return tools;
    }

    private static Path backendRoot() {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (dir != null && !Files.isDirectory(dir.resolve("ecos_backend"))) {
            dir = dir.getParent();
        }
        assertTrue(dir != null, "上溯未找到仓库根，无法定位 ecos_backend");
        Path backend = dir.resolve("ecos_backend");
        assertTrue(Files.isDirectory(backend), "ecos_backend 根不存在: " + backend);
        return backend;
    }
}
