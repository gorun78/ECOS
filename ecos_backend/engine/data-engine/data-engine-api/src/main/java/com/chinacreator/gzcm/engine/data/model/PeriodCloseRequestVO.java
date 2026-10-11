package com.chinacreator.gzcm.engine.data.model;

/**
 * 期间关账请求体（详细设计-09 v1.2 §十一 H-3 交付①；
 * {@code POST /api/v1/datanet/facts/periods/{yyyMM}/close}）。
 *
 * <p>可整体缺省（空体=仅关账，无附言）。角色门槛由 security-engine OPA 裁决
 * （action=data.period.close，role 取调用方 token claims 原样透传，<b>禁硬编码角色映射</b>）。
 */
public class PeriodCloseRequestVO {

    /** 关账说明（月结批次号/审批单号等自由文本，可空）。 */
    private String reason;

    /** 附加上下文序列化 JSON 字符串（受控 JSON，仅描述用，可空；落 close_detail_json TEXT）。 */
    private String detailJson;

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getDetailJson() { return detailJson; }
    public void setDetailJson(String detailJson) { this.detailJson = detailJson; }
}
