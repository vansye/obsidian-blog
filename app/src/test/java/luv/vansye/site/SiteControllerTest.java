package luv.vansye.site;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SiteControllerTest {
    @Test
    void publicApiReturnsOnlySiteFieldsAndNavigation() throws Exception {
        SiteProperties site = new SiteProperties();
        site.setName("Example Journal");
        site.setDescription("Public description");
        site.setAuthor("Example Author");
        site.setUrl("https://example.test");
        site.setGithub("https://github.com/example");
        site.setEmail("writer@example.test");
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new SiteController(site)).build();

        String json = mvc.perform(get("/api/v1/site")).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Example Journal"))
                .andExpect(jsonPath("$.github").value("https://github.com/example"))
                .andExpect(jsonPath("$.email").value("writer@example.test"))
                .andExpect(jsonPath("$.navigation.length()").value(5))
                .andExpect(jsonPath("$.navigation[3].path").value("/lab"))
                .andReturn().getResponse().getContentAsString();

        assertThat(new ObjectMapper().readTree(json).properties()).extracting(java.util.Map.Entry::getKey)
                .containsExactlyInAnyOrder("name", "description", "author", "url", "github", "email", "navigation");
    }
}
