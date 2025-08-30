package site.noqiokweb.wyj.dev.tech.test;

import com.alibaba.fastjson.JSON;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.OllamaChatClient;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author TheLastSavior noqiokweb.site @wyj
 * @description
 * @create 8/30/2025 12:04 上午
 */
@Slf4j   // Lombok 注解，自动为类生成一个 log 对象，用于日志输出
@RunWith(SpringRunner.class)   // 告诉 JUnit 使用 SpringRunner 来运行测试，能加载 Spring 上下文
@SpringBootTest   // 标记这是一个 Spring Boot 集成测试，会启动完整的 Spring 容器
public class RAGTest {

    @Resource   // 注入 Spring 容器中的 OllamaChatClient，用于调用大模型（Ollama 接口）
    private OllamaChatClient ollamaChatClient;

    @Resource   // 注入分词器，用于把大文档切分成小片段（按 token 数量）
    private TokenTextSplitter tokenTextSplitter;

    @Resource   // 注入内存向量存储（简单的本地向量数据库，开发调试用）
    private SimpleVectorStore simpleVectorStore;

    @Resource   // 注入 PostgreSQL 的向量存储（基于 pgvector 插件的数据库存储，生产常用）
    private PgVectorStore pgVectorStore;

    // ========== 第一个测试方法：上传文档到知识库 ==========
    @Test
    public void upload() {
        // 使用 Apache Tika 封装的 DocumentReader 读取文件内容，自动解析文本格式
        TikaDocumentReader reader = new TikaDocumentReader("./data/file.txt");

        // 读取得到的文档列表（每个 Document 封装了文本和元数据）
        List<Document> documents = reader.get();

        // 使用分词器将文档切分成更小的片段，保证每个片段 token 数量适合模型输入
        List<Document> documentSplitterList = tokenTextSplitter.apply(documents);

        // 给原始文档添加 metadata 标记（知识库名称）
        documents.forEach(doc -> doc.getMetadata().put("knowledge", "知识库名称"));

        // 给切分后的文档片段也打上相同 metadata 标记
        documentSplitterList.forEach(doc -> doc.getMetadata().put("knowledge", "知识库名称"));

        // 将切分后的文档片段写入 PostgreSQL 向量数据库，完成“知识入库”
        pgVectorStore.accept(documentSplitterList);

        // 打印日志，说明上传完成
        log.info("上传完成");
    }

    // ========== 第二个测试方法：基于知识库进行对话 ==========
    @Test
    public void chat() {
        // 用户提问内容
        String message = "谢建韬，哪年出生";

        // 系统提示词模板，告诉大模型如何使用检索到的文档
        // {documents} 会在后面被替换为实际检索出来的文档内容
        String SYSTEM_PROMPT = """
                Use the information from the DOCUMENTS section to provide accurate answers but act as if you knew this information innately.
                If unsure, simply state that you don't know.
                Another thing you need to note is that your reply must be in Chinese!
                DOCUMENTS:
                    {documents}
                """;

        // 构造向量检索请求：以用户问题 message 为查询，取 Top 5 相似文档，并限定知识库
        SearchRequest request = SearchRequest.query(message)
                .withTopK(5)   // 只取最相关的 5 条
                .withFilterExpression("knowledge == '知识库名称'"); // 过滤条件，只在指定知识库中检索

        // 执行相似度检索，从向量数据库里找相关的文档片段
        List<Document> documents = pgVectorStore.similaritySearch(request);

        // 把检索到的文档内容拼接成一个字符串，作为上下文提供给模型
        String documentsCollectors = documents.stream()
                .map(Document::getContent)
                .collect(Collectors.joining());

        // 用系统提示词模板替换 {documents}，得到最终的系统 Message
        Message ragMessage = new SystemPromptTemplate(SYSTEM_PROMPT)
                .createMessage(Map.of("documents", documentsCollectors));

        // 构造消息列表，模拟对话历史
        ArrayList<Message> messages = new ArrayList<>();
        messages.add(new UserMessage(message)); // 用户输入
        messages.add(ragMessage);               // 系统提示 + 文档内容

        // 调用 Ollama 大模型：传入消息列表和模型参数，得到模型回答
        ChatResponse chatResponse = ollamaChatClient.call(
                new Prompt(messages, OllamaOptions.create().withModel("deepseek-r1:1.5b"))
        );

        // 打印模型返回结果，转为 JSON 格式便于阅读
        log.info("测试结果:{}", JSON.toJSONString(chatResponse));
    }

}
