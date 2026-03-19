package com.hospital.triage;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@MapperScan("com.hospital.triage.modules")
@SpringBootApplication
public class TriageQueueApplication {

    public static void main(String[] args) {
        SpringApplication.run(TriageQueueApplication.class, args);
    }
}
