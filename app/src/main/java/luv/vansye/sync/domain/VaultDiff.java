package luv.vansye.sync.domain;

import java.util.List;

public record VaultDiff(List<String> touched, List<String> deleted) {

    public VaultDiff(List<String> touched, List<String> deleted) {
        this.touched = List.copyOf(touched);
        this.deleted = List.copyOf(deleted);
    }

    public static VaultDiff empty() {
        return new VaultDiff(List.of(), List.of());
    }

    public boolean isEmpty() {
        return touched.isEmpty() && deleted.isEmpty();
    }

    public List<String> allPaths() {
        List<String> all = new java.util.ArrayList<>(touched);
        all.addAll(deleted);
        return List.copyOf(all);
    }
}
