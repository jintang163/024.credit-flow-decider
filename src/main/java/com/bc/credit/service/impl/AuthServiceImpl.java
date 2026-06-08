package com.bc.credit.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.bc.credit.common.exception.BusinessException;
import com.bc.credit.dto.LoginRequestDTO;
import com.bc.credit.dto.LoginResponseDTO;
import com.bc.credit.entity.SysUser;
import com.bc.credit.mapper.SysUserMapper;
import com.bc.credit.service.AuthService;
import com.bc.credit.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired(required = false)
    private PasswordEncoder passwordEncoder;

    @Value("${credit.auth.jwt-expire-minutes:720}")
    private long expireMinutes;

    @Value("${credit.auth.default-admin-username:admin}")
    private String defaultAdminUsername;

    @Value("${credit.auth.default-admin-password:123456}")
    private String defaultAdminPassword;

    private final Map<String, String> tokenStore = new ConcurrentHashMap<>();

    @PostConstruct
    public void initDefaultUser() {
        try {
            SysUser admin = sysUserMapper.selectByUsername(defaultAdminUsername);
            if (admin == null) {
                admin = new SysUser();
                admin.setId(IdWorker.getId());
                admin.setUsername(defaultAdminUsername);
                admin.setPassword(passwordEncoder != null
                        ? passwordEncoder.encode(defaultAdminPassword)
                        : defaultAdminPassword);
                admin.setRealName("系统管理员");
                admin.setEmail("admin@bc.com");
                admin.setPhone("13800138000");
                admin.setOrgId(1L);
                admin.setStatus(1);
                admin.setUserType("ADMIN");
                admin.setCreatedTime(LocalDateTime.now());
                admin.setUpdatedTime(LocalDateTime.now());
                admin.setDeleted(0);
                sysUserMapper.insert(admin);
                log.info("初始化默认管理员用户成功: {}", defaultAdminUsername);
            }
        } catch (Exception e) {
            log.warn("初始化默认管理员用户失败: {}", e.getMessage());
        }
    }

    @Override
    public LoginResponseDTO login(LoginRequestDTO request) {
        log.info("用户登录请求: {}", request.getUsername());

        SysUser user = sysUserMapper.selectByUsername(request.getUsername());
        if (user == null) {
            throw new BusinessException("用户名或密码错误");
        }

        if (user.getStatus() != null && user.getStatus() != 1) {
            throw new BusinessException("用户已被禁用");
        }

        boolean passwordMatch = false;
        if (passwordEncoder != null) {
            passwordMatch = passwordEncoder.matches(request.getPassword(), user.getPassword());
        } else {
            passwordMatch = request.getPassword().equals(user.getPassword());
        }

        if (!passwordMatch) {
            throw new BusinessException("用户名或密码错误");
        }

        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRealName());
        tokenStore.put(token, user.getUsername());

        List<String> roles = getUserRoles(user.getId());
        String orgName = sysUserMapper.selectOrgNameById(user.getOrgId());

        LoginResponseDTO response = new LoginResponseDTO();
        response.setUserId(user.getId());
        response.setUsername(user.getUsername());
        response.setRealName(user.getRealName());
        response.setToken(token);
        response.setExpireSeconds(expireMinutes * 60);
        response.setRoles(roles);
        response.setOrgId(user.getOrgId());
        response.setOrgName(orgName);

        log.info("用户登录成功: {}, roles: {}", user.getUsername(), roles);
        return response;
    }

    @Override
    public void logout(String token) {
        if (token != null && !token.isEmpty()) {
            tokenStore.remove(token);
            log.info("用户登出成功");
        }
    }

    @Override
    public SysUser getCurrentUser(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }

        String username = jwtUtil.getUsernameFromToken(token);
        if (username == null) {
            return null;
        }

        if (!tokenStore.containsKey(token)) {
            return null;
        }

        return sysUserMapper.selectByUsername(username);
    }

    @Override
    public List<String> getUserRoles(Long userId) {
        return sysUserMapper.selectRoleCodesByUserId(userId);
    }

    @Override
    public boolean hasRole(Long userId, String roleCode) {
        List<String> roles = getUserRoles(userId);
        return roles != null && roles.contains(roleCode);
    }
}
