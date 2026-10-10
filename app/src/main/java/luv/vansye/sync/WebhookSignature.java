package luv.vansye.sync;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class WebhookSignature {

    private static final String PREFIX = "sha256=";

    private static final String ALGORITHM = "HmacSHA256";

    private final String secret;

    public WebhookSignature(String secret) {
        this.secret = secret == null ? "" : secret.trim();
    }

    public boolean configured() {
        return !secret.isEmpty();
    }

    public boolean verify(byte[] body, String header) {
        if (!configured() || body == null || header == null) {
            return false;
        }
        String expected;
        try {
            expected = PREFIX + HexFormat.of().formatHex(hmac(body));
        } catch (GeneralSecurityException e) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                header.getBytes(StandardCharsets.UTF_8));
    }

    public String sign(byte[] body) {
        if (!configured()) {
            throw new IllegalStateException("密钥没配，算不出签名");
        }
        try {
            return PREFIX + HexFormat.of().formatHex(hmac(body));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("算不出 HMAC-SHA256", e);
        }
    }

    private byte[] hmac(byte[] body) throws GeneralSecurityException {
        Mac mac = Mac.getInstance(ALGORITHM);
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
        return mac.doFinal(body);
    }
}
