package luv.vansye.site;

import luv.vansye.read.PageController;
import luv.vansye.read.PostDetail;
import luv.vansye.read.PostSummary;
import luv.vansye.read.ReadService;
import luv.vansye.sync.domain.Section;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(properties = {
        "blog.site.name=Example Journal",
        "blog.site.description=A public description",
        "blog.site.author=Example Author",
        "blog.site.url=https://example.test/"
})
@ContextConfiguration(classes = {PageController.class, SiteModelAdvice.class})
@EnableConfigurationProperties(SiteProperties.class)
@AutoConfigureMockMvc(addFilters = false)
class PageControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ReadService read;

    @BeforeEach
    void emptyPublishedContent() {
        when(read.bySection(any(), anyInt(), anyInt())).thenAnswer(invocation ->
                new ReadService.Page(List.of(), 0, invocation.getArgument(1), invocation.getArgument(2)));
        when(read.about()).thenReturn(Optional.empty());
        when(read.links()).thenReturn(Optional.empty());
    }

    @Test
    void homeRendersArchiveDataAndConfiguredIdentity() throws Exception {
        when(read.bySection(Section.ARCHIVE, 0, 6)).thenReturn(
                new ReadService.Page(List.of(summary("hello", "An article")), 24, 0, 6));

        String html = page("/");

        assertThat(html).contains("Example Journal", "Example Author", "A public description", "An article")
                .contains("href=\"/p/hello\"", "href=\"#main\"", "aria-current=\"page\"")
                .contains("rel=\"canonical\" href=\"https://example.test/\"")
                .doesNotContain("mailto:", "th:", "${");
        verify(read).bySection(Section.ARCHIVE, 0, 6);
    }

    @Test
    void listingsRenderActualPaginationForEverySection() throws Exception {
        for (Section section : List.of(Section.ARCHIVE, Section.NOTES, Section.LAB)) {
            String path = switch (section) {
                case ARCHIVE -> "/archive";
                case NOTES -> "/notes";
                default -> "/lab";
            };
            when(read.bySection(section, 1, 20)).thenReturn(
                    new ReadService.Page(List.of(summary("second-page", "Page two article")), 41, 1, 20));

            String html = page(path + "?page=1");

            assertThat(html).contains("Page two article", "2 / 3")
                    .contains("rel=\"prev\" href=\"" + path + "?page=0\"")
                    .contains("rel=\"next\" href=\"" + path + "?page=2\"")
                    .contains("rel=\"canonical\" href=\"https://example.test" + path + "?page=1\"")
                    .doesNotContain("th:", "${");
            verify(read).bySection(section, 1, 20);
        }
    }

    @Test
    void lastPageDoesNotOfferAnEmptyNextPage() throws Exception {
        when(read.bySection(Section.ARCHIVE, 2, 20)).thenReturn(
                new ReadService.Page(List.of(summary("last", "Last article")), 41, 2, 20));
        assertThat(page("/archive?page=2")).contains("3 / 3", "rel=\"prev\"").doesNotContain("rel=\"next\"");
    }

    @Test
    void negativePageStartsAtZeroAndInvalidPageIsBadRequest() throws Exception {
        assertThat(page("/archive?page=-1")).contains("这里还没有公开的内容。");
        verify(read).bySection(Section.ARCHIVE, 0, 20);
        mvc.perform(get("/archive?page=not-a-number")).andExpect(status().isBadRequest());
    }

    @Test
    void outOfRangePageOffersAPathBack() throws Exception {
        String html = page("/notes?page=99");
        assertThat(html).contains("这一页没有更多内容了。", "回到第一页", "100 / 1")
                .doesNotContain("rel=\"next\"");
        verify(read).bySection(Section.NOTES, 99, 20);
    }

    @Test
    void postRendersHtmlAndCanonicalSlugWhenRequestedByPlate() throws Exception {
        PostDetail post = detail("<A title>", "<p>Rendered <strong>body</strong>.</p>");
        when(read.findByPath("A-001")).thenReturn(Optional.of(post));
        when(read.neighborsOf("A-001")).thenReturn(new ReadService.Neighbors(
                summary("newer", "Newer article"), summary("older", "Older article")));

        String html = page("/p/A-001");

        assertThat(html).contains("<p>Rendered <strong>body</strong>.</p>", "&lt;A title&gt;")
                .contains("rel=\"canonical\" href=\"https://example.test/p/hello\"")
                .contains("name=\"description\" content=\"Article description\"")
                .contains("href=\"/p/newer\"", "href=\"/p/older\"", "2026-01-02")
                .doesNotContain("&lt;p&gt;", "th:", "${");
        verify(read).neighborsOf("A-001");
    }

    @Test
    void aboutSupportsBothMissingAndPublishedPageContent() throws Exception {
        assertThat(page("/about")).contains("A public description").doesNotContain("保持联系");
        when(read.about()).thenReturn(Optional.of(detail("About", "<p>About this author.</p>")));
        when(read.links()).thenReturn(Optional.of(detail("Links", "<p>Friends elsewhere.</p>")));
        assertThat(page("/about")).contains("<p>About this author.</p>", "<p>Friends elsewhere.</p>")
                .doesNotContain("th:", "${");
    }

    @Test
    void missingPostRendersNotFoundWith404Status() throws Exception {
        when(read.findByPath("missing")).thenReturn(Optional.empty());
        String html = mvc.perform(get("/p/missing")).andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(html).contains("这一页，暂时留白。", "Example Journal", "回到首页").doesNotContain("th:", "${");
    }

    private String page(String path) throws Exception {
        return mvc.perform(get(path)).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private PostSummary summary(String slug, String title) {
        return new PostSummary("A-001", slug, "ARCHIVE", title, "Article description",
                LocalDate.of(2026, 1, 1), 500, 3, List.of("writing"), "/p/" + slug);
    }

    private PostDetail detail(String title, String html) {
        return new PostDetail("A-001", "hello", "ARCHIVE", title, "Article description", html,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2), 500, 3,
                List.of("writing"), "/p/hello");
    }
}
