package com.hospital.triage;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@MapperScan(basePackages = "com.hospital.triage.modules", annotationClass = Mapper.class)
@SpringBootApplication
public class TriageQueueApplication {

    public static void main(String[] args) {
        SpringApplication.run(TriageQueueApplication.class, args);
    }
}
