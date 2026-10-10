package luv.vansye.asset;

import luv.vansye.sync.domain.AssetMediaTypes;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;


@RestController
public class AssetController {


    private final AssetService assets;

    public AssetController(AssetService assets) {
        this.assets = assets;
    }

    @GetMapping("/assets/{fileName}")
    public ResponseEntity<byte[]> asset(@PathVariable String fileName) {
        return assets.find(fileName)
                .map(this::respond)
                .orElseGet(() -> ResponseEntity.notFound().cacheControl(CacheControl.noCache()).build());
    }

    private ResponseEntity<byte[]> respond(AssetService.Asset asset) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(asset.mime()))
                .contentLength(asset.bytes().length)
                .cacheControl(CacheControl.noCache())
                .eTag("\"" + asset.contentHash() + "\"")
                .header("X-Content-Type-Options", "nosniff");

        if (AssetMediaTypes.isScriptCapable(asset.mime())) {
            builder.header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'");
        }
        return builder.body(asset.bytes());
    }
}
