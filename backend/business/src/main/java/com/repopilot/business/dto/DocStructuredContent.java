package com.repopilot.business.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class DocStructuredContent {

    private String schemaVersion = "1";
    private String project;
    private String branch;
    private String commitId;
    private String sourceFilePath;
    private List<TypeDoc> types = new ArrayList<>();

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
