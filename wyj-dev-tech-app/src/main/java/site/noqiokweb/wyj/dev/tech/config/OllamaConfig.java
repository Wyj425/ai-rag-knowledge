package site.noqiokweb.wyj.dev.tech.config;

import org.springframework.ai.ollama.OllamaChatClient;
import org.springframework.ai.ollama.OllamaEmbeddingClient;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.openai.OpenAiChatClient;
import org.springframework.ai.openai.OpenAiEmbeddingClient;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class OllamaConfig {

    // ---- Ollama ----
    @Bean
    public OllamaApi ollamaApi(@Value("${spring.ai.ollama.base-url}") String baseUrl) {
        return new OllamaApi(baseUrl);
    }

    @Bean
    public OllamaChatClient ollamaChatClient(OllamaApi ollamaApi) {
        return new OllamaChatClient(ollamaApi);
    }

    // ---- OpenAI 主渠道（spring.ai.openai.*）----
    @Bean
    @Qualifier("openAiApi")
    public OpenAiApi openAiApi(
            @Value("${spring.ai.openai.base-url}") String baseUrl,
            @Value("${spring.ai.openai.api-key}") String apiKey) {
        return new OpenAiApi(baseUrl, apiKey);
    }

    @Bean
    @Qualifier("openAiChatClient")
    public OpenAiChatClient openAiChatClient(@Qualifier("openAiApi") OpenAiApi openAiApi) {
        return new OpenAiChatClient(openAiApi);
    }

    // ---- OpenAI 备用渠道（spring.ai.openai1.*）给 gpt-4o-mini 用 ----
    @Bean
    @Qualifier("openAi1Api")
    public OpenAiApi openAi1Api(
            @Value("${spring.ai.openai1.base-url}") String baseUrl,
            @Value("${spring.ai.openai1.api-key}") String apiKey) {
        return new OpenAiApi(baseUrl, apiKey);
    }

    @Bean
    @Qualifier("openAi1ChatClient")
    public OpenAiChatClient openAi1ChatClient(@Qualifier("openAi1Api") OpenAiApi openAi1Api) {
        return new OpenAiChatClient(openAi1Api);
    }

    // ---- 其它通用 Bean ----
    @Bean
    public TokenTextSplitter tokenTextSplitter() {
        return new TokenTextSplitter();
    }

    @Bean
    public SimpleVectorStore vectorStore(
            @Value("${spring.ai.rag.embed}") String model,
            OllamaApi ollamaApi,
            @Qualifier("openAiApi") OpenAiApi openAiApi) {
        if ("nomic-embed-text".equalsIgnoreCase(model)) {
            OllamaEmbeddingClient embeddingClient = new OllamaEmbeddingClient(ollamaApi);
            embeddingClient.withDefaultOptions(OllamaOptions.create().withModel("nomic-embed-text"));
            return new SimpleVectorStore(embeddingClient);
        } else {
            OpenAiEmbeddingClient embeddingClient = new OpenAiEmbeddingClient(openAiApi);
            return new SimpleVectorStore(embeddingClient);
        }
    }

    @Bean
    public PgVectorStore pgVectorStore(
            @Value("${spring.ai.rag.embed}") String model,
            OllamaApi ollamaApi,
            @Qualifier("openAiApi") OpenAiApi openAiApi,
            JdbcTemplate jdbcTemplate) {
        if ("nomic-embed-text".equalsIgnoreCase(model)) {
            OllamaEmbeddingClient embeddingClient = new OllamaEmbeddingClient(ollamaApi);
            embeddingClient.withDefaultOptions(OllamaOptions.create().withModel("nomic-embed-text"));
            return new PgVectorStore(jdbcTemplate, embeddingClient);
        } else {
            OpenAiEmbeddingClient embeddingClient = new OpenAiEmbeddingClient(openAiApi);
            return new PgVectorStore(jdbcTemplate, embeddingClient);
        }
    }
//    @Bean
//    @Qualifier("openAi1WebClient")
//    public WebClient openAi1WebClient(
//            @Value("${spring.ai.openai1.base-url}") String baseUrl,
//            @Value("${spring.ai.openai1.api-key}") String apiKey
//    ) {
//        return WebClient.builder()
//                .baseUrl(baseUrl) // 注意：不要写 /v1
//                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
//                .build();
//    }
}