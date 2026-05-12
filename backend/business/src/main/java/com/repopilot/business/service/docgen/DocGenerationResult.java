package com.repopilot.business.service.docgen;

import lombok.Builder;
import lombok.Value;

//@Value是一个Lombok 注解，它表示这个类是一个不可变对象，
//所有字段默认变成 private final，自动生成getter，构造方法toString()，equals()，hashCode()
//与@Data不同的是，@Data是普通可变对象，可以 set
@Value
@Builder
public class DocGenerationResult {

    String docFilePath;
}
