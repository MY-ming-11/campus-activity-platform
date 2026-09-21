package com.luosh.activity.controller;

import com.luosh.activity.common.R;
import com.luosh.activity.dto.CheckinDTO;
import com.luosh.activity.security.CurrentUser;
import com.luosh.activity.service.CheckinService;
import com.luosh.activity.vo.CheckinResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "签到")
@RestController
@RequestMapping("/api/activities/{activityId}/checkin")
@RequiredArgsConstructor
public class CheckinController {

    private final CheckinService checkinService;

    @Operation(summary = "用签到码核销自己")
    @PostMapping
    public R<CheckinResultVO> checkin(@PathVariable Long activityId,
                                      @RequestBody @Valid CheckinDTO dto) {
        return R.ok(checkinService.checkinByCode(activityId, CurrentUser.id(), dto.getCode()));
    }
}
