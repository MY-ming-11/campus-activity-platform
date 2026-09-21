package com.luosh.activity.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.luosh.activity.common.BizException;
import com.luosh.activity.dto.LoginDTO;
import com.luosh.activity.dto.RegisterDTO;
import com.luosh.activity.entity.User;
import com.luosh.activity.mapper.UserMapper;
import com.luosh.activity.security.JwtUtil;
import com.luosh.activity.vo.LoginVO;
import com.luosh.activity.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String ROLE_USER = "USER";

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Transactional
    public LoginVO register(RegisterDTO dto) {
        boolean exists = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, dto.getUsername())) > 0;
        if (exists) {
            throw new BizException("用户名已被占用");
        }

        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setNickname(StringUtils.hasText(dto.getNickname()) ? dto.getNickname() : dto.getUsername());
        user.setPhone(dto.getPhone() == null ? "" : dto.getPhone());
        user.setRole(ROLE_USER);
        userMapper.insert(user);

        return toLoginVO(user);
    }

    public LoginVO login(LoginDTO dto) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, dto.getUsername()));

        // 用户不存在和密码错误返回同一句提示，避免被人枚举出哪些用户名存在
        if (user == null || !passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BizException("用户名或密码错误");
        }
        return toLoginVO(user);
    }

    public UserVO me(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(401, "用户不存在");
        }
        return toUserVO(user);
    }

    private LoginVO toLoginVO(User user) {
        String token = jwtUtil.sign(user.getId(), user.getRole());
        return new LoginVO(token, toUserVO(user));
    }

    private UserVO toUserVO(User user) {
        return new UserVO(user.getId(), user.getUsername(), user.getNickname(), user.getPhone(), user.getRole());
    }
}
