package com.chinacreator.gzcm.workspace.forecast;

import com.chinacreator.gzcm.workspace.scenario.RunMode;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 12.9 具名离线门 {@code ForecastMoneyPrecisionTest}（差距分析 v1.0.6 随 B2② 落地，R9②「门禁补代码实现」口径）。
 *
 * <p>守的是预测 run 金额面上的三条红线：</p>
 * <ol>
 *   <li><b>禁浮点载体</b>（B-2 金额唯一来源 + 详设 12.3.4「BigDecimal 全程」）——
 *       {@code workspace.forecast} 包内任何字段/构造参数/方法参数/返回值都不得是
 *       {@code double}/{@code float}（含包装类型），一旦有人图省事用 double 承载金额即变红；</li>
 *   <li><b>跨 REST 通道不失真</b>——18 位有效数字 + 4 位小数的金额经 HTTP 原文搬运后
 *       字面量逐字符一致（缺省 JSON 解析会走 double，实测在该量级必然漂移）；</li>
 *   <li><b>缺位保持缺位</b>（R-62 ①）——区间三值物理无列时原样 null，
 *       禁适配器插值、取均值或补 0 冒充有区间。</li>
 * </ol>
 *
 * <p>认知侧同主题的确定性计算精度门已由既有
 * {@code DeterministicForecastArtifactTest}（cognitive-engine-impl）覆盖，本门只守 workspace 金额面，
 * 两侧不重复设闸也不互相顶替。</p>
 */
class ForecastMoneyPrecisionTest {

    private static final String FORECAST_PACKAGE = "com.chinacreator.gzcm.workspace.forecast";

    /** 浮点载体全名（原始 + 包装两类都算违约）。 */
    private static final Set<String> FLOATING_POINT_TYPES =
            Set.of("double", "float", "java.lang.Double", "java.lang.Float");

    @Test
    @DisplayName("金额面零浮点：forecast 包内字段/入参/返回值出现 double|float 即违例")
    void forecastMoneySurfaceHasNoFloatingPointCarrier() {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages(FORECAST_PACKAGE);
        assertTrue(!classes.isEmpty(), "未导入任何 forecast 类 —— 检查包名或模块依赖方向");

        Set<String> violations = new TreeSet<>();
        for (JavaClass owner : classes) {
            if (!owner.getPackageName().startsWith(FORECAST_PACKAGE)) {
                continue;
            }
            String ownerName = owner.getSimpleName();
            owner.getFields().forEach(field -> {
                if (isFloatingPoint(field.getRawType().getFullName())) {
                    violations.add(ownerName + "#" + field.getName() + " : " + field.getRawType().getFullName());
                }
            });
            owner.getConstructors().forEach(constructor ->
                    constructor.getRawParameterTypes().forEach(param -> {
                        if (isFloatingPoint(param.getFullName())) {
                            violations.add(ownerName + "<init>(" + param.getFullName() + ")");
                        }
                    }));
            owner.getMethods().forEach(method -> {
                method.getRawParameterTypes().forEach(param -> {
                    if (isFloatingPoint(param.getFullName())) {
                        violations.add(ownerName + "#" + method.getName() + "(" + param.getFullName() + ")");
                    }
                });
                if (isFloatingPoint(method.getRawReturnType().getFullName())) {
                    violations.add(ownerName + "#" + method.getName() + " : " + method.getRawReturnType().getFullName());
                }
            });
        }
        assertTrue(violations.isEmpty(),
                "金额不得经浮点载体（B-2 金额唯一来源，浮点会把金额洗成近似值）: " + violations);
    }

    @Test
    @DisplayName("18 位金额经 REST 通道原样到编排面：字面量逐字符一致，不落 double 近似")
    void longAmountSurvivesTheRestChannelWithoutDrift() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        Map<String, String> bodies = new LinkedHashMap<>();
        bodies.put(CognitiveForecastRunGateway.RESULTS_PATH, """
                {"code":0,"data":{"forecastRunId":"fc-0001",
                 "cells":[{"projectId":"P-1","period":"202601",
                           "baseline":1234567890123456.7891,"scenario":1234567890123456.7892}],
                 "baselineProfit":1234567890123456.7890,"scenarioProfit":1234567890123456.7890,
                 "p10":null,"p50":null,"p90":null,"quantileSource":"NOT_AVAILABLE"}}""");
        when(restTemplate.exchange(anyString(), any(HttpMethod.class), nullable(HttpEntity.class),
                eq(String.class), ArgumentMatchers.<String, Object>anyMap()))
                .thenAnswer(invocation -> {
                    String body = bodies.get(invocation.getArgument(0));
                    if (body == null) {
                        throw new AssertionError("未登记的路径模板: " + invocation.getArgument(0));
                    }
                    return new ResponseEntity<>(body, HttpStatus.OK);
                });

        Map<String, Object> results = new CognitiveForecastRunGateway(restTemplate)
                .readResults("fc-0001");

        assertEquals("1234567890123456.7890", String.valueOf(results.get("scenarioProfit")));
        Map<String, Object> cell = castMap((List<?>) results.get("cells"), 0);
        assertEquals("1234567890123456.7891", String.valueOf(cell.get("baseline")));
        assertEquals("1234567890123456.7892", String.valueOf(cell.get("scenario")));
        assertTrue(cell.get("baseline") instanceof BigDecimal,
                "金额落进 Map 后仍须是 BigDecimal，不得退化成 Double/String: " + cell.get("baseline").getClass());
    }

    @Test
    @DisplayName("三值缺位保持 null：run 视图与明细都不插值、不补 0（R-62 ①）")
    void missingQuantileStaysMissingInsteadOfSynthesized() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        Map<String, String> bodies = new LinkedHashMap<>();
        bodies.put(CognitiveForecastRunGateway.COMPUTE_PATH, """
                {"code":0,"data":{"forecastRunId":"fc-0001","status":"SUCCEEDED",
                 "idempotencyKey":"caller-key-1","contentHash":"caller-hash-1",
                 "quantile":{"p10":null,"p50":null,"p90":null}}}""");
        when(restTemplate.exchange(anyString(), any(HttpMethod.class), nullable(HttpEntity.class),
                eq(String.class), ArgumentMatchers.<String, Object>anyMap()))
                .thenAnswer(invocation -> {
                    String body = bodies.get(invocation.getArgument(0));
                    if (body == null) {
                        throw new AssertionError("未登记的路径模板: " + invocation.getArgument(0));
                    }
                    return new ResponseEntity<>(body, HttpStatus.OK);
                });

        ForecastRunCommand command = new ForecastRunCommand("scn-09", "FORECAST", "cal-2026", "v3",
                "2026-10-01T00:00:00", "snap-77", "f-12", "caller-key-1", "caller-hash-1",
                RunMode.FORMAL, null);
        ForecastRunView view = new CognitiveForecastRunGateway(restTemplate).submit(command);

        assertNull(view.getQuantile().getP10(), "禁按 baseline 插值造下界");
        assertNull(view.getQuantile().getP50(), "禁取均值冒充中位");
        assertNull(view.getQuantile().getP90(), "禁补 0 冒充有区间");
    }

    private static boolean isFloatingPoint(String rawTypeName) {
        return FLOATING_POINT_TYPES.contains(rawTypeName);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(List<?> rows, int index) {
        assertEquals(1, rows.size(), "夹具只种一行明细");
        return (Map<String, Object>) rows.get(index);
    }
}
