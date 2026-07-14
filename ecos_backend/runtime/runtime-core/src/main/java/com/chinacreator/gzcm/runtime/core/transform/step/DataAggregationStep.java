package com.chinacreator.gzcm.runtime.core.transform.step;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.runtime.core.transform.TransformException;
import com.chinacreator.gzcm.runtime.core.transform.TransformStep;
import com.chinacreator.gzcm.runtime.core.transform.model.DataFrame;

/**
 * 数据聚合转换步骤
 * 对数据进行分组聚合操作
 * 
 * 参数说明：
 * - groupBy: List<String>，分组字段列表
 * - aggregations: List<Map<String, Object>>，聚合操作列表
 *   - field: String，要聚合的字段名
 *   - function: String，聚合函数（sum, avg, count, min, max）
 *   - alias: String (可选)，聚合结果别名
 */
public class DataAggregationStep implements TransformStep {
    @Override
    public String getName() {
        return "DataAggregation";
    }

    @Override
    public String getType() {
        return "aggregation";
    }

    @Override
    public DataFrame transform(DataFrame input, Map<String, Object> params) throws TransformException {
        if (input == null || input.isEmpty()) {
            return input;
        }

        @SuppressWarnings("unchecked")
        List<String> groupBy = (List<String>) params.get("groupBy");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> aggregations = (List<Map<String, Object>>) params.get("aggregations");

        if (groupBy == null || groupBy.isEmpty()) {
            throw new TransformException("GroupBy fields are required for aggregation");
        }

        DataFrame output = new DataFrame();
        List<String> outputColumns = new ArrayList<>(groupBy);
        if (aggregations != null) {
            for (Map<String, Object> agg : aggregations) {
                String alias = (String) agg.get("alias");
                String field = (String) agg.get("field");
                String function = (String) agg.get("function");
                if (alias != null) {
                    outputColumns.add(alias);
                } else if (field != null && function != null) {
                    outputColumns.add(function + "_" + field);
                }
            }
        }
        output.setColumns(outputColumns);

        // 按分组字段分组并聚合
        Map<String, Map<String, Object>> groupedData = new HashMap<>();

        for (Map<String, Object> row : input.getRows()) {
            // 构建分组键
            StringBuilder groupKey = new StringBuilder();
            for (String field : groupBy) {
                Object value = row.get(field);
                groupKey.append(value != null ? value.toString() : "null").append("|");
            }
            String key = groupKey.toString();

            Map<String, Object> groupRow = groupedData.get(key);
            if (groupRow == null) {
                groupRow = new HashMap<>();
                for (String field : groupBy) {
                    groupRow.put(field, row.get(field));
                }
                groupedData.put(key, groupRow);
            }

            // 执行聚合操作
            if (aggregations != null) {
                for (Map<String, Object> agg : aggregations) {
                    String field = (String) agg.get("field");
                    String function = (String) agg.get("function");
                    String alias = (String) agg.getOrDefault("alias", function + "_" + field);

                    Object fieldValue = row.get(field);
                    Object currentValue = groupRow.get(alias);

                    Object newValue = aggregateValue(currentValue, fieldValue, function);
                    groupRow.put(alias, newValue);
                }
            }
        }

        // 将分组结果添加到输出
        for (Map<String, Object> groupRow : groupedData.values()) {
            output.addRow(groupRow);
        }

        output.setMetadata(new HashMap<>(input.getMetadata()));
        return output;
    }

    private Object aggregateValue(Object currentValue, Object fieldValue, String function) {
        if (fieldValue == null && !"count".equals(function)) {
            return currentValue;
        }

        switch (function.toLowerCase()) {
            case "sum":
                if (currentValue == null) {
                    currentValue = 0.0;
                }
                if (fieldValue instanceof Number) {
                    return ((Number) currentValue).doubleValue() + ((Number) fieldValue).doubleValue();
                }
                return currentValue;
            case "avg":
                // 简化实现：需要记录计数，这里只做累加
                if (currentValue == null) {
                    currentValue = new double[]{0.0, 0.0}; // [sum, count]
                }
                if (fieldValue instanceof Number) {
                    double[] arr = (double[]) currentValue;
                    arr[0] += ((Number) fieldValue).doubleValue();
                    arr[1] += 1.0;
                    return arr[0] / arr[1];
                }
                return currentValue;
            case "count":
                if (currentValue == null) {
                    currentValue = 0;
                }
                return ((Number) currentValue).intValue() + 1;
            case "min":
                if (currentValue == null) {
                    return fieldValue;
                }
                if (fieldValue instanceof Number && currentValue instanceof Number) {
                    return Math.min(((Number) currentValue).doubleValue(), ((Number) fieldValue).doubleValue());
                }
                return currentValue;
            case "max":
                if (currentValue == null) {
                    return fieldValue;
                }
                if (fieldValue instanceof Number && currentValue instanceof Number) {
                    return Math.max(((Number) currentValue).doubleValue(), ((Number) fieldValue).doubleValue());
                }
                return currentValue;
            default:
                return currentValue;
        }
    }

    @Override
    public boolean validateParams(Map<String, Object> params) {
        if (params == null) {
            return false;
        }
        @SuppressWarnings("unchecked")
        List<String> groupBy = (List<String>) params.get("groupBy");
        return groupBy != null && !groupBy.isEmpty();
    }
}

