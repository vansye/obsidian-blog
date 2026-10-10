package luv.vansye.site;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;

import java.util.List;

@RestController
public class SiteController {
    private final SiteProperties site;

    public SiteController(SiteProperties site) {
        this.site = site;
    }

    public record PublicSite(String name, String description, String author, String url,
                             String github, String email,
                             List<SiteProperties.NavigationItem> navigation) {}

    @GetMapping("/api/v1/site")
    public ResponseEntity<PublicSite> site() {
        return ResponseEntity.ok().cacheControl(CacheControl.noCache()).body(
                new PublicSite(site.getName(), site.getDescription(), site.getAuthor(),
                        site.getUrl(), site.getGithub(), site.getEmail(), site.getNavigation()));
    }
}
