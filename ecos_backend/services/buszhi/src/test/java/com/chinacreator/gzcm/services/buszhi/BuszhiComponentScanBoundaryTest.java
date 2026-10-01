package com.chinacreator.gzcm.services.buszhi;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * F03-14 / W88 (O-20) 架构守卫《BuszhiComponentScanBoundaryTest》—
 * buszhi 服务不再扫描 data-engine（土·D）包。
 *
 * <p>架构铁律 §0.3.1 单向依赖：buszhi（金·I 本体域）封装 service 层，不得把
 * data-engine 的实现类（如 {@code CopilotServiceImpl}）经 {@code @ComponentScan}
 * / {@code @MapperScan} 的 {@code basePackages} 撤入自身 JVM。原先为
 * {@code OntologyCopilotController} 的 {@code ICopilotService} 依赖而扫描
 * {@code com.chinacreator.gzcm.engine.data.service} 属跨域越界，现由本域的
 * {@code CopilotServiceShim}（同包 {@code ...services.buszhi.impl}）承接。</p>
 *
 * <p>本守卫对 {@code BuszhiServiceApplication.java} 源码读取，断言其
 * {@code @ComponentScan}/{@code @MapperScan} 的 {@code basePackages}/
 * {@code basePackageClasses} 中<b>不存在</b>任何 {@code com.chinacreator.gzcm.engine.data}
 * token（含 {@code engine.data.} 前缀形式的裸引用）。</p>
 */
@DisplayName("F03-14/W88(O-20)：buszhi 不再扫描 data-engine 包（@ComponentScan/@MapperScan 无 engine.data）")
class BuszhiComponentScanBoundaryTest {

    /** 任一 engine.data 包 token（既有全限定 com.chinacreator...engine.data，亦有裸 engine.data. 前缀）。 */
    private static final Pattern ENGINE_DATA_PACKAGE = Pattern.compile(
            "(?:com\\.chinacreator\\.gzcm\\.)?engine\\.data\\b");

    private static final String APP_REL =
            "ecos_backend/services/buszhi/src/main/java/com/chinacreator/gzcm/services/buszhi/BuszhiServiceApplication.java";

    @Test
    @DisplayName("BuszhiServiceApplication 的 @ComponentScan/@MapperScan 无任何 engine.data 包条目")
    void buszhiScanHasNoDataEnginePackage() throws IOException {
        Path app = findRepoRoot().resolve(APP_REL);
        assertTrue(Files.exists(app), "缺失 BuszhiServiceApplication 源文件: " + app);

        List<String> lines = Files.readAllLines(app, StandardCharsets.UTF_8);
        List<String> violations = new ArrayList<>();
        int lin = 0;
        for (String raw : lines) {
            lin++;
            String t = raw.trim();
            // 跳过纯注释行（规则意图锁定 token 字样本身，而非注释措辞）
            if (t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")) {
                continue;
            }
            if (ENGINE_DATA_PACKAGE.matcher(raw).find()) {
                violations.add(app.getFileName() + ":" + lin + ": 命中 data-engine 包 token: " + t);
            }
        }
        assertTrue(violations.isEmpty(),
                "W88/O-20：buszhi 不得经 @ComponentScan/@MapperScan 扫描 data-engine 包"
                        + "（跨域越界；Copilot 应由本域 CopilotServiceShim 承接）：\n"
                        + String.join("\n", violations));
    }

    static Path findRepoRoot() {
        Path dir = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        Path cursor = dir;
        while (cursor != null) {
            if (Files.isDirectory(cursor.resolve(".git"))
                    || Files.exists(cursor.resolve(".git"))) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        cursor = dir;
        while (cursor != null) {
            if (Files.exists(cursor.resolve("pom.xml"))) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("无法从 user.dir=" + dir + " 定位仓库根（.git/pom.xml 均缺失）");
    }
}
