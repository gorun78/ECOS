package com.chinacreator.gzcm.engine.security.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * 详细设计-01 C.2.5 ② / W36 — OPA 策略目录<b>跨平台</b>解析器。
 *
 * <p>现状 {@code opa.policy-dir} 默认 {@code /opt/ecos/opa-policies}（Linux 路径，
 * Windows 本机开发不存在）。解析优先级：</p>
 * <ol>
 *   <li>{@code ECOS_OPA_POLICY_DIR} 环境变量（运维显式指定，最高优先）；</li>
 *   <li>显式传入的 {@code configured} 值若存在且为目录 → 直接用；</li>
 *   <li>兜底：把 classpath {@code opa/policies/*.rego} 解包到
 *       {@code ${java.io.tmpdir}/ecos-opa-&lt;ts&gt;}/（Windows 本地可运行）。</li>
 * </ol>
 *
 * <p>解析结果永远是<b>已存在的目录</b>（不存在则创建）。不抛异常——无策略时上层
 * {@code OpaPolicyService} 仍以"OPA 不可用 → fail-close 拒绝"的安全默认继续。</p>
 */
public final class OpaPolicyDirResolver {

    private static final Logger log = LoggerFactory.getLogger(OpaPolicyDirResolver.class);

    /** 运维覆盖用环境变量名（C.2.5 ②）。 */
    public static final String ENV_POLICY_DIR = "ECOS_OPA_POLICY_DIR";
    /** classpath 内建策略种子目录。 */
    public static final String CLASSPATH_POLICIES = "opa/policies";

    private OpaPolicyDirResolver() {}

    /**
     * 解析策略目录。
     *
     * @param configured 来自 {@code @Value("${opa.policy-dir}")} 的原始值（可能不可用）
     * @return 已存在的目录（不存在则创建）
     */
    public static Path resolve(String configured) {
        // ① env 覆盖
        String env = System.getenv(ENV_POLICY_DIR);
        if (env != null && !env.isBlank()) {
            Path p = ensureDir(Path.of(env.trim()));
            log.info("OPA 策略目录 = env {}:{}", ENV_POLICY_DIR, p);
            return p;
        }
        // ② 显式配置且可用
        if (configured != null && !configured.isBlank()) {
            Path p = Path.of(configured.trim()).toAbsolutePath().normalize();
            if (Files.isDirectory(p)) {
                return p;
            }
            log.warn("配置的 opa.policy-dir 不可用, 转 classpath 解包: {}", configured);
        }
        // ③ 兜底：classpath 解包到临时目录（跨平台，Windows 可运行）
        return unpackClasspath();
    }

    private static Path ensureDir(Path p) {
        try {
            Files.createDirectories(p);
            return p;
        } catch (Exception e) {
            log.warn("创建目录失败, 转临时目录: {} ({})", p, e.getMessage());
            return emptyTempDir();
        }
    }

    /** classpath {@link #CLASSPATH_POLICIES} 下所有 {@code .rego} 解包到一个新建临时目录。 */
    static Path unpackClasspath() {
        Path base = tempDir();
        try {
            int n = copyClasspathRego(base);
            if (n == 0) {
                log.warn("classpath {} 无 .rego, OPA 无内建策略 → fail-close 兜底: {}", CLASSPATH_POLICIES, base);
            } else {
                log.info("OPA 策略从 classpath 解包 {} 个 .rego → {}", n, base);
            }
            return base;
        } catch (Exception e) {
            log.error("classpath 策略解包失败: {}", e.getMessage());
            return emptyTempDir();
        }
    }

    /**
     * 把 classpath {@link #CLASSPATH_POLICIES} 下的 {@code .rego} 文件复制到 {@code base}。
     * 兼容 exploded 目录（dev / target/classes，{@code file:} URL）与 jar（{@code jar:} URL）。
     * <p>public 以便单测直接驱动（W36 跨平台解包验证）。</p>
     */
    public static int copyClasspathRego(Path base) throws IOException {
        Enumeration<URL> urls = OpaPolicyDirResolver.class.getClassLoader().getResources(CLASSPATH_POLICIES);
        int count = 0;
        while (urls.hasMoreElements()) {
            URL url = urls.nextElement();
            String protocol = url.getProtocol();
            if ("file".equals(protocol)) {
                String raw = URLDecoder.decode(url.getPath(), StandardCharsets.UTF_8);
                Path dir = Path.of(raw);
                if (Files.isDirectory(dir)) {
                    try (java.util.stream.Stream<Path> w = Files.list(dir)) {
                        for (Path f : (Iterable<Path>) w.iterator()) {
                            if (Files.isRegularFile(f) && f.toString().endsWith(".rego")) {
                                Files.copy(f, base.resolve(f.getFileName().toString()),
                                        StandardCopyOption.REPLACE_EXISTING);
                                count++;
                            }
                        }
                    }
                }
            } else if ("jar".equals(protocol)) {
                count += copyFromJar(url, base);
            }
        }
        return count;
    }

    private static int copyFromJar(URL jarUrl, Path base) throws IOException {
        int count = 0;
        String spec = jarUrl.getFile(); // file:/.../x.jar!/opa/policies
        int bang = spec.indexOf("!/");
        String jarPath = URLDecoder.decode(spec.substring(0, bang), StandardCharsets.UTF_8);
        String innerRoot = spec.substring(bang + 2); // opa/policies
        String prefix = innerRoot.endsWith("/") ? innerRoot : innerRoot + "/";
        try (java.util.jar.JarFile jar = new java.util.jar.JarFile(jarPath)) {
            java.util.Enumeration<java.util.jar.JarEntry> en = jar.entries();
            while (en.hasMoreElements()) {
                java.util.jar.JarEntry e = en.nextElement();
                String name = e.getName();
                if (!e.isDirectory() && name.startsWith(prefix) && name.endsWith(".rego")) {
                    String file = name.substring(name.lastIndexOf('/') + 1);
                    try (InputStream in = jar.getInputStream(e)) {
                        Files.copy(in, base.resolve(file), StandardCopyOption.REPLACE_EXISTING);
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private static Path tempDir() {
        try {
            return Files.createTempDirectory("ecos-opa-");
        } catch (Exception e) {
            return Path.of(System.getProperty("java.io.tmpdir", "."));
        }
    }

    private static Path emptyTempDir() {
        return tempDir();
    }
}
