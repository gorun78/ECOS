package com.chinacreator.gzcm.runtime.core.common.util;

/**
 * StringUtils - 字符串工具类
 * 用于字符串相关的工具方法
 */
public class StringUtils {
    
    /**
     * 判断字符串是否为空
     */
    public static boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }
    
    /**
     * 判断字符串是否不为空
     */
    public static boolean isNotBlank(String str) {
        return !isBlank(str);
    }
    
    /**
     * 判断字符串是否为空（包括null和空字符串）
     */
    public static boolean isEmpty(String str) {
        return str == null || str.length() == 0;
    }
    
    /**
     * 判断字符串是否不为空
     */
    public static boolean isNotEmpty(String str) {
        return !isEmpty(str);
    }
    
    /**
     * 判断字符串是否为空（包括null和空字符串）
     * 兼容isNull方法
     */
    public static boolean isNull(String str) {
        return isEmpty(str);
    }
    
    /**
     * 判断两个字符串是否相等（null安全）
     * @param str1 第一个字符串
     * @param str2 第二个字符串
     * @return 如果两个字符串相等（包括都为null的情况），返回true；否则返回false
     */
    public static boolean equals(String str1, String str2) {
        if (str1 == null && str2 == null) {
            return true;
        }
        if (str1 == null || str2 == null) {
            return false;
        }
        return str1.equals(str2);
    }
    
    /**
     * 如果字符串为null，返回默认值；否则返回原字符串
     * @param str 待检查的字符串
     * @param defaultStr 默认值
     * @return 如果str为null，返回defaultStr；否则返回str
     */
    public static String defaultString(String str, String defaultStr) {
        return str == null ? defaultStr : str;
    }
    
    /**
     * 如果字符串为null，返回空字符串；否则返回原字符串
     * @param str 待检查的字符串
     * @return 如果str为null，返回空字符串；否则返回str
     */
    public static String defaultString(String str) {
        return defaultString(str, "");
    }
    
    /**
     * 转换null为空字符串（兼容方法，注意拼写为convetNull）
     * @param str 待检查的字符串
     * @return 如果str为null，返回空字符串；否则返回str
     */
    public static String convetNull(String str) {
        return defaultString(str, "");
    }
    
    /**
     * 判断字符串是否为数字
     */
    public static boolean isNumber(String str) {
        if (isBlank(str)) {
            return false;
        }
        try {
            Double.parseDouble(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    /**
     * 解析字符串（占位实现，用于兼容旧代码）
     * @param className 类名
     * @param methodName 方法名
     * @param expression 表达式
     * @return 解析后的字符串
     * @throws Exception 解析异常
     */
    public static String parseString(String className, String methodName, String expression) throws Exception {
        // Placeholder implementation
        // TODO: 实现实际的解析逻辑
        return expression;
    }
    
    /**
     * 去除字符串末尾的零和小数点
     * 例如: "1.00" -> "1", "1.10" -> "1.1", "1.0" -> "1"
     */
    public static String subZeroAndDot(String str) {
        if (isBlank(str)) {
            return str;
        }
        if (str.indexOf('.') > 0) {
            // 去除末尾的零
            str = str.replaceAll("0+?$", "");
            // 去除末尾的小数点
            str = str.replaceAll("[.]$", "");
        }
        return str;
    }
    
    /**
     * 判断String是否为true (true/1/yes/on)
     * 兼容 Utils.isTrue(String) 方法
     */
    public static boolean isTrue(String bool) {
        if (bool == null) return false;
        return "true".equalsIgnoreCase(bool) || "1".equals(bool) || "yes".equalsIgnoreCase(bool) || "on".equalsIgnoreCase(bool);
    }
    
    /**
     * 将对象数组用指定分隔符连接成字符串
     * @param array 对象数组
     * @param separator 分隔符
     * @return 连接后的字符串
     */
    public static String join(Object[] array, String separator) {
        if (array == null) {
            return null;
        }
        if (separator == null) {
            separator = "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < array.length; i++) {
            if (i > 0) {
                sb.append(separator);
            }
            if (array[i] != null) {
                sb.append(array[i]);
            }
        }
        return sb.toString();
    }
    
    /**
     * 判断字符串是否有文本内容（不为null且去除空白后不为空）
     * 兼容Spring的StringUtils.hasText方法
     * @param str 待检查的字符串
     * @return 如果字符串有文本内容，返回true；否则返回false
     */
    public static boolean hasText(String str) {
        return str != null && !str.trim().isEmpty();
    }
    
    /**
     * 判断字符串是否为数字
     * 兼容 isNumeric 方法
     * @param str 待检查的字符串
     * @return 如果字符串是数字，返回true；否则返回false
     */
    public static boolean isNumeric(String str) {
        return isNumber(str);
    }
    
    /**
     * 判断字符串是否为日期格式
     * @param str 待检查的字符串
     * @param pattern 日期格式
     * @return 如果字符串是有效的日期格式，返回true；否则返回false
     */
    public static boolean isDate(String str, String pattern) {
        if (isBlank(str) || isBlank(pattern)) {
            return false;
        }
        try {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(pattern);
            sdf.setLenient(false);
            sdf.parse(str);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 将字符串转换为Date对象
     * @param str 日期字符串
     * @param pattern 日期格式
     * @return Date对象，如果解析失败返回null
     */
    public static java.util.Date formatStringToDate(String str, String pattern) {
        if (isBlank(str) || isBlank(pattern)) {
            return null;
        }
        try {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(pattern);
            sdf.setLenient(false);
            return sdf.parse(str);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 将字符串数组转换为Date数组
     * @param strs 日期字符串数组
     * @param pattern 日期格式
     * @return Date数组，如果解析失败对应位置为null
     */
    public static java.util.Date[] formatStringToDate(String[] strs, String pattern) {
        if (strs == null || strs.length == 0) {
            return new java.util.Date[0];
        }
        java.util.Date[] dates = new java.util.Date[strs.length];
        for (int i = 0; i < strs.length; i++) {
            dates[i] = formatStringToDate(strs[i], pattern);
        }
        return dates;
    }
}

