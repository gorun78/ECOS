// TODO D4: 归位 ge-service（格）
package com.chinacreator.gzcm.engine.data.transform.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.data.transform.TransformChain;
import com.chinacreator.gzcm.engine.data.transform.TransformException;
import com.chinacreator.gzcm.engine.data.transform.TransformStep;
import com.chinacreator.gzcm.engine.data.transform.impl.TransformChainImpl;
import com.chinacreator.gzcm.engine.data.transform.model.DataFrame;
import com.chinacreator.gzcm.engine.data.transform.model.TransformResult;
import com.chinacreator.gzcm.engine.data.transform.service.ITransformService;
import com.chinacreator.gzcm.engine.data.transform.step.CalculatorStep;
import com.chinacreator.gzcm.engine.data.transform.step.DataAggregationStep;
import com.chinacreator.gzcm.engine.data.transform.step.DataCleansingStep;
import com.chinacreator.gzcm.engine.data.transform.step.DataValidationStep;
import com.chinacreator.gzcm.engine.data.transform.step.FieldMappingStep;
import com.chinacreator.gzcm.engine.data.transform.step.TypeConversionStep;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ge (D→I) 数据转换 Controller — 暴露 transform 能力给前端/服务层。
 * <p>
 * 对应 G2 文档 §3.5「data transform 端点」：把原始数据 (data-engine 数据源)
 * 经清洗/映射/校验/类型转换/聚合/计算 6 步 Pipeline 转成结构化信息，
 * 输出可喂给 ontology-engine 做本体对象落地。
 * <p>
 * <b>路由</b>：{@code /api/v1/engine/data/transform}（质量/lineage 同路径模式），
 * 三滤波器已覆盖（{@code /api/v1/engine/**} permitAll + ClearanceInterceptor 豁免）。
 * <p>
 * <b>架构定位</b>：此 Controller 仅作 ge D→I 转换的 API 暴露层，
 * 不写 DB、不调 LLM、不自建调度 — 所有能力委托 {@link ITransformService}。
 * 转换步骤的 Bean 不被 Spring 注册（继承自 GZCM runtime 历史代码），
 * 因此 Controller 内按 type 字符串 {@code new} 创建（架构铁律允许的
 * 同模块 internal 实例化，不跨引擎）。
 *
 * @author ECOS Wave-2B ge D→I 收口
 */
@RestController
@RequestMapping("/api/v1/engine/data/transform")
public class TransformController {

    private final ITransformService transformService;

    public TransformController(ITransformService transformService) {
        this.transformService = transformService;
    }

    /**
     * 元数据端点 — 当前可用的转换步骤清单（type → name → 描述）。
     *
     * @return 步骤清单
     */
    @GetMapping("/meta")
    public ApiResponse<Map<String, Object>> meta() {
        List<Map<String, Object>> steps = buildStepRegistry();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("availableSteps", steps);
        payload.put("totalSteps", steps.size());
        return ApiResponse.success(payload);
    }

    /**
     * 执行转换 — 入参 {input: DataFrame, chain: [{type, params?}]}，
     * 按 chain 顺序执行 6 类 step 之一。
     *
     * @param body 请求体
     * @return 转换结果（含 output/statistics/errors/warnings）
     */
    @PostMapping("/execute")
    public ApiResponse<Map<String, Object>> execute(@RequestBody Map<String, Object> body) {
        // ── 参数校验 ──
        if (body == null) {
            return ApiResponse.badRequest("请求体不能为空");
        }
        Object inputObj = body.get("input");
        Object chainObj = body.get("chain");
        if (!(inputObj instanceof Map)) {
            return ApiResponse.badRequest("input 必须是 DataFrame 描述对象（含 columns[] / rows[]）");
        }
        if (!(chainObj instanceof List)) {
            return ApiResponse.badRequest("chain 必须是步骤数组（空数组表示直通）");
        }

        // ── 反序列化 DataFrame ──
        DataFrame input = toDataFrame((Map<String, Object>) inputObj);

        // ── 构建转换链 ──
        TransformChain chain = new TransformChainImpl();
        List<Map<String, Object>> stepDefs = (List<Map<String, Object>>) chainObj;
        for (Map<String, Object> def : stepDefs) {
            if (def == null || def.get("type") == null) {
                return ApiResponse.badRequest("step 必须含 type 字段");
            }
            String type = String.valueOf(def.get("type"));
            Map<String, Object> params = def.get("params") instanceof Map
                    ? (Map<String, Object>) def.get("params")
                    : null;
            TransformStep step = newStepByType(type, params);
            if (step == null) {
                return ApiResponse.badRequest("未知 step type: " + type);
            }
            chain.addStep(step, params);
        }

        // ── 执行 ──
        try {
            TransformResult result = transformService.transform(input, chain);
            return ApiResponse.success(toResultView(result));
        } catch (TransformException te) {
            return ApiResponse.badRequest("转换失败: " + te.getMessage());
        }
    }

    // ── 私有辅助 ─────────────────────────────────────

    /**
     * 6 类 step 注册表（与 G2 文档 §3.5 一一对应）。
     */
    private List<Map<String, Object>> buildStepRegistry() {
        List<Map<String, Object>> steps = new ArrayList<>();
        steps.add(stepMeta("cleansing", "DataCleansing", "数据清洗：trim / 去重 / 空值处理"));
        steps.add(stepMeta("mapping", "FieldMapping", "字段映射：源字段 → 目标字段重命名"));
        steps.add(stepMeta("typeConversion", "TypeConversion", "类型转换：string/integer/long/double/boolean/date"));
        steps.add(stepMeta("validation", "DataValidation", "数据验证：按规则过滤不符合条件的行"));
        steps.add(stepMeta("aggregation", "DataAggregation", "数据聚合：groupBy + sum/avg/count/min/max"));
        steps.add(stepMeta("calculator", "Calculator", "表达式计算：JS 表达式生成/更新字段"));
        return steps;
    }

    private Map<String, Object> stepMeta(String type, String name, String desc) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        m.put("name", name);
        m.put("description", desc);
        return m;
    }

    /**
     * 按 type 字符串新建 TransformStep 实例（mode A：同模块 internal new）。
     * 各 Step 无状态，单例并不必要，每次请求 new 一份最安全。
     */
    private TransformStep newStepByType(String type, Map<String, Object> params) {
        // params 由 TransformStep.transform 内部读取，这里仅做合法性提示
        switch (type) {
            case "cleansing":
                return new DataCleansingStep();
            case "mapping":
                return new FieldMappingStep();
            case "typeConversion":
                return new TypeConversionStep();
            case "validation":
                return new DataValidationStep();
            case "aggregation":
                return new DataAggregationStep();
            case "calculator":
                return new CalculatorStep();
            default:
                return null;
        }
    }

    /**
     * JSON body → {@link DataFrame}。
     * <p>兼容两种序列化：
     * <pre>
     * {"columns": ["a","b"], "rows": [{"a":1,"b":2}, ...]}
     * 或
     * {"data": [{"a":1,"b":2}, ...]}
     * </pre>
     */
    @SuppressWarnings("unchecked")
    private DataFrame toDataFrame(Map<String, Object> obj) {
        DataFrame df = new DataFrame();
        if (obj.containsKey("rows") && obj.get("rows") instanceof List) {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Object row : (List<Object>) obj.get("rows")) {
                if (row instanceof Map) {
                    rows.add((Map<String, Object>) row);
                }
            }
            df.setRows(rows);
        } else if (obj.containsKey("data") && obj.get("data") instanceof List) {
            List<Map<String, Object>> data = new ArrayList<>();
            for (Object row : (List<Object>) obj.get("data")) {
                if (row instanceof Map) {
                    data.add((Map<String, Object>) row);
                }
            }
            df.setData(data);
        }
        if (obj.containsKey("columns") && obj.get("columns") instanceof List) {
            List<String> cols = new ArrayList<>();
            for (Object c : (List<Object>) obj.get("columns")) {
                if (c != null) cols.add(c.toString());
            }
            df.setColumns(cols);
        }
        return df;
    }

    /**
     * {@link TransformResult} → JSON 视图（避免把 DataFrame 整个 dump 给前端）。
     */
    private Map<String, Object> toResultView(TransformResult result) {
        Map<String, Object> view = new LinkedHashMap<>();
        DataFrame output = result.getOutput();
        Map<String, Object> outMap = new LinkedHashMap<>();
        if (output != null) {
            outMap.put("columns", output.getColumns());
            outMap.put("rows", output.getRows());
        }
        view.put("output", outMap);
        view.put("success", result.isSuccess());
        view.put("errors", result.getErrors());
        view.put("warnings", result.getWarnings());
        TransformResult.TransformStatistics stats = result.getStatistics();
        if (stats != null) {
            Map<String, Object> statMap = new LinkedHashMap<>();
            statMap.put("inputCount", stats.getInputCount());
            statMap.put("outputCount", stats.getOutputCount());
            statMap.put("filteredCount", stats.getFilteredCount());
            statMap.put("errorCount", stats.getErrorCount());
            view.put("statistics", statMap);
        }
        return view;
    }
}
