package com.chinacreator.gzcm.engine.ai.error;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * 智能域源码扫描器（F06-14/15 验收测试的唯一读源工具）。
 *
 * <p>三册（04/05/06）的门禁测试都以"源码即事实"形态落地（无反射 Spring 上下文）：
 * 掩蔽类缺陷（{@code catch (Exception)} 吞异常、mock-success、异常原文外泄）在运行期
 * 只有真打流量才暴露，静态扫描能在 Gate B 前判死。为避免把注释/文档里的"禁写形态"
 * 误判为违规，扫描前先做词法归一：</p>
 * <ul>
 *   <li>注释字符以空格抹平（换行保留 ⇒ 行号与原文一致，违规可直接定位）；</li>
 *   <li>字符串字面量内的转义引号 {@code \"} 还原为 {@code "} ⇒ 能匹配到代码里
 *       写死的 JSON 片段（如 mock-success 的 {@code "status":"success"}）。</li>
 * </ul>
 */
final class AiSourceScan {

    private AiSourceScan() {
    }

    /** 智能域控制器源码目录（F06-15 禁捕获面）。 */
    static Path aiControllerDir() {
        return locateRepoRoot().resolve(
                "ecos_backend/engine/ai-engine/ai-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ai/controller");
    }

    /** 智能域引擎实现主源码根（mock-success / 绕行面扫描域）。 */
    static Path aiEngineMainRoot() {
        return locateRepoRoot().resolve("ecos_backend/engine/ai-engine");
    }

    /** 后端全部主源码根（X-22 降 provider 单源扫描域）。 */
    static Path backendRoot() {
        return locateRepoRoot().resolve("ecos_backend");
    }

    /** 目录下的直接 {@code *.java}（不递归；控制器目录为平铺结构）。 */
    static List<Path> flatJavaFiles(Path dir) throws IOException {
        List<Path> files = new ArrayList<>();
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".java"))
                    .sorted()
                    .forEach(files::add);
        }
        return files;
    }

    /**
     * 递归收集 {@code root} 下的 {@code src/main/java} 源码（跳过 target/archive/.git/node_modules）。
     */
    static List<Path> mainSources(Path root) throws IOException {
        List<Path> files = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    .filter(p -> p.toString().replace('\\', '/').contains("/src/main/java/"))
                    .filter(p -> !p.toString().replace('\\', '/').contains("/target/"))
                    .filter(p -> !p.toString().replace('\\', '/').contains("/archive/"))
                    .forEach(files::add);
        }
        return files;
    }

    static String read(Path path) {
        try {
            return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("读取源码失败: " + path, e);
        }
    }

    /**
     * 词法归一：抹平注释 + 还原字符串内转义引号；换行数与原文一致。
     */
    static String normalize(String source) {
        StringBuilder out = new StringBuilder(source.length());
        int i = 0;
        int n = source.length();
        while (i < n) {
            char c = source.charAt(i);
            if (c == '/' && i + 1 < n && source.charAt(i + 1) == '/') {
                while (i < n && source.charAt(i) != '\n') {
                    i++;
                }
            } else if (c == '/' && i + 1 < n && source.charAt(i + 1) == '*') {
                int end = source.indexOf("*/", i + 2);
                int stop = end < 0 ? n : end + 2;
                for (int k = i; k < stop; k++) {
                    out.append(source.charAt(k) == '\n' ? '\n' : ' ');
                }
                i = stop;
            } else if (c == '"') {
                StringBuilder literal = new StringBuilder("\"");
                i++;
                while (i < n) {
                    char sc = source.charAt(i);
                    if (sc == '\\' && i + 1 < n) {
                        char next = source.charAt(i + 1);
                        if (next == '"') {
                            literal.append('"');
                        } else {
                            literal.append(sc).append(next);
                        }
                        i += 2;
                        continue;
                    }
                    if (sc == '"') {
                        literal.append('"');
                        i++;
                        break;
                    }
                    if (sc == '\n') {
                        // 非法跨行字面量：交回后续分支按原文输出（编译不过的源码不该由本扫描器修）
                        break;
                    }
                    literal.append(sc);
                    i++;
                }
                out.append(literal);
            } else if (c == '\'') {
                out.append('\'');
                i++;
                while (i < n) {
                    char cc = source.charAt(i);
                    if (cc == '\\' && i + 1 < n) {
                        out.append(source, i, i + 2);
                        i += 2;
                        continue;
                    }
                    out.append(cc);
                    i++;
                    if (cc == '\'') {
                        break;
                    }
                }
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    /** 从运行目录向上遍历，定位同时含 ecos_frontend 与 ecos_backend 的仓库根。 */
    static Path locateRepoRoot() {
        Path dir = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 12 && dir != null; i++) {
            if (Files.isDirectory(dir.resolve("ecos_frontend"))
                    && Files.isDirectory(dir.resolve("ecos_backend"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException(
                "无法定位仓库根：自 user.dir 向上 12 级未找到同时含 ecos_frontend 与 ecos_backend 的目录");
    }
}
