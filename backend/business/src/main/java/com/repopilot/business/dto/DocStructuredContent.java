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

    private String schemaVersion = "1";
    private String project;
    private String branch;
    private String commitId;
    private String sourceFilePath;
    private List<TypeDoc> types = new ArrayList<>();

    //静态内部类:TypeDoc 逻辑上属于 DocStructuredContent，
    //但创建 TypeDoc 对象时不依赖外部 DocStructuredContent 实例。
    @Data
    public static class TypeDoc {
        private String htmlFile;
        private String kind;
        private String name;
        private String qualifiedName;
        private String signature;
        private String description;
        private List<MemberDoc> fields = new ArrayList<>();
        private List<MemberDoc> constructors = new ArrayList<>();
        private List<MemberDoc> methods = new ArrayList<>();
    }

    @Data
    public static class MemberDoc {
        private String id;
        private String kind;
        private String name;
        private String signature;
        private String description;
        private List<ParameterDoc> parameters = new ArrayList<>();
        private ReturnDoc returns;
        @JsonProperty("throws")
        private List<ThrowsDoc> throwsItems = new ArrayList<>();
    }

    @Data
    public static class ParameterDoc {
        private String name;
        private String type;
        private String description;
    }

    @Data
    public static class ReturnDoc {
        private String type;
        private String description;
    }

    @Data
    public static class ThrowsDoc {
        private String type;
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