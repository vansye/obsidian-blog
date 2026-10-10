package luv.vansye.sync.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class PublicationLayoutTest {
    @ParameterizedTest
    @ValueSource(strings = {"", ".", "..", "/public", "C:/public", "a/../b", "a//b", "a\\b", "日记", "个人/网站", "网站/草稿", "a/", "a\nother"})
    void unsafeRootsFailAtConfiguration(String root) {
        assertThatIllegalArgumentException().isThrownBy(() -> new PublicationLayout(root));
    }

    @Test
    void rootControlsArticlesSectionsAndAttachmentsTogether() {
        var layout = new PublicationLayout("公开/站点");
        var gate = new PublicationGate(layout);
        assertThat(gate.evaluate("公开/站点/文章/欢迎.md", true).publishable()).isTrue();
        assertThat(gate.evaluate("博客/文章/泄漏.md", true).publishable()).isFalse();
        assertThat(gate.evaluate("公开/站点/文章/草稿/私密.md", true).publishable()).isFalse();
        assertThat(gate.evaluate("公开/站点/_assets/隐藏.md", true).publishable()).isFalse();
        assertThat(gate.inPublishArea("公开/站点/_assets/image.png")).isTrue();
        assertThat(gate.inPublishArea("博客/_assets/private.png")).isFalse();
        assertThat(Section.of("公开/站点/随笔/日常.md", layout)).contains(Section.NOTES);
        assertThat(Section.of("公开/站点/关于.md", layout)).contains(Section.ABOUT);
        assertThat(Section.of("博客/文章/泄漏.md", layout)).isEmpty();
    }
}
