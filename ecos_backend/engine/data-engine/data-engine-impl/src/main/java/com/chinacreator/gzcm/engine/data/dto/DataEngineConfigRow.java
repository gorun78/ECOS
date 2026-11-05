package com.chinacreator.gzcm.engine.data.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * data-engine 配置行的强类型投影（{@code SysConfigService#getByGroup} 出 Map 行）。
 *
 * <p>键名 {@code config_key}/{@code config_value} 是 sysman 配置域的属主契约；本记录只做
 * 「取哪两列、空值怎么折」的收敛：{@code key == null} 的行跳过，{@code value == null} 折空串
 * （与原分组逻辑等价）。</p>
 */
public record DataEngineConfigRow(String key, String value) {

    public static List<DataEngineConfigRow> fromRows(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<DataEngineConfigRow> out = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            if (row == null) {
                continue;
            }
            Object k = row.get("config_key");
            if (k == null) {
                continue;
            }
            Object v = row.get("config_value");
            out.add(new DataEngineConfigRow(k.toString(), v == null ? "" : v.toString()));
        }
        return out;
    }
}
