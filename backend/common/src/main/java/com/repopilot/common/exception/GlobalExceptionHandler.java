package com.repopilot.common.exception;

import com.repopilot.common.dto.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> handleBusinessException(BusinessException e) {
        log.warn("Business exception: {}", e.getMessage());
        return ApiResponse.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleValidationException(Exception e) {
        log.warn("Validation exception: {}", e.getMessage());
        return ApiResponse.error(400, resolveBindingMessage(e));
    }

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            ConstraintViolationException.class,
            HttpMessageNotReadableException.class
    })
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleBadRequestException(Exception e) {
        log.warn("Bad request exception: {}", e.getMessage());
        return ApiResponse.error(400, resolveBadRequestMessage(e));
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleException(Exception e) {
        log.error("Unexpected exception: {}", e.getMessage(), e);
        return ApiResponse.error("Internal server error");
    }

    private String resolveBindingMessage(Exception e) {
        if (e instanceof MethodArgumentNotValidException methodArgumentNotValidException) {
            String message = methodArgumentNotValidException.getBindingResult()
                    .getFieldErrors()
                    .stream()
                    .map(this::formatFieldError)
                    .collect(Collectors.joining("; "));
            return message.isBlank() ? "Invalid request parameters" : message;
        }
        if (e instanceof BindException bindException) {
            String message = bindException.getBindingResult()
                    .getFieldErrors()
                    .stream()
                    .map(this::formatFieldError)
                    .collect(Collectors.joining("; "));
            return message.isBlank() ? "Invalid request parameters" : message;
        }
        return "Invalid request parameters";
    }

    private String resolveBadRequestMessage(Exception e) {
        if (e instanceof MissingServletRequestParameterException missingParameterException) {
            return missingParameterException.getParameterName() + " is required";
        }
        if (e instanceof MethodArgumentTypeMismatchException typeMismatchException) {
            return typeMismatchException.getName() + " has invalid type";
        }
        if (e instanceof ConstraintViolationException constraintViolationException) {
            String message = constraintViolationException.getConstraintViolations()
                    .stream()
                    .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
                    .collect(Collectors.joining("; "));
            return message.isBlank() ? "Invalid request parameters" : message;
        }
        if (e instanceof HttpMessageNotReadableException) {
            return "Request body is invalid";
        }
        return "Invalid request parameters";
    }

    private String formatFieldError(FieldError fieldError) {
        String defaultMessage = fieldError.getDefaultMessage();
        return fieldError.getField() + " " + (defaultMessage == null ? "is invalid" : defaultMessage);
    }
}
