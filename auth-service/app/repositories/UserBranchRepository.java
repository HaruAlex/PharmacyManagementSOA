package repositories;

import java.util.List;

/**
 * Repository interface cho UserBranches (mapping table)
 */
public interface UserBranchRepository {
    List<Integer> getBranchIdsByUserId(Integer userId);
    
    /**
     * Thay thế toàn bộ danh sách chi nhánh của user trong 1 transaction
     */
    void replaceBranches(Integer userId, List<Integer> branchIds);
    
    /**
     * Xóa tất cả chi nhánh của user
     */
    void deleteByUserId(Integer userId);
}
