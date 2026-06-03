package com.repopilot.business.service.terminal;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

//WebSocket 终端消息中继客户端
//作用：business 模块通过 HTTP 调用 terminal 模块的内部接口，将消息推送到 WebSocket 终端
//这样 business 模块不需要直接管理 WebSocket 连接，只需要把消息发给 terminal 模块即可
@Slf4j
@Component
@RequiredArgsConstructor
public class TerminalRelayClient {

    //JSON 序列化工具
    private final ObjectMapper objectMapper;

    //terminal 模块的内部接口地址（从配置文件读取，默认是本地 8081 端口）
    @Value("${terminal.relay.base-url:http://localhost:8081/internal/terminal/sessions}")
    private String relayBaseUrl;

    //向指定的终端会话发送一行消息
    //sessionId: WebSocket 会话 ID（前端连接时生成）
    //line: 要发送的文本内容
    public void emit(String sessionId, String line) {
        //没有终端会话或没有消息内容时直接跳过
        //这样业务代码可以放心调用 emit，不需要每个调用点都判断 sessionId
        if (!StringUtils.hasText(sessionId) || !StringUtils.hasText(line)) {
            return;
        }

        //配置里的 baseUrl 可能以 / 结尾，先去掉，避免拼接出双斜杠
        String normalizedBaseUrl = relayBaseUrl.endsWith("/")
                ? relayBaseUrl.substring(0, relayBaseUrl.length() - 1)
                : relayBaseUrl;
        String url = normalizedBaseUrl + "/" + sessionId + "/stdout";

        try {
            //terminal 模块内部接口约定的请求体格式是 {"data":"..."}
            //ensureLineEnding 保证前端终端按“行”显示，而不是把多条消息粘在一行
            String payload = objectMapper.writeValueAsString(Map.of("data", ensureLineEnding(line)));
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(2))
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                //终端推送失败不应该影响主业务流程，所以只打 debug 日志
                log.debug("Relay stdout failed, sessionId={}, status={}, body={}",
                        sessionId, response.statusCode(), response.body());
            }
        } catch (Exception e) {
            //同上：终端只是辅助展示，不能因为前端终端断开就让 clone/scan 失败
            log.debug("Relay stdout failed silently, sessionId={}, message={}", sessionId, e.getMessage());
        }
    }

    private String ensureLineEnding(String line) {
        //大多数终端需要换行符才会把本条消息显示成完整一行
        if (line.endsWith("\n") || line.endsWith("\r")) {
            return line;
        }
        return line + "\r\n";
    }
}
