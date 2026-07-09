package com.zhubao.erp.common.error;

import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.gateway.error.DeviceAuthenticationException;
import com.zhubao.erp.security.error.PermissionDeniedException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleBusinessException(BusinessException ex) {
        return ApiResponse.error(ex.code(), ex.getMessage());
    }

    @ExceptionHandler(PermissionDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiResponse<Void> handlePermissionDeniedException(PermissionDeniedException ex) {
        return ApiResponse.error(ex.code(), ex.getMessage());
    }

    @ExceptionHandler(DeviceAuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiResponse<Void> handleDeviceAuthenticationException(DeviceAuthenticationException ex) {
        return ApiResponse.error(40101, ex.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleValidationException(Exception ex) {
        return ApiResponse.error(422, ex.getMessage());
    }
}
