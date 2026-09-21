package com.luosh.activity.config;

import com.luosh.activity.entity.User;
import com.luosh.activity.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 首次启动时灌入两个演示账号，让 docker compose 起来就能直接登录。
 * 用户表只要有数据就跳过，不会覆盖你后来改的密码。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        if (userMapper.selectCount(null) > 0) {
            return;
        }
        userMapper.insert(buildUser("admin", "admin123", "管理员", "ADMIN"));
        userMapper.insert(buildUser("student", "123456", "张同学", "USER"));
        log.warn("已初始化演示账号 admin/admin123（管理员）与 student/123456（学生），上线前请修改");
    }

    private User buildUser(String username, String rawPassword, String nickname, String role) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setNickname(nickname);
        user.setPhone("");
        user.setRole(role);
        return user;
    }
}
