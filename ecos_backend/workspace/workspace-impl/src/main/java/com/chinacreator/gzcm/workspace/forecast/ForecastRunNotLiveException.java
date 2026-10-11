package com.chinacreator.gzcm.workspace.forecast;

import com.chinacreator.gzcm.workspace.scenario.ScenarioDomainException;

/**
 * 能力未上线（H-5 依赖未就绪，HTTP 501，code=STUB_CAPABILITY）。
 *
 * <p>X-44/X-50 假阳性红线：预测 run 计算 SPI（{@link ForecastRunGateway}）在当前 JVM 未装配
 * （cognitive 快照 reader 尚无 implements bean，U-3）时，编排面必须回「未上线」语义，
 * <b>严禁 mock 成功、严禁 200 + 造数值</b>。诊断文案含"该能力尚未上线"，与 ai-engine
 * {@code STUB_CAPABILITY} 口径同形。</p>
 */
public class ForecastRunNotLiveException extends ScenarioDomainException {

    private final String technicalReason;

    public ForecastRunNotLiveException(String capability, String reason) {
        super(501, "STUB_CAPABILITY", "该能力尚未上线: " + capability + "（凭追踪号联系管理员）");
        putDetail("capability", capability);
        this.technicalReason = reason;
    }

    public String getTechnicalReason() {
        return technicalReason;
    }
}
