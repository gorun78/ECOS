package com.chinacreator.gzcm.runtime.core.core.util;

import java.sql.Date;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;

/**
 * 日期工具类
 */
public class DateUtil {
    
    /**
     * 将字符串转换为Timestamp
     * @param dateStr 日期字符串
     * @return Timestamp对象
     * @throws ParseException 解析失败时抛出异常
     */
    public static Timestamp str2Timestamp(String dateStr) throws ParseException {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }
        
        // 尝试多种日期格式
        String[] patterns = {
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss.SSS",
            "yyyy-MM-dd",
            "yyyy/MM/dd HH:mm:ss",
            "yyyy/MM/dd"
        };
        
        for (String pattern : patterns) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(pattern);
                java.util.Date date = sdf.parse(dateStr);
                return new Timestamp(date.getTime());
            } catch (ParseException e) {
                // 继续尝试下一个格式
            }
        }
        
        throw new ParseException("无法解析日期字符串: " + dateStr, 0);
    }
    
    /**
     * 将字符串转换为Date
     * @param dateStr 日期字符串
     * @param pattern 日期格式
     * @return Date对象
     * @throws ParseException 解析失败时抛出异常
     */
    public static Date str2Date(String dateStr, String pattern) throws ParseException {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }
        
        SimpleDateFormat sdf = new SimpleDateFormat(pattern);
        java.util.Date date = sdf.parse(dateStr);
        return new Date(date.getTime());
    }
    
    /**
     * 将Timestamp转换为字符串
     * @param timestamp Timestamp对象
     * @param pattern 日期格式
     * @return 日期字符串
     */
    public static String timestamp2Str(Timestamp timestamp, String pattern) {
        if (timestamp == null) {
            return null;
        }
        
        SimpleDateFormat sdf = new SimpleDateFormat(pattern);
        return sdf.format(timestamp);
    }
}
