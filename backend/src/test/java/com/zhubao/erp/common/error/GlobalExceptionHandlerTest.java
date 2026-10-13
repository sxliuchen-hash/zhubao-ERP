package com.zhubao.erp.common.error;

import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.security.domain.AuthLoginCommand;
import com.zhubao.erp.security.web.AuthController;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.constraints.NotBlank;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void bindingErrorsReturnOnlyDistinctSortedMessages() throws Exception {
        BeanPropertyBindingResult errors = new BeanPropertyBindingResult(
                new AuthLoginCommand("private-user", "private-password"), "authLoginCommand");
        errors.addError(fieldError("username", "请输入用户名"));
        errors.addError(fieldError("password", " 请输入密码 "));
        errors.addError(fieldError("password", "请输入密码"));
        errors.addError(fieldError("username", null));
        errors.addError(fieldError("username", " "));

        ApiResponse<Void> response = handler.handleValidationException(bindingException(errors));

        assertThat(response.code()).isEqualTo(422);
        assertThat(response.message()).isEqualTo("请输入密码；请输入用户名")
                .doesNotContain("private-user", "private-password", "rejected value", "AuthController", "login(");
        assertThat(response.data()).isNull();
    }

    @Test
    void bindingErrorsWithoutMessagesUseSafeFallback() throws Exception {
        BeanPropertyBindingResult errors = new BeanPropertyBindingResult(new AuthLoginCommand(null, null), "login");
        errors.addError(fieldError("username", null));
        errors.addError(fieldError("password", " "));

        assertThat(handler.handleValidationException(bindingException(errors)).message())
                .isEqualTo("请求参数不正确");
    }

    @Test
    void constraintViolationsReturnOnlyDistinctSortedMessages() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var violations = factory.getValidator().validate(new InvalidParameters(null, null, null, null));
            ApiResponse<Void> response = handler.handleValidationException(
                    new ConstraintViolationException("内部异常 private-password", violations));

            assertThat(response.code()).isEqualTo(422);
            assertThat(response.message()).isEqualTo("请输入密码；请输入用户名")
                    .doesNotContain("内部异常", "private-password", "username", "password");
        }
    }

    @Test
    void constraintViolationsWithoutMessagesUseSafeFallback() {
        ApiResponse<Void> response = handler.handleValidationException(
                new ConstraintViolationException("内部异常 private-password", Set.of()));

        assertThat(response.code()).isEqualTo(422);
        assertThat(response.message()).isEqualTo("请求参数不正确");
    }

    private FieldError fieldError(String field, String message) {
        return new FieldError("authLoginCommand", field, "private-password", false, null, null, message);
    }

    private MethodArgumentNotValidException bindingException(BeanPropertyBindingResult errors) throws Exception {
        return new MethodArgumentNotValidException(new MethodParameter(
                AuthController.class.getMethod("login", AuthLoginCommand.class), 0), errors);
    }

    private record InvalidParameters(
            @NotBlank(message = "请输入用户名") String username,
            @NotBlank(message = "请输入密码") String password,
            @NotBlank(message = "请输入密码") String repeatedPassword,
            @NotBlank(message = " ") String ignored
    ) {
    }
}
