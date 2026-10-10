package luv.vansye.sync;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import luv.vansye.BackendApplication;
import luv.vansye.sync.store.SyncLogMapper;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

@Testcontainers
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"blog.sync.startup-enabled=false", "blog.sync.poll-enabled=false", "blog.cors-origins=https://frontend.example"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BlogIT {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");
    @Container static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);
    static final String SECRET = "test-only-webhook-key";
    static final Path VAULT = makeVault();
    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper json;
    @Autowired SyncService sync;
    @Autowired JdbcTemplate db;
    @Autowired SyncLogMapper logs;
    static String assetUrl;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url", MYSQL::getJdbcUrl);
        p.add("spring.datasource.username", MYSQL::getUsername);
        p.add("spring.datasource.password", MYSQL::getPassword);
        p.add("spring.data.redis.host", REDIS::getHost);
        p.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        p.add("blog.vault.dir", () -> VAULT.toString());
        p.add("blog.sync.webhook-secret", () -> SECRET);
    }

    private static Path makeVault() {
        try {
            Path path = Files.createTempDirectory("obsidian-blog-it-");
            try (Git git = Git.init().setDirectory(path.toFile()).call()) {
                Files.writeString(path.resolve("README.md"), "isolated integration fixture");
                commit(git);
            }
            return path;
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }

    private static void commit(Git git) throws Exception {
        git.add().addFilepattern(".").call();
        git.add().addFilepattern(".").setUpdate(true).call();
        git.commit().setMessage("fixture update").setAuthor("Fixture", "fixture@example.invalid")
                .setCommitter("Fixture", "fixture@example.invalid").call();
    }

    private void write(String path, String body) throws Exception {
        Path file = VAULT.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, body, StandardCharsets.UTF_8);
    }

    private void commit() throws Exception { try (Git git = Git.open(VAULT.toFile())) { commit(git); } }

    private String post(String title, String slug, boolean publish, String body) {
        return "---\npublish: " + publish + "\ntitle: " + title + "\nslug: " + slug
                + "\ncreated: 2026-01-02\nupdated: 2026-01-03\ntags: [demo, test]\n---\n" + body;
    }

    private JsonNode get(String path) throws Exception {
        var response = http.getForEntity(path, String.class);
        assertThat(response.getStatusCode()).as(path).isEqualTo(HttpStatus.OK);
        return json.readTree(response.getBody());
    }

    @Test @Order(1)
    void freshSchemaAndCommittedContentOnly() throws Exception {
        assertThat(db.queryForObject("SELECT COUNT(*) FROM plate_seq", Integer.class)).isEqualTo(4);
        write("博客/文章/欢迎.md", post("欢迎", "welcome", true,
                "# Hello\n![[博客/_assets/sample.svg|示例图片]]\n\n| Key | Value |\n| --- | --- |\n| A | B |\n\n```java\nint answer = 42;\n```"));
        write("博客/_assets/sample.svg", "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"40\" height=\"40\"><rect width=\"40\" height=\"40\" fill=\"teal\"/></svg>");
        write("博客/随笔/想法.md", post("想法", "a-note", true, "short note"));
        write("博客/文章/草稿/私密.md", post("私密", "private", true, "PRIVATE-SENTINEL"));
        write("博客/文章/未发布.md", post("未发布", "draft", false, "DRAFT-SENTINEL"));
        write("博客/关于.md", post("关于", "about-me", true, "About this blog"));
        write("博客/友链.md", post("友链", "friends", true, "[Example](https://example.org)"));
        commit();
        write("博客/文章/未提交.md", post("未提交", "uncommitted", true, "NOT-COMMITTED"));
        new VaultPoller(sync, true, false).afterSingletonsInstantiated();
        JsonNode page = get("/api/v1/posts");
        assertThat(page.get("total").asInt()).isEqualTo(2);
        assertThat(page.toString()).doesNotContain("PRIVATE-SENTINEL", "DRAFT-SENTINEL", "vault", "bodyMd", "commit");
        assertThat(http.getForEntity("/api/v1/posts/uncommitted", String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        JsonNode detail = get("/api/v1/posts/welcome");
        assertThat(detail.get("plate").asText()).startsWith("A-");
        assertThat(detail.get("bodyHtml").asText()).contains("<table>", "<code class=\"language-java\"", "/assets/");
        var matcher = java.util.regex.Pattern.compile("src=\"([^\"]+)\"").matcher(detail.get("bodyHtml").asText());
        assertThat(matcher.find()).isTrue();
        assetUrl = matcher.group(1);
        assertThat(get("/api/v1/about").get("title").asText()).isEqualTo("关于");
        assertThat(get("/api/v1/links").get("title").asText()).isEqualTo("友链");
        assertThat(http.getForEntity("/", String.class).getBody()).contains("欢迎", "site.css");
        assertThat(http.getForEntity("/p/welcome", String.class).getBody()).contains("示例图片");
        Files.delete(VAULT.resolve("博客/文章/未提交.md"));
    }

    @Test @Order(2)
    void apiContractCacheCorsAndErrors() throws Exception {
        var site = get("/api/v1/site");
        assertThat(site.properties().stream().map(Map.Entry::getKey).toList())
                .containsExactlyInAnyOrder("name", "description", "author", "url", "github", "email", "navigation");
        assertThat(get("/api/v1/sections/ARCHIVE").get("total").asInt()).isEqualTo(1);
        assertThat(get("/api/v1/posts/welcome/neighbors").get("newer").isNull()).isTrue();
        for (String path : List.of("/api/v1/posts?page=-1", "/api/v1/posts?size=101", "/api/v1/posts?size=0", "/api/v1/posts?page=2147483647", "/api/v1/posts?page=no")) {
            var response = http.getForEntity(path, String.class);
            assertThat(response.getStatusCode()).as(path).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(json.readTree(response.getBody()).get("code").asText()).isEqualTo("INVALID_ARGUMENT");
        }
        assertThat(get("/api/v1/posts?page=99").get("items")).isEmpty();
        for (String path : List.of("/api/v1/posts/private", "/api/v1/posts/absent", "/api/v1/posts/absent/neighbors")) {
            var response = http.getForEntity(path, String.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(json.readTree(response.getBody()).get("code").asText()).isEqualTo("NOT_FOUND");
        }
        for (String path : List.of("/api/v1/site", "/api/v1/posts", assetUrl)) {
            var response = http.getForEntity(path, String.class);
            assertThat(response.getHeaders().getCacheControl()).contains("no-cache");
            assertThat(response.getHeaders().getETag()).isNotBlank();
            HttpHeaders conditional = new HttpHeaders();
            conditional.setIfNoneMatch(response.getHeaders().getETag());
            assertThat(http.exchange(path, HttpMethod.GET, new HttpEntity<>(conditional), String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_MODIFIED);
        }
        HttpHeaders origin = new HttpHeaders();
        origin.setOrigin("https://frontend.example");
        assertThat(http.exchange("/api/v1/site", HttpMethod.GET, new HttpEntity<>(origin), String.class)
                .getHeaders().getAccessControlAllowOrigin()).isEqualTo("https://frontend.example");
        assertThat(http.exchange("/actuator/health", HttpMethod.GET, new HttpEntity<>(origin), String.class)
                .getHeaders().getAccessControlAllowOrigin()).isNull();
        assertThat(http.postForEntity("/api/v1/posts", "{}", String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test @Order(3)
    void signedWebhookUpdatesMetadataDeduplicatesAndWithdrawsAssets() throws Exception {
        assertThat(http.postForEntity("/api/sync/webhook", "{}", String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        write("博客/文章/欢迎.md", post("欢迎", "renamed-slug", true, "# Updated\n![[博客/_assets/sample.svg|示例图片]]")
                .replace("updated: 2026-01-03", "updated: 2026-02-05"));
        commit();
        assertThat(hook("delivery-update").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(hook("delivery-update").getBody()).contains("already handled");
        var detail = get("/api/v1/posts/renamed-slug");
        assertThat(detail.get("updated").asText()).isEqualTo("2026-02-05");
        assertThat(detail.get("canonicalPath").asText()).isEqualTo("/p/renamed-slug");
        var before = http.getForEntity(assetUrl, String.class);
        write("博客/文章/欢迎.md", post("欢迎", "renamed-slug", false, "withdrawn"));
        commit();
        assertThat(hook("delivery-withdraw").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(http.getForEntity("/api/v1/posts/renamed-slug", String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        HttpHeaders headers = new HttpHeaders();
        headers.setIfNoneMatch(before.getHeaders().getETag());
        assertThat(http.exchange(assetUrl, HttpMethod.GET, new HttpEntity<>(headers), String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM sync_log WHERE delivery_id='delivery-update'", Integer.class)).isEqualTo(1);
    }

    private ResponseEntity<String> hook(String delivery) throws Exception {
        String body = "{\"ref\":\"refs/heads/main\"}";
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Hub-Signature-256", "sha256=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8))));
        headers.set("X-GitHub-Event", "push");
        headers.set("X-GitHub-Delivery", delivery);
        return http.postForEntity("/api/sync/webhook", new HttpEntity<>(body, headers), String.class);
    }

    @Test @Order(4)
    void identicalAssetBytesUnderDifferentExtensionsRemainAccessible() throws Exception {
        write("博客/_assets/one.jpg", "fixture-image-bytes");
        write("博客/_assets/two.jpeg", "fixture-image-bytes");
        write("博客/文章/图片别名.md", post("相同内容两种扩展名", "image-alias", true,
                "![[博客/_assets/one.jpg]]\n![[博客/_assets/two.jpeg]]"));
        commit();
        hook("alias-images");
        String html = get("/api/v1/posts/image-alias").get("bodyHtml").asText();
        var matcher = java.util.regex.Pattern.compile("src=\"([^\"]+)\"").matcher(html);
        int count = 0;
        while (matcher.find()) {
            assertThat(http.getForEntity(matcher.group(1), byte[].class).getStatusCode()).isEqualTo(HttpStatus.OK);
            count++;
        }
        assertThat(count).isEqualTo(2);
    }

    @Test @Order(5)
    void partialFullSyncStillWithdrawsKnownRejectedContent() throws Exception {
        write("博客/文章/临时.md", post("临时", "temporary", true, "public first"));
        commit();
        hook("before-partial");
        String lastComplete = logs.lastCompleteSha();
        write("博客/文章/临时.md", post("临时", "temporary", false, "withdrawn"));
        Files.write(VAULT.resolve("博客/文章/无法解码.md"), new byte[]{(byte) 0xff, (byte) 0xfe});
        commit();
        var outcome = sync.fullSync("partial-withdrawal");
        assertThat(outcome).isInstanceOf(SyncOutcome.Done.class);
        assertThat(((SyncOutcome.Done) outcome).complete()).isFalse();
        assertThat(logs.lastCompleteSha()).isEqualTo(lastComplete);
        assertThat(http.getForEntity("/api/v1/posts/temporary", String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(db.queryForObject("SELECT status FROM sync_log WHERE delivery_id='partial-withdrawal'", String.class)).isEqualTo("partial");
        Files.delete(VAULT.resolve("博客/文章/无法解码.md"));
        commit();
        assertThat(((SyncOutcome.Done) sync.fullSync("partial-recovery")).complete()).isTrue();
    }

    @Test @Order(6)
    void periodicFullSyncRepairsAssetsAndRestartSwitchesPublishRoot() throws Exception {
        write("博客/文章/等待图片.md", post("图片随后提交", "late-image", true, "![[博客/_assets/later.svg]]"));
        commit();
        assertThat(hook("late-post").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("/api/v1/posts/late-image").get("bodyHtml").asText()).doesNotContain("/assets/");
        write("博客/_assets/later.svg", "<svg xmlns=\"http://www.w3.org/2000/svg\"/>");
        commit();
        hook("late-image");
        new VaultPoller(sync, false, true).poll();
        assertThat(get("/api/v1/posts/late-image").get("bodyHtml").asText()).contains("/assets/");
        write("公开/站点/文章/新的.md", post("新发布区", "new-root", true, "new root body"));
        write("公开/站点/文章/草稿/隐藏.md", post("隐藏", "hidden-new-root", true, "not public"));
        commit();
        Map<String, Object> props = new HashMap<>();
        props.put("server.port", "0");
        props.put("spring.datasource.url", MYSQL.getJdbcUrl());
        props.put("spring.datasource.username", MYSQL.getUsername());
        props.put("spring.datasource.password", MYSQL.getPassword());
        props.put("spring.data.redis.host", REDIS.getHost());
        props.put("spring.data.redis.port", REDIS.getMappedPort(6379));
        props.put("blog.vault.dir", VAULT.toString());
        props.put("blog.vault.publish-root", "公开/站点");
        props.put("blog.sync.poll-enabled", "false");
        props.put("blog.sync.startup-enabled", "true");
        // Command-line property priority is needed above application.yaml's env defaults.
        String[] args = props.entrySet().stream().map(e -> "--" + e.getKey() + "=" + e.getValue()).toArray(String[]::new);
        try (var restarted = new SpringApplicationBuilder(BackendApplication.class).run(args)) {
            assertThat(get("/api/v1/posts").get("total").asInt()).isEqualTo(1);
            assertThat(get("/api/v1/posts/new-root").get("section").asText()).isEqualTo("ARCHIVE");
            assertThat(http.getForEntity("/api/v1/posts/late-image", String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}
