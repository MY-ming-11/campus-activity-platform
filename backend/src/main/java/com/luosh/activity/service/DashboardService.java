package com.luosh.activity.service;

import com.luosh.activity.mapper.DashboardMapper;
import com.luosh.activity.vo.DashboardVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final int TREND_DAYS = 14;
    private static final String STATUS_PUBLISHED = "PUBLISHED";

    private final DashboardMapper dashboardMapper;

    public DashboardVO dashboard() {
        long activityTotal = dashboardMapper.countActivities();
        long publishedTotal = dashboardMapper.countActivitiesByStatus(STATUS_PUBLISHED);
        long signupTotal = dashboardMapper.countSignups();
        long checkinTotal = dashboardMapper.countCheckins();
        long attendRate = signupTotal == 0 ? 0 : Math.round(checkinTotal * 100.0 / signupTotal);

        DashboardVO dashboard = new DashboardVO();
        dashboard.setOverview(new DashboardVO.Overview(
                activityTotal, publishedTotal, signupTotal, checkinTotal, attendRate));
        dashboard.setTrend(buildTrend());
        dashboard.setActivities(dashboardMapper.selectActivityStats());
        return dashboard;
    }

    /**
     * 数据库只返回"有报名的那几天"，这里把 14 天补齐。
     * 不补的话前端得自己判断哪天缺了，画出来的柱子会挤在一起。
     */
    private List<DashboardVO.TrendPoint> buildTrend() {
        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(TREND_DAYS - 1L);

        Map<String, DashboardVO.TrendPoint> indexed = dashboardMapper
                .selectDailySignups(from.atStartOfDay())
                .stream()
                .collect(Collectors.toMap(DashboardVO.TrendPoint::getDate, Function.identity()));

        List<DashboardVO.TrendPoint> trend = new ArrayList<>(TREND_DAYS);
        for (LocalDate day = from; !day.isAfter(today); day = day.plusDays(1)) {
            DashboardVO.TrendPoint point = indexed.get(day.toString());
            if (point == null) {
                point = new DashboardVO.TrendPoint();
                point.setDate(day.toString());
                point.setCount(0);
            }
            trend.add(point);
        }
        return trend;
    }
}
