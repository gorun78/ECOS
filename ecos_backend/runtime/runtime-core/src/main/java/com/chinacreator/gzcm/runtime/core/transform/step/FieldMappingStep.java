package com.chinacreator.gzcm.runtime.core.transform.step;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.runtime.core.transform.TransformException;
import com.chinacreator.gzcm.runtime.core.transform.TransformStep;
import com.chinacreator.gzcm.runtime.core.transform.model.DataFrame;

/**
 * 字段映射转换步骤
 * 将输入DataFrame的字段映射到新的字段名
 * 
 * 参数说明：
 * - mapping: Map<String, String>，源字段名 -> 目标字段名的映射
 * - keepUnmapped: Boolean (可选)，是否保留未映射的字段，默认true
 */
public class FieldMappingStep implements TransformStep {
    @Override
    public String getName() {
        return "FieldMapping";
    }

    @Override
    public String getType() {
        return "mapping";
    }

    @Override
    public DataFrame transform(DataFrame input, Map<String, Object> params) throws TransformException {
        if (input == null || input.isEmpty()) {
            return input;
        }

        @SuppressWarnings("unchecked")
        Map<String, String> mapping = (Map<String, String>) params.get("mapping");
        if (mapping == null || mapping.isEmpty()) {
            throw new TransformException("Field mapping parameter 'mapping' is required and cannot be empty");
        }

        Boolean keepUnmapped = (Boolean) params.getOrDefault("keepUnmapped", true);

        // 创建新的DataFrame
        DataFrame output = new DataFrame();
        List<String> newColumns = new ArrayList<>();
        
        // 构建新列名列表
        for (String oldCol : input.getColumns()) {
            String newCol = mapping.get(oldCol);
            if (newCol != null) {
                newColumns.add(newCol);
            } else if (keepUnmapped) {
                newColumns.add(oldCol);
            }
        }
        output.setColumns(newColumns);

        // 转换每一行数据
        for (Map<String, Object> row : input.getRows()) {
            Map<String, Object> newRow = new HashMap<>();
            for (String oldCol : input.getColumns()) {
                String newCol = mapping.get(oldCol);
                if (newCol != null) {
                    newRow.put(newCol, row.get(oldCol));
                } else if (keepUnmapped) {
                    newRow.put(oldCol, row.get(oldCol));
                }
            }
            output.addRow(newRow);
        }

        // 复制元数据
        output.setMetadata(new HashMap<>(input.getMetadata()));

        return output;
    }

    @Override
    public boolean validateParams(Map<String, Object> params) {
        if (params == null) {
            return false;
        }
        @SuppressWarnings("unchecked")
        Map<String, String> mapping = (Map<String, String>) params.get("mapping");
        return mapping != null && !mapping.isEmpty();
    }
}

