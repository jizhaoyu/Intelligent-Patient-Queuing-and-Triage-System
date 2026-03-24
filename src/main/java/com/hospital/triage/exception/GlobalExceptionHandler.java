package com.hospital.triage.exception;

import com.hospital.triage.common.api.Result;
import com.hospital.triage.common.enums.ErrorCodeEnum;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ServiceException.class)
    public ResponseEntity<Result<Void>> handleServiceException(ServiceException ex) {
        HttpStatus httpStatus = ErrorCodeEnum.resolveHttpStatus(ex.getCode());
        return ResponseEntity.status(httpStatus)
                .body(Result.failed(httpStatus, ex.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class, ConstraintViolationException.class,
            HttpMessageNotReadableException.class})
    public ResponseEntity<Result<Void>> handleBadRequest(Exception ex) {
        return ResponseEntity.status(ErrorCodeEnum.BAD_REQUEST.getHttpStatus())
                .body(Result.failed(ErrorCodeEnum.BAD_REQUEST.getHttpStatus(), ex.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<Void>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(ErrorCodeEnum.FORBIDDEN.getHttpStatus())
                .body(Result.failed(ErrorCodeEnum.FORBIDDEN));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(ErrorCodeEnum.SYSTEM_ERROR.getHttpStatus())
                .body(Result.failed(ErrorCodeEnum.SYSTEM_ERROR));
    }
}
