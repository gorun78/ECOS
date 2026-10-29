package com.chinacreator.gzcm.workspace.backtest;

import com.chinacreator.gzcm.workspace.scenario.ScenarioDomainException;

import java.util.List;

/**
 * 回测读面能力未上线（#146aa 附录A 第 33 条，HTTP 501，code=STUB_CAPABILITY）。
 *
 * <p>形态与 workspace 既有「依赖未就绪即未上线」同形（{@code ForecastRunNotLiveException}、
 * #146w cognitive world-model 四段 501 承流）：真实 HTTP 501＋{@code data.stub=true}
 * ＋逐条可复验的缺载体取证 {@code missing[]}，<b>禁</b> 200 空列表伪装「查询成功但无数据」、
 * <b>禁</b>固定指标值（X-43/X-44 同族红线）。</p>
 *
 * <p>{@code missing} 每条必须是可复验事实（表未实跑／属主读端点缺失／写通道未接线），
 * 失败技术细节（SQL、URL、堆栈）走 {@link #getTechnicalReason()} 只进日志面（X-41）。</p>
 */
public class BacktestNotLiveException extends ScenarioDomainException {

    private final String technicalReason;

    public BacktestNotLiveException(String capability, List<String> missing, String technicalReason) {
        super(501, "STUB_CAPABILITY", "该能力尚未上线: " + capability + "（凭追踪号联系管理员）");
        putDetail("capability", capability);
        putDetail("stub", true);
        putDetail("missing", missing);
        putDetail("path", "GET /api/v1/workspace/backtests");
        this.technicalReason = technicalReason;
    }

    public String getTechnicalReason() {
        return technicalReason;
    }
}
