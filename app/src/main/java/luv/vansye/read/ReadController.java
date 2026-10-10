package luv.vansye.read;

import luv.vansye.sync.domain.Section;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1")
public class ReadController {
    private final ReadService read;
    public ReadController(ReadService read) { this.read = read; }

    @GetMapping("/posts/{path}")
    public ResponseEntity<PostDetail> post(@PathVariable String path) {
        return ok(read.findByPath(path).orElseThrow(ReadController::notFound));
    }

    @GetMapping("/posts/{path}/neighbors")
    public ResponseEntity<ReadService.Neighbors> neighbors(@PathVariable String path) {
        var post = read.findByPath(path).orElseThrow(ReadController::notFound);
        return ok(read.neighborsOf(post.plate()));
    }

    @GetMapping("/posts")
    public ResponseEntity<ReadService.Page> index(@RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        return ok(read.index(page, size));
    }

    @GetMapping("/sections/{section}")
    public ResponseEntity<ReadService.Page> section(@PathVariable String section,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ok(read.bySection(Section.fromCode(section).orElseThrow(ReadController::notFound), page, size));
    }

    @GetMapping("/about")
    public ResponseEntity<PostDetail> about() { return ok(read.about().orElseThrow(ReadController::notFound)); }

    @GetMapping("/links")
    public ResponseEntity<PostDetail> links() { return ok(read.links().orElseThrow(ReadController::notFound)); }

    private static ResponseStatusException notFound() { return new ResponseStatusException(HttpStatus.NOT_FOUND); }
    private static <T> ResponseEntity<T> ok(T body) { return ResponseEntity.ok().cacheControl(CacheControl.noCache()).body(body); }
}