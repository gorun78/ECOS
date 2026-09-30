package com.chinacreator.gzcm.engine.data.pipeline.w59;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.chinacreator.gzcm.engine.data.pipeline.PipelineNodeTypesCatalog;
import com.chinacreator.gzcm.engine.data.pipeline.PipelineNodeTypesService;
import com.chinacreator.gzcm.engine.data.pipeline.PipelineNodeTypesVO;
import com.chinacreator.gzcm.engine.data.pipeline.PipelineServiceImpl;

/**
 * F02 / W59 《NodeCatalogParityTest（W559 行为变体）》— 节点目录 端到端 shape 与消费点对账
 * （设计分册 02 D.2，M0 缺口收口，2026-09-30 起算）。
 *
 * <p>与既有 W57 {@code NodeCatalogParityTest}（目录 ↔ 执行器 switch 等值）互补：本测试
 * 锁 <b>D.2 消费点</b> —— {@code GET /api/v1/pipeline/node-types}（{@code
 * PipelineController#nodeTypes} → {@code PipelineNodeTypesService#listNodeTypes}）与
 * {@code savePipelineDefinition} 保存校验共用同一目录源
 * （{@link PipelineNodeTypesCatalog}）。</p>
 *
 * <p>断言四条（source-scan + POJO 实例化，无 Spring 上下文）：</p>
 * <ol>
 *   <li><b>消费点复用</b>：{@code PipelineServiceImpl} 源码含
 *       {@code PipelineNodeTypesCatalog.SUPPORTED} 与 {@code PipelineNodeTypesCatalog.isAvailable(...)}
 *       两调用（建卡位 + 可用性位双复用）；{@code PipelineNodeTypesService} 源码含
 *       调用。以防单一集中漂移（目录修一处、消费点漏改另一处）。</li>
 *   <li><b>VO shape</b>：{@code PipelineNodeTypesVO.EntryVO} 必须暴露
 *       {@code name} / {@code available} / {@code unavailableReason}
 *       三字段（i18n 展示名 + 能力位 + 原因文案）—— Lombok @Data 主码可取，本测试
 *       靠反射直接锁签名，不依赖预先 import Lombok。</li>
 *   <li><b>行为：实例化 listNodeTypes()</b> —— 无参构造 {@code PipelineNodeTypesService}
 *       (POJO，无依赖)，调 {@code listNodeTypes()} 得到目录清单：
 *       {@code items.stream().count() >= 12}（M0 缺口预期：当前 12 类，若后续 B 阶段补
 *       SOURCE_OB/Kingbase/Dmdb/PolarDB &nbsp;等 JDBC 方言，仅下限不封顶）；
 *       每个 item {@code name} 非空 (展示名)，{@code available} 非 null (Boolean)。</li>
 *   <li><b>源码扫描服务清单</b>：{@code PipelineNodeTypesService.listNodeTypes()} 源码里
 *       {@code entry("TYPE"} 行数 ≥ 12（目录 VO 项来自源码字面量，非硬构造）。</li>
 * </ol>
 *
 * @author ecos-p0-data-engine
 * <p>2026-09-30（W59 / M0；设计行：D.2 节点目录端点 + 保存校验同源）</p>
 */
@DisplayName("F02/W59：节点目录 消费点同源 + shape 完整 + 行为 12+ 行清单")
class NodeCatalogParityTest {

    private static final String SVC_IMPL = "PipelineServiceImpl.java";
    private static final String SVC_TYPES = "PipelineNodeTypesService.java";
    private static final String CATALOG = "PipelineNodeTypesCatalog.java";
    private static final String REL_PIPE =
            "src/main/java/com/chinacreator/gzcm/engine/data/pipeline/";

    /** W59 源目录（复用主源码树，不 find 副片）。 */
    private static final Path MAIN =
            moduleRoot().resolve(REL_PIPE);

    /** D.2 请求产物（VO 实例化）：{@code listNodeTypes()} 的返回清单。 */
    private static PipelineNodeTypesVO capturedVo;

    @BeforeAll
    static void captureVo() {
        capturedVo = new PipelineNodeTypesService().listNodeTypes();
    }

    @Test
    @DisplayName("消费点复用：保存校验 & node-types 端点共同同源于 Catalog（禁切换/硬造）")
    void consumersReuseCatalog() throws IOException {
        String implSrc = read(PipelineServiceImpl.class);
        assertTrue(implSrc.contains("PipelineNodeTypesCatalog.SUPPORTED"),
                "PipelineServiceImpl 必须复用 PipelineNodeTypesCatalog.SUPPORTED "
                        + "作 VALID_NODE_TYPES（W59 D.2 消费点同源）");
        assertTrue(implSrc.contains("PipelineNodeTypesCatalog.isAvailable("),
                "PipelineServiceImpl 必须复用 PipelineNodeTypesCatalog.isAvailable(type) "
                        + "作为可用位判定（W59 D.2）");

        String svcSrc = readSVC_TYPES();
        assertTrue(svcSrc.contains("PipelineNodeTypesCatalog.isAvailable("),
                "PipelineNodeTypesService.listNodeTypes 必须复用 Catalog.isAvailable "
                        + "作为 available 位（W59 D.2 VO 同源）");
        assertTrue(svcSrc.contains("PipelineNodeTypesCatalog.unavailableReasonOf("),
                "PipelineNodeTypesService.listNodeTypes 必须复用 Catalog.unavailableReasonOf "
                        + "作为 unavailableReason（W59 D.2 VO 同源）");
    }

