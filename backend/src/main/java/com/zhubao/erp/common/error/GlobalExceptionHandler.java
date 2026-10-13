package com.zhubao.erp.common.error;

import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.gateway.error.DeviceAuthenticationException;
import com.zhubao.erp.gateway.error.GatewayRateLimitException;
import com.zhubao.erp.gateway.error.GatewayReplayException;
import com.zhubao.erp.security.error.PermissionDeniedException;
import jakarta.validation.ConstraintViolationException;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.http.HttpStatus;
import org.springframework.validation.ObjectError;
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

    @ExceptionHandler(GatewayRateLimitException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public ApiResponse<Void> handleGatewayRateLimitException(GatewayRateLimitException ex) {
        return ApiResponse.error(42901, ex.getMessage());
    }

    @ExceptionHandler(GatewayReplayException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<Void> handleGatewayReplayException(GatewayReplayException ex) {
        return ApiResponse.error(40901, ex.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleValidationException(Exception ex) {
        Stream<String> messages = Stream.empty();
        if (ex instanceof MethodArgumentNotValidException validationException) {
            messages = validationException.getBindingResult().getAllErrors().stream()
                    .map(ObjectError::getDefaultMessage);
        } else if (ex instanceof ConstraintViolationException validationException) {
            messages = validationException.getConstraintViolations().stream()
                    .map(violation -> violation.getMessage());
        }
        String message = messages.filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .sorted()
                .collect(Collectors.joining("；"));
        return ApiResponse.error(422, message.isEmpty() ? "请求参数不正确" : message);
    }
}
