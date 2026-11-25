package clients;

import javax.inject.Singleton;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Fake/In-memory implementation của BranchLookupClient cho test và dev
 * Giả lập có 3 chi nhánh: 1, 2, 3 (CN000, CN001, CN002)
 */
@Singleton
public class FakeBranchLookupClient implements BranchLookupClient {
    // Giả lập 3 branches từ seed data: CN000 (id=1), CN001 (id=2), CN002 (id=3)
    private static final Set<Integer> ACTIVE_BRANCHES = new HashSet<>(Arrays.asList(1, 2, 3));

    @Override
    public CompletionStage<List<Integer>> getAllActiveBranchIds() {
        return CompletableFuture.completedFuture(Arrays.asList(1, 2, 3));
    }

    @Override
    public CompletionStage<Boolean> isBranchActive(Integer branchId) {
        return CompletableFuture.completedFuture(ACTIVE_BRANCHES.contains(branchId));
    }

    @Override
    public CompletionStage<Boolean> validateBranchIds(List<Integer> branchIds) {
        if (branchIds == null || branchIds.isEmpty()) {
            return CompletableFuture.completedFuture(false);
        }
        boolean allValid = branchIds.stream().allMatch(ACTIVE_BRANCHES::contains);
        return CompletableFuture.completedFuture(allValid);
    }
}
