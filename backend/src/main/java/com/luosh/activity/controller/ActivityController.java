package com.luosh.activity.controller;

import com.luosh.activity.common.PageVO;
import com.luosh.activity.common.R;
import com.luosh.activity.entity.Activity;
import com.luosh.activity.service.ActivityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 面向学生的公开接口，不需要登录。
 */
@Tag(name = "活动（公开）")
@RestController
@RequestMapping("/api/activities")
@RequiredArgsConstructor
public class ActivityController {

    private static final String STATUS_PUBLISHED = "PUBLISHED";

    private final ActivityService activityService;

    @Operation(summary = "活动列表，只返回已发布的活动")
    @GetMapping
    public R<PageVO<Activity>> list(@RequestParam(defaultValue = "1") long page,
                                    @RequestParam(defaultValue = "10") long size,
                                    @RequestParam(required = false) String keyword) {
        return R.ok(activityService.page(page, size, keyword, STATUS_PUBLISHED));
    }

    @Operation(summary = "活动详情")
    @GetMapping("/{id}")
    public R<Activity> detail(@PathVariable Long id) {
        return R.ok(activityService.detail(id));
    }
}
