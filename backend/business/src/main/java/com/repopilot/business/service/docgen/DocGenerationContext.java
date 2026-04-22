package com.repopilot.business.service.docgen;

import lombok.Builder;
import lombok.Value;

import java.nio.file.Path;

@Value
@Builder
public class DocGenerationContext {

    String project;
    String branch;
    String commitId;
    String filePath;
    String sourceContent;
    Path sourceRoot;
    Path outputRoot;
}
