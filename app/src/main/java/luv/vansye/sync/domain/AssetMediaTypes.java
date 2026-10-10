package luv.vansye.sync.domain;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class AssetMediaTypes {

    private static final Map<String, String> MIME_BY_EXT = mimeByExt();

    private AssetMediaTypes() {}

    private static Map<String, String> mimeByExt() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put(".png", "image/png");
        map.put(".jpg", "image/jpeg");
        map.put(".jpeg", "image/jpeg");
        map.put(".gif", "image/gif");
        map.put(".webp", "image/webp");
        map.put(".bmp", "image/bmp");
        map.put(".svg", "image/svg+xml");
        map.put(".pdf", "application/pdf");
        return Map.copyOf(map);
    }

    public static Set<String> extensions() {
        return MIME_BY_EXT.keySet();
    }

    public static String mimeOf(String ext) {
        if (ext == null) {
            return "application/octet-stream";
        }
        return MIME_BY_EXT.getOrDefault(ext.toLowerCase(Locale.ROOT), "application/octet-stream");
    }

    public static boolean isScriptCapable(String mime) {
        return "image/svg+xml".equals(mime) || "application/pdf".equals(mime);
    }
}
