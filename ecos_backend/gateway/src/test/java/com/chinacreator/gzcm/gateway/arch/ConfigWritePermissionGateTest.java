package com.chinacreator.gzcm.gateway.arch;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 配置写面细粒度权限门（§2.9.9 G9「refreshCache 无细粒度门」收口；#147AB）。
 *
 * <p><b>缺陷实况</b>：{@code @PostMapping("/refresh")} 在三处引擎配置控制器上**零权限注解**，
 * 兜底只有 {@code anyRequest().authenticated()} —— 任何登录用户都能刷新全量 {@code sys_config}
 * 缓存；同一批控制器的 {@code @PutMapping}（配置批量写入）同样裸奔。配置面在 #147AA 之后
 * 直接决定缓存行为，「登录即可刷/改配置」的半径比原来更大。</p>
 *
 * <p><b>执行者口径（必须写清，否则门是装饰）</b>：{@code com.chinacreator.gzcm.common.annotation
 * .RequirePermission} 由 gateway {@code PermissionAspect} 执行（支持 {@code admin}/{@code ROLE_SUPER_ADMIN}
 * 旁路）；{@code ...sysman.security.RequirePermission} 由 sysman {@code RequirePermissionAspect} 执行。
 * 两者在 gateway fat-jar 拓扑下都生效；<b>独立 boot</b> 启动的引擎只有网络隔离（ADR-7）而无 AOP 执行者，
 * 故本门只保证「注解在位」，不宣称独立部署同样生效（登记于 #147AB 台账）。</p>
 *
 * <p><b>判据</b>：{@code *ConfigController} 的每个 {@code @Put/@Post/@DeleteMapping} 方法，
 * 其注解块（映射注解上一段连续注解，或映射注解到方法签名之间）必须出现 {@code @RequirePermission}。
 * 配两枚防退化断言（扫描面与写法总数）与一枚反证（合成无门样例必须判红）。</p>
 *
 * @author ECOS Gateway #147AB
 */
class ConfigWritePermissionGateTest {

    /** 写映射注解。 */
    private static final Pattern WRITE_MAPPING =
            Pattern.compile("@(Put|Post|Delete)Mapping\\b");

    /** 类名过滤：只圈配置控制器。 */
    private static final String CONFIG_CONTROLLER_SUFFIX = "ConfigController.java";

    /**
     * 豁免：{@code AgentConfigController} 是「Agent 定义 CRUD」（表 {@code ecos_agent_*}），
     * 不写 {@code sys_config}，其权限面归 agent 域；接入前如实登记，不计入本门判据。
     * 豁免按<b>简单类名</b>钉死，且下方断言要求该文件仍存在于扫描面（防豁免腐化为空转）。
     */
    private static final Set<String> ALLOWLIST = Set.of("AgentConfigController");

    /** 扫描面下限（实测 9 个配置控制器 / 20+ 处写映射）。 */
    private static final int MIN_FILES = 9;
    private static final int MIN_WRITE_MAPPINGS = 20;

    @Test
    @DisplayName("配置控制器的每个写映射方法都必须带 @RequirePermission（refresh 与批量写同判）")
    void everyConfigWriteMappingIsGuarded() throws IOException {
        Path backend = repoRoot().resolve("ecos_backend");
        Map<String, List<String>> unguarded = new TreeMap<>();
        int files = 0;
        int mappings = 0;
        Set<String> allowlistHits = new TreeSet<>();

        for (Path file : configControllers(backend)) {
            files++;
            String simpleName = file.getFileName().toString().replace(".java", "");
            List<String> lines = Files.readAllLines(file);
            for (int i = 0; i < lines.size(); i++) {
                Matcher m = WRITE_MAPPING.matcher(lines.get(i));
                if (!m.find()) {
                    continue;
                }
                mappings++;
                if (ALLOWLIST.contains(simpleName)) {
                    allowlistHits.add(simpleName);
                    continue;
                }
                if (!hasPermissionAnnotation(lines, i)) {
                    unguarded.computeIfAbsent(simpleName, k -> new ArrayList<>())
                            .add("L" + (i + 1) + " " + lines.get(i).trim());
                }
            }
        }

        assertTrue(files >= MIN_FILES,
                "扫描面退化：只看到 " + files + " 个 *ConfigController（基线 " + MIN_FILES + "）——门禁失去覆盖面");
        assertTrue(mappings >= MIN_WRITE_MAPPINGS,
                "写映射总数退化：只看到 " + mappings + " 处（基线 " + MIN_WRITE_MAPPINGS + "）");
        assertTrue(allowlistHits.equals(ALLOWLIST),
                "豁免腐化：豁免名单中的类在扫描面缺失 " + ALLOWLIST + "，实际命中 " + allowlistHits
                        + "（文件搬家/改名须同批订正本门）");
        assertTrue(unguarded.isEmpty(),
                "以下配置写面缺 @RequirePermission（登录即可改/刷配置缓存）：\n"
                        + render(unguarded));
    }

