package com.hospital.triage.common.constant;

public final class RedisKeyConstants {

    public static final String QUEUE_DEPT_ACTIVE = "queue:dept:%s:active";
    public static final String QUEUE_DEPT_UNASSIGNED_HIGH = "queue:dept:%s:unassigned-high";
    public static final String QUEUE_ROOM_ACTIVE = "queue:room:%s:active";
    public static final String QUEUE_TICKET = "queue:ticket:%s";
    public static final String QUEUE_CALLING = "queue:calling:%s";
    public static final String QUEUE_SEQ = "queue:seq:%s:%s";
    public static final String AUTH_TOKEN = "auth:token:%s";
    public static final String DASHBOARD_DEPT = "dashboard:dept:%s";

    private RedisKeyConstants() {
    }
}
