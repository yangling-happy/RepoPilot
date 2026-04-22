package com.repopilot.business.service.docgen;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class DocGenerationResult {

    String docFilePath;
}
