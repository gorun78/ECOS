package com.chinacreator.gzcm.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 详细设计-01 W31（P0 安全）— RLS 注入面源码门禁。
 *
 * <p>现状 S-4：{@code QueryController#execute} 曾做
 * {@code rlsSql = "SELECT * FROM (" + sql + ") AS _rls WHERE " + rlsCondition;} 三重违规
 * （IR05 未参数化 + 引擎自拼 SQL + OWASP 注入）。</p>
 *
 * <p>本门禁按设计 §7.1 验收方式扫描全仓 Java 源码，命中以下任一形态即 FAIL：</p>
 * <ol>
 *   <li>{@code "SELECT * FROM (" + sql}（或 {@code ... + rlsCondition} / {@code ... + rls} 变体）——
 *       字符串拼接 wrapper 的 RLS 条件；</li>
 *   <li>引擎侧硬编码 {@code http://localhost:8080/api/security/}（W32 应由 ServiceEndpointResolver 寻址）。</li>
 * </ol>
 *
 * <p>豁免：{@code /src/test/} 是本门禁自身，被过滤排除，不与 LlmSoleEgressTest 自身扫描循环嵌套自摘除；
 * 目标态改走 SELECT * FROM (%s) + 占位符 + params 参数化绑定，命中应为 0。</p>
 */
class RlsInjectionGuardArchTest {

    private static final String BACKEND_ROOT = "..";  // surefire cwd = gateway/

    // 设计 §7.1 验收方式：禁 `"SELECT * FROM (" + sql`（或 rlsCondition 变体）形态
    private static final String[] FORBIDDEN_RLS_WRAP = {
            "\"SELECT * FROM (\" + sql",
            "\"SELECT * FROM (\" + sql ",
            "SELECT * FROM (\" + sql",
            "), \" AS _rls WHERE \" + rlsCondition",
            "AS _rls WHERE \" + rlsCondition",
            "WHERE \" + rlsCondition"
    };

    // W32: 引擎/服务 内硬编码 security-engine 端点（应由 ServiceEndpointResolver 提供）
    private static final String HARD_CODED_SECURITY_ENDPOINT = "http://localhost:8080/api/security/";

    @Test
    @DisplayName("全仓 Java 源码零 RLS wrap 拼接（W31 P0）")
    void noRlsInjectionConcat() throws IOException {
        Path backendRoot = Paths.get(BACKEND_ROOT).toAbsolutePath().normalize();
        assertTrue(Files.isDirectory(backendRoot), "源根未找到: " + backendRoot);

        Set<String> violations = new LinkedHashSet<>();
        try (Stream<Path> walk = Files.walk(backendRoot)) {
            walk.filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .filter(p -> {
                    String s = p.toString().replace('\\', '/');
                    return !s.contains("/target/") && !s.contains("/src/test/");
                })
                .forEach(p -> {
                    String content = read(p);
                    for (String pat : FORBIDDEN_RLS_WRAP) {
                        if (content.contains(pat)) {
                            violations.add(p.getFileName() + " :: " + pat);
                        }
                    }
                });
        }
        assertTrue(violations.isEmpty(),
                "W31 P0: RLS 拼接注入面残留（应改参数化谓词 + PreparedStatement）: "
                        + String.join("\n  ", violations));
    }

    @Test
    @DisplayName("引擎/服务内禁硬编码 security-engine :8080 端点（W32）")
    void noHardCodedSecurityEndpoint() throws IOException {
        Path backendRoot = Paths.get(BACKEND_ROOT).toAbsolutePath().normalize();

        Set<String> violations = new LinkedHashSet<>();
        try (Stream<Path> walk = Files.walk(backendRoot)) {
            violations = walk
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> {
                        String s = p.toString().replace('\\', '/');
                        // 豁免：设计-00 ServiceEndpointResolver 自己的 fallback 常量路径 + test 目录
                        return !s.contains("/target/")
                                && !s.contains("/src/test/")
                                && !s.contains("runtime/runtime-access");
                    })
                    .filter(p -> read(p).contains(HARD_CODED_SECURITY_ENDPOINT))
                    .map(p -> p.getFileName().toString())
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        }
        assertTrue(violations.isEmpty(),
                "W32: 硬编码 security-engine 端点（应经 ServiceEndpointResolver 两态寻址）: "
                        + String.join(", ", violations));
    }

    private static String read(Path p) {
        try { return Files.readString(p, java.nio.charset.StandardCharsets.UTF_8); }
        catch (IOException e) { return ""; }
    }
}
