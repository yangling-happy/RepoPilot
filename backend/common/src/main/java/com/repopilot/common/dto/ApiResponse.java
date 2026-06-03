package com.repopilot.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

//Lombok 注解，自动生成 getter/setter、toString()、equals()、hashCode() 等方法，减少样板代码
@Data
//Data中本来是有@RequiredArgsConstructor。也就是说，所有final/注解@NotNull的字段都将自动被赋值，
//但是由于我们在下面显式声明了@NoArgsConstructor和@AllArgsConstructor，所以data中的构造器会被忽略

//生成无参构造器 public ApiResponse() {}。
//这对 JSON 反序列化（比如 Spring 的 @RequestBody）很重要，框架通常需要一个无参构造器先创建对象，再通过 setter 赋值
@NoArgsConstructor
//生成包含三个字段 code、message、data 的构造器。
//静态工厂方法内部就是通过 new ApiResponse<>(code, message, data) 调用这个全参构造来创建实例
@AllArgsConstructor
public class ApiResponse<T> {
    //这里的code不是http状态码，是自定义的错误码
    //使用Interger，代表值可以是null
    private Integer code; 
    private String message;
    private T data;

    //简单成功，不需要返回成功信息
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(200, "Success", data);
    }

    //自定义成功信息
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(200, message, data);
    }

    //自定义错误码和消息
    public static <T> ApiResponse<T> error(Integer code, String message) {
        return new ApiResponse<>(code, message, null);
    }

    //默认服务器错误
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(500, message, null);
    }
}

/*返回格式如下 
{
    "code": 200,
    "message": "Success",
    "data": {}
} 
*/