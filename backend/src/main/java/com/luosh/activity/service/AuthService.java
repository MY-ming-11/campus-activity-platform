package com.luosh.activity.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.luosh.activity.common.BizException;
import com.luosh.activity.dto.ChangePasswordDTO;
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

    /**
     * 改密码。必须验证旧密码——不然拿到一个还没过期的 token 就能把账号彻底接管。
     *
     * 注意：JWT 是无状态的，改完密码之前签发的 token 在过期前依然有效。
     * 要做到"改密码立即踢掉所有旧登录"，得给用户加一个 token 版本号并写进 JWT，
     * 这里没做，前端改完会主动退出登录来cover 正常使用场景。
     */
    public void changePassword(Long userId, ChangePasswordDTO dto) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(401, "用户不存在");
        }
        if (!passwordEncoder.matches(dto.getOldPassword(), user.getPassword())) {
            throw new BizException("当前密码不正确");
        }
        if (passwordEncoder.matches(dto.getNewPassword(), user.getPassword())) {
            throw new BizException("新密码不能和当前密码一样");
        }

        User update = new User();
        update.setId(userId);
        update.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        userMapper.updateById(update);
    }

    private LoginVO toLoginVO(User user) {
        String token = jwtUtil.sign(user.getId(), user.getRole());
        return new LoginVO(token, toUserVO(user));
    }

    private UserVO toUserVO(User user) {
        return new UserVO(user.getId(), user.getUsername(), user.getNickname(), user.getPhone(), user.getRole());
    }
}
