package luv.vansye.sync.domain;

import org.commonmark.Extension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.task.list.items.TaskListItemsExtension;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import java.util.List;

public class MarkdownRenderer {

    private static final List<Extension> EXTENSIONS = List.of(
            TablesExtension.create(),
            StrikethroughExtension.create(),
            TaskListItemsExtension.create());
    private final Parser parser = Parser.builder()
            .extensions(EXTENSIONS)
            .build();

    private final HtmlRenderer renderer = HtmlRenderer.builder()
            .extensions(EXTENSIONS)
            .escapeHtml(true)
            .sanitizeUrls(true)
            .build();

    public String toHtml(String markdown) {
        return renderer.render(parser.parse(markdown == null ? "" : markdown));
    }
}
