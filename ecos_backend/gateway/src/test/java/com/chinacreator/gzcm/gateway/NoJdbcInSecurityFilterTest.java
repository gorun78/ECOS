package com.chinacreator.gzcm.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 详细设计-00 W04（阶段 gate）— 认证/准入组件 JdbcTemplate 裸查收口。
 *
 * <p><b>01 册 D 章将落成</b>「security-engine REST 门面」把 {@code ClearanceInterceptor} 和
 * {@code JwtAuthenticationFilter} 的 <b>两组内嵌 JdbcTemplate 裸查（td_user_security_profile /
 * td_role_security_profile / TD_USER / ecos_token_blacklist）</b>替换成 HTTP 调用门面；两拦截器
 * 届时零 {@code JdbcTemplate} 依赖 → 本 gate 减 exemptions → 免名清单 {@link #KNOWN_EXEMPT}
 * 清空 → 转无条件禁。</p>
 *
 * <p>本 stage gate 语义：<b>filter / interceptor 包新代码新增 JdbcTemplate import</b> → FAIL；
 * 已知豁免（01 册承接迁移的两处）暂放行。01 册完成后应:</p>
 * <ol>
 *   <li>把两处 JdbcTemplate 改走 security-engine REST</li>
 *   <li>删除 {@link #KNOWN_EXEMPT} 条目</li>
 *   <li>本 gate 覆盖面扩大到清户，无豁免路径</li>
 * </ol>
 *
 * <p>包范围：{@code com.chinacreator.gzcm.sysman.security.*}（sysman，filter+interceptor 同域，
 * 00 册 W04 依赖 01 册表关系）。等价于设计稿「filter/interceptor 包」 但落在事实上那俩类位置。</p>
 */
class NoJdbcInSecurityFilterTest {

    /** 01 册承接迁移的已知遗留；01 册完成后应清空此清单。 */
    private static final Set<String> KNOWN_EXEMPT = Set.of(
            "com.chinacreator.gzcm.sysman.security.ClearanceInterceptor",
            "com.chinacreator.gzcm.sysman.security.JwtAuthenticationFilter");

    /** 00/01 册 scope：sysman 安全域（认证/准入 filter+interceptor 同包）。 */
    private static final String SECURITY_PKG = "com.chinacreator.gzcm.sysman.security";

    @Test
    @DisplayName("W04 阶段 gate：sysman.security 新增 JdbcTemplate 裸查 → FAIL；仅 KNOWN_EXEMPT 放行")
    void noNewJdbcInSecurityFilter() throws IOException {
        Path backendRoot = Paths.get("..").toAbsolutePath().normalize();
        assertTrue(Files.isDirectory(backendRoot), "源根未找到: " + backendRoot);

        java.util.Set<String> offenders = new TreeSet<>();

        try (Stream<Path> walk = Files.walk(backendRoot)) {
            walk.filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .filter(p -> {
                    String s = p.toString().replace('\\', '/');
                    // 仅生产代码（src/main），排除 target / 测试脚手架
                    return !s.contains("/target/")
                        && s.contains("/src/main/")
                        && s.contains("/sysman/security/");
                })
                .forEach(p -> {
                    String pkg = detectPackage(p);
                    if (!SECURITY_PKG.equals(pkg)) return;
                    String content = readString(p);
                    if (!content.contains("JdbcTemplate")) return;
                    String fq = pkg + "." + simpleName(p);
                    if (!KNOWN_EXEMPT.contains(fq)) {
                        offenders.add(fq + "  (" + tail(p) + ")");
                    }
                });
        }

        assertTrue(offenders.isEmpty(),
            "W04 gate 违规：filter/interceptor 新增 JdbcTemplate 裸查。\n  "
            + String.join("\n  ", offenders)
            + "\n  只有 KNOWN_EXEMPT 放行（01 册 承接迁移）：" + KNOWN_EXEMPT);
    }

    // ---- 内部工具 ----

    private static String detectPackage(Path p) {
        Pattern pkgPattern = Pattern.compile("package\\s+([\\w.]+)\\s*;");
        try {
            List<String> lines = Files.readAllLines(p, StandardCharsets.UTF_8);
            for (String line : lines) {
                Matcher mm = pkgPattern.matcher(line);
                if (mm.find()) return mm.group(1);
            }
        } catch (IOException ignore) {}
        return null;
    }

    private static String simpleName(Path p) {
        String n = p.getFileName().toString();
        return n.substring(0, n.length() - 5);
    }

    private static String readString(Path p) {
        try { return Files.readString(p, StandardCharsets.UTF_8); }
        catch (IOException e) { return ""; }
    }

    private static String tail(Path p) {
        String s = p.toString().replace('\\', '/');
        int i = s.lastIndexOf("src/");
        return i >= 0 ? s.substring(i) : s;
    }
}
