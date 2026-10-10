package luv.vansye.site;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConfigurationProperties(prefix = "blog.site")
public class SiteProperties {
    private String name = "My Blog";
    private String description = "记录想法、日常与正在做的事。";
    private String author = "作者";
    private String url = "http://localhost:8080";
    private String github = "";
    private String email = "";

    public record NavigationItem(String label, String path) {}

    public List<NavigationItem> getNavigation() {
        return List.of(new NavigationItem("首页", "/"),
                new NavigationItem("归档", "/archive"),
                new NavigationItem("随笔", "/notes"),
                new NavigationItem("项目", "/lab"),
                new NavigationItem("关于", "/about"));
    }

    public String canonicalUrl(String path) {
        return url.replaceAll("/+$", "") + path;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getGithub() { return github; }
    public void setGithub(String github) { this.github = github; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}
