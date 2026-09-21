package com.luosh.activity.common;

/**
 * 业务异常。抛出后由 GlobalExceptionHandler 统一转成 R.fail。
 */
public class BizException extends RuntimeException {

    private final int code;

    public BizException(String msg) {
        this(400, msg);
    }

    public BizException(int code, String msg) {
        super(msg);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
