package com.chinacreator.gzcm.engine.data.fact.service;

import com.chinacreator.gzcm.common.data.DataDomainErrorCode;
import com.chinacreator.gzcm.common.exception.DataBridgeException;
import com.chinacreator.gzcm.common.security.decision.DataScopeContract;
import com.chinacreator.gzcm.engine.data.fact.dao.AttributionFactMapper;
import com.chinacreator.gzcm.engine.data.fact.dao.CostFactMapper;
import com.chinacreator.gzcm.engine.data.fact.dao.ForecastSnapshotMapper;
import com.chinacreator.gzcm.engine.data.fact.dao.ResourceFactMapper;
import com.chinacreator.gzcm.engine.data.fact.dao.StageFactMapper;
import com.chinacreator.gzcm.engine.data.fact.dq.DqRowGate;
import com.chinacreator.gzcm.engine.data.fact.period.PeriodLock;
import org.apache.ibatis.annotations.Param;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * #142/B4-3e-79（差距 D-B4-36 属主侧）— {@code GET /api/v1/datanet/facts/attribution/departments}
 * 的供数面门（{@code FactController#listAttributionDepartments} → 本服务方法）。
 *
 * <p><b>属主侧的纪律只有三件，每件都有正反判据</b>：</p>
 * <ol>
 *   <li><b>只供数、不判定</b>：返回的是「项目 → 权威归属部门」事实与去重后的部门集合，
 *       <b>不含</b>任何"是否越权/该放行"的结论（判定归 security 的
 *       {@code DeclaredScopeResolutionService}）。这一条靠"读面只触达一枚只读查询"取证。</li>
 *   <li><b>禁无项目集的全表展开</b>：空/全空白入参 → 400 {@code ECOS-DATA-023}，且一次查询都不发。
 *       没有这条，"声明域解析"会变成任意调用方都能拉的<b>全量组织归属导出</b>面。</li>
 *   <li><b>批量上限同族（EN03 ≤100）</b>：超限同样 400，防止把 IN 列表当无界扇出。</li>
 * </ol>
 *
 * <p>失败腿一律以 {@link DataBridgeException} 携带状态行抛出，<b>不写成</b>
 * {@code return ApiResponse.badRequest(...)}——后者会命中全后端裸回棘轮门
 * {@code SecurityDenialHttpStatusRatchetTest} 的判据 B 只减不增上限（属主侧新增裸回即红）。</p>
 */
@ExtendWith(MockitoExtension.class)
class AttributionDepartmentLookupTest {

    @Mock private StageFactMapper stageMapper;
    @Mock private AttributionFactMapper attributionMapper;
    @Mock private ResourceFactMapper resourceMapper;
    @Mock private CostFactMapper costMapper;
    @Mock private ForecastSnapshotMapper forecastMapper;

    private FactImportService service() {
        DqRowGate okGate = (t, rows, ctx) -> List.of();
        PeriodLock openLock = p -> false;
        return new FactImportService(attributionMapper, stageMapper, resourceMapper,
                costMapper, forecastMapper, okGate, openLock, Optional.empty(), 0.9);
    }

    private static Map<String, Object> row(String projectId, String departmentId) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("projectId", projectId);
        m.put("departmentId", departmentId);
        return m;
    }

    @Test
    @DisplayName("正常供数：requested 去重保序、每项都有键（无归属＝空数组）、departments 去重排序")
    void groupsRowsWithoutJudgingAnything() {
        when(attributionMapper.listDepartmentRowsByProjectIds(anyList())).thenReturn(List.of(
                row("P2", "D-B"), row("P1", "D-A"), row("P2", "D-A"), row("P3", null)));

        Map<String, Object> out = service().listAttributionDepartments(
                new ArrayList<>(List.of("P1", "P2", "P1", "P3", "  ")));

        assertEquals(List.of("P1", "P2", "P3"), out.get("requested"),
                "空白项丢弃 + 去重 + 保持声明顺序（消费方按此逐值对账）");
        @SuppressWarnings("unchecked")
        Map<String, List<String>> byProject = (Map<String, List<String>>) out.get("byProject");
        assertEquals(List.of("D-A"), byProject.get("P1"));
        assertEquals(List.of("D-B", "D-A"), byProject.get("P2"), "行内顺序按属主读数原样承流");
        assertEquals(List.of(), byProject.get("P3"),
                "查无归属必须回显空数组，而不是缺键——缺键会让消费方读成\"没问这个项目\"");
        assertEquals(List.of("D-A", "D-B"), out.get("departments"), "跨项目部门集合去重 + 稳定序");

        // 只读取证：除这一枚只读查询外，五张 fact 表的任何写/读面都不该被触达
        verify(attributionMapper).listDepartmentRowsByProjectIds(List.of("P1", "P2", "P3"));
        verifyNoInteractions(stageMapper, resourceMapper, costMapper, forecastMapper);
    }

    @Test
    @DisplayName("空/全空白项目集 → 400 ECOS-DATA-023 且零查询（禁退化成全量归属导出面）")
    void emptyScopeIsRejectedBeforeAnyQuery() {
        List<List<String>> bad = List.of(List.of(), java.util.Arrays.asList("  ", null),
                java.util.Arrays.asList((String) null));
        for (List<String> ids : bad) {
            DataBridgeException ex = assertThrows(DataBridgeException.class,
                    () -> service().listAttributionDepartments(ids), "空声明不得展开全表");
            assertTrue(ex.getMessage().contains(DataDomainErrorCode.ECOS_DATA_023),
                    "机读码必须在场：" + ex.getMessage());
            assertEquals(400, ex.getHttpStatus(), "参数面错误不得折成 500");
        }
        verifyNoInteractions(attributionMapper, stageMapper, resourceMapper, costMapper, forecastMapper);
    }

    @Test
    @DisplayName("超 EN03 同族上限（>100 项）→ 400 且零查询（IN 列表不是无界扇出通道）")
    void overCapIsRejected() {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i <= FactImportService.MAX_PROJECT_IDS_PER_LOOKUP; i++) {
            ids.add("P" + i);
        }
        DataBridgeException ex = assertThrows(DataBridgeException.class,
                () -> service().listAttributionDepartments(ids));
        assertTrue(ex.getMessage().contains(String.valueOf(FactImportService.MAX_PROJECT_IDS_PER_LOOKUP)),
                "400 要回显上限，调用方才猜得到改法：" + ex.getMessage());
        verifyNoInteractions(attributionMapper);
    }

    @Test
    @DisplayName("边界：恰好 100 项放行到查询（上限是\"最多 100\"，不是\"小于 100\"）")
    void exactlyCapReachesTheQuery() {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < FactImportService.MAX_PROJECT_IDS_PER_LOOKUP; i++) {
            ids.add("P" + i);
        }
        when(attributionMapper.listDepartmentRowsByProjectIds(anyList())).thenReturn(List.of());

        Map<String, Object> out = service().listAttributionDepartments(ids);

        assertEquals(ids, out.get("requested"));
        @SuppressWarnings("unchecked")
        Map<String, List<String>> byProject = (Map<String, List<String>>) out.get("byProject");
        assertEquals(FactImportService.MAX_PROJECT_IDS_PER_LOOKUP, byProject.size(),
                "无行也要逐项回显空数组，让消费方能区分\"没归属\"与\"没问\"");
        verify(attributionMapper).listDepartmentRowsByProjectIds(ids);
    }

    @Test
    @DisplayName("属主读数里的未知项目也如实回显（禁静默丢弃：那会让消费方把多归属读成单归属）")
    void unexpectedRowIsEchoedNotSwallowed() {
        when(attributionMapper.listDepartmentRowsByProjectIds(anyList()))
                .thenReturn(List.of(row("P-OUTSIDE", "D-X")));

        Map<String, Object> out = service().listAttributionDepartments(List.of("P1"));

        @SuppressWarnings("unchecked")
        Map<String, List<String>> byProject = (Map<String, List<String>>) out.get("byProject");
        assertEquals(List.of(), byProject.get("P1"));
        assertEquals(List.of("D-X"), byProject.get("P-OUTSIDE"),
                "读数面与请求面不一致时如实并列，由消费方对账失败暴露漂移，禁属主侧静默裁剪");
    }

    /**
     * XML 键面绑定钉（D-B4-36 单源面的唯一不可编译期证明处）。
     *
     * <p>{@code DataScopeSingleSourceGateTest} 只扫 {@code .java} 主源码，而 MyBatis 的
     * {@code <foreach collection="...">} 写在 XML 里、无法引用 Java 常量 —— 于是
     * {@code "projectIds"} 在仓内合法地存在<b>第二处字面量</b>。该处与 {@code @Param}
     * 之间的等值只由<b>运行时</b>保证（改常量 ⇒ MyBatis 报 "Parameter not found"，
     * 改 XML ⇒ 同一时刻报），编译期零信号。本钉把它从"约定"升级为"实跑断言"。</p>
     */
    @Test
    @DisplayName("XML 键面绑定：@Param 与 <foreach collection> 同取 DataScopeContract 单源键（禁漂移成运行时才炸）")
    void mapperXmlCollectionBindsToTheContractKey() throws Exception {
        Method method = AttributionFactMapper.class
                .getMethod("listDepartmentRowsByProjectIds", List.class);
        Param param = method.getParameterAnnotations()[0].length == 0 ? null
                : (Param) method.getParameterAnnotations()[0][0];
        assertTrue(param != null, "取数腿参数缺 @Param ⇒ MyBatis 侧按 arg0 绑定，XML 键面漂移无人管");
        assertEquals(DataScopeContract.KEY_PROJECT_IDS, param.value(),
                "@Param 值必须等于单源常量（写死字面量即违反 D-B4-20 单源纪律）");

        Path xml = Path.of(System.getProperty("user.dir"))
                .resolve("src/main/resources/mapper/AttributionFactMapper.xml");
        assertTrue(Files.isRegularFile(xml), "XML 读不到不得当成无违例：" + xml);
        String text = Files.readString(xml, java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(text.contains("collection=\"" + DataScopeContract.KEY_PROJECT_IDS + "\""),
                "XML collection 名与 @Param 不一致 ⇒ 只在运行时炸，必须在此变红");
    }

}
