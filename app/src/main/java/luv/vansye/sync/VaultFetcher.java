package luv.vansye.sync;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Fetch only an independent deployment clone, under SyncService's lock. */
@Component
public class VaultFetcher {
    private final File vaultDir;
    private final boolean enabled;
    private final Duration timeout;

    public VaultFetcher(@Value("${blog.vault.dir}") String vaultDir,
                        @Value("${blog.sync.fetch-enabled:false}") boolean enabled,
                        @Value("${blog.sync.fetch-timeout:PT60S}") Duration timeout) {
        if (timeout.isZero() || timeout.isNegative()) throw new IllegalArgumentException("fetch-timeout must be positive");
        this.vaultDir = new File(vaultDir);
        this.enabled = enabled;
        this.timeout = timeout;
    }

    public record Result(boolean ok, String detail) {}

    public Result fetch() {
        if (!enabled) return new Result(true, "local committed content; fetch disabled");
        Result clean = run(List.of("diff", "--quiet", "HEAD"));
        if (!clean.ok()) return new Result(false, "deployment clone has local changes or is unreadable");
        Result fetched = run(List.of("fetch", "--prune", "origin"));
        return fetched.ok() ? run(List.of("merge", "--ff-only", "@{u}")) : fetched;
    }

    private Result run(List<String> args) {
        List<String> command = new ArrayList<>(List.of("git", "-C", vaultDir.getPath()));
        command.addAll(args);
        Process process = null;
        try {
            // Output can contain credential-bearing remote URLs. Discard also avoids a blocking pipe.
            ProcessBuilder builder = new ProcessBuilder(command)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD);
            builder.environment().put("GIT_TERMINAL_PROMPT", "0");
            process = builder.start();
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                stop(process);
                return new Result(false, args.get(0) + " timed out");
            }
            return new Result(process.exitValue() == 0, args.get(0) + " exited " + process.exitValue());
        } catch (IOException e) {
            return new Result(false, "cannot execute git");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (process != null) stop(process);
            return new Result(false, "git interrupted");
        }
    }

    private void stop(Process process) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
    }
}