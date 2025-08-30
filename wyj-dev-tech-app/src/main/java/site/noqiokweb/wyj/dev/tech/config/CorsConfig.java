package site.noqiokweb.wyj.dev.tech.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * @author TheLastSavior noqiokweb.site @wyj
 * @description
 * @create 8/30/2025 7:45 下午
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {
    @Override public void addCorsMappings(CorsRegistry r) {
        r.addMapping("/**").allowedOriginPatterns("*")
                .allowedMethods("*").allowedHeaders("*");
    }
}
