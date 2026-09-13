package com.chinacreator.gzcm.engine.cognitive2.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 认知引擎配置批量保存结果 VO — PUT {@code /api/v1/cognitive/config} 的响应体。
 *
 * @param updated   实际 UPDATE 命中的行数
 * @param inserted  新增 INSERT 命中的行数
 * @param total     入参 DTO 数量
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CognitiveConfigSaveResult {

    private final int updated;
    private final int inserted;
    private final int total;

    public CognitiveConfigSaveResult(int updated, int inserted, int total) {
        this.updated = updated;
        this.inserted = inserted;
        this.total = total;
    }

    public int getUpdated() { return updated; }
    public int getInserted() { return inserted; }
    public int getTotal() { return total; }
}
