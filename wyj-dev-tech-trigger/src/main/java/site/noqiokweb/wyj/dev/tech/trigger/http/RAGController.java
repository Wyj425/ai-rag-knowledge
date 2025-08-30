package site.noqiokweb.wyj.dev.tech.trigger.http;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.redisson.api.RList;
import org.redisson.api.RedissonClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.OllamaChatClient;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.core.io.PathResource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import site.noqiokweb.wyj.dev.tech.api.IRAGService;
import site.noqiokweb.wyj.dev.tech.api.response.Response;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
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
public class RAGController implements IRAGService {                     // 声明一个控制器类，实现你自定义的 RAG 接口 IRAGService（便于统一规范和对接）

    @Resource                                                          // 从 Spring 容器中注入 OllamaChatClient，后续做对话/补全时会用到（本类这两接口没直接用到）
    private OllamaChatClient ollamaChatClient;

    @Resource                                                          // 注入“按 token 切分文本”的工具：把大文档切成适合向量化/召回的小片段
    private TokenTextSplitter tokenTextSplitter;

    @Resource                                                          // 注入“内存版向量库”（开发调试可用，本类未使用，真正入库用的是 pgVectorStore）
    private SimpleVectorStore simpleVectorStore;

    @Resource                                                          // 注入“PostgreSQL + pgvector”的向量存储实现，生产用它把片段向量+元信息写入数据库
    private PgVectorStore pgVectorStore;

    @Resource                                                          // 注入 Redisson 客户端，用来跟 Redis 交互（维护 ragTag 列表）
    private RedissonClient redissonClient;

    @GetMapping("query_rag_tag_list")                                  // 暴露 GET 接口：/query_rag_tag_list  —— 查询已有的知识库标签列表
    @Override
    public Response<List<String>> queryRagTagList() {                  // 返回一个通用响应体，数据部分是 List<String>（每个元素是一个 ragTag）
        RList<String> elements = redissonClient.getList("ragTag");     // 从 Redis 里拿一个名为 "ragTag" 的 List 结构（RList 是 Redisson 的分布式列表）
        return Response.<List<String>>builder()                        // 用 builder 构造统一响应
                .code("0000")                                          // 业务码：0000 表示成功（按你们项目自定义）
                .info("调用成功")                                         // 附带的提示信息
                .data(elements)                                        // 把 Redis 列表作为数据返回（RList 实现了 List 接口，可直接返回为 JSON 数组）
                .build();                                              // 构建响应对象
    }

