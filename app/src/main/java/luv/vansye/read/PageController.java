package luv.vansye.read;

import jakarta.servlet.http.HttpServletResponse;
import luv.vansye.site.SiteProperties;
import luv.vansye.sync.domain.Section;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class PageController {
    private static final int PAGE_SIZE = 20;
    private final ReadService read;
    private final SiteProperties site;

    public PageController(ReadService read, SiteProperties site) {
        this.read = read;
        this.site = site;
    }

    @GetMapping("/")
    public String home(Model model) {
        ReadService.Page recent = read.bySection(Section.ARCHIVE, 0, 6);
        model.addAttribute("items", recent.items());
        model.addAttribute("total", recent.total());
        metadata(model, site.getName(), site.getDescription(), "/", "/");
        return "home";
    }

    @GetMapping("/archive")
    public String archive(@RequestParam(defaultValue = "0") int page, Model model) {
        return listing(Section.ARCHIVE, page, "归档", "按时间收录的文章。", "/archive", model);
    }

    @GetMapping("/notes")
    public String notes(@RequestParam(defaultValue = "0") int page, Model model) {
        return listing(Section.NOTES, page, "随笔", "日常片段与还在生长的想法。", "/notes", model);
    }

    @GetMapping("/lab")
    public String lab(@RequestParam(defaultValue = "0") int page, Model model) {
        return listing(Section.LAB, page, "项目", "动手实践，留下一些作品。", "/lab", model);
    }

    private String listing(Section section, int requestedPage, String title, String description,
                           String path, Model model) {
        ReadService.Page page = read.bySection(section, Math.max(requestedPage, 0), PAGE_SIZE);
        long totalPages = Math.max(1L, ((long) page.total() + page.size() - 1) / page.size());
        model.addAttribute("items", page.items());
        model.addAttribute("total", page.total());
        model.addAttribute("page", page);
        model.addAttribute("pageNumber", (long) page.page() + 1);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("hasPrevious", page.page() > 0);
        model.addAttribute("hasNext", (long) page.page() + 1 < totalPages);
        model.addAttribute("previousPage", Math.max(0, page.page() - 1));
        model.addAttribute("nextPage", (long) page.page() + 1);
        model.addAttribute("listingPath", path);
        model.addAttribute("heading", title);
        String canonicalPath = page.page() == 0 ? path : path + "?page=" + page.page();
        metadata(model, title, description, canonicalPath, path);
        return "listing";
    }

    @GetMapping("/p/{path}")
    public String post(@PathVariable String path, Model model, HttpServletResponse response) {
        PostDetail post = read.findByPath(path).orElse(null);
        if (post == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            metadata(model, "页面未找到", "这个页面不存在，或已不再公开。", "/p/" + path, "");
            return "error/404";
        }
        model.addAttribute("post", post);
        model.addAttribute("neighbors", read.neighborsOf(post.plate()));
        String sectionPath = switch (post.section()) {
            case "NOTES" -> "/notes";
            case "LAB" -> "/lab";
            case "ABOUT", "LINKS" -> "/about";
            default -> "/archive";
        };
        String sectionLabel = switch (sectionPath) {
            case "/notes" -> "随笔";
            case "/lab" -> "项目";
            case "/about" -> "关于";
            default -> "归档";
        };
        model.addAttribute("sectionPath", sectionPath);
        model.addAttribute("sectionLabel", sectionLabel);
        metadata(model, post.title(), post.description(), post.canonicalPath(), sectionPath);
        return "post";
    }

    @GetMapping("/about")
    public String about(Model model) {
        PostDetail about = read.about().orElse(null);
        model.addAttribute("about", about);
        model.addAttribute("links", read.links().orElse(null));
        metadata(model, "关于", about == null ? site.getDescription() : about.description(), "/about", "/about");
        return "about";
    }

    private void metadata(Model model, String title, String description, String path, String activePath) {
        model.addAttribute("pageTitle", title == null || title.isBlank() ? "未命名文章" : title);
        model.addAttribute("pageDescription", description == null || description.isBlank()
                ? site.getDescription() : description);
        model.addAttribute("canonicalUrl", site.canonicalUrl(path));
        model.addAttribute("activePath", activePath);
    }
}
