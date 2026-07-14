package com.chinacreator.gzcm.runtime.core.common.util;

import java.util.HashMap;
import java.util.Map;
import com.chinacreator.gzcm.runtime.core.common.datasourcemgr.bean.Tddxdatasource;

/**
 * 通用工具类
 * 补充缺失的工具方法
 */
public class Utils {
    
    public static String getDriver(String dbType) {
        if ("oracle".equalsIgnoreCase(dbType)) {
            return "oracle.jdbc.driver.OracleDriver";
        } else if ("mysql".equalsIgnoreCase(dbType)) {
            return "com.mysql.jdbc.Driver";
        } else if ("sqlserver".equalsIgnoreCase(dbType)) {
            return "com.microsoft.sqlserver.jdbc.SQLServerDriver";
        } else if ("postgresql".equalsIgnoreCase(dbType)) {
            return "org.postgresql.Driver";
        } else if ("dm".equalsIgnoreCase(dbType)) {
            return "dm.jdbc.driver.DmDriver";
        } else if ("kingbase".equalsIgnoreCase(dbType)) {
            return "com.kingbase.Driver";
        }
        return "";
    }

    public static String getJDBCUrl(Tddxdatasource ds) {
        // Implement based on db type
        // This is a simplified version
        return "jdbc:mock:url"; 
    }
    
    /**
     * 判断Boolean是否为true
     */
    public static boolean isTrue(Boolean bool) {
        return bool != null && bool;
    }
    
    /**
     * 判断String是否为true (true/1/yes/on)
     */
    public static boolean isTrue(String bool) {
        if (bool == null) return false;
        return "true".equalsIgnoreCase(bool) || "1".equals(bool) || "yes".equalsIgnoreCase(bool) || "on".equalsIgnoreCase(bool);
    }

    /**
     * 判断对象是否为空
     * @param obj 待检查的对象
     * @param trim 是否去除字符串首尾空格
     * @return 如果对象为null、空字符串或空集合，返回true；否则返回false
     */
    public static boolean isEmpty(Object obj, boolean trim) {
        if (obj == null) {
            return true;
        }
        if (obj instanceof String) {
            String str = (String) obj;
            if (trim) {
                str = str.trim();
            }
            return str.isEmpty();
        }
        if (obj instanceof java.util.Collection) {
            return ((java.util.Collection<?>) obj).isEmpty();
        }
        if (obj instanceof java.util.Map) {
            return ((java.util.Map<?, ?>) obj).isEmpty();
        }
        return false;
    }
    
    /**
     * 解析参数字符串
     * 格式: key1=value1;key2=value2
     */
    public static Map<String, String> getParamValues(String paramsAndValues) {
        Map<String, String> map = new HashMap<>();
        if (StringUtils.isBlank(paramsAndValues)) {
            return map;
        }
        
        String[] pairs = paramsAndValues.split(";");
        for (String pair : pairs) {
            if (StringUtils.isBlank(pair)) continue;
            
            String[] kv = pair.split("=");
            if (kv.length == 2) {
                map.put(kv[0].trim(), kv[1].trim());
            } else if (kv.length == 1) {
                map.put(kv[0].trim(), "");
            }
        }
        return map;
    }
    
    /**
     * 判断字符串是否为空
     * @param str 待判断的字符串
     * @return 如果字符串为null或空字符串，返回true；否则返回false
     */
    public static boolean isEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }
    
    /**
     * 判断字符串是否为null或空
     * @param str 待检查的字符串
     * @return 如果字符串为null或空，返回true；否则返回false
     */
    public static boolean isNull(String str) {
        return str == null || str.isEmpty();
    }
    
    /**
     * 解析参数字符串（带类型）
     * 格式: key1=value1;key2=value2
     * @param paramsAndValues 参数字符串
     * @return 参数Map
     */
    public static Map<String, String> getParamValuesWithType(String paramsAndValues) {
        // 与 getParamValues 相同实现
        return getParamValues(paramsAndValues);
    }
    
    /**
     * 将异常转换为字符串
     * @param e 异常对象
     * @return 异常信息字符串
     */
    public static String convertExceptionToString(Exception e) {
        if (e == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(e.getClass().getName());
        if (e.getMessage() != null) {
            sb.append(": ").append(e.getMessage());
        }
        StackTraceElement[] stackTrace = e.getStackTrace();
        if (stackTrace != null && stackTrace.length > 0) {
            sb.append("\n\tat ").append(stackTrace[0]);
            if (stackTrace.length > 1) {
                sb.append("\n\t... ").append(stackTrace.length - 1).append(" more");
            }
        }
        if (e.getCause() != null) {
            sb.append("\nCaused by: ").append(convertExceptionToString((Exception) e.getCause()));
        }
        return sb.toString();
    }
    
    /**
     * 执行JavaScript ETL字符串处理
     * @param js JavaScript代码字符串
     * @param valueMap 值映射表
     * @return 处理后的字符串结果
     * @throws Exception 执行异常
     */
    public static String getJsEtlString(String js, java.util.LinkedHashMap<String, String[]> valueMap) throws Exception {
        if (js == null || js.trim().isEmpty()) {
            return "";
        }
        // TODO: 实现JavaScript执行逻辑
        // 这是一个占位实现，实际应该使用JavaScript引擎（如Rhino、Nashorn等）执行js代码
        // 并使用valueMap中的值替换变量
        try {
            // 简单的字符串替换实现（占位）
            String result = js;
            if (valueMap != null) {
                for (Map.Entry<String, String[]> entry : valueMap.entrySet()) {
                    String key = entry.getKey();
                    String[] values = entry.getValue();
                    if (values != null && values.length > 0) {
                        // 替换 ${key} 或 key 格式的变量
                        result = result.replace("${" + key + "}", values[0]);
                        result = result.replace(key, values[0]);
                    }
                }
            }
            return result;
        } catch (Exception e) {
            throw new Exception("执行JavaScript ETL字符串处理失败: " + e.getMessage(), e);
        }
    }
}
