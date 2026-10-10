package luv.vansye.sync.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 板块归类。纯函数，毫秒级。
 *
 * 这个类的测试重点不是「四个目录各归各的」——那是显然的——
 * 而是**归不进任何板块时不要瞎猜**。给一个默认板块的话，
 * 一篇放错位置的文章会静默出现在某个入口里，而「它为什么在这儿」查不出来。
 */
class SectionTest {

    @Test
    @DisplayName("① 三个目录 + 两个单文件，各归各的")
    void classifiesEachPublishArea() {
        assertThat(Section.of("博客/文章/低空略过Redis的学习.md")).contains(Section.ARCHIVE);
        assertThat(Section.of("博客/随笔/今天想到的.md")).contains(Section.NOTES);
        assertThat(Section.of("博客/项目/这个博客.md")).contains(Section.LAB);
        assertThat(Section.of("博客/关于.md")).contains(Section.ABOUT);
        assertThat(Section.of("博客/友链.md")).contains(Section.LINKS);
    }

    @Test
    @DisplayName("② 深层子目录跟着它的顶层板块走")
    void nestedPathsInheritTheirSection() {
        assertThat(Section.of("博客/文章/2026/夏/一篇.md")).contains(Section.ARCHIVE);
    }

    @Test
    @DisplayName("③ 归不进板块的一律 empty，**不给默认值** —— 否则放错位置的文章会静默出现在某个入口")
    void refusesToGuess() {
        assertThat(Section.of("博客/_assets/shot.png")).isEmpty();   // 附件不是文章
        assertThat(Section.of("博客/草稿/没写完.md")).isEmpty();       // 禁止清单
        assertThat(Section.of("学习/JavaWeb/笔记.md")).isEmpty();      // 发布区之外
        assertThat(Section.of("博客/随便放的.md")).isEmpty();          // 发布区里但不在任何板块
        assertThat(Section.of(null)).isEmpty();
    }

    @Test
    @DisplayName("④ `关于.md` 是精确匹配，不是前缀 —— `博客/关于我的猫.md` 不是 ABOUT 页")
    void singleFileSectionsMatchExactly() {
        assertThat(Section.of("博客/关于我的猫.md")).isEmpty();
        assertThat(Section.of("博客/关于.md.bak")).isEmpty();
    }

    @Test
    @DisplayName("⑤ 反斜杠路径也认 —— Windows 上拿到的是 `博客\\文章\\a.md`")
    void normalizesBackslashes() {
        assertThat(Section.of("博客\\文章\\a.md")).contains(Section.ARCHIVE);
    }

    @Test
    @DisplayName("⑥ 从数据库读回来：认不出的值返回 empty，不猜")
    void parsesCodeStrictly() {
        assertThat(Section.fromCode("ARCHIVE")).contains(Section.ARCHIVE);
        assertThat(Section.fromCode("archive")).as("大小写不敏感，前端传小写也认").contains(Section.ARCHIVE);
        assertThat(Section.fromCode("ARCHIVES")).isEmpty();
        assertThat(Section.fromCode("")).isEmpty();
        assertThat(Section.fromCode(null)).isEmpty();
    }

    @Test
    @DisplayName("图版号前缀：三个内容板块各自一段，两个页面板块共用 P")
    void platePrefixesPartitionTheNumberSpace() {
        assertThat(Section.ARCHIVE.platePrefix()).isEqualTo("A");
        assertThat(Section.NOTES.platePrefix()).isEqualTo("N");
        assertThat(Section.LAB.platePrefix()).isEqualTo("L");
        assertThat(Section.ABOUT.platePrefix()).isEqualTo("P");
        assertThat(Section.LINKS.platePrefix()).isEqualTo("P");

        // **三个内容板块的前缀必须互不相同**，否则它们共用号段：
        // 两篇不同板块的文章会拿到同一个 plate，撞 uk_plate，整批同步失败。
        // 这一条不是重复上面五行——上面验的是「值对不对」，
        // 这一条验的是「加板块时不会不小心复制粘贴一个已用过的字母」
        assertThat(List.of(Section.ARCHIVE, Section.NOTES, Section.LAB)
                .stream().map(Section::platePrefix).distinct().count())
                .as("内容板块的前缀撞了，它们会共用号段")
                .isEqualTo(3);

        // 每个板块都得有前缀。漏一个的话 nextPlate 会拼出 `null-001`
        assertThat(Section.values())
                .allSatisfy(section -> assertThat(section.platePrefix())
                        .as("%s 没有图版号前缀", section)
                        .isNotBlank());
    }
}
