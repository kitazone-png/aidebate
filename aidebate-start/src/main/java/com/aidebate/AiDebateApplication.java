package com.aidebate;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
/**
 * AI Debate Simulator Application
 *
 * @author AI Debate Team
 */
@SpringBootApplication
@MapperScan("com.aidebate.infrastructure.mapper")
public class AiDebateApplication {

    public static void main(String[] args) {
        //FactoryBeanRegistrySupport a;
        SpringApplication.run(AiDebateApplication.class, args);
    }
}
