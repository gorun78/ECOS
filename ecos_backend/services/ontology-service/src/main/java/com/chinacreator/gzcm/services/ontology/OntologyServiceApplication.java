package com.chinacreator.gzcm.services.ontology;

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
        "com.chinacreator.gzcm.services.ontology",
        "com.chinacreator.gzcm.dccheng",
        "com.chinacreator.gzcm.buszhi",
        "com.chinacreator.gzcm.engine.ontology",
        "com.chinacreator.gzcm.runtime"
})
public class OntologyServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(OntologyServiceApplication.class, args);
    }
}
