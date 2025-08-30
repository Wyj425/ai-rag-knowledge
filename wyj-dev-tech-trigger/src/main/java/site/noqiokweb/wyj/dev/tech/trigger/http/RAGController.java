package site.noqiokweb.wyj.dev.tech.trigger.http;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RList;
import org.redisson.api.RedissonClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.OllamaChatClient;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import site.noqiokweb.wyj.dev.tech.api.IRAGService;
import site.noqiokweb.wyj.dev.tech.api.response.Response;

import java.util.List;

/**
 * @author TheLastSavior noqiokweb.site @wyj
 * @description
 * @create 8/30/2025 5:22 下午
 */
@Slf4j
@RestController()
@CrossOrigin("*")
@RequestMapping("/api/v1/rag/")
public class RAGController implements IRAGService {
    @Resource   // 注入 Spring 容器中的 OllamaChatClient，用于调用大模型（Ollama 接口）
    private OllamaChatClient ollamaChatClient;

    @Resource   // 注入分词器，用于把大文档切分成小片段（按 token 数量）
    private TokenTextSplitter tokenTextSplitter;

    @Resource   // 注入内存向量存储（简单的本地向量数据库，开发调试用）
    private SimpleVectorStore simpleVectorStore;

    @Resource   // 注入 PostgreSQL 的向量存储（基于 pgvector 插件的数据库存储，生产常用）
    private PgVectorStore pgVectorStore;

    @Resource
    private RedissonClient redissonClient;
    @GetMapping("query_rag_tag_list")
    @Override
    public Response<List<String>> queryRagTagList() {
        RList<String> elements = redissonClient.getList("ragTag");
        return Response.<List<String>>builder()
                .code("0000")
                .info("调用成功")
                .data(elements)
                .build();
    }
    @PostMapping(value = "file/upload",headers = "Content-Type=multipart/form-data")
    @Override
    public Response<String> uploadFile(@RequestParam  String ragTag, @RequestParam("file") List<MultipartFile> files) {
        log.info("上传知识库开始 {}", ragTag);
        for (MultipartFile file : files) {
            TikaDocumentReader documentReader = new TikaDocumentReader(file.getResource());
            List<Document> documents = documentReader.get();
            List<Document> documentSplitterList = tokenTextSplitter.apply(documents);

            documents.forEach(doc -> doc.getMetadata().put("knowledge", ragTag));
            documentSplitterList.forEach(doc -> doc.getMetadata().put("knowledge", ragTag));

            pgVectorStore.accept(documentSplitterList);

            RList<String> elements = redissonClient.getList("ragTag");
            if (!elements.contains(ragTag)){
                elements.add(ragTag);
            }
        }

        log.info("上传知识库完成 {}", ragTag);
        return Response.<String>builder().code("0000").info("调用成功").build();
    }
}
