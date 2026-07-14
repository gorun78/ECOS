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
 * 数据清洗转换步骤
 * 对数据进行清洗处理，包括去除空白、去除重复、处理空值等
 * 
 * 参数说明：
 * - trimWhitespace: Boolean (可选)，是否去除字符串首尾空白，默认true
 * - removeEmptyRows: Boolean (可选)，是否移除空行，默认false
 * - removeDuplicates: Boolean (可选)，是否移除重复行，默认false
 * - nullValueReplacement: Object (可选)，空值替换值，默认null（不替换）
 * - columns: List<String> (可选)，指定要清洗的列，默认所有列
 */
public class DataCleansingStep implements TransformStep {
    @Override
    public String getName() {
        return "DataCleansing";
    }

    @Override
    public String getType() {
        return "cleansing";
    }

    @Override
    public DataFrame transform(DataFrame input, Map<String, Object> params) throws TransformException {
        if (input == null || input.isEmpty()) {
            return input;
        }

        Boolean trimWhitespace = (Boolean) params.getOrDefault("trimWhitespace", true);
        Boolean removeEmptyRows = (Boolean) params.getOrDefault("removeEmptyRows", false);
        Boolean removeDuplicates = (Boolean) params.getOrDefault("removeDuplicates", false);
        Object nullValueReplacement = params.get("nullValueReplacement");
        @SuppressWarnings("unchecked")
        List<String> columns = (List<String>) params.get("columns");

        DataFrame output = new DataFrame();
        output.setColumns(new ArrayList<>(input.getColumns()));
        output.setMetadata(new HashMap<>(input.getMetadata()));

        List<Map<String, Object>> seenRows = removeDuplicates ? new ArrayList<>() : null;

        for (Map<String, Object> row : input.getRows()) {
            Map<String, Object> cleanedRow = new HashMap<>();
            boolean isEmpty = true;

            for (String col : input.getColumns()) {
                if (columns != null && !columns.contains(col)) {
                    cleanedRow.put(col, row.get(col));
                    if (row.get(col) != null) {
                        isEmpty = false;
                    }
                    continue;
                }

                Object value = row.get(col);
                
                // 处理空值
                if (value == null) {
                    if (nullValueReplacement != null) {
                        value = nullValueReplacement;
                        isEmpty = false;
                    }
                } else {
                    isEmpty = false;
                    
                    // 去除字符串首尾空白
                    if (trimWhitespace && value instanceof String) {
                        value = StringUtils.trim((String) value);
                        if (StringUtils.isEmpty((String) value)) {
                            value = nullValueReplacement != null ? nullValueReplacement : null;
                        }
                    }
                }

                cleanedRow.put(col, value);
            }

            // 移除空行
            if (removeEmptyRows && isEmpty) {
                continue;
            }

            // 移除重复行
            if (removeDuplicates) {
                if (seenRows.contains(cleanedRow)) {
                    continue;
                }
                seenRows.add(new HashMap<>(cleanedRow));
            }

            output.addRow(cleanedRow);
        }

        return output;
    }

    @Override
    public boolean validateParams(Map<String, Object> params) {
        return params != null;
    }
}

