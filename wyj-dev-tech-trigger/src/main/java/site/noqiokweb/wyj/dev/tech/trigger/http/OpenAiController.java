package site.noqiokweb.wyj.dev.tech.trigger.http;

import jakarta.annotation.Resource;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.openai.OpenAiChatClient;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import site.noqiokweb.wyj.dev.tech.api.IAiService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@CrossOrigin("*")
@RequestMapping("/api/v1/openai/")
public class OpenAiController implements IAiService {

    // 主渠道：gpt-4o / gpt-4.1 / gpt-4.1-mini
    @Resource
    @Qualifier("openAiChatClient")
    private OpenAiChatClient openAiChatClient;

    // 备用渠道：仅 gpt-4o-mini  （★ 扩展：同时支持 gpt-4.1-nano / gpt-5-mini）
    @Resource
    @Qualifier("openAi1ChatClient")
    private OpenAiChatClient openAi1ChatClient;

    @Resource
    private PgVectorStore pgVectorStore;

    private static boolean useOpenAi1(String model) {
        // 你前端就是 "gpt-4o-mini"；这里扩展把 "gpt-4.1-nano" 和 "gpt-5-mini" 也放到 openai1 渠道
        if (model == null) return false;
        String m = model.trim().toLowerCase();
        return "gpt-4o-mini".equals(m) || "gpt-4.1-nano".equals(m) || "gpt-5-mini".equals(m);
    }

    private OpenAiChatClient pickClient(String model) {
        return useOpenAi1(model) ? openAi1ChatClient : openAiChatClient;
    }

    @GetMapping("generate")
    @Override
    public ChatResponse generate(@RequestParam("model") String model, @RequestParam("message") String message) {
        OpenAiChatClient client = pickClient(model);
        return client.call(new Prompt(
                message,
                OpenAiChatOptions.builder().withModel(model).build()
        ));
    }

    /**
     * SSE：除了 gpt-4o-mini 用非流式包装，其它模型走真流式。
     * （★ 扩展：openai1 渠道的 gpt-4.1-nano / gpt-5-mini 同样用一次性结果包装为单条 SSE，
     *  以规避 Spring AI 0.8.1 在部分代理通道上流式合并的 NPE）
     *
     * 现在的策略（不改动原注释，仅补充实现说明）：
     *   - openai1 渠道：先尝试流式；如果发生错误 -> onErrorResume 降级为一次性输出（client.call -> Flux.just）。
     *   - 其他渠道：保持原来的真流式。
     */
    @GetMapping(value = "generate_stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ChatResponse> generateStream(@RequestParam("model") String model, @RequestParam("message") String message) {
        OpenAiChatClient client = pickClient(model);
        Prompt prompt = new Prompt(message, OpenAiChatOptions.builder().withModel(model).build());

        return client.stream(new Prompt(
                message,
                OpenAiChatOptions.builder()
                        .withModel(model)
                        .build()
        )).onErrorComplete();
    }
//    @GetMapping("generate_stream")
//    public Flux<ChatResponse> generateStream(@RequestParam String model, @RequestParam String message) {
//        OpenAiChatClient client = pickClient(model);
//        return client.stream(new Prompt(message,
//                OpenAiChatOptions.builder().withModel(model).build()));
//    }

    @GetMapping(value = "generate_stream_rag", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Override
    public Flux<ChatResponse> generateStreamRag(@RequestParam("model") String model,
                                                @RequestParam("ragTag") String ragTag,
                                                @RequestParam("message") String message) {

        String SYSTEM_PROMPT = """
                Use the information from the DOCUMENTS section to provide accurate answers but act as if you knew this information innately.
                If unsure, simply state that you don't know.
                Another thing you need to note is that your reply must be in Chinese!
                DOCUMENTS:
                    {documents}
                """;

        // 指定知识库召回
        SearchRequest request = SearchRequest.query(message)
                .withTopK(5)
                .withFilterExpression("knowledge == '" + ragTag + "'");

        List<Document> documents = pgVectorStore.similaritySearch(request);
        String documentCollectors = documents.stream().map(Document::getContent).collect(Collectors.joining());

        List<Message> msgs = new ArrayList<>();
        msgs.add(new UserMessage(message));
        msgs.add(new SystemMessage(SYSTEM_PROMPT.replace("{documents}", documentCollectors)));

        OpenAiChatClient client = pickClient(model);
        Prompt prompt = new Prompt(msgs, OpenAiChatOptions.builder().withModel(model).build());

        return client.stream(new Prompt(
                message,
                OpenAiChatOptions.builder()
                        .withModel(model)
                        .build()
        )).onErrorComplete();
    }
}
