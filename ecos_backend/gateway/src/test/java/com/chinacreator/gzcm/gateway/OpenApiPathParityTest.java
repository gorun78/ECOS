package com.chinacreator.gzcm.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F07-15 / 详设-07 D-2 + D-4.4 —— 场景工作台「契约 ↔ Controller mapping 集合相等」门禁。
 *
 * <p><b>为什么需要本类（差距分析 §1.14 / 15.3 挂账项）</b>：{@code workspace/AGENTS.md:26}
 * 与详设-07 D-4.4 都把本类当作既成事实引用，但类此前<b>不存在</b>＝「虚假验收」传染源
 * （差距分析 §1.14 行、15.3 行 6/20 缺项之一）。按 R9② 裁决「缺的门禁补代码实现」落地，
 * 且本类是<b>静态可即时实装</b>那一档（不依赖功能批载体）。</p>
 *
 * <p><b>三方对齐（真集合相等，不是单向 presence）</b>，三个独立事实源：
 * <ol>
 *   <li><b>设计权威</b>：详设-07 §D-2 新增端点表（N1~N12，方法/路径/operationId 三列）；</li>
 *   <li><b>契约文档</b>：{@code docs/40-实现/SCENARIO_OPENAPI.md} 的 OpenAPI 片段
 *       （paths → method → operationId）；</li>
 *   <li><b>代码现实</b>：workspace 部署单元 {@code @RestController} 类的请求映射
 *       （类级 {@code @RequestMapping} 前缀 × 方法级 {@code @Get/Post/Put/Patch/DeleteMapping}）。</li>
 * </ol>
 * 断言：① ≡ ②（三元组集合相等）、② ⊆ ③（契约不得写代码里没有的端点＝幻影契约）、
 * ③ 的<b>契约属主类全量映射</b> ⊆ ②（属主类不得夹带未进契约的端点＝反向漏项）。
 * 第三向取「属主类闭包」而非全 workspace 端点集：契约片段按 D-4.1「只增不改」只承载
 * N1~N12，把 A1~A32 既有端点强拉进本片段属反向越界；而<b>恰好承载契约端点的那几个类
 * 是新端点的独占载体</b>（实测 6 个类共 12 条映射＝契约全集），故对它们做全量对账既严格
 * 又不越界。日后若在这 6 个类里加一条未登记端点，本门禁立刻变红。</p>
 *
 * <p><b>反空集设计（防解析器塌陷成假绿）</b>：{@link #parsersThemselvesAreTruthful()} 先钉
 * 三源各自解析出的条数（设计 12 / 契约 12 / workspace 映射 ≥ 60）与「yaml 内 operationId
 * token 数 = 解析条数」，任一源解析为 0 或条数与 token 数不等 ⇒ 直接 FAIL。
 * 没有这一步，"集合相等" 在两个空集上恒真＝本类最大的假绿风险。</p>
 *
 * <p><b>不主张可达性</b>：本类只校验「映射字面 ↔ 契约字面」，<b>不</b>断言端点在运行时可通
 * （三滤波器/切流可达性属 {@code GatewayRouteIntegrityTest} + 真容器 IT，
 * 详设-07 D-3 明文禁止静态门禁宣称可达性；口径同 F07-24 反身门禁）。
 * 同样地，状态码真值化（X-16/X-17）由 {@code ApiResponseTruthTest} 与
 * {@code docs/40-实现/check-scenario-openapi.mjs}（文档内 method/path/503 政策）负责，
 * 本类不重复声明，只补 mjs 明确让给后端的那一半职责。</p>
 *
 * <p><b>与详设-07 原文的两处口径差（登记而非掩盖）</b>：
 * <ol>
 *   <li>详设 line 529 说「反射 mapping 表」——本类实现为<b>源码静态扫描注解</b>而非
 *       Spring {@code RequestMappingHandlerMapping} 反射。理由：反射需要拉起 workspace
 *       上下文（属"需真实栈"档），而 F07-15/D-4.4 的不变量是纯字面对账，静态档可在
 *       无 DB/无端口的 CI 里恒跑；反射档若要与本类同判，须由真容器 IT 补，本类不冒充。</li>
 *   <li>详设 line 529 的具名用例 {@code #everyWorkspaceMappingHasAnOperationId} 已按该名字
 *       实装（否则设计里的验收命令 {@code mvn -Dtest=OpenApiPathParityTest#...} 仍是悬空引用）。
 *       Spring 映射注解本身不携带 operationId，故该用例的<b>可实现语义</b>=
 *       「契约属主类的每条映射都解析到契约里唯一且非空的 operationId」，
 *       而非"注解字段有 operationId"（该字段在 Java 侧不存在）。</li>
 * </ol></p>
 */
@DisplayName("F07-15：SCENARIO_OPENAPI 契约 ↔ workspace Controller 映射集合相等")
class OpenApiPathParityTest {

    /** 契约片段（F07-15 单源）。 */
    private static final String CONTRACT_REL = "docs/40-实现/SCENARIO_OPENAPI.md";

    /** 设计权威：详设-07 §D-2 表。 */
    private static final String DESIGN_REL =
            "docs/30-设计/详细设计-07-场景层-workspace场景工作台-2026-09-29.md";

    /** 代码现实：workspace 部署单元源码根（只扫 main，测试 harness 控制器不算对外面）。 */
    private static final Path CONTROLLER_ROOT_REL =
            Paths.get("ecos_backend/workspace/workspace-impl/src/main/java");

    /** 详设-07 D-2 交付条数（原文「12 条 N1~N12」）。 */
    private static final int D2_ENDPOINT_COUNT = 12;

    /** 契约端点的独占承载类数（实测；漂移即红，见类注释第三向说明）。 */
    private static final int CONTRACT_OWNER_CLASS_COUNT = 6;

    // ── 数据形态 ─────────────────────────────────────────

    /** 一条操作 = (HTTP 方法, 路径, operationId)；operationId 允许为 null（Controller 侧无此概念）。 */
    private record Operation(String method, String path, String operationId) {

        /** 方法+路径键：契约↔代码对账用。 */
        String endpoint() {
            return method + " " + path;
        }

        /** 三列全键：设计↔契约对账用。 */
        @Override
        public String toString() {
            return endpoint() + " :: " + operationId;
        }
    }

    /** Controller 侧一条映射，带来源文件（反向闭包按文件收口）。 */
    private record Mapping(String method, String path, String source) {

        String endpoint() {
            return method + " " + path;
        }

        Operation asOperation() {
            return new Operation(method, path, null);
        }
    }

    /** 解析诊断包：把「解析器自己是否可信」变成可断言的数据，而不是靠人眼看代码。 */
    private record ControllerScan(List<Mapping> mappings,
                                  List<String> ambiguousMethodless,
                                  List<String> unparsableArgs,
                                  int controllerFileCount) {}

    // ── 门禁 ────────────────────────────────────────────

    @Test
    @DisplayName("解析器自证：设计 12 行 / 契约 12 条（=yaml operationId token 数）/ workspace 映射 ≥60 条，任一塌陷即 FAIL")
    void parsersThemselvesAreTruthful() throws IOException {
        Path root = repoRoot();

        List<Operation> design = parseDesignD2(read(root.resolve(DESIGN_REL)));
        assertEquals(D2_ENDPOINT_COUNT, design.size(),
                "详设-07 D-2 表应解析出 12 行（N1~N12）；实得 " + design.size()
                        + " —— 解析式或表格形态漂移，本门禁会退化成空集对账");
        assertTrue(design.stream().allMatch(o -> o.path().startsWith("/api/v1/")),
                "D-2 每行路径必须是 /api/v1/ 绝对路径: " + design);
        assertTrue(design.stream().allMatch(o -> o.operationId() != null && !o.operationId().isBlank()),
                "D-2 每行必须带 operationId（缺失则三元组对账失去意义）");

        List<Operation> contract = parseContract(read(root.resolve(CONTRACT_REL)));
        assertEquals(D2_ENDPOINT_COUNT, contract.size(),
                "SCENARIO_OPENAPI.md 应解析出 12 条操作；实得 " + contract.size());
        String yaml = extractYamlBlock(read(root.resolve(CONTRACT_REL)));
        assertEquals(countOccurrences(yaml, "operationId:"), contract.size(),
                "yaml 里 operationId token 数必须等于解析条数（否则有条目被缩进式变化吞掉＝静默漏读）");

        ControllerScan scan = parseControllers(root);
        assertTrue(scan.controllerFileCount() >= 20,
                "workspace main 源码 @RestController 类数应 ≥20，实得 "
                        + scan.controllerFileCount() + "（源码根路径漂移？）");
        assertTrue(scan.mappings().size() >= 60,
                "workspace 映射总数应 ≥60（详设-07 D-1 附注：该部署单元 68 端点量级），实得 "
                        + scan.mappings().size() + " —— 注解解析塌陷");
        assertTrue(scan.ambiguousMethodless().isEmpty(),
                "方法级 @RequestMapping 必须显式 RequestMethod.*，否则映射方法不确定：\n"
                        + String.join("\n", scan.ambiguousMethodless()));
        assertTrue(scan.unparsableArgs().isEmpty(),
                "映射注解参数含非字面量（常量/拼接）时本门禁无法对账，须显式处理而不是跳过：\n"
                        + String.join("\n", scan.unparsableArgs()));
    }

    @Test
    @DisplayName("设计 D-2 三元组集合 ≡ 契约片段三元组集合（方法/路径/operationId 三列全等）")
    void designTableAndContractAreTheSameSet() throws IOException {
        Set<String> design = toSet(parseDesignD2(read(repoRoot().resolve(DESIGN_REL))));
        Set<String> contract = toSet(parseContract(read(repoRoot().resolve(CONTRACT_REL))));

        assertEquals(design, contract,
                "契约片段与设计 D-2 表不等（多＝幻影契约，少＝契约漏项）。"
                        + "只增不改前提下，任一侧改动必须同步另一侧并重开裁决");
    }

    @Test
    @DisplayName("契约 12 条逐条在 workspace Controller 映射中真实存在（禁幻影契约）")
    void everyContractedOperationHasALiveMapping() throws IOException {
        Set<String> live = new TreeSet<>();
        for (Mapping m : parseControllers(repoRoot()).mappings()) {
            live.add(m.endpoint());
        }
        List<String> phantom = new ArrayList<>();
        for (Operation o : parseContract(read(repoRoot().resolve(CONTRACT_REL)))) {
            if (!live.contains(o.endpoint())) {
                phantom.add(o.endpoint() + " (operationId=" + o.operationId() + ")");
            }
        }
        assertTrue(phantom.isEmpty(),
                "以下契约端点在 Controller 映射中不存在（文档写了代码没有＝虚假契约面）：\n"
                        + String.join("\n", phantom));
    }

    @Test
    @DisplayName("反向闭包：承载契约端点的 6 个 Controller 类，其全量映射不得超出契约 12 条")
    void contractOwningControllersExposeNothingUndocumented() throws IOException {
        List<Operation> contract = parseContract(read(repoRoot().resolve(CONTRACT_REL)));
        Set<String> declared = toSet(contract);
        ControllerScan scan = parseControllers(repoRoot());
        Map<String, Integer> declaredByEndpoint = new TreeMap<>();
        for (Operation o : contract) {
            declaredByEndpoint.put(o.endpoint(), 1);
        }

        // 属主类 = 至少命中一条契约端点的类
        Set<String> owners = new TreeSet<>();
        for (Mapping m : scan.mappings()) {
            if (declaredByEndpoint.containsKey(m.endpoint())) {
                owners.add(m.source());
            }
        }
        assertEquals(CONTRACT_OWNER_CLASS_COUNT, owners.size(),
                "契约 12 条应由 " + CONTRACT_OWNER_CLASS_COUNT + " 个类独占承载（实测基线）；实得 "
                        + owners.size() + " = " + owners
                        + " —— 类数变化意味着有人把契约端点搬走或新增同类载体，须重开对账口径");

        List<String> undocumented = new ArrayList<>();
        for (Mapping m : scan.mappings()) {
            if (owners.contains(m.source()) && !declaredByEndpoint.containsKey(m.endpoint())) {
                undocumented.add(m.source() + " → " + m.endpoint());
            }
        }
        assertTrue(undocumented.isEmpty(),
                "契约属主类暴露了未进 SCENARIO_OPENAPI 的映射（D-4.4 要求新增端点必进契约）：\n"
                        + String.join("\n", undocumented));

        // 属主类映射总数必须恰等于契约条数：两侧数量与集合都闭合，排除"重复端点被 Set 吞掉"
        int ownerMappingCount = (int) scan.mappings().stream()
                .filter(m -> owners.contains(m.source())).count();
        assertEquals(D2_ENDPOINT_COUNT, ownerMappingCount,
                "属主类映射总数应 = 契约条数 12，实得 " + ownerMappingCount);
    }

    @Test
    @DisplayName("详设-07 line 529 具名用例：workspace 契约面每条映射都解析到唯一 operationId（缺项/多义即 FAIL）")
    void everyWorkspaceMappingHasAnOperationId() throws IOException {
        List<Operation> contract = parseContract(read(repoRoot().resolve(CONTRACT_REL)));
        Map<String, List<Operation>> byEndpoint = new TreeMap<>();
        for (Operation o : contract) {
            byEndpoint.computeIfAbsent(o.endpoint(), k -> new ArrayList<>()).add(o);
        }

        ControllerScan scan = parseControllers(repoRoot());
        Set<String> owners = new TreeSet<>();
        for (Mapping m : scan.mappings()) {
            if (byEndpoint.containsKey(m.endpoint())) owners.add(m.source());
        }

        List<String> missing = new ArrayList<>();
        List<String> ambiguous = new ArrayList<>();
        int matched = 0;
        for (Mapping m : scan.mappings()) {
            if (!owners.contains(m.source())) continue;
            List<Operation> hits = byEndpoint.get(m.endpoint());
            if (hits == null || hits.isEmpty()) {
                missing.add(m.source() + " → " + m.endpoint());
            } else if (hits.size() > 1) {
                ambiguous.add(m.endpoint() + " → " + hits);
            } else if (hits.get(0).operationId() == null || hits.get(0).operationId().isBlank()) {
                missing.add(m.source() + " → " + m.endpoint() + "（契约条目 operationId 为空）");
            } else {
                matched++;
            }
        }
        assertTrue(missing.isEmpty(),
                "以下属主类映射在契约里没有唯一非空 operationId（详设-07 D-4.4「新增端点必进契约」）：\n"
                        + String.join("\n", missing));
        assertTrue(ambiguous.isEmpty(),
                "以下端点在契约里有 >1 条定义，operationId 归属不确定：\n" + String.join("\n", ambiguous));
        assertEquals(D2_ENDPOINT_COUNT, matched,
                "应恰有 12 条映射解析到 operationId，实得 " + matched + " —— 对账面塌陷");
    }

    @Test
    @DisplayName("方法/路径唯一性：契约内 (方法,路径) 与 operationId 均不得重复（重复即语义分叉）")
    void contractEntriesAreUnique() throws IOException {
        List<Operation> contract = parseContract(read(repoRoot().resolve(CONTRACT_REL)));
        assertEquals(D2_ENDPOINT_COUNT, distinctCount(contract, Operation::endpoint),
                "(method,path) 有重复：同一端点两条定义");
        assertEquals(D2_ENDPOINT_COUNT, distinctCount(contract, Operation::operationId),
                "operationId 有重复：OpenAPI 要求 operationId 全局唯一");
        Set<String> samePathMethods = new TreeSet<>();
        for (Operation o : contract) {
            samePathMethods.add(o.path());
        }
        assertEquals(11, samePathMethods.size(),
                "12 条操作落在 11 个路径上（actions 路径 GET+POST 双方法）；实得 "
                        + samePathMethods.size() + " —— 路径形态漂移");
    }

    // ── 解析：契约 OpenAPI 片段 ───────────────────────────

    private static String extractYamlBlock(String md) {
        int start = md.indexOf("```yaml");
        assertTrue(start >= 0, "SCENARIO_OPENAPI.md 缺 ```yaml 围栏块");
        int end = md.indexOf("```", start + 7);
        assertTrue(end > start, "SCENARIO_OPENAPI.md yaml 围栏未闭合");
        return md.substring(start + 7, end);
    }

    /**
     * 按 OpenAPI 缩进约定解析：paths 段 key = 缩进 2 的 {@code /xxx:}，操作 = 缩进 4 的动词，
     * {@code operationId} = 缩进 6。缩进即结构，禁按"看起来像"的正则跨块抓。
     */
    private static List<Operation> parseContract(String md) {
        String yaml = extractYamlBlock(md);
        List<Operation> out = new ArrayList<>();
        String path = null;
        String method = null;
        for (String raw : yaml.split("\r?\n")) {
            if (raw.isBlank() || raw.trim().startsWith("#")) continue;
            Matcher p = Pattern.compile("^ {2}(/[^\"]*?):\\s*$").matcher(raw);
            if (p.matches()) {
                path = p.group(1);
                method = null;
                continue;
            }
            Matcher m = Pattern.compile("^ {4}(get|post|put|patch|delete|head|options):\\s*$")
                    .matcher(raw);
            if (m.matches()) {
                assertTrue(path != null, "动词行出现在 path 之前，解析器假设被打破: " + raw);
                method = m.group(1).toUpperCase();
                continue;
            }
            Matcher o = Pattern.compile("^ {6}operationId:\\s*([A-Za-z0-9_]+)\\s*$").matcher(raw);
            if (o.matches()) {
                assertTrue(path != null && method != null,
                        "operationId 出现在无 path/method 上下文处（缩进漂移？）: " + raw);
                out.add(new Operation(method, path, o.group(1)));
            }
        }
        return out;
    }

    // ── 解析：详设-07 D-2 表 ──────────────────────────────

    private static List<Operation> parseDesignD2(String md) {
        int from = md.indexOf("### D-2");
        assertTrue(from >= 0, "详设-07 缺 '### D-2' 小节锚点（标题改版需同步本门禁）");
        int to = md.indexOf("\n### D-3", from);
        String section = to > from ? md.substring(from, to) : md.substring(from);

        Pattern row = Pattern.compile(
                "^\\|\\s*N\\d+\\s*\\|\\s*(GET|POST|PUT|PATCH|DELETE)\\s*\\|\\s*`([^`]+)`\\s*\\|\\s*`([^`]+)`\\s*\\|",
                Pattern.MULTILINE);
        List<Operation> out = new ArrayList<>();
        Matcher m = row.matcher(section);
        while (m.find()) {
            out.add(new Operation(m.group(1), m.group(2), m.group(3)));
        }
        return out;
    }

    // ── 解析：workspace Controller 映射 ───────────────────

    private static ControllerScan parseControllers(Path root) throws IOException {
        Path srcRoot = root.resolve(CONTROLLER_ROOT_REL);
        assertTrue(Files.isDirectory(srcRoot), "workspace 源码根缺失: " + srcRoot);

        List<Mapping> mappings = new ArrayList<>();
        List<String> ambiguous = new ArrayList<>();
        List<String> unparsable = new ArrayList<>();
        int controllerFiles = 0;

        try (Stream<Path> s = Files.walk(srcRoot)) {
            List<Path> files = s.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    .sorted()
                    .toList();
            for (Path f : files) {
                String src = stripComments(Files.readString(f, StandardCharsets.UTF_8));
                if (!src.contains("@RestController") && !src.contains("@Controller")) continue;
                controllerFiles++;

                int classIdx = indexOfClassDeclaration(src);
                assertTrue(classIdx >= 0, "找不到类声明，无法区分类级/方法级注解: " + f);

                List<String> bases = new ArrayList<>();
                Matcher anno = ANNOTATION.matcher(src);
                while (anno.find()) {
                    boolean classLevel = anno.start() < classIdx;
                    String kind = anno.group(1);        // Get|Post|Put|Patch|Delete|Request
                    String args = anno.group(2);        // 可能为 null（裸注解）
                    if (classLevel) {
                        if ("Request".equals(kind)) {
                            bases.addAll(literalPaths(args, f, unparsable));
                        } else {
                            unparsable.add(f + " 类级用了 @" + kind + "Mapping（应为 @RequestMapping 前缀）");
                        }
                        continue;
                    }
                    List<String> paths = literalPaths(args, f, unparsable);
                    String method = verbOf(kind, args);
                    if (method == null) {
                        ambiguous.add(f + " → 方法级 @RequestMapping 未显式 RequestMethod.*: " + trim(args));
                        continue;
                    }
                    if (paths.isEmpty()) {
                        // 裸 @GetMapping() 等：映射落在类前缀上（BindingCatalogController 形态）
                        if (bases.isEmpty()) {
                            unparsable.add(f + " 方法级 " + kind + "Mapping 无路径且类无 @RequestMapping 前缀");
                            continue;
                        }
                        for (String base : bases) {
                            mappings.add(new Mapping(method, normalize(base), relOf(f, srcRoot)));
                        }
                        continue;
                    }
                    for (String base : (bases.isEmpty() ? List.of("") : bases)) {
                        for (String p : paths) {
                            mappings.add(new Mapping(method, normalize(base + "/" + p), relOf(f, srcRoot)));
                        }
                    }
                }
            }
        }
        return new ControllerScan(mappings, ambiguous, unparsable, controllerFiles);
    }

    /** {@code @GetMapping}／{@code @GetMapping("/x")}／{@code @RequestMapping(value="/x", method=...)}。 */
    private static final Pattern ANNOTATION = Pattern.compile(
            "@(Get|Post|Put|Patch|Delete|Request)Mapping\\b(\\s*\\(([^()]*(\\([^()]*\\)[^()]*)*)\\))?",
            Pattern.DOTALL);

    private static String verbOf(String kind, String args) {
        if (!"Request".equals(kind)) {
            return switch (kind) {
                case "Get" -> "GET";
                case "Post" -> "POST";
                case "Put" -> "PUT";
                case "Patch" -> "PATCH";
                case "Delete" -> "DELETE";
                default -> null;
            };
        }
        if (args == null) return null;
        Matcher m = Pattern.compile("RequestMethod\\.(GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS)")
                .matcher(args);
        return m.find() ? m.group(1) : null;
    }

    /**
     * 从注解参数里取字面量路径。规则：注解里出现了 {@code (} 但抓不到引号字面量，且参数非空
     * ⇒ 判为「非字面量参数」列入 unparsable（禁静默跳过＝本门禁的假绿入口）。
     * 裸注解（无括号）返回空列表，由调用方按"落在类前缀上"处理。
     */
    private static List<String> literalPaths(String args, Path file, List<String> unparsable) {
        List<String> out = new ArrayList<>();
        if (args == null) return out;
        String body = args.trim();
        if (body.isEmpty()) return out;
        Matcher q = Pattern.compile("\"([^\"]*)\"").matcher(body);
        while (q.find()) {
            out.add(q.group(1));
        }
        if (out.isEmpty()) {
            unparsable.add(file + " 注解参数无字符串字面量: " + trim(body));
        }
        return out;
    }

    private static String normalize(String joined) {
        String s = joined.replaceAll("/+", "/").trim();
        if (!s.startsWith("/")) s = "/" + s;
        if (s.length() > 1 && s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    private static String relOf(Path f, Path srcRoot) {
        return srcRoot.relativize(f).toString().replace('\\', '/');
    }

    private static int indexOfClassDeclaration(String src) {
        Matcher m = Pattern.compile("\\b(?:public\\s+|final\\s+|abstract\\s+)*class\\s+\\w+").matcher(src);
        return m.find() ? m.start() : -1;
    }

    /** 去块注释（javadoc 里的 @GetMapping 示例不是真映射）+ 去行注释。 */
    private static String stripComments(String src) {
        String noBlock = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL).matcher(src).replaceAll(" ");
        StringBuilder sb = new StringBuilder();
        for (String line : noBlock.split("\r?\n")) {
            int idx = line.indexOf("//");
            sb.append(idx >= 0 ? line.substring(0, idx) : line).append('\n');
        }
        return sb.toString();
    }

    // ── 夹具 ─────────────────────────────────────────────

    private static Set<String> toSet(List<Operation> ops) {
        Set<String> out = new LinkedHashSet<>();
        for (Operation o : ops) out.add(o.toString());
        return out;
    }

    private static <T> int distinctCount(List<T> list, java.util.function.Function<T, String> key) {
        Set<String> seen = new TreeSet<>();
        for (T t : list) seen.add(key.apply(t));
        return seen.size();
    }

    private static int countOccurrences(String text, String token) {
        int n = 0, i = 0;
        while ((i = text.indexOf(token, i)) >= 0) {
            n++;
            i += token.length();
        }
        return n;
    }

    private static String trim(String s) {
        if (s == null) return "<null>";
        String t = s.replaceAll("\\s+", " ").trim();
        return t.length() > 120 ? t.substring(0, 120) + "…" : t;
    }

    private static String read(Path f) throws IOException {
        assertTrue(Files.isRegularFile(f), "事实源文件缺失: " + f);
        return Files.readString(f, StandardCharsets.UTF_8);
    }

    /** 与其他 gateway 静态门禁同形：从 cwd 上溯找 ecos_backend/。 */
    private static Path repoRoot() throws IOException {
        Path p = Paths.get("").toAbsolutePath();
        while (p != null) {
            if (Files.isDirectory(p.resolve("ecos_backend"))) return p;
            Path parent = p.getParent();
            if (parent == null || parent.equals(p)) break;
            p = parent;
        }
        throw new IllegalStateException("cwd 未锚定 ECOS 仓库根（找不到 ecos_backend/）");
    }
}
