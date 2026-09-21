package com.luosh.activity.controller;

import com.luosh.activity.common.R;
import com.luosh.activity.dto.ManualCheckinDTO;
import com.luosh.activity.security.CurrentUser;
import com.luosh.activity.service.CheckinService;
import com.luosh.activity.vo.CheckinBoardVO;
import com.luosh.activity.vo.CheckinCodeVO;
import com.luosh.activity.vo.CheckinResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 核销台相关接口，整段路径在 SecurityConfig 里要求 ADMIN 角色。
 * 签到码从这里下发意味着学生端拿不到它，也就没法自己算出码来。
 */
@Tag(name = "管理端-签到核销")
@RestController
@RequestMapping("/api/admin/activities/{activityId}")
@RequiredArgsConstructor
public class AdminCheckinController {

    private final CheckinService checkinService;

    @Operation(summary = "获取当前签到码，供现场大屏展示")
    @GetMapping("/checkin-code")
    public R<CheckinCodeVO> checkinCode(@PathVariable Long activityId) {
        return R.ok(checkinService.currentCode(activityId));
    }

    @Operation(summary = "核销台数据：报名名单、到场统计")
    @GetMapping("/checkins")
    public R<CheckinBoardVO> board(@PathVariable Long activityId) {
        return R.ok(checkinService.board(activityId));
    }

    @Operation(summary = "手动核销某条报名")
    @PostMapping("/checkins")
    public R<CheckinResultVO> manualCheckin(@PathVariable Long activityId,
                                            @RequestBody @Valid ManualCheckinDTO dto) {
        return R.ok(checkinService.manualCheckin(activityId, dto.getSignupId(), CurrentUser.id()));
    }
}
