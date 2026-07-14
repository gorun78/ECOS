package com.chinacreator.gzcm.runtime.core.transform.step;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import com.chinacreator.gzcm.runtime.core.transform.TransformException;
import com.chinacreator.gzcm.runtime.core.transform.TransformStep;
import com.chinacreator.gzcm.runtime.core.transform.model.DataFrame;

/**
 * 数据验证转换步骤
 * 对数据进行验证，可以过滤不符合条件的数据
 * 
 * 参数说明：
 * - rules: List<Map<String, Object>>，验证规则列表
 *   - field: String，要验证的字段名
 *   - type: String，验证类型（required, notNull, notEmpty, min, max, pattern等）
 *   - value: Object，验证值（用于min/max/pattern等）
 * - onError: String (可选)，错误处理方式：skip（跳过）、fail（抛出异常）、keep（保留），默认skip
 */
public class DataValidationStep implements TransformStep {
    @Override
    public String getName() {
        return "DataValidation";
    }

    @Override
    public String getType() {
        return "validation";
    }

    @Override
    public DataFrame transform(DataFrame input, Map<String, Object> params) throws TransformException {
        if (input == null || input.isEmpty()) {
            return input;
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rules = (List<Map<String, Object>>) params.get("rules");
        String onError = (String) params.getOrDefault("onError", "skip");

        DataFrame output = new DataFrame();
        output.setColumns(new ArrayList<>(input.getColumns()));
        output.setMetadata(new HashMap<>(input.getMetadata()));

        for (Map<String, Object> row : input.getRows()) {
            boolean isValid = true;

            if (rules != null && !rules.isEmpty()) {
                for (Map<String, Object> rule : rules) {
                    String field = (String) rule.get("field");
                    String type = (String) rule.get("type");
                    Object value = rule.get("value");

                    if (field == null || type == null) {
                        continue;
                    }

                    Object fieldValue = row.get(field);

                    if (!validateField(fieldValue, type, value)) {
                        isValid = false;
                        break;
                    }
                }
            }

            if (isValid) {
                output.addRow(row);
            } else if ("fail".equals(onError)) {
                throw new TransformException("Data validation failed for row: " + row);
            } else if ("keep".equals(onError)) {
                output.addRow(row);
            }
            // "skip" 时直接跳过，不添加到输出
        }

        return output;
    }

    private boolean validateField(Object fieldValue, String type, Object value) {
        switch (type) {
            case "required":
            case "notNull":
                return fieldValue != null;
            case "notEmpty":
                if (fieldValue == null) {
                    return false;
                }
                if (fieldValue instanceof String) {
                    return StringUtils.isNotBlank((String) fieldValue);
                }
                return true;
            case "min":
                if (fieldValue == null) {
                    return false;
                }
                if (fieldValue instanceof Number && value instanceof Number) {
                    return ((Number) fieldValue).doubleValue() >= ((Number) value).doubleValue();
                }
                return true;
            case "max":
                if (fieldValue == null) {
                    return false;
                }
                if (fieldValue instanceof Number && value instanceof Number) {
                    return ((Number) fieldValue).doubleValue() <= ((Number) value).doubleValue();
                }
                return true;
            case "pattern":
                if (fieldValue == null) {
                    return false;
                }
                if (fieldValue instanceof String && value instanceof String) {
                    return ((String) fieldValue).matches((String) value);
                }
                return true;
            default:
                return true;
        }
    }

    @Override
    public boolean validateParams(Map<String, Object> params) {
        return params != null;
    }
}

