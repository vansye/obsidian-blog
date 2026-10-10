package luv.vansye.site;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class SiteModelAdvice {
    private final SiteProperties site;

    public SiteModelAdvice(SiteProperties site) {
        this.site = site;
    }

    @ModelAttribute
    public void siteModel(Model model) {
        model.addAttribute("site", site);
        model.addAttribute("pageTitle", site.getName());
        model.addAttribute("pageDescription", site.getDescription());
        model.addAttribute("canonicalUrl", site.canonicalUrl("/"));
        model.addAttribute("activePath", "");
    }
}
