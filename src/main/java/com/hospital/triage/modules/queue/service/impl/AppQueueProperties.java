package com.hospital.triage.modules.queue.service.impl;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

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
    private Integer surgeWaitingThreshold = 6;
    private Integer surgeHighPriorityThreshold = 2;
    private Integer surgePriorityBonus = 120;
    private Integer surgeFastTrackBonus = 80;
    private Integer agingExplainThresholdMinutes = 15;
    private Integer surgeEligibleLevelThreshold = 2;
    private Integer kioskSevereLevelThreshold = 2;
    private Integer roomDiversionHighLevelThreshold = 2;
    private Integer emergencyPriorityRoomHighLevelThreshold = 2;
    private Integer kioskPriorityRoomBonus = 180;
    private Integer kioskPriorityRoomReservePenalty = 120;
    private Integer kioskWaitingWeight = 100;
    private Integer kioskCallingWeight = 130;
    private Integer kioskMissedWeight = 90;
    private Integer kioskDailyAssignmentWeight = 12;
    private Map<Long, Long> severePriorityRoomByDept = new HashMap<>();
}
