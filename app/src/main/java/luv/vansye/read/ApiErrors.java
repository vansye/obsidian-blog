package luv.vansye.read;

import luv.vansye.site.SiteController;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(assignableTypes = {ReadController.class, SiteController.class, PageController.class})
public class ApiErrors {
    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ProblemDetail> invalid(Exception error) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "Check page, size and path parameters.");
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ProblemDetail> status(ResponseStatusException error) {
        HttpStatus status = HttpStatus.valueOf(error.getStatusCode().value());
        return problem(status, status == HttpStatus.NOT_FOUND ? "NOT_FOUND" : "REQUEST_FAILED",
                status == HttpStatus.NOT_FOUND ? "The requested content is unavailable." : "The request could not be completed.");
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        return ResponseEntity.status(status).cacheControl(CacheControl.noCache()).body(problem);
    }
}