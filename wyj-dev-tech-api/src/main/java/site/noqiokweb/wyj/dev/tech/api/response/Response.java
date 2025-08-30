package site.noqiokweb.wyj.dev.tech.api.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * @author TheLastSavior noqiokweb.site @wyj
 * @description
 * @create 8/30/2025 5:16 下午
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Response< T> implements Serializable {
    private String code;
    private String info;
    private T data;
}
