package com.bc.credit.service;

import com.bc.credit.dto.LoginRequestDTO;
import com.bc.credit.dto.LoginResponseDTO;
import com.bc.credit.entity.SysUser;

import java.util.List;

public interface AuthService {

    LoginResponseDTO login(LoginRequestDTO request);

    void logout(String token);

    SysUser getCurrentUser(String token);

    List<String> getUserRoles(Long userId);

    boolean hasRole(Long userId, String roleCode);
}
