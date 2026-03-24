package com.hospital.triage.common.enums;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;

@Getter
public enum ErrorCodeEnum {

    SUCCESS(HttpStatus.OK, "操作成功"),
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "请求参数错误"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "未登录或登录已失效"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "无权限访问"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "资源不存在"),
    CONFLICT(HttpStatus.CONFLICT, "资源状态冲突"),
    SYSTEM_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "系统繁忙，请稍后再试");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    ErrorCodeEnum(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.code = String.valueOf(httpStatus.value());
        this.message = message;
    }

    public static HttpStatus resolveHttpStatus(String code) {
        if (StringUtils.hasText(code)) {
            try {
                HttpStatus resolved = HttpStatus.resolve(Integer.parseInt(code.trim()));
                if (resolved != null) {
                    return resolved;
                }
            } catch (NumberFormatException ignored) {
                // Fall back to known enum values when the code is not numeric.
            }
            for (ErrorCodeEnum value : values()) {
                if (value.code.equals(code.trim())) {
                    return value.httpStatus;
                }
            }
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