    @Test
    @DisplayName("反证：合成样例中「映射注解在前、无权限注解」必须判红，注解在后（security 风格）必须判绿")
    void predicateGoesRedOnUnguardedSamples() {
        List<String> unguarded = List.of(
                "    @PostMapping(\"/refresh\")",
                "    public Object refresh() { return null; }");
        assertFalse(hasPermissionAnnotation(unguarded, 0), "裸 @PostMapping 必须判红");

        List<String> guardedBefore = List.of(
                "    @RequirePermission(permission = \"system:config:manage\")",
                "    @PostMapping(\"/refresh\")",
                "    public Object refresh() { return null; }");
        assertTrue(hasPermissionAnnotation(guardedBefore, 1));

        List<String> guardedAfter = List.of(
                "    @PutMapping(\"/profile\")",
                "    @RequirePermission(permission = \"security:config:update\")",
                "    public Object update() { return null; }");
        assertTrue(hasPermissionAnnotation(guardedAfter, 0), "注解写在映射之后同样有效（security 侧既有风格）");

        List<String> previousMethodGuarded = List.of(
                "    @RequirePermission(permission = \"x:read\")",
                "    @GetMapping",
                "    public Object read() { return null; }",
                "",
                "    @PostMapping(\"/refresh\")",
                "    public Object refresh() { return null; }");
        assertFalse(hasPermissionAnnotation(previousMethodGuarded, 4),
                "上一个方法的注解不得替下一个方法买单（否则本门会出现假绿通道）");
    }

    /**
     * 注解块判定：向**上**只吃连续注解/注释行、遇非注解即刻停止；
     * 向**下**吃到方法签名行为止（兼容 {@code @PutMapping} 后紧跟 {@code @RequirePermission} 的写法）。
     */
    static boolean hasPermissionAnnotation(List<String> lines, int mappingIdx) {
        for (int i = mappingIdx - 1; i >= 0; i--) {
            String t = lines.get(i).trim();
            if (t.isEmpty() || t.startsWith("*") || t.startsWith("/*") || t.startsWith("//")) {
                continue;
            }
            if (t.startsWith("@")) {
                if (t.contains("RequirePermission")) {
                    return true;
                }
                continue;
            }
            break;
        }
        for (int i = mappingIdx + 1; i < lines.size() && i <= mappingIdx + 6; i++) {
            String t = lines.get(i).trim();
            if (t.startsWith("@")) {
                if (t.contains("RequirePermission")) {
                    return true;
                }
                continue;
            }
            if (t.startsWith("public") || t.startsWith("private") || t.startsWith("protected")) {
                break;
            }
        }
        return false;
    }

    private static String render(Map<String, List<String>> unguarded) {
        StringBuilder sb = new StringBuilder();
        unguarded.forEach((cls, sites) -> {
            sb.append("  - ").append(cls).append('\n');
            sites.forEach(s -> sb.append("      ").append(s).append('\n'));
        });
        return sb.toString();
    }

    private static List<Path> configControllers(Path backend) throws IOException {
        try (Stream<Path> s = Files.walk(backend)) {
            return s.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(CONFIG_CONTROLLER_SUFFIX))
                    .filter(p -> !p.toString().contains("target"))
                    .filter(p -> !p.toString().contains("src" + java.io.File.separator + "test"))
                    .toList();
        }
    }

    /** Surefire CWD 可能漂移，向上探测同时含 ecos_backend/ 与 ecos-docker/ 的仓库根。 */
    private static Path repoRoot() {
        Path cur = Paths.get("").toAbsolutePath().normalize();
        for (int i = 0; i < 8 && cur != null; i++) {
            if (Files.isDirectory(cur.resolve("ecos_backend")) && Files.isDirectory(cur.resolve("ecos-docker"))) {
                return cur;
            }
            cur = cur.getParent();
        }
        throw new IllegalStateException("未找到仓库根（同时含 ecos_backend/ 与 ecos-docker/），CWD=" + Paths.get("").toAbsolutePath());
    }
}
