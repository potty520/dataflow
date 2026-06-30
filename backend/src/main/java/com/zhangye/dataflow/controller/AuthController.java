package com.zhangye.dataflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhangye.dataflow.common.BusinessException;
import com.zhangye.dataflow.common.Result;
import com.zhangye.dataflow.dto.LoginRequest;
import com.zhangye.dataflow.dto.LoginResponse;
import com.zhangye.dataflow.dto.PasswordChangeRequest;
import com.zhangye.dataflow.dto.ProfileUpdateRequest;
import com.zhangye.dataflow.entity.SysMenu;
import com.zhangye.dataflow.entity.SysUser;
import com.zhangye.dataflow.mapper.SysMenuMapper;
import com.zhangye.dataflow.mapper.SysUserMapper;
import com.zhangye.dataflow.security.JwtTokenProvider;
import com.zhangye.dataflow.security.LoginUser;
import com.zhangye.dataflow.security.SecurityUtils;
import com.zhangye.dataflow.service.OperLogService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final SysUserMapper userMapper;
    private final SysMenuMapper menuMapper;
    private final PasswordEncoder passwordEncoder;
    private final OperLogService operLogService;

    public AuthController(AuthenticationManager authenticationManager, JwtTokenProvider tokenProvider,
                          SysUserMapper userMapper, SysMenuMapper menuMapper,
                          PasswordEncoder passwordEncoder, OperLogService operLogService) {
        this.authenticationManager = authenticationManager;
        this.tokenProvider = tokenProvider;
        this.userMapper = userMapper;
        this.menuMapper = menuMapper;
        this.passwordEncoder = passwordEncoder;
        this.operLogService = operLogService;
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
        LoginUser user = (LoginUser) auth.getPrincipal();
        String token = tokenProvider.generateToken(user.getUserId(), user.getUsername(), user.getTenantId());
        operLogService.log(httpRequest, "用户登录", "认证", request.getUsername(), true, "登录成功");

        LoginResponse resp = new LoginResponse();
        resp.setToken(token);
        resp.setUserId(user.getUserId());
        resp.setUsername(user.getUsername());
        resp.setTenantId(user.getTenantId());
        resp.setRoles(user.getRoles());

        SysUser dbUser = userMapper.selectById(user.getUserId());
        if (dbUser != null) {
            resp.setNickname(dbUser.getNickname());
        }
        return Result.ok(resp);
    }

    @GetMapping("/info")
    public Result<Map<String, Object>> info() {
        LoginUser user = SecurityUtils.getCurrentUser();
        if (user == null) {
            throw new BusinessException("未登录");
        }
        SysUser dbUser = userMapper.selectById(user.getUserId());
        if (dbUser != null) {
            dbUser.setPassword(null);
        }
        List<SysMenu> menus = menuMapper.selectMenusByUserId(user.getUserId());
        if (menus.isEmpty()) {
            menus = menuMapper.selectList(new LambdaQueryWrapper<SysMenu>().eq(SysMenu::getVisible, 1).orderByAsc(SysMenu::getSortOrder));
        }
        List<SysMenu> tree = buildMenuTree(menus, 0L);
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("user", dbUser);
        data.put("roles", user.getRoles());
        data.put("menus", tree);
        return Result.ok(data);
    }

    @PutMapping("/password")
    public Result<Void> changePassword(@RequestBody PasswordChangeRequest req, HttpServletRequest httpRequest) {
        SysUser user = userMapper.selectById(SecurityUtils.getCurrentUserId());
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (!passwordEncoder.matches(req.getOldPassword(), user.getPassword())) {
            throw new BusinessException("原密码错误");
        }
        if (!StringUtils.hasText(req.getNewPassword()) || !req.getNewPassword().equals(req.getConfirmPassword())) {
            throw new BusinessException("两次新密码不一致");
        }
        user.setPassword(passwordEncoder.encode(req.getNewPassword()));
        userMapper.updateById(user);
        operLogService.log(httpRequest, "修改密码", "个人中心", user.getUsername(), true, null);
        return Result.ok();
    }

    @PutMapping("/profile")
    public Result<Void> updateProfile(@RequestBody ProfileUpdateRequest req, HttpServletRequest httpRequest) {
        SysUser user = userMapper.selectById(SecurityUtils.getCurrentUserId());
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (StringUtils.hasText(req.getNickname())) {
            user.setNickname(req.getNickname());
        }
        if (StringUtils.hasText(req.getPhone())) {
            user.setPhone(req.getPhone());
        }
        if (StringUtils.hasText(req.getAvatar())) {
            user.setAvatar(req.getAvatar());
        }
        userMapper.updateById(user);
        operLogService.log(httpRequest, "编辑个人信息", "个人中心", user.getUsername(), true, null);
        return Result.ok();
    }

    private List<SysMenu> buildMenuTree(List<SysMenu> menus, Long parentId) {
        return menus.stream()
                .filter(m -> parentId.equals(m.getParentId() == null ? 0L : m.getParentId()))
                .peek(m -> m.setChildren(buildMenuTree(menus, m.getId())))
                .collect(Collectors.toList());
    }
}
