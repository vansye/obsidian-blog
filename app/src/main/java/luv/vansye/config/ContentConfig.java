package luv.vansye.config;

import luv.vansye.sync.domain.PublicationLayout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ContentConfig {
    @Bean
    public PublicationLayout publicationLayout(@Value("${blog.vault.publish-root:博客}") String root) {
        return new PublicationLayout(root);
    }
}
