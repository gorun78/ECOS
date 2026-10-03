package com.chinacreator.gzcm.gateway.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W03（docs/30-设计/详细设计-00-平台入口与横切底座-2026-09-28.md §W03 行 / MC06，M1）
 * — 制品 yml 的 {@code spring.datasource.url} 必须走环境变量占位符
 * （{@code ${DB_URL:...}} / {@code ${ECOS_DB_URL:...}}），禁硬编码
 * {@code jdbc:postgresql://localhost} 字面量（铁律 MC06：连接串可注入、云化不重编译）。
 *
 * <p>7 个制品各一份 application.yml（相对 repo 根）：
 * {@code gateway / services/{sysman,datanet,buszhi,aiming,dccheng} / workspace/workspace-service}。
 *
 * <p>2 用例：
 * <ol>
 *   <li>各 yml {@code spring.datasource.url} 行不得出现 {@code jdbc:postgresql://localhost}
 *       直接字面（必须以占位符形态出现）。<b>现状：gateway 已改占位符（W03 收口）</b>，
 *       若回退硬编码 → FAIL。原 @Disabled(W03 未执行) 因 gateway 已占位符化而解除。</li>
 *   <li>每个 yml 若含占位符，其 default 值必须含 {@code currentSchema=} 参数（ST07 schema 路由）。</li>
 * </ol>
 * 纯文本扫，不打 Spring 上下文。
 */
class DatasourceConfigGuardTest {

    /** 7 个制品的 application.yml（相对 repo 根 = ecos_backend 上溯一层的父；这里相对 ecos_backend） */
    private static final String[] ARTIFACT_YMLS = {
            "gateway/src/main/resources/application.yml",
            "services/sysman/src/main/resources/application.yml",
            "services/datanet/src/main/resources/application.yml",
            "services/buszhi/src/main/resources/application.yml",
            "services/aiming/src/main/resources/application.yml",
            "services/dccheng/src/main/resources/application.yml",
            "workspace/workspace-service/src/main/resources/application.yml",
    };

    /** 定位 ecos_backend 根（surefire 工作目录可能是 gateway 或 sysman-impl 模块根） */
    private static Path backendRoot() {
        Path cwd = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path guess = cwd;
        while (guess != null) {
            if (Files.isDirectory(guess.resolve("gateway/src/main/java"))
                    && Files.isDirectory(guess.resolve("services/sysman"))) {
                return guess;
            }
            guess = guess.getParent();
        }
        return cwd;
    }

    private static String read(Path root, String relative) {
        Path p = root.resolve(relative);
        try {
            return Files.readString(p, java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("yml 读取失败: " + p, e);
        }
    }

    /** 提取 spring.datasource.url 的 value（首个 url: 行冒号后的内容，trim） */
    private static List<String> datasourceUrls(String yaml) {
        List<String> urls = new ArrayList<>();
        for (String line : yaml.split("\\r?\\n")) {
            String t = line.trim();
            if (t.startsWith("url:") && t.contains("jdbc:")) {
                urls.add(t.substring(t.indexOf(':') + 1).trim());
            }
        }
        return urls;
    }

    /** tc1：任一制品的 datasource url 不得硬编码 jdbc:postgresql://localhost 字面（必须占位符形态） */
    @Test
    @DisplayName("tc1 7 制品 datasource.url 不得出现 jdbc:postgresql://localhost 硬编码（须 ${ENV:} 占位符）")
    void noHardcodedLocalhostUrl() {
        Path root = backendRoot();
        List<String> offenders = new ArrayList<>();
        for (String rel : ARTIFACT_YMLS) {
            String yaml = read(root, rel);
            for (String url : datasourceUrls(yaml)) {
                // 占位符形态：形如 ${XXX:...} 开头；其 default 值里出现 localhost 是允许的
                boolean wrapped = url.contains("${") && url.contains("}");
                if (!wrapped) {
                    offenders.add(rel + " -> 未占位符: " + url);
                } else if (url.contains("jdbc:postgresql://localhost") && !looksLikeDefault(url)) {
                    offenders.add(rel + " -> 占位符外裸 localhost: " + url);
                }
            }
        }
        assertTrue(offenders.isEmpty(),
                "以下制品 datasource.url 违反 W03/MC06（必须 ${ENV:} 占位符、禁裸 localhost 硬编码）：\n  "
                        + String.join("\n  ", offenders));
    }

    /** tc2：占位符形态的 default 值必须带 currentSchema= 参数（ST07 schema 路由） */
    @Test
    @DisplayName("tc2 占位符 default 值必须含 currentSchema= 参数")
    void placeholderDefaultMustCarryCurrentSchema() {
        Path root = backendRoot();
        for (String rel : ARTIFACT_YMLS) {
            String yaml = read(root, rel);
            for (String url : datasourceUrls(yaml)) {
                boolean isPlaceholder = url.contains("${") && url.contains("}");
                assertTrue(isPlaceholder, rel + " datasource.url 必须是占位符形态: " + url);
                if (isPlaceholder && url.contains("jdbc:")) {
                    // default 值（${...:...} 冒号后整段）必须带 currentSchema= 参数
                    assertTrue(url.contains("currentSchema="),
                            "占位符 default 必须显式声明 currentSchema=（ST07 schema 路由），实际 " + url);
                }
            }
        }
    }

    /** 判断 localhost 是否仅作为 ${...} 的 default 值（允许），而非占位符外的裸字面 */
    private static boolean looksLikeDefault(String url) {
        // 形如 ${ENV:jdbc:postgresql://localhost...} → localhost 在 default 内，允许
        Matcher m = Pattern.compile("\\$\\{[^:]+:(.+)}").matcher(url);
        return m.find();
    }
}
