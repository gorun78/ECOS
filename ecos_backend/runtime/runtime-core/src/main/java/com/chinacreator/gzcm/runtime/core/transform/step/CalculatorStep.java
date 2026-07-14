package com.chinacreator.gzcm.runtime.core.transform.step;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import com.chinacreator.gzcm.runtime.core.transform.TransformException;
import com.chinacreator.gzcm.runtime.core.transform.TransformStep;
import com.chinacreator.gzcm.runtime.core.transform.model.DataFrame;

/**
 * 计算器转换步骤
 * 基于表达式计算新字段或更新现有字段
 * 
 * 参数说明：
 * - expressions: List<Map<String, Object>>，计算表达式列表
 *   - field: String，目标字段名（如果不存在则创建新字段）
 *   - expression: String，计算表达式（支持JavaScript语法，可以使用其他字段名作为变量）
 *   - type: String (可选)，结果类型（string, integer, double, boolean），默认根据计算结果推断
 */
public class CalculatorStep implements TransformStep {
    
    private static final ScriptEngineManager scriptEngineManager = new ScriptEngineManager();
    private static final ThreadLocal<ScriptEngine> scriptEngine = ThreadLocal.withInitial(() -> 
        scriptEngineManager.getEngineByName("JavaScript"));

    @Override
    public String getName() {
        return "Calculator";
    }

    @Override
    public String getType() {
        return "calculator";
    }

    @Override
    public DataFrame transform(DataFrame input, Map<String, Object> params) throws TransformException {
        if (input == null || input.isEmpty()) {
            return input;
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> expressions = (List<Map<String, Object>>) params.get("expressions");
        if (expressions == null || expressions.isEmpty()) {
            return input;
        }

        DataFrame output = new DataFrame();
        List<String> outputColumns = new ArrayList<>(input.getColumns());
        
        // 添加新字段到列列表
        for (Map<String, Object> expr : expressions) {
            String field = (String) expr.get("field");
            if (field != null && !outputColumns.contains(field)) {
                outputColumns.add(field);
            }
        }
        output.setColumns(outputColumns);
        output.setMetadata(new HashMap<>(input.getMetadata()));

        ScriptEngine engine = scriptEngine.get();

        for (Map<String, Object> row : input.getRows()) {
            Map<String, Object> newRow = new HashMap<>(row);

            for (Map<String, Object> expr : expressions) {
                String field = (String) expr.get("field");
                String expression = (String) expr.get("expression");
                String type = (String) expr.get("type");

                if (field == null || expression == null) {
                    continue;
                }

                try {
                    // 将当前行的所有字段作为变量注入到脚本引擎
                    for (Map.Entry<String, Object> entry : row.entrySet()) {
                        engine.put(entry.getKey(), entry.getValue());
                    }

                    // 执行表达式
                    Object result = engine.eval(expression);

                    // 类型转换
                    if (type != null) {
                        result = convertResult(result, type);
                    }

                    newRow.put(field, result);
                } catch (ScriptException e) {
                    throw new TransformException("Failed to evaluate expression for field '" + field + "': " + expression, e);
                }
            }

            output.addRow(newRow);
        }

        return output;
    }

    private Object convertResult(Object result, String type) {
        if (result == null) {
            return null;
        }

        switch (type.toLowerCase()) {
            case "string":
                return result.toString();
            case "integer":
            case "int":
                if (result instanceof Number) {
                    return ((Number) result).intValue();
                }
                return Integer.parseInt(result.toString());
            case "double":
                if (result instanceof Number) {
                    return ((Number) result).doubleValue();
                }
                return Double.parseDouble(result.toString());
            case "boolean":
            case "bool":
                if (result instanceof Boolean) {
                    return result;
                }
                String str = result.toString().toLowerCase();
                return "true".equals(str) || "1".equals(str);
            default:
                return result;
        }
    }

    @Override
    public boolean validateParams(Map<String, Object> params) {
        if (params == null) {
            return false;
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> expressions = (List<Map<String, Object>>) params.get("expressions");
        if (expressions == null || expressions.isEmpty()) {
            return false;
        }
        for (Map<String, Object> expr : expressions) {
            String field = (String) expr.get("field");
            String expression = (String) expr.get("expression");
            if (field == null || expression == null) {
                return false;
            }
        }
        return true;
    }
}

