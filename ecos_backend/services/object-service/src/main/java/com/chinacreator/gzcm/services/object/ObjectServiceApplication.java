package com.chinacreator.gzcm.services.object;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = {
        HibernateJpaAutoConfiguration.class,
        JpaRepositoriesAutoConfiguration.class
})
@EnableScheduling
@ComponentScan(basePackages = {
        "com.chinacreator.gzcm.services.object",
        "com.chinacreator.gzcm.workspace",
        "com.chinacreator.gzcm.runtime"
})
public class ObjectServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ObjectServiceApplication.class, args);
    }
}
