package com.chinacreator.gzcm.workspace.scenario;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V07 F07-25 反锚点（对应设计 §6.x 验收命令 line 559
 * `EndpointConfigTest#noHardcodedServicePortInAnyProfile`「遍历三档 profile yml 断言」）。
 *
 * 红线：workspace 三档 profile yml（standard default / enterprise / ultimate）
 * 允许出现的唯一端口是**本模块 spring.server.port: 18090**；任何其它 1808x / 1809x
 * 内网数字出现即红。
 *
 * 范围：
 * - 只扫 `application*.yml`（design line 559 的三档 profile yml 三项）；
 * - Java 内 `@Value("${ecos.*-base:http://localhost:1808x}")` 默认值不属本条
 *   （那些是 F07-17 底座收口 Runtime 化的存量 X-51/X-54，已在 §7.3 C163 行登记为
 *   F07-17 阻塞项，与本条 yml 反锚点分置双红）；
 * - 反虚假：本条不经 ArchUnit 亦不经 WireMock，纯文本 pattern，可离线证伪。
 */
class EndpointConfigTest {

    /** 允许字符串——"workspace 自身端口 18090"字面。 */
    private static final Pattern SELF_PORT =
            Pattern.compile("port\\s*:\\s*18090\\b");

    /** 禁止字符串——任意 1808x 或 1809x（本端口除外）。 */
    private static final Pattern FORBIDDEN =
            Pattern.compile("\\b1808[0-9]\\b|\\b1809[0-9]\\b");

    @Test
    void noHardcodedServicePortInAnyProfile() throws IOException {
        Path implRoot = monorepoRoot().resolve("ecos_backend/workspace");
        assertTrue(Files.isDirectory(implRoot), "workspace 模块根目录缺失: " + implRoot);

        List<String> hits = new ArrayList<>();
        String[] candidates = {
                "workspace-service/src/main/resources/application.yml",
                "workspace-service/src/main/resources/application-standard.yml",
                "workspace-service/src/main/resources/application-enterprise.yml",
                "workspace-service/src/main/resources/application-ultimate.yml",
                "workspace-impl/src/main/resources/application.yml",
                "workspace-impl/src/main/resources/application-standard.yml",
                "workspace-impl/src/main/resources/application-enterprise.yml",
                "workspace-impl/src/main/resources/application-ultimate.yml",
        };
        int scanned = 0;
        for (String candidate : candidates) {
            Path p = implRoot.resolve(candidate);
            if (!Files.exists(p)) {
                continue; // 该 profile yml 可缺省（standard 为 default 合并）
            }
            scanned++;
            List<String> lines = Files.readAllLines(p);
            for (int i = 0; i < lines.size(); i++) {
                String raw = lines.get(i);
                // 剥行内注释段（保留 # 前的 code）
                int hash = raw.indexOf('#');
                String code = hash >= 0 ? raw.substring(0, hash) : raw;
                if (code.isBlank()) continue;

                // 提取该行所有 1808x/1809x 数字，若**每一个**都是 18090 且是 server.port 允许
                Matcher m = FORBIDDEN.matcher(code);
                boolean hasForbidden = false;
                while (m.find()) {
                    String token = m.group();
                    if (token.equals("18090") && SELF_PORT.matcher(code).find()) {
                        // 本模块自身端口，视为合法
                        continue;
                    }
                    hasForbidden = true;
                    break;
                }
                if (hasForbidden) {
                    hits.add(p.getFileName() + ":" + (i + 1) + "  " + raw.trim());
                }
            }
        }
        assertTrue(scanned > 0, "预期至少扫到 1 个 workspace yml（三档 profile 可缺省但 default 必存在）");
        assertTrue(hits.isEmpty(),
                "workspace profile yml 命中硬编码跨引擎/非本模块端口（该段应走 env 注入，非本地数字）：\n  "
                        + String.join("\n  ", hits));
    }

    private static Path monorepoRoot() {
        String override = System.getenv("ECOS_ROOT");
        if (override != null && !override.isBlank()) {
            return Paths.get(override);
        }
        return Paths.get("").toAbsolutePath().getParent().getParent().getParent();
    }
}
