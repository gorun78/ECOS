package com.chinacreator.gzcm.datanet;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * DataNet 启动入口。
 * <p>
 * 数据网络服务独立运行，端口 8082。
 * 基于 MyBatis + MySQL 持久化存储数据源和目录数据。
 */
@SpringBootApplication
@MapperScan(basePackages = {
    "com.chinacreator.gzcm.datanet.repository"
})
public class DatanetApplication {

    public static void main(String[] args) {
        SpringApplication.run(DatanetApplication.class, args);
    }
}
