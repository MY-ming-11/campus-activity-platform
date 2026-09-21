package com.luosh.activity.security;

/**
 * 从 token 解析出来的登录身份，作为 SecurityContext 的 principal。
 */
public record LoginUser(Long userId, String role) {

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }
}
