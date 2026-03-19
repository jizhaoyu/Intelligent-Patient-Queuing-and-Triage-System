package com.hospital.triage.common.api;

import com.hospital.triage.common.enums.ErrorCodeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {

    private boolean success;
    private String code;
    private String message;
    private T data;

    public static <T> Result<T> success(T data) {
        return Result.<T>builder()
                .success(true)
                .code(ErrorCodeEnum.SUCCESS.getCode())
                .message(ErrorCodeEnum.SUCCESS.getMessage())
                .data(data)
                .build();
    }

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> failed(ErrorCodeEnum errorCode) {
        return failed(errorCode.getCode(), errorCode.getMessage());
    }

    public static <T> Result<T> failed(String code, String message) {
        return Result.<T>builder()
                .success(false)
                .code(code)
                .message(message)
                .build();
    }
}
