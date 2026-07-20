package com.chinacreator.gzcm.engine.cognitive2.boot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {
    "com.chinacreator.gzcm.engine.cognitive2"
})
public class CognitiveEngine2Application {
    public static void main(String[] args) {
        SpringApplication.run(CognitiveEngine2Application.class, args);
    }
}
