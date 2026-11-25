package clients;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit test cho FakeBranchLookupClient
 */
class FakeBranchLookupClientTest {
    private final BranchLookupClient client = new FakeBranchLookupClient();

    @Test
    void getAllActiveBranchIds_shouldReturnThreeBranches() {
        List<Integer> branchIds = client.getAllActiveBranchIds()
                .toCompletableFuture()
                .join();
        
        assertThat(branchIds).containsExactly(1, 2, 3);
    }

    @Test
    void isBranchActive_withValidBranch_shouldReturnTrue() {
        Boolean isActive = client.isBranchActive(1)
                .toCompletableFuture()
                .join();
        
        assertThat(isActive).isTrue();
    }

    @Test
    void isBranchActive_withInvalidBranch_shouldReturnFalse() {
        Boolean isActive = client.isBranchActive(999)
                .toCompletableFuture()
                .join();
        
        assertThat(isActive).isFalse();
    }

    @Test
    void validateBranchIds_withAllValidIds_shouldReturnTrue() {
        Boolean isValid = client.validateBranchIds(Arrays.asList(1, 2))
                .toCompletableFuture()
                .join();
        
        assertThat(isValid).isTrue();
    }

    @Test
    void validateBranchIds_withInvalidId_shouldReturnFalse() {
        Boolean isValid = client.validateBranchIds(Arrays.asList(1, 999))
                .toCompletableFuture()
                .join();
        
        assertThat(isValid).isFalse();
    }

    @Test
    void validateBranchIds_withEmptyList_shouldReturnFalse() {
        Boolean isValid = client.validateBranchIds(Arrays.asList())
                .toCompletableFuture()
                .join();
        
        assertThat(isValid).isFalse();
    }

    @Test
    void validateBranchIds_withNull_shouldReturnFalse() {
        Boolean isValid = client.validateBranchIds(null)
                .toCompletableFuture()
                .join();
        
        assertThat(isValid).isFalse();
    }
}
