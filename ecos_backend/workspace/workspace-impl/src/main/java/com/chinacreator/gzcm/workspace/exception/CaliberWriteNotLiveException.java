package com.chinacreator.gzcm.workspace.exception;

import java.util.List;

/**
 * 口径写面能力未上线（#146ad 附录A 第 31 条 {@code submitCaliber} / {@code supersedeCaliber}，
 * HTTP 501，code={@code STUB_CAPABILITY}）。
 *
 * <p>形态与 workspace 既有「依赖未就绪即未上线」同族（{@code ForecastRunNotLiveException}、
 * {@code BacktestNotLiveException}、#146w cognitive world-model 四段 501 承流）：真实 HTTP 501
 * ＋ {@code data.stub=true} ＋逐条可复验的缺载体取证 {@code missing[]}。<b>禁</b>用 200 回显
 * 一个「看起来推进了」的状态值冒充契约状态机（X-43/X-44 同族红线），也<b>禁</b>为了凑成功
 * 去调属主既有的 approve/retire 端点把「送审」偷换成「批准」。</p>
 *
 * <p>失败技术细节走日志面（X-41）；{@code missing} 每条必须是可由 grep / DDL 实扫复验的事实。</p>
 */
public class CaliberWriteNotLiveException extends WorkspaceDomainException {

    public CaliberWriteNotLiveException(String capability, String path, List<String> missing) {
        super(501, "STUB_CAPABILITY", "该能力尚未上线: " + capability + "（凭追踪号联系管理员）");
        putDetail("capability", capability);
        putDetail("path", path);
        putDetail("stub", true);
        putDetail("missing", missing);
    }
}
