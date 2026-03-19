package com.hospital.triage.modules.auth.security;

import lombok.Builder;
import lombok.Getter;

import java.io.Serializable;
import java.util.List;

@Getter
@Builder
public class AuthenticatedUser implements Serializable {

    private final Long userId;
    private final String username;
    private final String nickname;
    private final String roleCode;
    private final Long deptId;
    private final Long roomId;
    private final List<String> permissions;
}
