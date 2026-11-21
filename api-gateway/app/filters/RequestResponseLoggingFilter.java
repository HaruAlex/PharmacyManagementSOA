package filters;

import play.Logger;
import play.mvc.Filter;
import play.mvc.Http;
import play.mvc.Result;
import org.apache.pekko.stream.Materializer;
import com.typesafe.config.Config;

import javax.inject.Inject;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * ============================================================================
 * REQUEST/RESPONSE LOGGING FILTER
 * ============================================================================
 * 
 * Filter này ghi log mỗi request với X-Request-Id và service instance ID.
 * Đồng thời thêm header X-Served-By vào response để client biết request
 * được phục vụ bởi instance nào.
 * 
 * Đợt 3: Cân bằng tải (Load Balancing)
 * 
 * @author Nhóm 4 - Đại học Quy Nhơn
 */
public class RequestResponseLoggingFilter extends Filter {
    
    // Instance ID của API Gateway (lấy từ service.instance.id)
    private final String instanceId;
    
    @Inject
    public RequestResponseLoggingFilter(Materializer materializer, Config config) {
        super(materializer);
        this.instanceId = config.hasPath("service.instance.id") ? config.getString("service.instance.id") : "gateway-1";
    }
    
    @Override
    public CompletionStage<Result> apply(Function<Http.RequestHeader, CompletionStage<Result>> next, Http.RequestHeader requestHeader) {
        
        // Lấy hoặc tạo X-Request-Id
        String xRequestId = requestHeader.header("X-Request-Id").orElse(
                "req-" + System.currentTimeMillis() + "-" + (int)(Math.random() * 10000)
        );
        
        // Log request
        String method = requestHeader.method();
        String path = requestHeader.path();
        
        Logger.info("[" + instanceId + "] [" + xRequestId + "] " + method + " " + path);
        
        // Gọi handler tiếp theo
        return next.apply(requestHeader).thenApply(result -> {
            // Thêm X-Served-By vào response (cho biết instance nào phục vụ)
            // Thêm X-Request-Id vào response (để client track request)
            return result
                    .withHeader("X-Served-By", instanceId)
                    .withHeader("X-Request-Id", xRequestId);
        });
    }
}
