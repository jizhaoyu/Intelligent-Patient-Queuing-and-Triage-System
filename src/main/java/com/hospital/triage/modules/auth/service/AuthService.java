package com.hospital.triage.modules.auth.service;

import com.hospital.triage.modules.auth.dto.LoginRequest;
import com.hospital.triage.modules.auth.security.AuthenticatedUser;
import com.hospital.triage.modules.auth.vo.LoginVO;
import com.hospital.triage.modules.auth.vo.UserProfileVO;

public interface AuthService {

    LoginVO login(LoginRequest request);

    void logout(AuthenticatedUser user);

    UserProfileVO me(AuthenticatedUser user);
}
