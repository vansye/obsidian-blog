package luv.vansye.sync.domain;

public record PublicationDecision(boolean publishable, String code) {

    public static final String PATH_FORBIDDEN   = "path_forbidden";
    public static final String PATH_NOT_ALLOWED = "path_not_allowed";
    public static final String NOT_MARKDOWN     = "not_markdown";
    public static final String NOT_PUBLISHED    = "not_published";

    public static PublicationDecision accept() {
        return new PublicationDecision(true, null);
    }

    public static PublicationDecision reject(String code) {
        return new PublicationDecision(false, code);
    }
}
