package com.chinacreator.gzcm.runtime.core.common;

import java.util.Map;

/**
 * DxConstans - 数据交换常量类
 */
public class DxConstans {
    
    // 数据库类型常量
    public static final String DBTYPE_MONGODB = "MONGODB";
    public static final String DBTYPE_ORACLE = "ORACLE";
    public static final String DBTYPE_MYSQL = "MYSQL";
    public static final String DBTYPE_MSSQL = "MSSQL";
    public static final String DBTYPE_POSTGRESQL = "POSTGRESQL";
    public static final String KETTLE_DBTYPE_ORACLE = "ORACLE";
    public static final String KETTLE_DBTYPE_POSTGRESQL = "POSTGRESQL";
    public static final String KETTLE_DBTYPE_SQLSERVER = "SQLSERVER";
    public static final String KETTLE_DBTYPE_KINGBASE = "KINGBASE";
    public static final String KETTLE_DBTYPE_IRIS = "IRIS";
    public static final String KETTLE_DBTYPE_MYSQL = "MYSQL";
    public static final String KETTLE_DBTYPE_DAMENG = "DAMENG";
    public static final String KETTLE_DBTYPE_DB2 = "DB2";
    
    // 数据对象类型常量
    public static final String HB_TYPE = "HBASE";
    public static final String WEBSERVICE_TYPE = "WEBSERVICE";
    public static final String SQL = "SQL";
    public static final String DB_TYPE = "DB";
    
    // 提取方式常量
    public static final String TOTAL = "TOTAL";
    public static final String SAMPLING = "SAMPLING";
    public static final String INC_BY_TIME = "INC_BY_TIME";
    public static final String INC_BY_CDC = "INC_BY_CDC";
    public static final String INC_BY_LOGO = "INC_BY_LOGO";
    
    // 数据交换类型常量
    public static final String DB_DX_TYPE_COLLECT = "COLLECT";
    public static final String DB_DX_TYPE_CLEAN = "CLEAN";
    public static final String DB_DX_TYPE_SHARE = "SHARE";
    public static final String DB_DX_TYPE_ASSESS = "ASSESS";
    public static final String DB_DX_TYPE_INTEGRATION = "INTEGRATION";
    public static final String DX_TYPE_COLLECT = "COLLECT";
    public static final String DX_TYPE_CLEAN = "CLEAN";
    public static final String DX_TYPE_SHARE = "SHARE";
    
    // 布尔值字符串常量
    public static final String DB_TRUE_STRING = "1";
    public static final String DB_FLASE_STRING = "0";
    
    // Excel类型常量
    public static final String EXCEL_TYPE_2003 = "2003";
    public static final String EXCEL_TYPE_2007 = "2007";
    
    // 默认数据库名
    public static final String DEFAULT_DB_NAME = "default";
    
    // 转换名分隔符
    public static final String TARNSNAME_SPLIT = "_";
    
    // 流程维护方式常量
    public static final String PROCESS_MAINTENANCE_MODE_ALL_CUSTOM = "ALL_CUSTOM";
    public static final String PROCESS_MAINTENANCE_MODE_SEMI_CUSTOM = "SEMI_CUSTOM";
    public static final String PROCESS_MAINTENANCE_MODE_STANDARD = "STANDARD";
    
    // 系统变量作用域级别
    public static final String SYSTEM_VARIABLE_SCOPE_LEVEL_DS = "DS";
    
    // 系统变量值类型
    public static final String SYSTEM_VARIABLE_VALUE_TYPE_SYSTIME = "SYSTIME";
    public static final String SYSTEM_VARIABLE_VALUE_TYPE_FIX = "FIX";
    
    // 调度处理类型常量
    public static final String SCHEDULE_PROC_TYPE_ETL = "ETL";
    public static final String SCHEDULE_PROC_TYPE_CONSOLE = "CONSOLE";
    
    // 调度状态常量
    public static final String SCHEDULE_STATUS_INVALID = "INVALID";
    public static final String SCHEDULE_STOPPING = "STOPPING";
    public static final String SCHEDULE_STOP = "STOP";
    public static final String SCHEDULE_RUNNING = "RUNNING";
    
    // 日志处理流程ID常量
    public static final String LOG_UPDATE_START_PROCESS_ID = "LOG_UPDATE_START";
    public static final String LOG_UPLOAD_START_PROCESS_ID = "LOG_UPLOAD_START";
    
    // 导出方式常量
    public static final String EXPORT_WAY_SINGLEOUT = "1";
    public static final String EXPORT_WAY_NOTOUT = "0";
    public static final String EXPORT_WAY_COPYMUTIPLEOUT = "2";
    public static final String EXPORT_WAY_SWITHMUTIPLEOUT = "3";
    
