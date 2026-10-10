package luv.vansye.sync.domain;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record Frontmatter(Map<String, Object> values, String body, String error, String detail) {

    public static final String ERR_UNTERMINATED = "frontmatter_unterminated";
    public static final String ERR_NOT_A_MAP    = "frontmatter_not_a_map";
    public static final String ERR_YAML         = "frontmatter_yaml_error";

    public Frontmatter {
        values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public static Frontmatter of(Map<String, Object> values, String body) {
        return new Frontmatter(values, body, null, null);
    }

    public static Frontmatter failed(String error, String detail, String rawText) {
        return new Frontmatter(Map.of(), rawText, error, detail);
    }

    public boolean ok() {
        return error == null;
    }

    public Boolean publish() {
        Object v = values.get("publish");
        return (v instanceof Boolean b) ? b : null;
    }

    public String string(String key) {
        Object v = values.get(key);
        if (v == null) {
            return null;
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    public List<String> stringList(String key) {
        Object v = values.get(key);
        if (v == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        if (v instanceof List<?> list) {
            list.forEach(item -> addIfNotBlank(out, item));
        } else {
            addIfNotBlank(out, v);
        }
        return List.copyOf(out);
    }

    public LocalDate date(String key) {
        Object v = values.get(key);
        if (v instanceof LocalDate d) {
            return d;
        }
        if (v instanceof Date d) {
            return d.toInstant().atZone(ZoneOffset.UTC).toLocalDate();
        }
        if (v == null) {
            return null;
        }
        try {
            return LocalDate.parse(String.valueOf(v).trim());
        } catch (RuntimeException e) {
            return null;   // 写成了别的格式：当作没写，走降级
        }
    }

    private void addIfNotBlank(List<String> out, Object item) {
        if (item == null) {
            return;
        }
        String s = String.valueOf(item).trim();
        if (!s.isEmpty()) {
            out.add(s);
        }
    }
}
