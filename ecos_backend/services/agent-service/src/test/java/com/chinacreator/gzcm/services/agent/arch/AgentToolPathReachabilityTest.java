package com.chinacreator.gzcm.services.agent.arch;

import com.chinacreator.gzcm.services.agent.runtime.toolrouter.AgentToolEndpointResolver;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * <p><b>D-B4-48 的另一面（#142/B4-3e-11 已关账）</b>：{@link ServiceEndpointResolver} 的查表键是
 * route-manifest 的 {@code owner} 字段而不是 URL 前缀，所以工具族的 {@code /api/v1/ecos}、
 * {@code /api/v1/engine/ontology}、{@code /api/v1/policy-engine} 三根前缀即使一条都没登记，
 * 基址照样解析得出来（属主名由调用方在 {@link AgentToolEndpointResolver} 里声明）——
 * 门禁看着全绿，族→属主的映射却只存在于注释里，切流时无人能核对打到 18083 的请求是否真由
 * buszhi 承流。本批按<b>子族</b>补登（{@code /api/v1/ecos/entities}、{@code /api/v1/ecos/ontologies}、
 * {@code /api/v1/engine/ontology} → {@code buszhi}；{@code /api/v1/policy-engine} → {@code sysman}），
 * <b>不登记整根 {@code /api/v1/ecos}</b>：实测该根由 ontology-engine-impl 与 workspace-impl
 * （{@code ObjectStateMachineController} 的 {@code /objects}、{@code /state-machine} 子族）双宿主
 * 同根不同属主，判给单一属主就是一句谎。关账后本门不再做"缺口棘轮"，改成两条正向不变式：
 * ①每条工具路径的<b>最长匹配登记前缀</b>其 owner 必须等于该工具实际调用的解析器访问器属主；
 * ②同一 owner 的多条 entry 的 artifact/route/ports 必须一致（resolver 按 owner 装 HashMap，
 * 后写覆盖，不一致会静默漂移）。清单为空＝零容忍。</p>
 */
class AgentToolPathReachabilityTest {

    /** 工具源码里的路径常量声明形态（收口后基址与路径分离，路径必须以常量声明）。 */
    private static final Pattern TOOL_PATH_DECL = Pattern.compile(
            "\\bstatic final String (PATH_\\w+)\\s*=\\s*\"([^\"]+)\"");

    /** 类级前缀：{@code @RequestMapping("...")} 或 {@code @RequestMapping({"...", "..."})} 中的<b>全部字面量</b>。
     *
     * <p>{@code #146j/B4-3e-91} 首跑时"{@code MetadataController}
     * {@code @RequestMapping({"/api/datanet/metadata","/api/v1/datanet/metadata"})}}"
     * 首字面量是 bare 形态、v1 形态在第二位，而工具腿声明的是 v1 形态——旧判据只取
     * 第一个字面量 ⇒ 把真实活着的 {@code /api/v1/datanet/metadata/fields/{id}} 判成
     * 幻影路径（D-B4-47 复发假红）。<b>订正为穷举式</b>：把 {@code @RequestMapping}
     * 参数列表里出现的全部字符串字面量都当类级前缀候选（与网关实测行为对齐：
     * Spring {@code @RequestMapping} 数组字面量<b>全部</b>注册到同一 Controller）。
     * 单字面量 Controller 的可达集合与此前完全一致；多字面量 Controller 的可达
     * 集合成为严格超集，既不新增幻影决定（v1 之前不可达也不会变可达），
     * 只是消掉"<b>同一 Controller 有两种活路径、门只看见一种</b>"这一族盲区。</p>
     */
    private static final Pattern CLASS_LEVEL_MAPPING = Pattern.compile(
            "@RequestMapping\\s*\\(\\s*([^)]*)\\)");

    /** 方法级映射：动词注解只取第一个字面量（与既有 route-rewrite-lint 口径一致）。 */
    private static final Pattern METHOD_LEVEL_MAPPING = Pattern.compile(
            "@(?:Get|Post|Put|Delete|Patch)Mapping\\s*\\(\\s*\\{?\\s*\"([^\"]+)\"");

    /** 工具取基址的访问器调用形态：一个访问器唯一对应一个属主，族→属主映射由此实证而非注释。
     * {@code knowledgeBaseUrl}（属主 {@code dccheng}）随 #146j/B4-3e-91 收口 D-B4-49 只读腿入册。 */
    private static final Pattern RESOLVER_ACCESSOR_CALL = Pattern.compile(
            "\\bendpoints\\.(securityBaseUrl|auditBaseUrl|ontologyBaseUrl|dataBaseUrl|knowledgeBaseUrl)\\(\\)");

