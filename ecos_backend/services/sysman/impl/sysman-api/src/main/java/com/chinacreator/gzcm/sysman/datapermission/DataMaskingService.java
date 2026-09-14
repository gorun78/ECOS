package com.chinacreator.gzcm.sysman.datapermission;

import com.chinacreator.gzcm.sysman.datapermission.annotation.Masking;

/**
 * 字段级脱敏服务：基于 {@link Masking} 注解对对象进行脱敏。
 */
public interface DataMaskingService {

    /**
     * 对目标对象进行就地脱敏（修改字段值）。
     * 仅处理带 {@link Masking} 注解的 String 字段。
     *
     * @param target 需要脱敏的对象
     */
    void maskObject(Object target);

    /**
     * 对单个值进行脱敏，可用于手工调用。
     *
     * @param strategy 脱敏策略：phone / idCard / email / bankCard / custom
     * @param value    原始值
     * @param prefix   前缀保留位数
     * @param suffix   后缀保留位数
     * @return 脱敏后的值
     */
    String maskValue(String strategy, String value, int prefix, int suffix);
}


