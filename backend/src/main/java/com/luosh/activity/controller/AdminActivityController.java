package com.luosh.activity.controller;

import com.luosh.activity.common.PageVO;
import com.luosh.activity.common.R;
import com.luosh.activity.dto.ActivityDTO;
import com.luosh.activity.entity.Activity;
import com.luosh.activity.security.CurrentUser;
import com.luosh.activity.service.ActivityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端接口，整段路径在 SecurityConfig 里要求 ADMIN 角色。
 */
@Tag(name = "管理端-活动")
@RestController
@RequestMapping("/api/admin/activities")
@RequiredArgsConstructor
public class AdminActivityController {

    private final ActivityService activityService;

    @Operation(summary = "活动列表，含草稿")
    @GetMapping
    public R<PageVO<Activity>> list(@RequestParam(defaultValue = "1") long page,
                                    @RequestParam(defaultValue = "10") long size,
                                    @RequestParam(required = false) String keyword,
                                    @RequestParam(required = false) String status) {
        return R.ok(activityService.page(page, size, keyword, status));
    }

    @Operation(summary = "新建活动")
    @PostMapping
    public R<Long> create(@RequestBody @Valid ActivityDTO dto) {
        return R.ok(activityService.create(dto, CurrentUser.id()));
    }

    @Operation(summary = "修改活动")
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody @Valid ActivityDTO dto) {
        activityService.update(id, dto);
        return R.ok();
    }

    @Operation(summary = "删除活动")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        activityService.delete(id);
        return R.ok();
    }
}