    @PostMapping(value = "file/upload",                                 // 暴露 POST 接口：/file/upload —— 上传文件到某个 ragTag 知识库
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)          // 要求请求头是 multipart/form-data（表单+文件上传）
    @Override
    public Response<String> uploadFile(@RequestParam String ragTag,     // 从表单参数里取知识库标签 ragTag（比如 “项目A”）
                                       @RequestParam("file") List<MultipartFile> files) { // 取名为 file 的多个文件（List<MultipartFile>）
        log.info("上传知识库开始 {}", ragTag);                             // 记录日志：开始上传哪个标签的知识库

        for (MultipartFile file : files) {                              // 遍历每一个上传的文件
            TikaDocumentReader documentReader =                         // 使用 Spring AI 包装的 Apache Tika 读取器
                    new TikaDocumentReader(file.getResource());         // 直接用 MultipartFile 的 Resource 读取（Tika 会根据 MIME/内容自动抽取文本）

            List<Document> documents = documentReader.get();            // 解析成 Spring AI 的 Document 列表（每个 Document 含文本 content 和 metadata）
            List<Document> documentSplitterList =                       // 对文档做 token 级切片，得到更细粒度的片段
                    tokenTextSplitter.apply(documents);

            documents.forEach(doc ->                                    // 给“原始文档”打上知识库标记（元数据项：knowledge=ragTag）
                    doc.getMetadata().put("knowledge", ragTag));
            documentSplitterList.forEach(doc ->                         // 给“切片后的每个片段”也打上同样的知识库标记
                    doc.getMetadata().put("knowledge", ragTag));

            pgVectorStore.accept(documentSplitterList);                 // 把这些片段写入 pgvector：会自动做“向量化(embedding)+落库+建索引”

            RList<String> elements = redissonClient.getList("ragTag");  // 取出 Redis 里的 ragTag 列表
            if (!elements.contains(ragTag)){                            // 如果列表里还没有当前这个标签（注意：RList 是允许重复的，这里做一次存在性检查）
                elements.add(ragTag);                                   // 加进去，供前端下拉/查询使用
            }
        }

        log.info("上传知识库完成 {}", ragTag);                             // 日志：这个 ragTag 的所有文件处理完成
        return Response.<String>builder().code("0000")                  // 返回成功响应
                .info("调用成功")
                .build();
    }
    @PostMapping("analyze_git_repoistory")                                // 暴露 POST 接口：/api/v1/.../analyze_git_repoistory
    @Override
    public Response<String> analyzeGitRepoistory(
            @RequestParam String repoUrl,                                  // 前端传入：Git 仓库地址（HTTPS / SSH 转换后的 HTTPS）
            @RequestParam String userName,                                 // 前端传入：用户名（建议用 token 搭配固定用户名或空）
            @RequestParam String token) throws Exception {                 // 前端传入：密码/访问令牌（建议使用 PAT），此处抛出异常交给全局处理

        String localPath = "./git-cloned-repo";                            // 临时克隆目录（相对项目根目录）
        String repoProjectName = extractProjectName(repoUrl);              // 从 repoUrl 提取“项目名”（作为知识库标签使用）
        log.info("克隆路径：{}", new File(localPath).getAbsolutePath());     // 打印实际克隆的绝对路径，便于排错

        FileUtils.deleteDirectory(new File(localPath));                    // 如果目录已存在则先清空，避免旧数据干扰

        Git git = Git.cloneRepository()                                    // JGit 克隆操作
                .setURI(repoUrl)                                           // 远端仓库 URL
                .setDirectory(new File(localPath))                         // 本地克隆目录
                .setCredentialsProvider(                                   // 设置凭据（公开仓库可省略）
                        new UsernamePasswordCredentialsProvider(userName, token))
                .call();                                                   // 执行克隆

        // 递归遍历克隆目录中的所有文件
        Files.walkFileTree(Paths.get(localPath), new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                log.info("{} 遍历解析路径，上传知识库:{}", repoProjectName, file.getFileName());  // 打印当前处理的文件名（不含路径）

                try {
                    // 用 Tika 读取文件内容（自动按类型抽取文本），封装成 Spring AI 的 Document 列表
                    TikaDocumentReader reader = new TikaDocumentReader(new PathResource(file));
                    List<Document> documents = reader.get();

                    // 用 TokenTextSplitter 按 token 把文档切成更小的片段，便于向量化与召回
                    List<Document> documentSplitterList = tokenTextSplitter.apply(documents);

                    // 给原文与切片都打上知识库标签：knowledge = <项目名>
                    documents.forEach(doc -> doc.getMetadata().put("knowledge", repoProjectName));
                    documentSplitterList.forEach(doc -> doc.getMetadata().put("knowledge", repoProjectName));

                    // 向 pgvector 入库：会自动进行 embedding（向量化）并把（文本+向量+元数据）保存到数据库
                    pgVectorStore.accept(documentSplitterList);

                } catch (Exception e) {
                    // 有文件解析/入库失败，记录错误后继续，不影响整个任务
                    log.error("遍历解析路径，上传知识库失败:{}", file.getFileName());
                }

                return FileVisitResult.CONTINUE;                           // 继续遍历后续文件
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                // 如果文件不可访问，也记录一下并继续遍历
                log.info("Failed to access file: {} - {}", file.toString(), exc.getMessage());
                return FileVisitResult.CONTINUE;
            }
        });



        RList<String> elements = redissonClient.getList("ragTag");         // 从 Redis 获取 ragTag 列表（Redisson 分布式 List）
        if (!elements.contains(repoProjectName)) {                         // 若列表里还没有当前项目名
            elements.add(repoProjectName);                                 // 追加（供前端做下拉/筛选）
        }

        git.close();                                                       // 关闭 JGit 资源（释放句柄）
        FileUtils.deleteDirectory(new File(localPath));                    // 入库完成后清理整个临时克隆目录，节省磁盘
        log.info("遍历解析路径，上传完成:{}", repoUrl);                       // 记录完成日志

        return Response.<String>builder().code("0000").info("调用成功").build(); // 返回统一成功响应
    }

    // 从仓库 URL 提取项目名（最后一个路径段），并去掉 .git 后缀
    private String extractProjectName(String repoUrl) {
        String[] parts = repoUrl.split("/");                               // 以斜杠切分 URL
        String projectNameWithGit = parts[parts.length - 1];               // 取最后一段（如 xxx.git 或 xxx）
        return projectNameWithGit.replace(".git", "");                     // 去掉 .git 后缀
    }

}

