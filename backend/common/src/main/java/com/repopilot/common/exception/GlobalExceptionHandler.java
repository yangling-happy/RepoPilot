package com.repopilot.common.exception;

import com.repopilot.common.dto.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

//Lombok 注解，编译后自动生成 private static final Logger log = ...，
//可直接用 log.info(...) 记录日志
@Slf4j
//@RestControllerAdvice 是一个用于 统一捕获并处理 REST 控制器异常 的复合注解
//它等价于 @ControllerAdvice + @ResponseBody，能确保异常信息以 JSON 格式 返回给客户端
@RestControllerAdvice
public class GlobalExceptionHandler {

    //处理业务逻辑错误
    //只要 Controller 执行过程中抛出了 BusinessException，就交给这个方法处理
    @ExceptionHandler(BusinessException.class)
    //这个方法最终返回的 HTTP 状态码是 200 OK
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> handleBusinessException(BusinessException e) {
        //BusinessException 通常是可预期的业务错误，不是系统崩溃，所以是warn信息
        log.warn("Business exception: {}", e.getMessage());
        //把 BusinessException 转换成统一响应格式
        return ApiResponse.error(e.getCode(), e.getMessage());
    }

    //处理参数校验失败错误
    //MethodArgumentNotValidException，常见于 @RequestBody + @Valid 的对象校验失败
    //BindException，常见于表单参数、对象绑定参数校验失败
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    //这个方法最终返回的 HTTP 状态码是 400 参数错误
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleValidationException(Exception e) {
        log.warn("Validation exception: {}", e.getMessage());
        //转换成统一响应格式返回
        return ApiResponse.error(400, resolveBindingMessage(e));
    }

    //处理请求格式错误
    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            ConstraintViolationException.class,
            HttpMessageNotReadableException.class
    })
    //这个方法最终返回的 HTTP 状态码是 400 参数错误
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleBadRequestException(Exception e) {
        log.warn("Bad request exception: {}", e.getMessage());
        return ApiResponse.error(400, resolveBadRequestMessage(e));
    }

    //兜底处理，Exception 是大多数异常的父类。如果前面那些更具体的异常处理方法都匹配不上，最后就会走到这里
    @ExceptionHandler(Exception.class)
    //这个方法最终返回的 HTTP 状态码是 500 服务器错误
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleException(Exception e) {
        log.error("Unexpected exception: {}", e.getMessage(), e);
        return ApiResponse.error("Internal server error");
    }

    //下面三个私有方法，是专门用来拼接body信息的，给上面的函数调用

    //专门处理字段绑定/字段校验错误
    private String resolveBindingMessage(Exception e) {
        if (e instanceof MethodArgumentNotValidException methodArgumentNotValidException) {
            String message = methodArgumentNotValidException.getBindingResult()
                    //获取绑定结果
                    .getFieldErrors()
                    //获取所有字段错误
                    .stream()
                    //把获取到的错误都map函数formatFieldError转为字符串
                    .map(this::formatFieldError)
                    //拼起来
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
        //MissingServletRequestParameterException，缺少必填的请求参数，返回 "参数名 is required"
        if (e instanceof MissingServletRequestParameterException missingParameterException) {
            return missingParameterException.getParameterName() + " is required";
        }
        //MethodArgumentTypeMismatchException，参数类型不匹配（比如要 Integer 却传了字符串），返回 "参数名 has invalid type"
        if (e instanceof MethodArgumentTypeMismatchException typeMismatchException) {
            return typeMismatchException.getName() + " has invalid type";
        }
        //通常由 @Validated 在 Controller 层触发（方法参数校验），遍历所有约束违反信息，拼接成 "属性路径 具体消息" 并用分号分隔
        if (e instanceof ConstraintViolationException constraintViolationException) {
            String message = constraintViolationException.getConstraintViolations()
                    .stream()
                    .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
                    .collect(Collectors.joining("; "));
            return message.isBlank() ? "Invalid request parameters" : message;
        }
        //请求体无法解析（比如 JSON 格式错误），返回 "Request body is invalid"
        if (e instanceof HttpMessageNotReadableException) {
            return "Request body is invalid";
        }
        return "Invalid request parameters";
    }

    //把一个 Spring 的 FieldError 对象格式化成字符串 "字段名 错误描述"
    //FieldError 一般来自 MethodArgumentNotValidException（用在 @Valid 校验 Java Bean 时）
    private String formatFieldError(FieldError fieldError) {
        String defaultMessage = fieldError.getDefaultMessage();
        return fieldError.getField() + " " + (defaultMessage == null ? "is invalid" : defaultMessage);
    }
}
