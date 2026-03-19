package com.hospital.triage.modules.queue.service.impl;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.queue")
public class AppQueueProperties {

    private Integer recallLimit = 2;
    private Integer agingScorePerMinute = 2;
    private Integer estimateMinutesPerPerson = 5;
    private Integer callNextRetryTimes = 3;
    private Integer callingTtlSeconds = 1800;
    private Boolean allowDeptFallback = true;
}
