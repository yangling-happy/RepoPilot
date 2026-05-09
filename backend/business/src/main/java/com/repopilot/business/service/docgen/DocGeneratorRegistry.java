package com.repopilot.business.service.docgen;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

//根据文件后缀名，找到对应的文档生成器，这个类交给 Spring 管理。
//Spring Boot 启动时会扫描到它，并创建一个 DocGeneratorRegistry 对象，放进 Spring 容器。
//这样其他 Service 就可以注入它
@Component
//根据文件后缀名，找到对应的文档生成器
public class DocGeneratorRegistry {
    //一个Map，存储文件后缀 -> 对应的文档生成器
    private final Map<String, DocGenerator> generatorsByExtension;

    //Spring 把所有实现了 DocGenerator 接口的 Bean 注入进来，然后注册到 Map 里
    public DocGeneratorRegistry(List<DocGenerator> generators) {
        this.generatorsByExtension = new LinkedHashMap<>();
        for (DocGenerator generator : generators) {
            for (String extension : generator.supportedExtensions()) {
                //normalizeExtension把后缀统一处理成标准格式
                generatorsByExtension.put(normalizeExtension(extension), generator);
            }
        }
    }

    //返回Optional是因为不一定能找到对应生成器，使用后，调用方必须显式处理“找不到”的情况
    public Optional<DocGenerator> findGenerator(String filePath) {
        //根据文件后缀去注册表里找到对应生成器
        String extension = extractExtension(filePath);
        // 如果没有对应生成器，返回空
        if (!StringUtils.hasText(extension)) {
            return Optional.empty();
        }
        return Optional.ofNullable(generatorsByExtension.get(extension));
    }

    //判断某个类型的文件是否有对应的文档生成器
    public boolean supports(String filePath) {
        return findGenerator(filePath).isPresent();
    }

    //从文件路径中提取后缀名
    private String extractExtension(String filePath) {
        //如果为空，返回null
        if (!StringUtils.hasText(filePath)) {
            return null;
        }
        //找到最后一个.的位置
        int dotIndex = filePath.lastIndexOf('.');
        //没有点号/点号在最后一个位置 -> 返回null
        if (dotIndex < 0 || dotIndex == filePath.length() - 1) {
            return null;
        }
        //把后缀统一变成小写、带点号的格式
        return normalizeExtension(filePath.substring(dotIndex));
    }

    private String normalizeExtension(String extension) {
        //先去掉前后空格，再转小写
        //locale.ROOT是一个比较规范的写法。它可以避免某些语言环境下大小写转换异常
        String normalized = extension.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith(".") ? normalized : "." + normalized;
    }
}
