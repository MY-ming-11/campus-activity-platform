package com.luosh.activity.controller;

import com.luosh.activity.common.R;
import com.luosh.activity.service.DashboardService;
import com.luosh.activity.vo.DashboardVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "管理端-数据看板")
@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final DashboardService dashboardService;

    @Operation(summary = "跨活动看板：总览、报名趋势、活动排行")
    @GetMapping
    public R<DashboardVO> dashboard() {
        return R.ok(dashboardService.dashboard());
    }
}
