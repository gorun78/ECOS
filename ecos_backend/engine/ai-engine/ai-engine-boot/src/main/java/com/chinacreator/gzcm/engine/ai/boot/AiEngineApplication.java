package com.chinacreator.gzcm.engine.ai.boot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = {
    HibernateJpaAutoConfiguration.class,
    JpaRepositoriesAutoConfiguration.class
})
@EnableScheduling
@ComponentScan(basePackages = {
    "com.chinacreator.gzcm.engine.ai",
        "com.chinacreator.gzcm.aimod",
        "com.chinacreator.gzcm.dccheng",
        "com.chinacreator.gzcm.cognitive",
        "com.chinacreator.gzcm.runtime"
})
public class AiEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiEngineApplication.class, args);
    }
}
