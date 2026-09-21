package com.luosh.activity.vo;

import java.util.List;

/**
 * 核销台一次请求拿到的全部数据：活动概况 + 到场统计 + 名单。
 */
public record CheckinBoardVO(Long activityId,
                             String title,
                             Integer quota,
                             Long remaining,
                             int signedCount,
                             int checkedCount,
                             List<CheckinRosterVO> records) {
}
