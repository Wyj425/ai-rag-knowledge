package site.noqiokweb.wyj.dev.tech.api;

import org.springframework.web.multipart.MultipartFile;
import site.noqiokweb.wyj.dev.tech.api.response.Response;

import java.io.IOException;
import java.util.List;

public interface IRAGService {
    Response<List<String>> queryRagTagList();
    Response<String> uploadFile(String ragTag, List<MultipartFile> files);
    Response<String> analyzeGitRepoistory(String repoUrl,String userName,String token) throws Exception;
}
