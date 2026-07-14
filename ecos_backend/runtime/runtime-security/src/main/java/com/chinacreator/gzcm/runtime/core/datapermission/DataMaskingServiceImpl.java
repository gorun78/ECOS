package com.chinacreator.gzcm.runtime.core.datapermission;

import java.lang.reflect.Field;

import com.chinacreator.gzcm.sysman.datapermission.annotation.Masking;
import com.chinacreator.gzcm.sysman.datapermission.DataMaskingService;

/**
 * 基于注解的简单脱敏实现：
 * - 仅处理带 {@link Masking} 注解的 String 字段；
 * - 支持常见策略：phone / idCard / email / bankCard；
 * - 提供 maskObject 与 maskValue 两种调用方式。
 */
public class DataMaskingServiceImpl implements DataMaskingService {

    @Override
    public void maskObject(Object target) {
        if (target == null) {
            return;
        }
        Class<?> clazz = target.getClass();
        while (clazz != null && clazz != Object.class) {
            Field[] fields = clazz.getDeclaredFields();
            for (Field f : fields) {
                Masking masking = f.getAnnotation(Masking.class);
                if (masking == null) {
                    continue;
                }
                if (!String.class.equals(f.getType())) {
                    continue;
                }
                f.setAccessible(true);
                try {
                    Object raw = f.get(target);
                    if (raw == null) {
                        continue;
                    }
                    String masked = maskValue(masking.strategy(), String.valueOf(raw),
                            masking.prefixVisible(), masking.suffixVisible());
                    f.set(target, masked);
                } catch (IllegalAccessException e) {
                    // ignore single field failure
                }
            }
            clazz = clazz.getSuperclass();
        }
    }

    @Override
    public String maskValue(String strategy, String value, int prefix, int suffix) {
        if (value == null) {
            return null;
        }
        String s = value.trim();
        if (s.length() == 0) {
            return s;
        }
        if ("phone".equalsIgnoreCase(strategy)) {
            return maskPhone(s);
        } else if ("idCard".equalsIgnoreCase(strategy)) {
            return maskIdCard(s);
        } else if ("email".equalsIgnoreCase(strategy)) {
            return maskEmail(s);
        } else if ("bankCard".equalsIgnoreCase(strategy)) {
            return maskBankCard(s);
        } else {
            // 通用策略：保留前 prefix 位和后 suffix 位，中间用 * 填充
            return maskGeneric(s, prefix, suffix);
        }
    }

    private String maskPhone(String v) {
        if (v.length() <= 7) {
            return v;
        }
        return v.substring(0, 3) + "****" + v.substring(v.length() - 4);
    }

    private String maskIdCard(String v) {
        if (v.length() <= 8) {
            return v;
        }
        int prefix = 6;
        int suffix = 4;
        return maskGeneric(v, prefix, suffix);
    }

    private String maskEmail(String v) {
        int at = v.indexOf('@');
        if (at <= 1) {
            return v;
        }
        String name = v.substring(0, at);
        String domain = v.substring(at);
        if (name.length() <= 2) {
            return name.charAt(0) + "***" + domain;
        }
        String visible = name.substring(0, 2);
        return visible + "***" + domain;
    }

    private String maskBankCard(String v) {
        if (v.length() <= 8) {
            return v;
        }
        int prefix = 4;
        int suffix = 4;
        return maskGeneric(v, prefix, suffix);
    }

    private String maskGeneric(String v, int prefix, int suffix) {
        if (v == null) {
            return null;
        }
        int len = v.length();
        if (prefix + suffix >= len || len <= 2) {
            return v;
        }
        int stars = len - prefix - suffix;
        StringBuilder sb = new StringBuilder();
        sb.append(v, 0, prefix);
        for (int i = 0; i < stars; i++) {
            sb.append('*');
        }
        sb.append(v.substring(len - suffix));
        return sb.toString();
    }
}


