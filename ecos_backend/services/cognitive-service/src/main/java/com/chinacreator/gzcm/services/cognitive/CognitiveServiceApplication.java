package com.chinacreator.gzcm.services.cognitive;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {
    "com.chinacreator.gzcm.services.cognitive"
})
public class CognitiveServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(CognitiveServiceApplication.class, args);
    }
}
