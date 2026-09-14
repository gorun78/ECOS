package com.chinacreator.gzcm.common.exception;

import java.io.Serial;

/**
 * 数据访问层异常。
 * <p>
 * DAO 层遇到数据库错误时抛出此异常，Service 层可捕获并转为 BusinessException。
 * 典型场景：SQL 执行失败、连接超时、唯一约束冲突。
 */
public class DataAccessException extends DataBridgeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final int DEFAULT_HTTP_STATUS = 500;
    public static final int DEFAULT_ERROR_CODE = -100;

    public DataAccessException(String message) {
        super(DEFAULT_HTTP_STATUS, DEFAULT_ERROR_CODE, message);
    }

    public DataAccessException(String message, Throwable cause) {
        super(DEFAULT_HTTP_STATUS, DEFAULT_ERROR_CODE, message, cause);
    }

    /** 快捷构建：数据库操作失败 */
    public static DataAccessException queryFailed(String entity, String id, Throwable cause) {
        return new DataAccessException(
                "查询 " + entity + " 失败: id=" + id, cause);
    }

    public static DataAccessException insertFailed(String entity, Throwable cause) {
        return new DataAccessException(
                "插入 " + entity + " 失败", cause);
    }

    public static DataAccessException updateFailed(String entity, String id, Throwable cause) {
        return new DataAccessException(
                "更新 " + entity + " 失败: id=" + id, cause);
    }
}
