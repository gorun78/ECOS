package com.chinacreator.gzcm.runtime.core.core.rpcpip.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * ZLibUtils - ZLib压缩工具类（占位实现）
 * 用于兼容旧代码中的压缩功能
 * 
 * 注意：此实现使用GZIP作为占位，实际应使用ZLib压缩
 */
public class ZLibUtils {
    
    /**
     * 压缩数据
     * @param data 原始数据
     * @return 压缩后的数据
     * @throws IOException
     */
    public static byte[] compress(byte[] data) throws IOException {
        if (data == null || data.length == 0) {
            return data;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gzipOut = new GZIPOutputStream(out)) {
            gzipOut.write(data);
        }
        return out.toByteArray();
    }
    
    /**
     * 解压数据
     * @param data 压缩的数据
     * @return 解压后的数据
     * @throws IOException
     */
    public static byte[] decompress(byte[] data) throws IOException {
        if (data == null || data.length == 0) {
            return data;
        }
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPInputStream gzipIn = new GZIPInputStream(in)) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = gzipIn.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
        }
        return out.toByteArray();
    }
}
