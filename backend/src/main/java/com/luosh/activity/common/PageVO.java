package com.luosh.activity.common;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;

/**
 * 分页结果，只暴露前端需要的四个字段，不把 MyBatis-Plus 的 IPage 直接吐给前端。
 */
public record PageVO<T>(long total, long page, long size, List<T> records) {

    public static <T> PageVO<T> of(IPage<T> p) {
        return new PageVO<>(p.getTotal(), p.getCurrent(), p.getSize(), p.getRecords());
    }
}
