package com.chinacreator.gzcm.runtime.core.dataaccess.exception;

/**
 * 数据访问错误码枚举
 * 
 * @author CDRC Runtime Team
 */
public enum DataAccessErrorCode {
    
    // 通用错误 (1000-1999)
    UNKNOWN_ERROR("1000", "未知错误"),
    INVALID_PARAMETER("1001", "参数无效"),
    DATA_PRODUCT_NOT_FOUND("1002", "数据产品不存在"),
    STORAGE_ADAPTER_NOT_FOUND("1003", "存储适配器不存在"),
    
    // 查询错误 (2000-2999)
    QUERY_FAILED("2000", "查询失败"),
    QUERY_TIMEOUT("2001", "查询超时"),
    INVALID_QUERY_CONDITION("2002", "查询条件无效"),
    QUERY_RESULT_CONVERSION_FAILED("2003", "查询结果转换失败"),
    
    // 插入错误 (3000-3999)
    INSERT_FAILED("3000", "插入失败"),
    INSERT_VALIDATION_FAILED("3001", "插入数据验证失败"),
    INSERT_DUPLICATE_KEY("3002", "插入数据主键冲突"),
    INSERT_BATCH_FAILED("3003", "批量插入失败"),
    INSERT_TRANSACTION_FAILED("3004", "插入事务失败"),
    
    // 更新错误 (4000-4999)
    UPDATE_FAILED("4000", "更新失败"),
    UPDATE_NO_RECORDS_AFFECTED("4001", "更新未影响任何记录"),
    UPDATE_OPTIMISTIC_LOCK_FAILED("4002", "乐观锁更新失败"),
    UPDATE_VALIDATION_FAILED("4003", "更新数据验证失败"),
    
    // 删除错误 (5000-5999)
    DELETE_FAILED("5000", "删除失败"),
    DELETE_NO_RECORDS_AFFECTED("5001", "删除未影响任何记录"),
    DELETE_CONSTRAINT_VIOLATION("5002", "删除违反约束"),
    
    // 批量操作错误 (6000-6999)
    BATCH_OPERATION_FAILED("6000", "批量操作失败"),
    BATCH_TRANSACTION_FAILED("6001", "批量操作事务失败"),
    BATCH_PARTIAL_FAILURE("6002", "批量操作部分失败"),
    
    // 数据验证错误 (7000-7999)
    VALIDATION_FAILED("7000", "数据验证失败"),
    VALIDATION_TYPE_MISMATCH("7001", "数据类型不匹配"),
    VALIDATION_REQUIRED_FIELD_MISSING("7002", "必填字段缺失"),
    VALIDATION_CONSTRAINT_VIOLATION("7003", "约束验证失败"),
    
    // 连接错误 (8000-8999)
    CONNECTION_FAILED("8000", "连接失败"),
    CONNECTION_TIMEOUT("8001", "连接超时"),
    CONNECTION_CLOSED("8002", "连接已关闭");
    
    private String code;
    private String message;
    
    DataAccessErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }
    
    public String getCode() {
        return code;
    }
    
    public String getMessage() {
        return message;
    }
    
    /**
     * 根据错误码获取枚举
     */
    public static DataAccessErrorCode fromCode(String code) {
        for (DataAccessErrorCode errorCode : values()) {
            if (errorCode.getCode().equals(code)) {
                return errorCode;
            }
        }
        return UNKNOWN_ERROR;
    }
}
