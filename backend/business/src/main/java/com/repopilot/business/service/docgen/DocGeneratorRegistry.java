package com.repopilot.business.service.docgen;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Component
public class DocGeneratorRegistry {

    private final Map<String, DocGenerator> generatorsByExtension;

    public DocGeneratorRegistry(List<DocGenerator> generators) {
        this.generatorsByExtension = new LinkedHashMap<>();
        for (DocGenerator generator : generators) {
            for (String extension : generator.supportedExtensions()) {
                generatorsByExtension.put(normalizeExtension(extension), generator);
            }
        }
    }

    public Optional<DocGenerator> findGenerator(String filePath) {
        String extension = extractExtension(filePath);
        if (!StringUtils.hasText(extension)) {
            return Optional.empty();
        }
        return Optional.ofNullable(generatorsByExtension.get(extension));
    }

    public boolean supports(String filePath) {
        return findGenerator(filePath).isPresent();
    }

    private String extractExtension(String filePath) {
        if (!StringUtils.hasText(filePath)) {
            return null;
        }
        int dotIndex = filePath.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filePath.length() - 1) {
            return null;
        }
        return normalizeExtension(filePath.substring(dotIndex));
    }

    private String normalizeExtension(String extension) {
        String normalized = extension.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith(".") ? normalized : "." + normalized;
    }
}