    @Test
    @DisplayName("shape：EntryVO.name / available / unavailableReason 三字段以 Lombok @Data 反射暴露")
    void entryVoExposesNameAndAvailableFlags() throws Exception {
        Class<?> entry = Class.forName(
                "com.chinacreator.gzcm.engine.data.pipeline.PipelineNodeTypesVO$EntryVO");
        assertNotNull(entry.getDeclaredField("name"),
                "EntryVO 必须含 name 字段（展示名，非空）");
        java.lang.reflect.Field fAvail = entry.getDeclaredField("available");
        assertEquals(Boolean.class, fAvail.getType(),
                "EntryVO.available 必须为 Boolean（W59/F02-04 能力位，禁 null 放入）");
        assertNotNull(entry.getDeclaredField("unavailableReason"),
                "EntryVO 必须含 unavailableReason (available=false 原因文案)");
        // name / available 反射判可写（Lombok @Data 的行为面）
        // 必须复用同一 Field 实例做 setAccessible + set（反射访问标志不跨 getDeclaredField 传播）
        java.lang.reflect.Field fName = entry.getDeclaredField("name");
        java.lang.reflect.Field fAvailable = entry.getDeclaredField("available");
        fName.setAccessible(true);
        fAvailable.setAccessible(true);
        Object entryObj = entry.getDeclaredConstructor().newInstance();
        fName.set(entryObj, "probe");
        fAvailable.set(entryObj, Boolean.TRUE);
    }

    @Test
    @DisplayName("行为：无参 POJO listNodeTypes → items 非空 + 12+ 行（M0 任务集下限）")
    void listNodeTypesReturnsFullCatalog() {
        assertNotNull(capturedVo, "listNodeTypes() 不得返回 null");
        assertNotNull(capturedVo.getItems(), "items 不得为 null");
        List<PipelineNodeTypesVO.EntryVO> items = capturedVo.getItems();
        assertTrue(items.size() >= 12,
                "listNodeTypes() 必须返回 ≥ 12 项（前端基础 5 + PMO-36 4 + SINK_MINIO + "
                        + "SOURCE_MINIO + TRANSFORM_DOC_PARSE）。当前：" + items.size());
    }

    @Test
    @DisplayName("行为：每项 name 非空展示名 + available 非 null Boolean + 不可用必带 reason")
    void eachEntryNonEmptyNameAndNonNullAvailable() {
        for (PipelineNodeTypesVO.EntryVO e : capturedVo.getItems()) {
            assertNotNull(e.getType(), "每 item 不得缺 type");
            assertFalse(e.getType().isBlank(), "每 item type 不得空白");
            assertNotNull(e.getName(), "每 item name 不得 null（展示名）");
            assertFalse(e.getName().isBlank(), "每 item name 不得空白：" + e.getType());
            assertNotNull(e.getAvailable(),
                    "每 item available 不得 null Boolean（F02-04 能力位）：" + e.getType());
            if (Boolean.FALSE.equals(e.getAvailable())) {
                assertNotNull(e.getUnavailableReason(),
                        "available=false 的 item 必须含 unavailableReason：" + e.getType());
                assertFalse(e.getUnavailableReason().isBlank(),
                        "available=false 的 unavailableReason 不得空白：" + e.getType());
            }
        }
    }

    @Test
    @DisplayName("源码扫描：listNodeTypes 内 entry(\"TYPE\") 字面量行数 ≥ 12（目录 VO 项自源码，非硬构造）")
    void serviceSourceDeclaresAtLeastTwelveEntries() throws IOException {
        String svcSrc = readSVC_TYPES();
        List<String> lines = List.of(svcSrc.split("\\r?\\n"));
        long entryLiterals = lines.stream()
                .filter(l -> Pattern.compile("\\bentry\\(\\s*\"[A-Z][A-Z0-9_]*\"\s*,")
                        .matcher(l).find())
                .count();
        assertTrue(entryLiterals >= 12,
                "PipelineNodeTypesService.listNodeTypes 源码必须字面声明 ≥ 12 个 entry(\"TYPE\") "
                        + "行（当前 " + entryLiterals + " —— 目录项禁止压缩硬造）");
    }

    @Test
    @DisplayName("Catalog SUPPORTED 集合 = VO 清单全部 type（W59 清单不相交）")
    void voTypesAllInSupportedSet() {
        for (PipelineNodeTypesVO.EntryVO e : capturedVo.getItems()) {
            assertTrue(PipelineNodeTypesCatalog.SUPPORTED.contains(e.getType()),
                    "listNodeTypes 返回的 type 必须都在 Catalog.SUPPORTED 里：" + e.getType());
        }
    }

    // ==================== 工具 ====================

    private static Path moduleRoot() {
        Path cursor = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        while (cursor != null) {
            if (Files.isDirectory(cursor.resolve(
                    "src/main/java/com/chinacreator/gzcm/engine/data/pipeline"))) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("无法定位 data-engine-impl 模块根: "
                + System.getProperty("user.dir"));
    }

    private static String readSVC_TYPES() throws IOException {
        Path p = moduleRoot().resolve(REL_PIPE).resolve(SVC_TYPES);
        assertTrue(Files.isRegularFile(p), "缺 " + SVC_TYPES + ": " + p);
        return Files.readString(p, StandardCharsets.UTF_8);
    }

    private static String read(Class<?> impl) throws IOException {
        Path p = moduleRoot().resolve(REL_PIPE).resolve(impl.getSimpleName() + ".java");
        assertTrue(Files.isRegularFile(p), "缺 " + impl.getSimpleName() + ": " + p);
        return Files.readString(p, StandardCharsets.UTF_8);
    }
}