    // RPC队列常量
    public static final String DestQueue_BUS = "BUS";
    public static final String DestQueue_METADATA = "METADATA";
    public static final String DestQueue_QUALITY = "QUALITY";
    public static final String DestQueue_STANDARD = "STANDARD";
    public static final String DestQueue_RESCATALOG = "RESCATALOG";
    
    // 日志上传数据源常量
    public static final String LOG_UPLOAD_DATASOURCE = "LOG_UPLOAD";
    
    // 错误数据标识常量
    public static final String CCC_DX_IS_ERROR_DATA = "CCC_DX_IS_ERROR_DATA";
    public static final String CCC_DX_IS_ERROR_DATA_NAME = "是否错误数据";
    
    // 字段内容类型常量
    public static final String FIELD_CONTENT_TYPE_JSON = "JSON";
    public static final String FIELD_CONTENT_TYPE_XML = "XML";
    
    // 参数类型常量
    public static final String PARAMETER_TYPE_DYNAMIC = "DYNAMIC";
    
    // 参数格式类型常量
    public static final String PARAMETER_FOEMAT_TYPE_TIMESTAMP = "TIMESTAMP";
    
    // 数据规则类型常量
    public static final String DATA_RULE_CODECONVERT = "CODECONVERT";
    public static final String DATA_RULE_STRINGREPLACE = "STRINGREPLACE";
    public static final String DATA_RULE_CUSTOMCONVERT = "CUSTOMCONVERT";
    public static final String DATA_RULE_XML_CONVERT = "XML_CONVERT";
    public static final String DATA_RULE_VALIDRULE = "VALIDRULE";
    public static final String DATA_RULE_ALIGNMENT = "ALIGNMENT";
    
    // 本地文件类型常量
    public static final String LOCAL_FILE_TYPE = "LOCAL_FILE";
    
    // 远程文件类型常量
    public static final String REMOTE_FILE_TYPE = "REMOTE_FILE";
    
    // FTP文件类型常量
    public static final String FTP_FILE_TYPE = "FTP_FILE";
    
    // HDFS文件类型常量
    public static final String HDFS_FILE_TYPE = "HDFS_FILE";
    
    // 数据库数据类型常量
    public static final String DB_DATA_TYPE_NUMBER = "NUMBER";
    public static final String DB_DATA_TYPE_CHAR = "CHAR";
    public static final String DB_DATA_TYPE_VARCHAR = "VARCHAR";
    public static final String DB_DATA_TYPE_VARCHAR2 = "VARCHAR2";
    
    // 逻辑数据类型常量
    public static final String LOGIC_TYPE_NUMBER = "NUMBER";
    public static final String LOGIC_TYPE_STRING = "STRING";
    
    // 数据类型常量（用于参数类型）
    public static final String STRING = "STRING";
    public static final String NUMBER = "NUMBER";
    
    // 校验类型常量
    public static final String PROCESS_VALID = "PROCESS_VALID";
    public static final String SCRIPT_VALID = "SCRIPT_VALID";
    
    // CCC系统字段常量
    public static final String FIELD_CCC_DX_ETL_TIME = "CCC_DX_ETL_TIME";
    public static final String FIELD_CCC_DX_ETL_TIME_NAME = "数据采集时间";
    public static final String CCC_DX_EXCUTE_LOG_ID = "CCC_DX_EXCUTE_LOG_ID";
    public static final String CCC_DX_EXCUTE_LOG_ID_NAME = "执行日志ID";
    
    // RESTful主键字段常量
    public static final String RESTFUL_PRIMARY_KEY_FIELD = "RESTFUL_PRIMARY_KEY";
    public static final String RESTFUL_PRIMARY_KEY_FIELD_NAME = "RESTFUL_PRIMARY_KEY";
    public static final String RESTFUL_FOREIGN_KEY_FIELD = "RESTFUL_FOREIGN_KEY";
    public static final String RESTFUL_FOREIGN_KEY_FIELD_NAME = "RESTFUL_FOREIGN_KEY";
    
    // 授权类型常量
    public static final String AUTHORIZATION_TYPE_NONE = "NONE";
    public static final String AUTHORIZATION_TYPE_BEARER_TOKEN = "BEARER_TOKEN";
    
    // Token来源常量
    public static final String TOKEN_SOURCE_DYNAMIC = "DYNAMIC";
    public static final String TOKEN_SOURCE_FIXED = "FIXED";
    public static final String TOKEN_VECTOR_HEADER = "HEADER";

