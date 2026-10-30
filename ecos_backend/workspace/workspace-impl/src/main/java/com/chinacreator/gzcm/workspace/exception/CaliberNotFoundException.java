package com.chinacreator.gzcm.workspace.exception;

import com.chinacreator.gzcm.common.error.FCScenarioErrorCode;

/**
 * 口径不存在（#146ac 附录A 第 31 条 {@code GET /api/v1/workspace/calibers/{id}}，HTTP 404）。
 *
 * <p>本面是 id 基寻址，属主面是 code 基寻址，故「查无此 id」有两种成因：库里确实没有，
 * 或 id 落在属主 list 面窗口之外。两者对调用方的处置不同（重试无用 vs 需属主提供无界枚举面），
 * 因此把解析窗口一并带出，禁把窗口溢出伪装成确定的「不存在」。</p>
 */
public class CaliberNotFoundException extends WorkspaceDomainException {

    public CaliberNotFoundException(String caliberId, int resolutionWindow, boolean windowSaturated) {
        super(404, FCScenarioErrorCode.CODE_CALIBER_NOT_FOUND, "口径不存在: " + caliberId);
        putDetail("caliberId", caliberId);
        putDetail("resolutionWindow", resolutionWindow);
        putDetail("windowSaturated", windowSaturated);
    }
}
