package com.hospital.triage.modules.triage.service.impl;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.ai")
public class PatientTriageAiProperties {

    private boolean enabled = false;
    private String provider = "moonshot";
    private String baseUrl = "https://api.moonshot.cn/v1";
    private String apiKey;
    private String model = "moonshot-v1-8k";
    private int connectTimeoutMs = 2000;
    private int readTimeoutMs = 5000;
    private double temperature = 0.2D;
    private int maxTokens = 300;
}

