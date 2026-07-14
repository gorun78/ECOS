package com.chinacreator.gzcm.runtime.core.transform.step;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.runtime.core.transform.TransformException;
import com.chinacreator.gzcm.runtime.core.transform.TransformStep;
import com.chinacreator.gzcm.runtime.core.transform.model.DataFrame;

/**
 * 类型转换转换步骤
 * 将DataFrame中的字段转换为指定类型
 * 
 * 参数说明：
 * - conversions: Map<String, String>，字段名 -> 目标类型的映射
 *   支持的类型：string, integer, long, double, boolean, date
 * - dateFormat: String (可选)，日期格式，默认"yyyy-MM-dd HH:mm:ss"
 * - onError: String (可选)，错误处理方式：skip（跳过该字段）、fail（抛出异常）、keep（保留原值），默认keep
 */
public class TypeConversionStep implements TransformStep {
    @Override
    public String getName() {
        return "TypeConversion";
    }

    @Override
    public String getType() {
        return "typeConversion";
    }

    @Override
    public DataFrame transform(DataFrame input, Map<String, Object> params) throws TransformException {
        if (input == null || input.isEmpty()) {
            return input;
        }

        @SuppressWarnings("unchecked")
        Map<String, String> conversions = (Map<String, String>) params.get("conversions");
        if (conversions == null || conversions.isEmpty()) {
            return input;
        }

        String dateFormat = (String) params.getOrDefault("dateFormat", "yyyy-MM-dd HH:mm:ss");
        String onError = (String) params.getOrDefault("onError", "keep");

        DataFrame output = new DataFrame();
        output.setColumns(new ArrayList<>(input.getColumns()));
        output.setMetadata(new HashMap<>(input.getMetadata()));

        for (Map<String, Object> row : input.getRows()) {
            Map<String, Object> convertedRow = new HashMap<>();
            for (String col : input.getColumns()) {
                Object value = row.get(col);
                String targetType = conversions.get(col);

                if (targetType != null && value != null) {
                    try {
                        value = convertValue(value, targetType, dateFormat);
                    } catch (Exception e) {
                        if ("fail".equals(onError)) {
                            throw new TransformException("Failed to convert field '" + col + "' to type '" + targetType + "'", e);
                        } else if ("skip".equals(onError)) {
                            continue; // 跳过该字段
                        }
                        // "keep" 时保留原值
                    }
                }

                convertedRow.put(col, value);
            }
            output.addRow(convertedRow);
        }

        return output;
    }

    private Object convertValue(Object value, String targetType, String dateFormat) throws Exception {
        String strValue = value.toString();

        switch (targetType.toLowerCase()) {
            case "string":
                return strValue;
            case "integer":
            case "int":
                if (value instanceof Number) {
                    return ((Number) value).intValue();
                }
                return Integer.parseInt(strValue);
            case "long":
                if (value instanceof Number) {
                    return ((Number) value).longValue();
                }
                return Long.parseLong(strValue);
            case "double":
                if (value instanceof Number) {
                    return ((Number) value).doubleValue();
                }
                return Double.parseDouble(strValue);
            case "boolean":
            case "bool":
                if (value instanceof Boolean) {
                    return value;
                }
                String lower = strValue.toLowerCase();
                return "true".equals(lower) || "1".equals(lower) || "yes".equals(lower);
            case "date":
            case "datetime":
                if (value instanceof Date) {
                    return value;
                }
                if (value instanceof Number) {
                    return new Date(((Number) value).longValue());
                }
                SimpleDateFormat sdf = new SimpleDateFormat(dateFormat);
                try {
                    return sdf.parse(strValue);
                } catch (ParseException e) {
                    // 尝试其他常见格式
                    String[] formats = {"yyyy-MM-dd", "yyyy/MM/dd", "yyyy-MM-dd HH:mm:ss", "yyyy/MM/dd HH:mm:ss"};
                    for (String fmt : formats) {
                        try {
                            return new SimpleDateFormat(fmt).parse(strValue);
                        } catch (ParseException ignored) {
                        }
                    }
                    throw e;
                }
            case "bigdecimal":
            case "decimal":
                if (value instanceof BigDecimal) {
                    return value;
                }
                if (value instanceof Number) {
                    return BigDecimal.valueOf(((Number) value).doubleValue());
                }
                return new BigDecimal(strValue);
            default:
                return value;
        }
    }

    @Override
    public boolean validateParams(Map<String, Object> params) {
        if (params == null) {
            return false;
        }
        @SuppressWarnings("unchecked")
        Map<String, String> conversions = (Map<String, String>) params.get("conversions");
        return conversions != null && !conversions.isEmpty();
    }
}

