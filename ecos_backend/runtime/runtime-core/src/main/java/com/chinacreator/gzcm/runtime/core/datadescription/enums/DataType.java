package com.chinacreator.gzcm.runtime.core.datadescription.enums;

/**
 * 数据类型枚举
 * 定义系统支持的数据类型
 * 
 * @author CDRC Runtime Team
 */
public enum DataType {
    
    /**
     * 结构化数据
     * 包括：数据库表、视图、SQL查询结果等
     */
    STRUCTURED("结构化数据", "包括数据库表、视图、SQL查询结果等结构化数据"),
    
    /**
     * 文件数据
     * 包括：文档、图像、视频、音频等文件类型
     */
    FILE("文件数据", "包括文档、图像、视频、音频等文件类型"),
    
    /**
     * 服务接口数据
     * 包括：RESTful API、GraphQL、gRPC等服务接口
     */
    SERVICE("服务接口数据", "包括RESTful API、GraphQL、gRPC等服务接口数据"),
    
    /**
     * 流数据
     * 包括：Kafka消息流、事件流等实时数据流
     */
    STREAM("流数据", "包括Kafka消息流、事件流等实时数据流");
    
    /**
     * 类型名称
     */
    private final String name;
    
    /**
     * 类型描述
     */
    private final String description;
    
    /**
     * 构造函数
     * 
     * @param name 类型名称
     * @param description 类型描述
     */
    DataType(String name, String description) {
        this.name = name;
        this.description = description;
    }
    
    /**
     * 获取类型名称
     * 
     * @return 类型名称
     */
    public String getName() {
        return name;
    }
    
    /**
     * 获取类型描述
     * 
     * @return 类型描述
     */
    public String getDescription() {
        return description;
    }
    
    /**
     * 判断是否为结构化数据
     * 
     * @return true表示是结构化数据
     */
    public boolean isStructured() {
        return this == STRUCTURED;
    }
    
    /**
     * 判断是否为文件数据
     * 
     * @return true表示是文件数据
     */
    public boolean isFile() {
        return this == FILE;
    }
    
    /**
     * 判断是否为服务接口数据
     * 
     * @return true表示是服务接口数据
     */
    public boolean isService() {
        return this == SERVICE;
    }
    
    /**
     * 判断是否为流数据
     * 
     * @return true表示是流数据
     */
    public boolean isStream() {
        return this == STREAM;
    }
}
