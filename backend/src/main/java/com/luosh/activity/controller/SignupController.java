package com.luosh.activity.controller;

import com.luosh.activity.common.R;
import com.luosh.activity.security.CurrentUser;
import com.luosh.activity.service.SignupService;
import com.luosh.activity.vo.MySignupVO;
import com.luosh.activity.vo.SignupResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "报名")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SignupController {

    private final SignupService signupService;

    @Operation(summary = "报名抢名额",
            description = "请求头 X-Signup-Strategy 可以在对比实验时覆盖并发策略（需服务端开启 allow-override）")
    @PostMapping("/activities/{activityId}/signup")
    public R<SignupResultVO> signup(@PathVariable Long activityId,
                                    @RequestHeader(value = "X-Signup-Strategy", required = false)
                                    String strategy) {
        return R.ok(signupService.signup(activityId, CurrentUser.id(), strategy));
    }

    @Operation(summary = "取消报名")
    @DeleteMapping("/activities/{activityId}/signup")
    public R<Void> cancel(@PathVariable Long activityId,
                          @RequestHeader(value = "X-Signup-Strategy", required = false)
                          String strategy) {
        signupService.cancel(activityId, CurrentUser.id(), strategy);
        return R.ok();
    }

    @Operation(summary = "我的报名")
    @GetMapping("/signups/my")
    public R<List<MySignupVO>> mySignups() {
        return R.ok(signupService.mySignups(CurrentUser.id()));
    }
}
