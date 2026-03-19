package com.hospital.triage.modules.auth.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.security")
public class AppSecurityProperties {

    private String jwtSecret;
    private Long jwtExpireSeconds;
    private String tokenPrefix = "Bearer";
}
