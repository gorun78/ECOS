package com.chinacreator.gzcm.common.base;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #142/B4-3e-24 收口 <b>D-B4-55</b>：机读码取法属主 {@link MachineErrorCode} 的形态契约。
 *
 * <p>本类的每条断言都对应一个"收口前会走错"的形态：数字串冒充机读码、截短错码、
 * 文案中转述的别人码冒充本请求码。判据以字面量钉死，防止后续把正则放宽成"任意位置取码"。</p>
 */
class MachineErrorCodeTest {

    @Test
    @DisplayName("message 起始位命中：ECOS-SEC-501 前缀 → 取回码本体，不含冒号与文案")
    void takesCodeFromMessageHead() {
        assertEquals(Optional.of("ECOS-SEC-501"),
                MachineErrorCode.fromMessage("ECOS-SEC-501: pipeline.execute 未获安全裁决"));
        assertEquals(Optional.of("ECOS-DATA-041"),
                MachineErrorCode.fromMessage("ECOS-DATA-041: 摄取失败"));
        assertEquals(Optional.of("ECOS-SEC-520"),
                MachineErrorCode.fromMessage(RlsFragments.scopeUndeclaredHead()));
    }

    @Test
    @DisplayName("右边界：五位数字必须整体不成码（防截短）；四位数字段合法、整枚取回")
    void refusesTruncatedMatch() {
        // 实测契约：数字段允许 2~4 位 ⇒ "ECOS-SEC-5011" 是一枚合法的四位码，整枚取回而非截成 501。
        assertEquals(Optional.of("ECOS-SEC-5011"),
                MachineErrorCode.fromMessage("ECOS-SEC-5011: 四位数字段属形态内"));
        // 五位数字 ⇒ \d{2,4} 若缺右边界会吃掉前 4 位并谎报 ECOS-SEC-5011
        assertEquals(Optional.empty(), MachineErrorCode.fromMessage("ECOS-SEC-50112: 五位数字属形态外"));
        // 尾母后仍有字母续体时不得截短
        assertEquals(Optional.empty(), MachineErrorCode.fromMessage("ECOS-SEC-501XY: 形态外"));
        // 域段 9 个字母以上属形态外，不许放宽成 .*
        assertEquals(Optional.empty(), MachineErrorCode.fromMessage("ECOS-SECURITYX-501: 形态外"));
    }

    @Test
    @DisplayName("只认起始位：文案中间转述的码不得冒充本请求错误码（防「起头即谎」）")
    void ignoresCodeInsideProse() {
        assertEquals(Optional.empty(),
                MachineErrorCode.fromMessage("上游返回 ECOS-SEC-501，请稍后重试"));
        assertEquals(Optional.empty(), MachineErrorCode.fromMessage(""));
        assertEquals(Optional.empty(), MachineErrorCode.fromMessage(null));
        assertEquals(Optional.empty(), MachineErrorCode.fromMessage("普通中文文案"));
    }

    @Test
    @DisplayName("orNumeric：命中用机读码；未命中逐字回落 String.valueOf(int)＝收口前形态")
    void fallsBackToNumericVerbatim() {
        assertEquals("ECOS-SEC-501",
                MachineErrorCode.orNumeric("ECOS-SEC-501: 未获安全裁决", 503));
        assertEquals("503", MachineErrorCode.orNumeric("未获安全裁决", 503));
        assertEquals("400", MachineErrorCode.orNumeric(null, 400));
        assertEquals("-100", MachineErrorCode.orNumeric("  ECOS-SEC-501 前导空格不成码", -100),
                "码必须严格起头于下标 0；带前导空白的 message 不成码（实测契约）");
    }

    /** 形态片段，避免测试侧硬依赖 security 包。 */
    private static final class RlsFragments {
        static String scopeUndeclaredHead() {
            return "ECOS-SEC-520" + ": 属主查询端点未声明 RLS 作用域";
        }
    }
}
