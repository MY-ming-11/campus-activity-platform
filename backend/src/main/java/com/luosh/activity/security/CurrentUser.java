package com.luosh.activity.security;

import com.luosh.activity.common.BizException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 取当前登录用户，避免每个 Controller 都写一遍 SecurityContext 的样板代码。
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static LoginUser get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof LoginUser user)) {
            throw new BizException(401, "未登录");
        }
        return user;
    }

    public static Long id() {
        return get().userId();
    }
}
