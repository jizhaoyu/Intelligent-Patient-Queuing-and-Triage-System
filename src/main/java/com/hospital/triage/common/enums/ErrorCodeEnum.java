package com.hospital.triage.common.enums;

import lombok.Getter;

@Getter
public enum ErrorCodeEnum {

    SUCCESS("00000", "操作成功"),
    BAD_REQUEST("40000", "请求参数错误"),
    UNAUTHORIZED("40100", "未登录或登录已失效"),
    FORBIDDEN("40300", "无权限访问"),
    NOT_FOUND("40400", "资源不存在"),
    CONFLICT("40900", "资源状态冲突"),
    SYSTEM_ERROR("50000", "系统繁忙，请稍后再试");

    private final String code;
    private final String message;

    ErrorCodeEnum(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
