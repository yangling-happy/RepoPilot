package com.repopilot.business.service.docgen;

import com.repopilot.business.dto.DocStructuredContent;
import com.repopilot.common.exception.BusinessException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class JavaDocHtmlParser {

    public DocStructuredContent parse(DocGenerationContext context, Path outputDir, List<Path> htmlFiles) {
        DocStructuredContent content = new DocStructuredContent();
        content.setProject(context.getProject());
        content.setBranch(context.getBranch());
        content.setCommitId(context.getCommitId());
        content.setSourceFilePath(context.getFilePath());

        for (Path htmlFile : htmlFiles) {
            DocStructuredContent.TypeDoc typeDoc = parseTypeDoc(outputDir, htmlFile);
            if (typeDoc != null) {
                content.getTypes().add(typeDoc);
            }
        }

        if (content.getTypes().isEmpty()) {
            throw new BusinessException(500, "No class HTML pages found in javadoc output: " + context.getFilePath());
        }
        return content;
    }

    private DocStructuredContent.TypeDoc parseTypeDoc(Path outputDir, Path htmlFile) {
        Document document;
        try {
            document = Jsoup.parse(htmlFile.toFile(), "UTF-8");
        } catch (IOException ex) {
            throw new BusinessException(500, "Failed to parse javadoc HTML: " + htmlFile.getFileName());
        }

        Element descriptionSection = firstElement(document, "section.class-description", ".description");
        TypeHeader typeHeader = parseTypeHeader(document, descriptionSection);
        if (typeHeader == null) {
            return null;
        }

        DocStructuredContent.TypeDoc typeDoc = new DocStructuredContent.TypeDoc();
        typeDoc.setHtmlFile(outputDir.relativize(htmlFile).toString().replace('\\', '/'));
        typeDoc.setKind(typeHeader.kind());
        typeDoc.setName(typeHeader.name());
        typeDoc.setQualifiedName(qualifiedName(document, typeHeader.name()));
        typeDoc.setSignature(typeSignature(descriptionSection));
        typeDoc.setDescription(firstBlockText(descriptionSection));
        typeDoc.setFields(parseMembers(document, "section.field-details", "FIELD"));
        typeDoc.setConstructors(parseMembers(document, "section.constructor-details", "CONSTRUCTOR"));
        typeDoc.setMethods(parseMembers(document, "section.method-details", "METHOD"));
        return typeDoc;
    }

    private List<DocStructuredContent.MemberDoc> parseMembers(Document document, String detailsSelector, String kind) {
        List<DocStructuredContent.MemberDoc> members = new ArrayList<>();
        for (Element detail : document.select(detailsSelector + " section.detail")) {
            DocStructuredContent.MemberDoc member = parseMember(detail, kind);
            if (StringUtils.hasText(member.getName()) || StringUtils.hasText(member.getSignature())) {
                members.add(member);
            }
        }
        return members;
    }

    private DocStructuredContent.MemberDoc parseMember(Element detail, String kind) {
        Element signatureElement = firstElement(detail, ".member-signature", "pre");
        String signature = clean(signatureElement == null ? "" : signatureElement.text());

        DocStructuredContent.MemberDoc member = new DocStructuredContent.MemberDoc();
        member.setId(detail.id());
        member.setKind(kind);
        member.setName(memberName(detail, signatureElement));
        member.setSignature(signature);
        member.setDescription(firstBlockText(detail));

        Map<String, String> parameterTypes = parameterTypes(signatureElement);
        parseNotes(detail, member, parameterTypes);
        if ("METHOD".equals(kind) && member.getReturns() == null) {
            String returnType = returnType(signatureElement);
            if (StringUtils.hasText(returnType)) {
                DocStructuredContent.ReturnDoc returns = new DocStructuredContent.ReturnDoc();
                returns.setType(returnType);
                returns.setDescription("");
                member.setReturns(returns);
            }
        }
        return member;
    }

    private void parseNotes(Element detail,
                            DocStructuredContent.MemberDoc member,
                            Map<String, String> parameterTypes) {
        for (Element notes : detail.select("dl.notes")) {
            String term = "";
            for (Element child : notes.children()) {
                if ("dt".equals(child.tagName())) {
                    term = clean(child.text());
                    continue;
                }
                if (!"dd".equals(child.tagName())) {
                    continue;
                }
                String code = firstText(child, "code");
                if (term.startsWith("Parameters") || parameterTypes.containsKey(code)) {
                    member.getParameters().add(parseParameter(child, parameterTypes));
                } else if (term.startsWith("Returns") || isReturnNote(child, detail, member)) {
                    member.setReturns(parseReturn(child, detail));
                } else if (term.startsWith("Throws") || StringUtils.hasText(code)) {
                    member.getThrowsItems().add(parseThrows(child));
                }
            }
        }
    }

    private boolean isReturnNote(Element dd, Element detail, DocStructuredContent.MemberDoc member) {
        return member.getReturns() == null
                && !StringUtils.hasText(firstText(dd, "code"))
                && StringUtils.hasText(returnType(firstElement(detail, ".member-signature", "pre")));
    }

    private DocStructuredContent.ParameterDoc parseParameter(Element dd, Map<String, String> parameterTypes) {
        String name = firstText(dd, "code");
        DocStructuredContent.ParameterDoc parameter = new DocStructuredContent.ParameterDoc();
        parameter.setName(name);
        parameter.setType(parameterTypes.getOrDefault(name, ""));
        parameter.setDescription(ddDescription(dd));
        return parameter;
    }

    private DocStructuredContent.ReturnDoc parseReturn(Element dd, Element detail) {
        DocStructuredContent.ReturnDoc returns = new DocStructuredContent.ReturnDoc();
        returns.setType(returnType(firstElement(detail, ".member-signature", "pre")));
        returns.setDescription(clean(dd.text()));
        return returns;
    }

    private DocStructuredContent.ThrowsDoc parseThrows(Element dd) {
        DocStructuredContent.ThrowsDoc throwsDoc = new DocStructuredContent.ThrowsDoc();
        throwsDoc.setType(firstText(dd, "code"));
        throwsDoc.setDescription(ddDescription(dd));
        return throwsDoc;
    }

    private String memberName(Element detail, Element signatureElement) {
        String heading = firstText(detail, "h3", "h4");
        if (StringUtils.hasText(heading)) {
            return heading;
        }
        Element nameElement = signatureElement == null ? null : firstElement(signatureElement, ".element-name");
        if (nameElement != null) {
            return clean(nameElement.text());
        }
        String id = detail.id();
        int argsStart = id.indexOf('(');
        return argsStart > 0 ? id.substring(0, argsStart) : id;
    }

    private Map<String, String> parameterTypes(Element signatureElement) {
        Map<String, String> parameterTypes = new LinkedHashMap<>();
        Element parametersElement = signatureElement == null ? null : firstElement(signatureElement, ".parameters");
        String parameters = clean(parametersElement == null ? "" : parametersElement.text());
        if (!parameters.startsWith("(") || !parameters.endsWith(")")) {
            return parameterTypes;
        }

        String body = parameters.substring(1, parameters.length() - 1).trim();
        if (!StringUtils.hasText(body)) {
            return parameterTypes;
        }

        for (String parameter : splitParameters(body)) {
            String normalized = stripParameterModifiers(parameter);
            int splitAt = normalized.lastIndexOf(' ');
            if (splitAt <= 0 || splitAt == normalized.length() - 1) {
                continue;
            }
            String type = normalized.substring(0, splitAt).trim();
            String name = normalized.substring(splitAt + 1).trim();
            parameterTypes.put(name, type);
        }
        return parameterTypes;
    }

    private List<String> splitParameters(String parameters) {
        List<String> result = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < parameters.length(); i++) {
            char ch = parameters.charAt(i);
            if (ch == '<') {
                depth++;
            } else if (ch == '>' && depth > 0) {
                depth--;
            } else if (ch == ',' && depth == 0) {
                result.add(parameters.substring(start, i).trim());
                start = i + 1;
            }
        }
        result.add(parameters.substring(start).trim());
        return result;
    }

    private String stripParameterModifiers(String parameter) {
        return parameter
                .replaceAll("@\\S+\\s+", "")
                .replaceAll("\\bfinal\\s+", "")
                .trim();
    }

    private String returnType(Element signatureElement) {
        if (signatureElement == null) {
            return "";
        }
        String returnType = firstText(signatureElement, ".return-type");
        if (StringUtils.hasText(returnType)) {
            return returnType;
        }
        return "";
    }

    private String typeSignature(Element descriptionSection) {
        if (descriptionSection == null) {
            return "";
        }
        String signature = firstText(descriptionSection, ".type-signature", "pre");
        return clean(signature);
    }

    private String qualifiedName(Document document, String name) {
        String packageName = firstText(document, ".sub-title a", ".sub-title");
        if (packageName.startsWith("Package ")) {
            packageName = packageName.substring("Package ".length()).trim();
        }
        return StringUtils.hasText(packageName) ? packageName + "." + name : name;
    }

    private String firstBlockText(Element element) {
        if (element == null) {
            return "";
        }
        for (Element child : element.children()) {
            if (child.hasClass("block")) {
                return clean(child.text());
            }
        }
        return firstText(element, ".block");
    }

    private String ddDescription(Element dd) {
        String text = clean(dd.text());
        String code = firstText(dd, "code");
        if (StringUtils.hasText(code) && text.startsWith(code)) {
            text = text.substring(code.length()).trim();
        }
        if (text.startsWith("-")) {
            text = text.substring(1).trim();
        }
        return text;
    }

    private Element firstElement(Element root, String... selectors) {
        if (root == null) {
            return null;
        }
        for (String selector : selectors) {
            Elements elements = root.select(selector);
            if (!elements.isEmpty()) {
                return elements.first();
            }
        }
        return null;
    }

    private String firstText(Element root, String... selectors) {
        Element element = firstElement(root, selectors);
        return element == null ? "" : clean(element.text());
    }

    private TypeHeader parseTypeHeader(Document document, Element descriptionSection) {
        if (descriptionSection == null) {
            return null;
        }

        String name = firstText(descriptionSection, ".type-name-label");
        String signature = typeSignature(descriptionSection);
        String kind = kindFromSignature(signature);
        if (!StringUtils.hasText(name) || !StringUtils.hasText(kind)) {
            TypeHeader metaHeader = parseTypeHeaderFromMeta(document);
            if (metaHeader != null) {
                return metaHeader;
            }
        }
        if (StringUtils.hasText(name) && StringUtils.hasText(kind)) {
            return new TypeHeader(kind, name);
        }

        String header = firstText(document, "main h1", ".header h1", "h1.title", "h2.title");
        return parseEnglishTypeHeader(header);
    }

    private TypeHeader parseTypeHeaderFromMeta(Document document) {
        Element description = firstElement(document, "meta[name=description]");
        String content = description == null ? "" : clean(description.attr("content"));
        if (!content.startsWith("declaration:")) {
            return null;
        }
        String[] parts = content.split(":");
        if (parts.length < 3) {
            return null;
        }
        String kind = kindFromDeclaration(parts[1].trim());
        String name = parts[2].trim();
        if (!StringUtils.hasText(kind) || !StringUtils.hasText(name)) {
            return null;
        }
        return new TypeHeader(kind, name);
    }

    private String kindFromSignature(String signature) {
        String normalized = " " + clean(signature) + " ";
        if (normalized.contains(" @interface ")) {
            return "ANNOTATION";
        }
        if (normalized.contains(" interface ")) {
            return "INTERFACE";
        }
        if (normalized.contains(" enum ")) {
            return "ENUM";
        }
        if (normalized.contains(" record ")) {
            return "RECORD";
        }
        if (normalized.contains(" class ")) {
            return "CLASS";
        }
        return "";
    }

    private String kindFromDeclaration(String declarationKind) {
        return switch (declarationKind) {
            case "annotation interface", "@interface" -> "ANNOTATION";
            case "class" -> "CLASS";
            case "interface" -> "INTERFACE";
            case "enum" -> "ENUM";
            case "record" -> "RECORD";
            default -> "";
        };
    }

    private TypeHeader parseEnglishTypeHeader(String header) {
        if (!StringUtils.hasText(header)) {
            return null;
        }
        if (header.startsWith("Annotation Interface ")) {
            return new TypeHeader("ANNOTATION", header.substring("Annotation Interface ".length()).trim());
        }
        if (header.startsWith("Class ")) {
            return new TypeHeader("CLASS", header.substring("Class ".length()).trim());
        }
        if (header.startsWith("Interface ")) {
            return new TypeHeader("INTERFACE", header.substring("Interface ".length()).trim());
        }
        if (header.startsWith("Enum ")) {
            return new TypeHeader("ENUM", header.substring("Enum ".length()).trim());
        }
        if (header.startsWith("Record ")) {
            return new TypeHeader("RECORD", header.substring("Record ".length()).trim());
        }
        return null;
    }

    private String clean(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }

    private record TypeHeader(String kind, String name) {
    }
}
