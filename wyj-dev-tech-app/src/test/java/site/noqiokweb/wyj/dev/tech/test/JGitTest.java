package site.noqiokweb.wyj.dev.tech.test;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.OllamaChatClient;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.PathResource;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class JGitTest {

    @Resource                               // Spring 注入：聊天客户端（本类没直接用到，但通常用于后续对话）
    private OllamaChatClient ollamaChatClient;
    @Resource                               // 文本切分器：把长文本按 token 切成小段
    private TokenTextSplitter tokenTextSplitter;
    @Resource                               // 简单内存向量库（这里未使用）
    private SimpleVectorStore simpleVectorStore;
    @Resource                               // PostgreSQL + pgvector 向量存储（RAG 入库用）
    private PgVectorStore pgVectorStore;

    @Test
    public void test() throws Exception {
        // ↓↓↓ 以下三项用你的仓库信息替换（公开仓库可不填用户名密码）
        String repoURL = "https://gitcode.com/helicopter69/group-buy-market"; // 远端仓库地址（HTTPS）
        String username = "helicopter69";                                               // 账号（建议用 token，而不是明文密码）
        String password = "cN9ErMcQMippkPtCF9RHpd-j";                                    // 密码/令牌（不要提交到版本库）

        String localPath = "./cloned-repo";                                              // 本地克隆目录
        log.info("克隆路径：" + new File(localPath).getAbsolutePath());                   // 日志：打印绝对路径

        FileUtils.deleteDirectory(new File(localPath));                                  // 若已存在，先全部删除（清理旧内容）

        // 使用 JGit 克隆仓库到本地目录
        Git git = Git.cloneRepository()
                .setURI(repoURL)                                                         // 远端仓库 URL
                .setDirectory(new File(localPath))                                       // 本地目标目录
                .setCredentialsProvider(                                                 // 设置凭据（公开仓库可去掉这行）
                        new UsernamePasswordCredentialsProvider(username, password))
                .call();                                                                 // 执行克隆

        git.close();                                                                     // 关闭资源（释放句柄）
    }

    @Test
    public void test_file() throws IOException {
        // 从 ./cloned-repo 开始，递归遍历目录树（Files.walkFileTree 比 walk 更可控）
        Files.walkFileTree(Paths.get("./cloned-repo"), new SimpleFileVisitor<>() {

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                log.info("文件路径:{}", file.toString());                                 // 日志：当前处理的文件路径

//                PathResource resource = new PathResource(file);                          // 把 Path 适配为 Spring 的 Resource
//                TikaDocumentReader reader = new TikaDocumentReader(resource);            // 交给 Tika 自动识别类型并抽取文本
//
//                List<Document> documents = reader.get();                                 // 把文件解析成 1~N 个 Document（含内容与元数据）
//                List<Document> documentSplitterList =                                    // 按 token 切分成更小的片段
//                        tokenTextSplitter.apply(documents);
//
//                // 给原文与切片都打上知识库标签（用于后续检索过滤）
//                documents.forEach(doc -> doc.getMetadata().put("knowledge", "group-buy-market"));
//                documentSplitterList.forEach(doc -> doc.getMetadata().put("knowledge", "group-buy-market"));
//
//                // 写入向量库：会调用 embedding 模型生成向量，并把（文本+向量+元数据）持久化到 pgvector
//                pgVectorStore.accept(documentSplitterList);

                return FileVisitResult.CONTINUE;                                         // 继续遍历
            }
        });
    }
}