    /**
     * D-B4-47 在册幻影路径清单：<b>#142/B4-3e-10 关账后为空</b>（原唯一一项
     * {@code /api/v1/ecos/ontology-mappings} 已改指 {@code /api/v1/ontology/mappings/validate}）。
     * 零容忍——任何工具声明无 Controller 服务的路径即红，包括退回上一条死路径。
     */
    private static final Set<String> KNOWN_PHANTOM_PATHS = Set.of();

    /** 扫描面阈值守卫：全仓 Controller 路径数低于此值＝扫错了根，不得把空集读成"路径都不存在"或"都存在"。 */
    private static final int MIN_CONTROLLER_PATHS = 300;

    /**
     * route-manifest 条目数下限（#142/B4-3e-11 补登 4 条后实测 19 条，只增不减）：
     * 解析器口径出错、文件被截断或读错根时，条目数会掉到下限以下——此时不得让
     * 「最长前缀匹配不到」被读成"这些路径全都合法"。
     */
    private static final int MIN_MANIFEST_ENTRIES = 19;

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
        Set<String> owners = new TreeSet<>();
        for (ManifestEntry e : manifestEntries()) {
            owners.add(e.owner());
        }
        for (String owner : List.of(AgentToolEndpointResolver.SYSMAN_ROUTE_OWNER,
                AgentToolEndpointResolver.ONTOLOGY_ROUTE_OWNER,
                AgentToolEndpointResolver.DATA_ROUTE_OWNER,
                AgentToolEndpointResolver.KNOWLEDGE_ROUTE_OWNER)) {
            assertTrue(owners.contains(owner),
                    "解析器声明的属主 " + owner + " 不在 route-manifest 的 owner 集合 " + owners
                            + " 里 ⇒ 该族基址恒为空，工具腿会静默走『未发起调用』分支");
        }
    }

    /**
     * D-B4-48 关账后的正向不变式：工具每条路径都要能在 route-manifest 找到<b>最长匹配前缀</b>，
     * 且该前缀登记的 owner 必须等于该工具实际调用的解析器访问器所属 owner。
     * 两个方向各自堵一种假绿——「找不到前缀」＝族→属主仍只活在注释里；
     * 「owner 不匹配」＝拿 buszhi 的基址去打 sysman 承流的族（切流后打到不承流的端口）。
     */
    @Test
    @Timeout(value = 120)
    @DisplayName("每条工具路径的最长匹配登记前缀，其 owner 必须等于该工具访问器的属主（D-B4-48 关账）")
    void everyToolPathIsRegisteredUnderTheOwnerItsAccessorDeclares() throws IOException {
        List<ManifestEntry> entries = manifestEntries();
        Map<String, String> toolPaths = toolPathConstants();
        Map<String, String> ownersByToolFile = toolAccessorOwners();

        List<String> violations = new ArrayList<>();
        for (Map.Entry<String, String> e : toolPaths.entrySet()) {
            String file = e.getKey().substring(0, e.getKey().indexOf('#'));
            String declaredOwner = ownersByToolFile.get(file);
            assertTrue(declaredOwner != null,
                    "工具 " + file + " 声明了路径常量却没有任何解析器访问器调用 ⇒ 无从核对族属主");
            ManifestEntry matched = longestMatchingPrefix(entries, e.getValue());
            if (matched == null) {
                violations.add(e.getValue() + "（" + file + "）在 route-manifest 无任何登记前缀");
            } else if (!matched.owner().equals(declaredOwner)) {
                violations.add(e.getValue() + " 最长匹配前缀 " + matched.prefix() + " 登记属主="
                        + matched.owner() + "，而 " + file + " 取基址用的访问器属主=" + declaredOwner);
            }
        }
        assertEquals(List.of(), violations,
                "agent 工具族到属主的映射与 route-manifest 不符（D-B4-48 复发）："
                        + "新增未登记前缀＝族属主只活在调用方注释里，owner 不匹配＝基址与承流服务错位");
    }

    /**
     * resolver 按 {@code owner} 装 HashMap，同一属主的多条 entry 是<b>后写覆盖</b>关系：
     * artifact/route/ports 不一致时，生效的是数组里最后一条，前面的登记静默失效。
     * 本腿把"同属主必同寻址参数"钉成不变式，防止补登记前缀时顺手写错端口。
     */
    @Test
    @Timeout(value = 60)
    @DisplayName("同一 owner 的多条 entry 必须 artifact/route/ports 全一致（HashMap 后写覆盖不可静默漂移）")
    void entriesOfTheSameOwnerAgreeOnAddressingParameters() throws IOException {
        Map<String, ManifestEntry> first = new LinkedHashMap<>();
        List<String> conflicts = new ArrayList<>();
        for (ManifestEntry e : manifestEntries()) {
            ManifestEntry ref = first.putIfAbsent(e.owner(), e);
            if (ref != null && !ref.sameAddressingAs(e)) {
                conflicts.add("owner=" + e.owner() + " 的 " + e.prefix() + " 与 " + ref.prefix()
                        + " 寻址参数不一致（artifact/route/ports）");
            }
        }
        assertEquals(List.of(), conflicts,
                "route-manifest 里同一属主登记了两套寻址参数：ServiceEndpointResolver 按 owner 装表，"
                        + "后写覆盖 ⇒ 只有一条真正生效，其余是幻影登记");
    }

    /**
     * 反向夹具：合成一个不存在的路径必须被判成幻影（证明判据不是恒真）。
     * 走同一条私有比较逻辑，不依赖真实源码。
     */
    @Test
    @DisplayName("合成幻影路径能变红（可达性与前缀匹配判据均非恒真）")
    void syntheticPhantomIsDetected() throws IOException {
        Set<String> controllerPaths = controllerPaths();
        assertTrue(!controllerPaths.contains(normalize("/api/v1/definitely/not/registered")),
                "合成幻影竟被判可达 ⇒ 可达性判据恒真，本门是假绿");
        assertTrue(controllerPaths.contains("/api/v1/ecos/ontologies"),
                "扫描器读出的真实路径集里找不到 /api/v1/ecos/ontologies ⇒ 解析器口径坏了");

        // 正向夹具：真实登记过的路径必须能匹配到前缀（否则上一条断言会因匹配器全空而假过）
        assertTrue(longestMatchingPrefix(manifestEntries(), "/api/v1/ontology/mappings/validate") != null,
                "已登记族根匹配不到 ⇒ 最长前缀匹配器恒空，属主一致性腿会变成假的零违例");
        // 多字面量类前缀夹具（#146j/B4-3e-91 订正锚）：MetadataController 声明
        // @RequestMapping({"/api/datanet/metadata", "/api/v1/datanet/metadata"})，
        // v1 形态在第二位 —— 旧判据只取首字面量会把它判成幻影（D-B4-47 假红）。
        assertTrue(controllerPaths().contains("/api/v1/datanet/metadata/fields/{}"),
                "第二字面量类前缀的后代数还在 ⇒ 多形态类前缀门无攻击点，"
                        + "GetTableSchemaTool 的 v1 腿会被误判幻影");
        // 反向夹具：段边界不得被当成前缀命中（naive startsWith 会把 /api/v1/ontology 命中到 /api/v1/ontologyx）
        assertTrue(longestMatchingPrefix(manifestEntries(), "/api/v1/ontologyx/actions") == null,
                "合成越界路径被判成已登记 ⇒ 前缀匹配未对齐段边界，任何同前缀字面量都能蒙过属主核对");
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

    /** 全仓主源码 Controller 的「类级前缀 + 方法级路径」全集（模板变量归一为 {@code {}}）。
     * 类级前缀取 {@code @RequestMapping} 参数列表里的<b>全部</b>字面量（两形态并存
     * 的 Controller 两形态都注册，漏一种即把真实活路径判成幻影——见
     * {@link #CLASS_LEVEL_MAPPING} 判锐注）。 */
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
                List<String> prefixes = classLevelPrefixLiterals(code);
                if (prefixes.isEmpty()) {
                    continue;
                }
                Matcher m = METHOD_LEVEL_MAPPING.matcher(code);
                while (m.find()) {
                    for (String prefix : prefixes) {
                        out.add(normalize(join(prefix, m.group(1))));
                    }
                }
                // 类级前缀本身也可能就是一个可寻址端点（无方法级注解的极简形态不在此面）
                for (String prefix : prefixes) {
                    out.add(normalize(prefix));
                }
            }
        }
        return out;
    }

    /** {@code @RequestMapping} 参数列表里的全部字符串字面量（判据见 {@link #CLASS_LEVEL_MAPPING}）。 */
    private static List<String> classLevelPrefixLiterals(String code) {
        List<String> out = new ArrayList<>();
        Matcher m = CLASS_LEVEL_MAPPING.matcher(code);
        if (m.find()) {
            Matcher lit = Pattern.compile("\"([^\"]*)\"").matcher(m.group(1));
            while (lit.find()) {
                out.add(lit.group(1));
            }
        }
        return out;
    }

    /**
     * 读 route-manifest 单源的全部条目（prefix + owner + 寻址参数）。
     * 走 JSON 解析而不是"前缀清单/属主清单各抓一遍"：两条独立清单会丢掉配对关系，
     * 而本门的判据恰好就是配对（哪条前缀挂在哪个属主下）。
     */
    private static List<ManifestEntry> manifestEntries() throws IOException {
        Path manifest = backendRoot().resolve(
                "runtime/runtime-access/src/main/resources/route-manifest.json");
        assertTrue(Files.isRegularFile(manifest), "route-manifest 单源不存在: " + manifest);
        JsonNode entries = new ObjectMapper()
                .readTree(Files.readString(manifest, StandardCharsets.UTF_8))
                .path("entries");
        assertTrue(entries.isArray() && entries.size() >= MIN_MANIFEST_ENTRIES,
                "route-manifest 条目扫描面异常（实扫 " + (entries.isArray() ? entries.size() : -1)
                        + " 条 < 下限 " + MIN_MANIFEST_ENTRIES + "），不得对可疑扫描面判定前缀可达性");
        List<ManifestEntry> out = new ArrayList<>();
        for (JsonNode e : entries) {
            assertTrue(e.path("prefix").isTextual() && e.path("owner").isTextual(),
                    "route-manifest 有条目缺 prefix/owner: " + e);
            JsonNode ports = e.path("ports");
            out.add(new ManifestEntry(e.path("prefix").asText(), e.path("owner").asText(),
                    e.path("artifact").asText(), e.path("route").asText(),
                    ports.path("monolith").asInt(-1), ports.path("service").asInt(-1)));
        }
        return out;
    }

    /**
     * 最长匹配登记前缀；<b>按段边界对齐</b>（完全相等或后接 {@code /}），
     * 免得 {@code /api/v1/ec} 之类同前缀字面量蒙过属主核对。匹配不到返回 null。
     */
    private static ManifestEntry longestMatchingPrefix(List<ManifestEntry> entries, String path) {
        ManifestEntry best = null;
        for (ManifestEntry e : entries) {
            String p = e.prefix();
            boolean hit = path.equals(p) || path.startsWith(p.endsWith("/") ? p : p + "/");
            if (hit && (best == null || p.length() > best.prefix().length())) {
                best = e;
            }
        }
        return best;
    }

    /**
     * 工具文件 → 该文件实际调用的解析器访问器所声明的<b>属主名</b>（与
     * {@link AgentToolEndpointResolver} 的 owner 常量同源，不另写字面量）。
     * 一个文件里出现两个不同属主的访问器即红：那说明族归属又开始靠调用方现场决定了。
     */
    private static Map<String, String> toolAccessorOwners() throws IOException {
        Map<String, String> out = new LinkedHashMap<>();
        for (Path p : filesIn(toolsDir())) {
            Matcher m = RESOLVER_ACCESSOR_CALL.matcher(Files.readString(p, StandardCharsets.UTF_8));
            Set<String> owners = new TreeSet<>();
            while (m.find()) {
                owners.add(ownerOfAccessor(m.group(1)));
            }
            if (owners.size() > 1) {
                throw new IllegalStateException("工具 " + p.getFileName() + " 同时调用两个属主的基址访问器 "
                        + owners + "，族属主归属不明，本门拒绝判定");
            }
            if (!owners.isEmpty()) {
                out.put(p.getFileName().toString(), owners.iterator().next());
            }
        }
        return out;
    }

    private static String ownerOfAccessor(String accessor) {
        return switch (accessor) {
            case "securityBaseUrl", "auditBaseUrl" -> AgentToolEndpointResolver.SYSMAN_ROUTE_OWNER;
            case "ontologyBaseUrl" -> AgentToolEndpointResolver.ONTOLOGY_ROUTE_OWNER;
            case "dataBaseUrl" -> AgentToolEndpointResolver.DATA_ROUTE_OWNER;
            case "knowledgeBaseUrl" -> AgentToolEndpointResolver.KNOWLEDGE_ROUTE_OWNER;
            default -> throw new IllegalStateException("未知解析器访问器: " + accessor);
        };
    }

    /** route-manifest 一条登记的寻址参数（同属主多条目必须逐项一致）。 */
    private record ManifestEntry(String prefix, String owner, String artifact, String route,
                                 int monolithPort, int servicePort) {

        boolean sameAddressingAs(ManifestEntry o) {
            return artifact.equals(o.artifact) && route.equals(o.route)
                    && monolithPort == o.monolithPort && servicePort == o.servicePort;
        }
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
