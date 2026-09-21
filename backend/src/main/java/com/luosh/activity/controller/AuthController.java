package com.luosh.activity.controller;

import com.luosh.activity.common.R;
import com.luosh.activity.dto.LoginDTO;
import com.luosh.activity.dto.RegisterDTO;
import com.luosh.activity.security.CurrentUser;
import com.luosh.activity.service.AuthService;
import com.luosh.activity.vo.LoginVO;
import com.luosh.activity.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "认证")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "注册，注册成功直接返回 token")
    @PostMapping("/register")
    public R<LoginVO> register(@RequestBody @Valid RegisterDTO dto) {
        return R.ok(authService.register(dto));
    }

    @Operation(summary = "登录")
    @PostMapping("/login")
    public R<LoginVO> login(@RequestBody @Valid LoginDTO dto) {
        return R.ok(authService.login(dto));
    }

    @Operation(summary = "获取当前登录用户")
    @GetMapping("/me")
    public R<UserVO> me() {
        return R.ok(authService.me(CurrentUser.id()));
    }
}
