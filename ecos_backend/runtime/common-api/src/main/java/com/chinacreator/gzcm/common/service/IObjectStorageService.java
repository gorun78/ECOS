package com.chinacreator.gzcm.common.service;

/**
 * A3 对象存储服务抽象接口。
 * <p>
 * 提供统一的文件存储访问能力，支持多种后端实现：
 * <ul>
 *   <li>MinioObjectStorageService — MinIO（企业版 / 旗舰版）</li>
 *   <li>PgObjectStorageService — PostgreSQL BYTEA（标准版）</li>
 * </ul>
 */
public interface IObjectStorageService {

    /**
     * 上传对象。
     *
     * @param key         对象标识（路径/文件名）
     * @param data        字节数据
     * @param contentType MIME 类型
     * @return 对象 key（与入参相同，便于链式调用和审计）
     */
    String putObject(String key, byte[] data, String contentType);

    /**
     * 下载对象。
     *
     * @param key 对象标识
     * @return 字节数据，若不存在则返回 null
     */
    byte[] getObject(String key);

    /**
     * 删除对象。
     *
     * @param key 对象标识
     */
    void deleteObject(String key);
}
