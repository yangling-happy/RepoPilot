package com.repopilot.business.dto;

//这是 Jackson 的注解。
// Jackson 是 Spring Boot 默认常用的 JSON 序列化 / 反序列化工具。
// 它可以把 Java 对象转成 JSON，也可以把 JSON 转成 Java 对象。
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

//把文档生成结果组织成一个固定的 Java 对象结构，然后方便序列化成 JSON 返回给前端，或者写入文件
//Lombok 注解，自动生成 getter/setter、toString()、equals()、hashCode() 等方法，减少样板代码
@Data
public class DocStructuredContent {

    //文档格式版本号，方便未来格式升级时做兼容
    private String schemaVersion = "1";
    //项目名称
    private String project;
    //分支名称
    private String branch;
    //源文件所属的 commit hash
    private String commitId;
    //源文件在仓库中的路径
    private String sourceFilePath;
    //一个 Java 文件中可能包含多个类型（类、接口、枚举等），用列表存储
    private List<TypeDoc> types = new ArrayList<>();

    //静态内部类:TypeDoc 逻辑上属于 DocStructuredContent，
    //但创建 TypeDoc 对象时不依赖外部 DocStructuredContent 实例。
    //表示一个类型（类/接口/枚举/注解）的文档
    @Data
    public static class TypeDoc {
        //对应的 javadoc HTML 文件名
        private String htmlFile;
        //类型种类：CLASS、INTERFACE、ENUM、ANNOTATION、RECORD
        private String kind;
        //类型简单名（如 UserService）
        private String name;
        //全限定名（如 com.example.UserService）
        private String qualifiedName;
        //类型签名（如 public class UserService）
        private String signature;
        //类型级别的 Javadoc 描述
        private String description;
        //字段列表
        private List<MemberDoc> fields = new ArrayList<>();
        //构造函数列表
        private List<MemberDoc> constructors = new ArrayList<>();
        //方法列表
        private List<MemberDoc> methods = new ArrayList<>();
    }

    //表示类成员（字段/构造函数/方法）的文档
    @Data
    public static class MemberDoc {
        //成员的唯一标识（通常是方法签名）
        private String id;
        //成员种类：FIELD、CONSTRUCTOR、METHOD
        private String kind;
        //成员名称
        private String name;
        //成员签名（如 public String getUserName(Long id)）
        private String signature;
        //成员的 Javadoc 描述
        private String description;
        //方法参数列表（仅方法和构造函数有）
        private List<ParameterDoc> parameters = new ArrayList<>();
        //返回值信息（仅方法有）
        private ReturnDoc returns;
        //@JsonProperty("throws") 是 Jackson 注解，指定序列化成 JSON 时字段名为 "throws"
        //因为 "throws" 是 Java 关键字，不能直接用作字段名，所以用 throwsItems 做 Java 字段名
        @JsonProperty("throws")
        private List<ThrowsDoc> throwsItems = new ArrayList<>();
    }

    //方法参数文档
    @Data
    public static class ParameterDoc {
        //参数名
        private String name;
        //参数类型（如 Long、String）
        private String type;
        //参数的 Javadoc 描述
        private String description;
    }

    //方法返回值文档
    @Data
    public static class ReturnDoc {
        //返回值类型
        private String type;
        //返回值的 Javadoc 描述
        private String description;
    }

    //方法抛出异常文档
    @Data
    public static class ThrowsDoc {
        //异常类型（如 IllegalArgumentException）
        private String type;
        //异常的 Javadoc 描述
        private String description;
    }
}
/* 假设有一个java文件:
package com.example;

/**
 * 用户服务。
 
public class UserService {

    /**
     * 根据 ID 查询用户。
     *
     * @param id 用户 ID
     * @return 用户名称
     * @throws IllegalArgumentException ID 为空时抛出
     
    public String getUserName(Long id) throws IllegalArgumentException {
        return "Tom";
    }
}
*/
/* 最终生成的结果会是: 
{
    "schemaVersion": "1",
    "project": "RepoPilot",
    "branch": "main",
    "commitId": "abc123",
    "sourceFilePath": "src/main/java/com/example/UserService.java",
    "types": [
      {
        "htmlFile": "UserService.html",
        "kind": "class",
        "name": "UserService",
        "qualifiedName": "com.example.UserService",
        "signature": "public class UserService",
        "description": "用户服务。",
        "fields": [],
        "constructors": [],
        "methods": [
          {
            "id": "getUserName(java.lang.Long)",
            "kind": "method",
            "name": "getUserName",
            "signature": "public String getUserName(Long id) throws IllegalArgumentException",
            "description": "根据 ID 查询用户。",
            "parameters": [
              {
                "name": "id",
                "type": "Long",
                "description": "用户 ID"
              }
            ],
            "returns": {
              "type": "String",
              "description": "用户名称"
            },
            "throws": [
              {
                "type": "IllegalArgumentException",
                "description": "ID 为空时抛出"
              }
            ]
          }
        ]
      }
    ]
  } 
*/