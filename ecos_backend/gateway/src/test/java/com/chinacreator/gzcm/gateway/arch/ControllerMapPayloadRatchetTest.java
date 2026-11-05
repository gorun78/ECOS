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
 * <p><b>基线台账（改小必须留这一行的来源）</b>：
 * <ul>
 *   <li>2302 ＝ #147AC（2026-11-02）核定值；</li>
 *   <li>2297 ＝ #147Z（2026-11-04）HEAD 面复测值，差额 −5 行<b>全部</b>来自
 *       {@code OntologyGitController}（6→1，commit {@code 279bd553}「[§2.9.9 VG-2] 本体 Git
 *       服务契约去 Map」），其余 6 枚自核定以来改过的 Controller 计数未变——即本次下调
 *       <b>是真实清退，不是把判据放松</b>。</li>
 *   <li>2265 ＝ #147BT（2026-11-05）HEAD 面复测值，差额 −32 行<b>全部</b>来自
 *       {@code services/datanet/GitController}（33→1）：请求体 {@code Map} 改 DTO
 *       （commit/tag/rollback/branch），八条响应改 VO；剩余 1 枚是 {@code toDiffVO} 承接属主
 *       {@link com.chinacreator.gzcm.runtime.access.git.GitService#diff} 返回值
 *       {@code Map<String,Object>} 的形参（属主契约未改，不在本批范围）。</li>
 *   <li>2261 ＝ #147BW（2026-11-05）HEAD 面复测值，差额 −4 行<b>全部</b>来自 sysman
 *       配置/参数列表读面：{@code ConfigController} 3→1、{@code SystemParamController} 3→1
 *       （分页响应改强类型 {@code ConfigPageVO}/{@code SystemParamPageVO}）；各剩 1 枚是
 *       服务契约 {@code listConfigs}/{@code listParams(Map<String,Object>)} 的入参。wire 键面
 *       由 {@code ConfigListWireShapeTest} 3 例按集合相等钉住（键改名即红）。</li>
 *   <li>2249 ＝ #147BX（2026-11-05）HEAD 面复测值，差额 −12 行<b>全部</b>来自 sysman
 *       {@code DictController}（14→2）：三类字典列表改 {@code DictTypeEntryVO}/
 *       {@code DictTypeSummaryVO}、删除回执改 {@code DictDeleteResultVO}；剩 2 枚是
 *       {@code refreshCache}/{@code getDictUsage} 两条透传腿的签名（属主 {@code IDictService}
 *       仍返 Map，改契约须连属主一并改，另批处理）。键面由 {@code DictControllerWireShapeTest}
 *       5 例按集合相等钉住（含「空类型 {@code subsystem} 键仍在、值为 null」的 null 语义）。</li>
 *   <li>2229 ＝ #147BY（2026-11-05）HEAD 面复测值，差额 −20 行<b>全部</b>来自 sysman
 *       {@code DictTableController}（20→0）：请求体改 record（{@code DictTableRequest}/
 *       {@code DictColumnRequest}/{@code ReorderRequest}），响应改 record VO；建表/改表回执
 *       <b>不含 {@code columns} 键</b>（旧 Map 即无，另立 {@code DictTableBaseVO}，避免用可空
 *       columns 造出多余键）。键面由 {@code DictTableWireShapeTest} 6 例按集合相等钉住
 *       （含键序、ISO 时间串、tags 数组、缺省字段不落值、400 校验不丢）。</li>
 * </ul>
 *
 * <p><b>记账时必须打折的一枚读数</b>：#147BT 提交时工作树实读 <b>2276</b> = 2265 + 11，
 * 这 11 行<b>全部</b>是并发会话未提交的在途面（{@code ScenarioBlueprintController} 6→7，
 * 以及未跟踪新文件 {@code CognitiveFrameworkController}(4)／{@code CognitiveExecutionController}(4)／
 * {@code CognitiveMethodController}(2)）。按本门「上调基线一律不许」的铁口径，基线取
 * HEAD 面 2265，<b>不</b>为在途面放宽；工作树红在未提交面是门在按其判据正常工作。
 * <b>#147BW/#147BX/#147BY 时该偏移不变</b>：工作树实读依次 2272 = 2261 + 11、2260 = 2249 + 11、
 * 2240 = 2229 + 11（同一批在途文件，未代改）。</p>
 *
 * <p><b>本门红了的正确处置</b>：只允许「把新增的 {@code Map<String,Object>} 改成 DTO/VO」或
 * 「同批给出净清退并把 {@link #BASELINE_LINES} 调低」。{@code Map<String,Object>} 出入参是
 * DoD DI-2 的违例本体，<b>上调基线一律不许</b>；共享工作树里若红差来自<b>他人未提交</b>的在途
 * 新增面，归因写进本报告即可，禁代改他人文件、更禁为其放宽基线。</p>
 *
 * @author ECOS Gateway #147AC/DI-2
 */
class ControllerMapPayloadRatchetTest {

    /**
     * HEAD 面实扫基线（行级命中）。只允许下调。
     * <p>#147BY 下调依据见类注释「基线台账」：2249 → 2229，−20 行来自 sysman 字典表/字段面。</p>
     */
    private static final int BASELINE_LINES = 2229;

    /** 扫描面下限（防扫描根漂移把门变成空转）。 */
    private static final int MIN_FILES = 200;
    private static final int MIN_LINES = 2000;

    private static final Pattern MAP_PAYLOAD = Pattern.compile("Map<String,\\s*Object>");

    @Test
    @DisplayName("Controller 的 Map<String,Object> 行数不得超过基线 2229（只减不增）")
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
