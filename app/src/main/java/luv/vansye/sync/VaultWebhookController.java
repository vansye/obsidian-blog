package luv.vansye.sync;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@RestController
public class VaultWebhookController {
    private final WebhookSignature signature;
    private final SyncService sync;

    public VaultWebhookController(@Value("${blog.sync.webhook-secret:}") String secret, SyncService sync) {
        this.signature = new WebhookSignature(secret);
        this.sync = sync;
    }

    @PostMapping(path = "/api/sync/webhook", produces = "text/plain")
    public ResponseEntity<String> receive(@RequestBody(required = false) byte[] body,
            @RequestHeader(name = "X-Hub-Signature-256", required = false) String signatureHeader,
            @RequestHeader(name = "X-GitHub-Event", required = false) String event,
            @RequestHeader(name = "X-GitHub-Delivery", required = false) String delivery) {
        byte[] payload = body == null ? new byte[0] : body;
        if (!signature.verify(payload, signatureHeader)) return ResponseEntity.status(401).body("bad signature\n");
        if ("ping".equals(event)) return ResponseEntity.ok("pong\n");
        if (!"push".equals(event)) return ResponseEntity.ok("ignored\n");
        if (delivery != null && !delivery.matches("[A-Za-z0-9:._-]{1,128}")) {
            return ResponseEntity.badRequest().body("invalid delivery id\n");
        }
        SyncOutcome outcome = sync.sync(delivery == null ? payloadId(payload) : delivery);
        if (outcome instanceof SyncOutcome.Done done) {
            return done.complete() ? ResponseEntity.ok("synced\n") : ResponseEntity.accepted().body("partial; scheduled retry\n");
        }
        if (outcome instanceof SyncOutcome.AlreadyHandled) return ResponseEntity.ok("already handled\n");
        if (outcome instanceof SyncOutcome.Busy) return ResponseEntity.accepted().body("busy; scheduled retry\n");
        return ResponseEntity.status(500).body("sync failed\n");
    }

    private String payloadId(byte[] payload) {
        try {
            return "hook:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}