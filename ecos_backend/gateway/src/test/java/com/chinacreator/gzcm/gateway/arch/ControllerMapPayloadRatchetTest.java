package com.chinacreator.gzcm.gateway.arch;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Controller 弱类型出入参「只减不增」棘轮门（DoD DI-2；#147AC 核定 → 本门落地）。
 *
 * <p><b>为什么是棘轮而不是等值</b>：DoD 判据是「无 {@code Map<String,Object>}」，实测存量
 * **2320 行 / 230 个 Controller 文件**（2026-11-02 实扫）。一次性清退属跨域契约重构，不在任何
 * 单批可达范围；若只把该行记成「未达成」而不设闸，存量会继续膨胀。故本门钉住「总数不增」：
 * 新增代码必须用强类型 DTO/VO，存量按域分批清退（每清退一处就把基线调低一次）。
 *
 * <p><b>口径</b>：main 源 {@code *Controller.java} 内**行级**命中
 * {@code Map<String,\s*Object>}（与核定时的 {@code grep -rn | wc -l} 同尺），排除
 * {@code target/} 与 {@code archive/}。基线变更必须同批给出清退证据（改小）或明确理由（改大=不许）。</p>
 *
 * @author ECOS Gateway #147AC/DI-2
 */
class ControllerMapPayloadRatchetTest {

    /** 2026-11-02 实扫基线（行级命中）。只允许下调。 */
    private static final int BASELINE_LINES = 2302;

    /** 扫描面下限（防扫描根漂移把门变成空转）。 */
    private static final int MIN_FILES = 200;
    private static final int MIN_LINES = 2000;

    private static final Pattern MAP_PAYLOAD = Pattern.compile("Map<String,\\s*Object>");

    @Test
    @DisplayName("Controller 的 Map<String,Object> 行数不得超过基线 2302（只减不增）")
    void controllerMapPayloadDoesNotGrow() throws IOException {
        List<String> offenders = new ArrayList<>();
        int lines = 0;
        int files = 0;

        try (Stream<Path> s = Files.walk(repoRoot().resolve("ecos_backend"))) {
            List<Path> controllers = s.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith("Controller.java"))
                    .filter(p -> !p.toString().contains("target"))
                    .filter(p -> !p.toString().contains("archive"))
                    .filter(p -> p.toString().contains("src" + java.io.File.separator + "main"))
                    .toList();
            files = controllers.size();
            for (Path file : controllers) {
                for (String line : Files.readAllLines(file)) {
                    if (MAP_PAYLOAD.matcher(line).find()) {
                        lines++;
                        if (offenders.size() < 20) {
                            offenders.add(file.getFileName() + ": " + line.trim());
                        }
                    }
                }
            }
        }

        assertTrue(files >= MIN_FILES,
                "扫描面退化：只看到 " + files + " 个 Controller（基线 " + MIN_FILES + "）");
        assertTrue(lines >= MIN_LINES,
                "命中面退化：只看到 " + lines + " 行 Map<String,Object>（基线 " + MIN_LINES + "）"
                        + "——若确已批量清退，请同批把 BASELINE_LINES 调低并留证据");
        assertTrue(lines <= BASELINE_LINES,
                "弱类型出入参在增长：" + lines + " 行 > 基线 " + BASELINE_LINES
                        + "（新增代码请用强类型 DTO/VO；样例行：\n" + String.join("\n", offenders) + "）");
    }

    @Test
    @DisplayName("反证：判据能变红（合成样例）且容忍空格变体")
    void predicateCatchesSpacingVariants() {
        assertTrue(MAP_PAYLOAD.matcher("public ApiResponse<Map<String,Object>> f() {").find());
        assertTrue(MAP_PAYLOAD.matcher("List<Map<String, Object>> rows = ...").find());
        assertTrue(!MAP_PAYLOAD.matcher("Map<String, String> m").find());
        assertTrue(!MAP_PAYLOAD.matcher("Map<String, MyDto> m").find());
    }

    /** Surefire CWD 漂移：向上探测同时含 ecos_backend/ 与 ecos-docker/ 的仓库根。 */
    private static Path repoRoot() {
        Path cur = Paths.get("").toAbsolutePath().normalize();
        for (int i = 0; i < 8 && cur != null; i++) {
            if (Files.isDirectory(cur.resolve("ecos_backend")) && Files.isDirectory(cur.resolve("ecos-docker"))) {
                return cur;
            }
            cur = cur.getParent();
        }
        throw new IllegalStateException("未找到仓库根，CWD=" + Paths.get("").toAbsolutePath());
    }
}
