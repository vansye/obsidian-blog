package luv.vansye.sync.domain;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

class CodeFence {

    private static final Pattern FENCE = Pattern.compile("^ {0,3}(`{3,}|~{3,})(.*)$");

    private String openMarker;

    boolean isInside() {
        return openMarker != null;
    }

    boolean consume(String line) {
        Matcher matcher = FENCE.matcher(line);
        if (!matcher.matches()) {
            return false;
        }
        String marker = matcher.group(1);
        if (openMarker == null) {
            openMarker = marker;
            return true;
        }
        boolean closes = marker.charAt(0) == openMarker.charAt(0)
                && marker.length() >= openMarker.length()
                && matcher.group(2).isBlank();      // 结束栅栏后面不能跟内容
        if (closes) {
            openMarker = null;
        }
        return true;
    }
}
