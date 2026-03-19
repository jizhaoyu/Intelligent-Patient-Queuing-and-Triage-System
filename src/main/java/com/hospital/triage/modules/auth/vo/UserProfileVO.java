package com.hospital.triage.modules.auth.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class UserProfileVO {

    private Long userId;
    private String username;
    private String nickname;
    private String roleCode;
    private Long deptId;
    private Long roomId;
    private List<String> permissions;
}
