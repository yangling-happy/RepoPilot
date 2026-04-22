package com.repopilot.business.service.docgen;

import java.util.Set;

public interface DocGenerator {

    String toolName();

    Set<String> supportedExtensions();

    DocGenerationResult generate(DocGenerationContext context);
}
