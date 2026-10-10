package luv.vansye.read;

import luv.vansye.site.SiteController;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class OpenApiContractTest {
    @Test
    @SuppressWarnings("unchecked")
    void publicDtoFieldsMatchThePublishedContract() throws Exception {
        Map<String, Object> spec;
        try (var stream = Files.newInputStream(Path.of("..", "docs", "openapi.yaml"))) {
            spec = new Yaml().load(stream);
        }
        var components = (Map<String, Object>) spec.get("components");
        var schemas = (Map<String, Map<String, Object>>) components.get("schemas");
        assertSchema(schemas.get("PostSummary"), PostSummary.class);
        assertSchema(schemas.get("PostDetail"), PostDetail.class);
        assertSchema(schemas.get("Page"), ReadService.Page.class);
        assertSchema(schemas.get("Neighbors"), ReadService.Neighbors.class);
        assertSchema(schemas.get("Site"), SiteController.PublicSite.class);
        var paths = (Map<String, Object>) spec.get("paths");
        assertThat(paths).containsKeys("/api/v1/site", "/api/v1/posts", "/api/v1/posts/{path}",
                "/api/v1/posts/{path}/neighbors", "/api/v1/sections/{section}", "/api/v1/about", "/api/v1/links", "/assets/{fileName}");
        assertThat(spec.get("openapi")).isEqualTo("3.0.3");
    }

    @SuppressWarnings("unchecked")
    private void assertSchema(Map<String, Object> schema, Class<?> dto) {
        var fields = Arrays.stream(dto.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName).toList();
        assertThat((Map<String, Object>) schema.get("properties")).containsOnlyKeys(fields.toArray(String[]::new));
        assertThat((List<String>) schema.get("required")).containsExactlyInAnyOrderElementsOf(fields);
        assertThat(schema.get("additionalProperties")).isEqualTo(false);
    }
}
