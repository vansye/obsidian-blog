package luv.vansye.sync.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class SlugContractTest {
    @ParameterizedTest
    @ValueSource(strings = {"a/b", "../private", "HELLO", "hello world", "hello?x=1", "a--b", "-hello", "hello-", "[a,b]"})
    void invalidSlugFailsPublication(String value) {
        var parsed = new FrontmatterParser().parse("---\npublish: true\nslug: " + value + "\n---\ntext");
        assertThat(parsed.ok()).isFalse();
        assertThat(parsed.error()).isEqualTo("invalid_slug");
        assertThat(parsed.publish()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"hello", "hello-2026", "123", "", "null"})
    void optionalValidSlugKeepsPublishTrue(String value) {
        assertThat(new FrontmatterParser().parse("---\npublish: true\nslug: " + value + "\n---\ntext").publish()).isTrue();
    }
}
