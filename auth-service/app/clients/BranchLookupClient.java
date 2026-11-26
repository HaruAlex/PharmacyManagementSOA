package clients;

import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * Giao diện tra cứu thông tin chi nhánh từ Catalog Service
 */
public interface BranchLookupClient {
    /**
     * Lấy tất cả BranchId đang hoạt động
     */
    CompletionStage<List<Integer>> getAllActiveBranchIds();
    
    /**
     * Kiểm tra chi nhánh có tồn tại và đang hoạt động không
     */
    CompletionStage<Boolean> isBranchActive(Integer branchId);
    
    /**
     * Kiểm tra danh sách branchId có hợp lệ không
     */
    CompletionStage<Boolean> validateBranchIds(List<Integer> branchIds);
}
