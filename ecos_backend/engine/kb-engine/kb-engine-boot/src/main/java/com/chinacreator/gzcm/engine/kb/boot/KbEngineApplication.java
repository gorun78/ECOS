package com.chinacreator.gzcm.engine.kb.boot;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication(exclude = {
    org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration.class,
    org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration.class
})
@ComponentScan(basePackages = {
    "com.chinacreator.gzcm.engine.kb",
    "com.chinacreator.gzcm.common",
    "com.chinacreator.gzcm.sysman.config"
})
@MapperScan("com.chinacreator.gzcm.engine.kb.repository")
public class KbEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(KbEngineApplication.class, args);
    }
}