    // 参数类型常量（用于参数配置）
    public static final String PARAMETER_TYPE_FIXED = "FIXED";

    // 过程监控默认行数常量（用于 ProcessMonitorServiceImpl）
    // 旧代码按 String 参数传递，这里保持 String 兼容
    public static final String PROCESS_MONITOR_INPUT_LINES = "200";
    public static final String PROCESS_MONITORLOG_INPUT_LINES = "200";
    public static final String PROCESS_MONITOR_OUTPUT_LINES = "200";
    
    // 日志清理配置参数常量（用于 LogCleanService）
    public static final String LOGCLEANCONFIG_PARAM_TYPE = "LOGCLEANCONFIG";
    public static final String LOGCLEANCONFIG_PARAM_ID = "LOGCLEANCONFIG_ID";
    
    // 调度类型常量（用于 LogUploadServiceImpl）
    public static final String DB_SCHEDULE_TYPE_USER = "USER";
    
    // 校准类型常量
    public static final String SIMPLE_CALIBRATION = "SIMPLE";
    public static final String USER_DEFINED_CALIBRATION = "USER_DEFINED";
    public static final String ALIGNMENT_CALIBRATION = "ALIGNMENT";
    
    // 问题类型常量
    public static final String PROPLEM_COMMONLY = "COMMONLY";
    public static final String PROPLEM_COMMONLY_SHOW = "一般问题";
    public static final String PROPLEM_GRAVENESS = "GRAVENESS";
    public static final String PROPLEM_GRAVENESS_SHOW = "严重问题";
    public static final String PROPLEM_NOT_GRAVENESS = "NOT_GRAVENESS";
    public static final String PROPLEM_NOT_GRAVENESS_SHOW = "非严重问题";
    
    // 基础数据校准类型常量
    public static final String BASE_DATAOBJECT_CALIBRATION = "BASE_DATAOBJECT";
    public static final String BASE_DICT_CALIBRATION = "BASE_DICT";
    public static final String BASE_OBJSELF_CALIBRATION = "BASE_OBJSELF";
    
    // 错误结果类型常量
    public static final String TO_ERROR_RESULT_TYPE_IN = "IN";
    public static final String TO_ERROR_RESULT_TYPE_IN_STR = "入库";
    public static final String TO_ERROR_RESULT_TYPE_NOTIN = "NOTIN";
    public static final String TO_ERROR_RESULT_TYPE_NOTIN_STR = "不入库";
    
    // 非结构化文件类型常量
    public static final String UNSTRUCTURED_FILE_TYPE = "non_struct";
    
    // 文件对象类型数组常量
    public static final String[] FILEOBJ_TYPES = {".xml", ".xls", ".xlsx", ".txt", "non_struct"};
    
    // 数据集类型常量
    public static final String REF_DATA_SET = "REF_DATA_SET";
    public static final String CITE_REF_DATA_SET = "CITE_REF_DATA_SET";
    public static final String PERFORM_DATA_SET = "PERFORM_DATA_SET";
    public static final String CITE_PERFORM_DATA_SET = "CITE_PERFORM_DATA_SET";
    
    // XML模板相关常量
    public static final String XML_NODE_ELE_TYPE = "ELE";
    public static final String XML_BIND_TYPE_DM = "DM";
    public static final String XML_BIND_TYPE_SYSVAR = "SYSVAR";
    public static final String SYSVAR_CURRENTTIME = "CURRENTTIME";
    public static final String SYSVAR_CURRENTINDEX = "CURRENTINDEX";
    
    // 对齐类型常量
    public static final String ALIGNMENT_TYPE_DICTMAP = "DICTMAP";
    public static final String ALIGNMENT_TYPE_OBJECT = "OBJECT";
    
    // 数据源类型常量
    public static final String DATA_CENTER_DATASOURCE = "DATA_CENTER";
    
    // 校验类型常量
    public static final String SIMPLE_FIELD_VALID = "SIMPLE_FIELD_VALID";
    
    // 校准列常量
    public static final String CALIBRATION_COLUMN = "CALIBRATION_COLUMN";
    
    // 数据对象创建类型常量
    public static final String DB_CREATE_BY_DX_TYPE_CREATED = "CREATED";
    
    // 服务状态常量
    public static final String STATUS_REGESTED = "REGESTED";
    
    /**
     * 获取用户登录会话信息
     * @param params 参数Map
     * @return 用户会话信息Map
     */
    public static Map<String, String> getUserLoginSession(Map<String, Object> params) {
        // TODO: 实现获取用户登录会话的逻辑
        return new java.util.HashMap<>();
    }
}

