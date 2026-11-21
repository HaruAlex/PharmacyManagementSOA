package filters;

import play.Logger;
import play.mvc.Filter;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.Results;
import org.apache.pekko.stream.Materializer;
import services.IdempotencyCache;

import javax.inject.Inject;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * ============================================================================
 * IDEMPOTENCY FILTER
 * ============================================================================
 * 
 * Xử lý Idempotency-Key header:
 * - Nếu client gửi Idempotency-Key → cache response 1 giờ
 * - Nếu request duplicate (cùng key) → trả response cũ
 * - Nếu lỗi → không cache, cho phép retry
 * 
 * Đợt 4: Chịu lỗi + An toàn (Fault Tolerance)
 * 
 * @author Nhóm 4 - Đại học Quy Nhơn
 */
public class IdempotencyFilter extends Filter {
    
    private final IdempotencyCache cache;
    
    @Inject
    public IdempotencyFilter(Materializer materializer, IdempotencyCache cache) {
        super(materializer);
        this.cache = cache;
    }
    
    @Override
    public CompletionStage<Result> apply(
            Function<Http.RequestHeader, CompletionStage<Result>> next,
            Http.RequestHeader requestHeader
    ) {
        // Chỉ kiểm tra POST, PUT, DELETE (write operations)
        String method = requestHeader.method();
        if (!isWriteOperation(method)) {
            return next.apply(requestHeader);
        }
        
        // Lấy Idempotency-Key từ header
        String idempotencyKey = requestHeader.header("Idempotency-Key").orElse(null);
        
        // Nếu không có key → không cache
        if (idempotencyKey == null || idempotencyKey.isEmpty()) {
            return next.apply(requestHeader);
        }
        
        Logger.info("[IdempotencyFilter] Found Idempotency-Key: " + idempotencyKey);
        
        // Kiểm tra cache
        IdempotencyCache.CachedResponse cached = cache.get(idempotencyKey);
        if (cached != null) {
            Logger.info("[IdempotencyFilter] ✓ Cache HIT for key: " + idempotencyKey + 
                    " - trả response cũ (status " + cached.statusCode + ")");
            
            // Trả response cũ từ cache
            Result cachedResult = Results.status(cached.statusCode, cached.body);
            
            return CompletableFuture.completedFuture(cachedResult);
        }
        
        Logger.info("[IdempotencyFilter] Cache MISS for key: " + idempotencyKey + " - processing request");
        
        // Cache miss → xử lý request
        return next.apply(requestHeader).thenApply(result -> {
            // Nếu success (2xx) → cache response
            if (result.status() >= 200 && result.status() < 300) {
                Logger.info("[IdempotencyFilter] ✓ Success (status " + result.status() + 
                        ") - caching response cho key: " + idempotencyKey);
                
                // Cache response (60 phút) - lưu body và status
                IdempotencyCache.CachedResponse toCache = new IdempotencyCache.CachedResponse(
                    result.status(),
                    result.body().toString(),
                    null  // Headers không cần cache
                );
                
                cache.put(idempotencyKey, toCache, 60 * 60); // 1 hour TTL
            } else {
                Logger.warn("[IdempotencyFilter] Error (status " + result.status() + 
                        ") - không cache, cho phép retry");
            }
            
            return result;
        });
    }
    
    private boolean isWriteOperation(String method) {
        return method.equalsIgnoreCase("POST") ||
               method.equalsIgnoreCase("PUT") ||
               method.equalsIgnoreCase("PATCH") ||
               method.equalsIgnoreCase("DELETE");
    }
}
