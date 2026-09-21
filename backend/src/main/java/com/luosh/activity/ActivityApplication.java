package com.luosh.activity;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 排除 UserDetailsServiceAutoConfiguration：
 * 我们自己用 JWT 认证，不需要 Spring Security 的默认内存用户，
 * 不排除的话每次启动都会打印一串随机密码，日志会误导人。
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@MapperScan("com.luosh.activity.mapper")
@EnableScheduling
public class ActivityApplication {

    public static void main(String[] args) {
        SpringApplication.run(ActivityApplication.class, args);
    }
}
