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

//Javadoc HTML 解析器
//职责：将 javadoc 工具生成的 HTML 页面解析成结构化的 DocStructuredContent 对象
//使用 Jsoup（Java 的 HTML 解析库）来解析 HTML DOM，提取类名、方法签名、参数、注释等信息
//这个类是纯工具类，没有 Spring 注解，由 JavaDocGenerator 直接 new 创建
public class JavaDocHtmlParser {

    //入口方法：解析 javadoc 输出的所有 HTML 文件，组装成一个完整的结构化文档
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

    //解析单个 HTML 文件，提取类型（类/接口/枚举）的文档信息
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

    //解析类的成员（字段/构造函数/方法）列表
    //detailsSelector: HTML 中的 CSS 选择器（如 "section.field-details"）
    //kind: 成员类型标识（FIELD/CONSTRUCTOR/METHOD）
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

    //解析单个成员（方法/构造函数/字段）的详细信息
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

    //解析 javadoc HTML 中的 <dl.notes> 元素，提取参数、返回值、异常信息
    //javadoc 将 @param/@return/@throws 标签渲染成 <dl> 列表，<dt> 是标题，<dd> 是内容
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

    //判断一个 <dd> 元素是否是返回值说明（没有 <code> 标签且方法有返回类型）
    private boolean isReturnNote(Element dd, Element detail, DocStructuredContent.MemberDoc member) {
        return member.getReturns() == null
                && !StringUtils.hasText(firstText(dd, "code"))
                && StringUtils.hasText(returnType(firstElement(detail, ".member-signature", "pre")));
    }

    //解析 @param 标签对应的 <dd> 元素，提取参数名、类型和描述
    private DocStructuredContent.ParameterDoc parseParameter(Element dd, Map<String, String> parameterTypes) {
        String name = firstText(dd, "code");
        DocStructuredContent.ParameterDoc parameter = new DocStructuredContent.ParameterDoc();
        parameter.setName(name);
        parameter.setType(parameterTypes.getOrDefault(name, ""));
        parameter.setDescription(ddDescription(dd));
        return parameter;
    }

    //解析 @return 标签对应的 <dd> 元素，提取返回值类型和描述
    private DocStructuredContent.ReturnDoc parseReturn(Element dd, Element detail) {
        DocStructuredContent.ReturnDoc returns = new DocStructuredContent.ReturnDoc();
        returns.setType(returnType(firstElement(detail, ".member-signature", "pre")));
        returns.setDescription(clean(dd.text()));
        return returns;
    }

    //解析 @throws 标签对应的 <dd> 元素，提取异常类型和描述
    private DocStructuredContent.ThrowsDoc parseThrows(Element dd) {
        DocStructuredContent.ThrowsDoc throwsDoc = new DocStructuredContent.ThrowsDoc();
        throwsDoc.setType(firstText(dd, "code"));
        throwsDoc.setDescription(ddDescription(dd));
        return throwsDoc;
    }

    //从 HTML 元素中提取成员名称（方法名/构造函数名/字段名）
    //优先从 h3/h4 标题中获取，其次从签名的 .element-name 中获取，最后从 id 属性中提取
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

    //从方法签名的 HTML 元素中提取参数类型映射（参数名 -> 参数类型）
    //例如签名 "public void save(String name, int age)" 会解析出 {"name": "String", "age": "int"}
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

    //将参数列表字符串按逗号分割，但要正确处理泛型中的逗号
    //例如 "List<String>, Map<K,V>" 应该分成两段而不是三段
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

    //去除参数中的注解（如 @NonNull）和 final 修饰符，只保留类型和参数名
    private String stripParameterModifiers(String parameter) {
        return parameter
                .replaceAll("@\\S+\\s+", "")
                .replaceAll("\\bfinal\\s+", "")
                .trim();
    }

    //从方法签名 HTML 中提取返回值类型（如 "String"、"void"）
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

    //从类描述区域提取类型签名（如 "public class UserService"）
    private String typeSignature(Element descriptionSection) {
        if (descriptionSection == null) {
            return "";
        }
        String signature = firstText(descriptionSection, ".type-signature", "pre");
        return clean(signature);
    }

    //从页面的 .sub-title 中提取包名，与类名拼接成全限定名
    //例如 .sub-title 显示 "Package com.example"，类名是 "UserService"，则返回 "com.example.UserService"
    private String qualifiedName(Document document, String name) {
        String packageName = firstText(document, ".sub-title a", ".sub-title");
        if (packageName.startsWith("Package ")) {
            packageName = packageName.substring("Package ".length()).trim();
        }
        return StringUtils.hasText(packageName) ? packageName + "." + name : name;
    }

    //提取元素中 class="block" 的第一个子元素的文本（javadoc 注释的主要内容）
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

    //提取 <dd> 元素中的描述文本，去掉前缀的 code 标签内容和横线
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

    //工具方法：按多个 CSS 选择器查找，返回第一个匹配的元素
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

    //工具方法：按多个 CSS 选择器查找，返回第一个匹配元素的文本
    private String firstText(Element root, String... selectors) {
        Element element = firstElement(root, selectors);
        return element == null ? "" : clean(element.text());
    }

    //从 HTML 页面中提取类型头信息（类型种类 + 类型名称）
    //尝试三种策略：1.从 class-description 区域提取 2.从 meta 标签提取 3.从 h1 标题提取
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

    //从 <meta name="description" content="declaration:class:UserService"> 标签中提取类型信息
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

    //从类型签名中识别类型种类（class/interface/enum/annotation/record）
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

    //将 meta 标签中的声明类型字符串转为枚举值
    //Java 14+ switch 表达式语法：case 可以用逗号分隔匹配多个值
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

    //从英文版 javadoc 的 h1 标题中提取类型信息（如 "Class UserService" -> CLASS + UserService）
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

    //清理文本：将不间断空格( )替换为普通空格，合并连续空白，去除首尾空格
    private String clean(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }

    //内部 record：封装类型头信息（种类 + 名称）
    private record TypeHeader(String kind, String name) {
    }
}
