package site.noqiokweb.wyj.dev.tech.trigger.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@CrossOrigin("*")
@RequestMapping("/api/v1/openai1/")
public class OpenAi1ProxyController {

//    private static final MediaType SSE = MediaType.TEXT_EVENT_STREAM;
//
//    private final WebClient openAi1WebClient;
//    private final ObjectMapper mapper = new ObjectMapper();
//
//    @Resource
//    private PgVectorStore pgVectorStore; // RAG 用；如果不用 RAG，可去掉
//
//    public OpenAi1ProxyController(@Qualifier("openAi1WebClient") WebClient openAi1WebClient) {
//        this.openAi1WebClient = openAi1WebClient;
//    }
//
//    /**
//     * gpt-4o-mini 流式：/api/v1/openai1/generate_stream?model=gpt-4o-mini&message=hi
//     * 前端仍用 EventSource 即可。
//     */
//    @GetMapping(value = "generate_stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
//    public Flux<String> generateStream(@RequestParam String model, @RequestParam String message) {
//        List<Map<String, Object>> messages = List.of(
//                Map.of("role", "user", "content", message)
//        );
//        return proxySseToFrontend(model, messages);
//    }
//
//    /**
//     * gpt-4o-mini + RAG 流式：
//     * /api/v1/openai1/generate_stream_rag?model=gpt-4o-mini&ragTag=xxx&message=hi
//     */
//    @GetMapping(value = "generate_stream_rag", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
//    public Flux<String> generateStreamRag(@RequestParam String model,
//                                          @RequestParam String ragTag,
//                                          @RequestParam String message) {
//        String SYSTEM_PROMPT = """
//                Use the information from the DOCUMENTS section to provide accurate answers but act as if you knew this information innately.
//                If unsure, simply state that you don't know.
//                Another thing you need to note is that your reply must be in Chinese!
//                DOCUMENTS:
//                    {documents}
//                """;
//
//        // 和你 OpenAiController 里保持一致
//        SearchRequest request = SearchRequest.query(message)
//                .withTopK(5)
//                .withFilterExpression("knowledge == '" + ragTag + "'");
//        List<Document> documents = pgVectorStore.similaritySearch(request);
//        String docs = documents.stream().map(Document::getContent).collect(Collectors.joining());
//
//        //（保持你原来的顺序：先 user 再 system —— 如果想更标准，也可把 system 放前面）
//        List<Map<String, Object>> messages = List.of(
//                Map.of("role", "user", "content", message),
//                Map.of("role", "system", "content", SYSTEM_PROMPT.replace("{documents}", docs))
//        );
//        return proxySseToFrontend(model, messages);
//    }
//
//    /** 把 openai1 的 SSE 代理给前端，并把 delta.content 抽出来拼成你前端能吃的最小 JSON */
//    private Flux<String> proxySseToFrontend(String model, List<Map<String, Object>> messages) {
//        Map<String, Object> body = new HashMap<>();
//        body.put("model", model);
//        body.put("stream", true);
//        body.put("messages", messages);
//
//        return openAi1WebClient.post()
//                .uri("/v1/chat/completions")  // 这里补 /v1
//                .contentType(MediaType.APPLICATION_JSON)
//                .accept(SSE)
//                .bodyValue(body)
//                .retrieve()
//                .bodyToFlux(String.class) // 逐块读取 SSE 文本
//                .flatMap(this::splitDataLines)        // 拆成多行，提取以 "data:" 开头的行
//                .takeUntil(s -> "[DONE]".equals(s))   // 收到 [DONE] 则收尾
//                .filter(s -> !"[DONE]".equals(s) && !s.isBlank())
//                .map(this::mapOpenAiDeltaToFrontJson) // 把 OpenAI 的 chunk → 你前端习惯的 JSON {result:{output:{content:"..."}}}
//                .concatWithValues("{\"result\":{\"output\":{\"content\":\"\"}},\"metadata\":{\"finishReason\":\"STOP\"}}")
//                .onErrorResume(ex -> Flux.just(
//                        "{\"result\":{\"output\":{\"content\":\"\"}},\"metadata\":{\"finishReason\":\"STOP\"},\"error\":\""
//                                + escape(ex.getMessage()) + "\"}"
//                ));
//    }
//
//    /** 拆分成纯 payload：去掉 data: 前缀，只保留 JSON 或 [DONE] */
//    private Flux<String> splitDataLines(String sseChunk) {
//        return Flux.fromStream(sseChunk.lines())
//                .filter(l -> l.startsWith("data:"))
//                .map(l -> l.substring(5).trim()); // 去掉 "data:"
//    }
//
//    /** 把 OpenAI 的 JSON chunk 映射成前端已兼容的最小结构 */
//    private String mapOpenAiDeltaToFrontJson(String jsonPayload) {
//        try {
//            JsonNode root = mapper.readTree(jsonPayload);
//            JsonNode choices = root.path("choices");
//            if (choices.isArray() && choices.size() > 0) {
//                JsonNode choice0 = choices.get(0);
//                // 1) delta.content（token）
//                String token = "";
//                JsonNode deltaContent = choice0.path("delta").path("content");
//                if (deltaContent.isTextual()) {
//                    token = deltaContent.asText();
//                }
//                // 2) finish_reason（有些实现会放在这里）
//                String finish = null;
//                JsonNode fr = choice0.path("finish_reason");
//                if (fr.isTextual()) finish = fr.asText();
//
//                if (token != null && !token.isEmpty()) {
//                    // 匹配你前端解析逻辑：优先找 JSON 的 result.output.content，找不到才走“原样追加”
//                    return "{\"result\":{\"output\":{\"content\":\"" + escape(token) + "\"}}}";
//                }
//                if ("stop".equalsIgnoreCase(finish)) {
//                    return "{\"result\":{\"output\":{\"content\":\"\"}},\"metadata\":{\"finishReason\":\"STOP\"}}";
//                }
//            }
//        } catch (Exception ignore) {
//            // 回退：把原始 payload 传下去，前端会走“原样追加”
//        }
//        return jsonPayload;
//    }
//
//    private String escape(String s) {
//        if (s == null) return "";
//        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
//    }
}
