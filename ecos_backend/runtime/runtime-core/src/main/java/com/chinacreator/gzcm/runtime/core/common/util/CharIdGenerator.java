package com.chinacreator.gzcm.runtime.core.common.util;

import java.util.UUID;

/**
 * 字符ID生成器
 */
public class CharIdGenerator {
    
    /**
     * 生成字符ID
     * @param prefix 前缀（可为null）
     * @param tableName 表名
     * @param parentId 父ID（可为null）
     * @return 生成的ID
     */
    public static String generate(String prefix, String tableName, String parentId) {
        if (prefix != null && !prefix.isEmpty()) {
            return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }
        return UUID.randomUUID().toString().replace("-", "");
    }
}
