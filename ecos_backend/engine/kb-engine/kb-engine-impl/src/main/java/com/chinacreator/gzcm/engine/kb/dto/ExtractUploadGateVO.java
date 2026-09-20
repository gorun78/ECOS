package com.chinacreator.gzcm.engine.kb.dto;

import lombok.Data;

/**
 * 临时文件上传开关门控 VO（K1 批次 E2 端点出参）。
 *
 * <p>强类型出参（禁 {@code Map<String,Object>} 作接口出入参）：
 * 告知前端知识抽取「直接上传临时文件」通道是否开启及提示语。</p>
 */
@Data
public class ExtractUploadGateVO {

    /** 是否允许知识工作台直接上传临时文件（非结构化快路径） */
    private boolean allowed;

    /** 提示语（开启/未开启分别给出操作指引） */
    private String hint;
}
