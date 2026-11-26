package filters;

import controllers.GatewayController;
import play.Logger;
import play.mvc.Filter;
import play.mvc.Http;
import play.mvc.Result;
import org.apache.pekko.stream.Materializer;
import com.typesafe.config.Config;
import integration.consul.ConsulServiceRegistration;

import javax.inject.Inject;
import java.util.UUID;
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
    public RequestResponseLoggingFilter(
            Materializer materializer,
            Config config) {
        super(materializer);
        this.instanceId = ConsulServiceRegistration.instanceId(config);
    }
    
    @Override
    public CompletionStage<Result> apply(Function<Http.RequestHeader, CompletionStage<Result>> next, Http.RequestHeader requestHeader) {
        
        // Lấy hoặc tạo X-Request-Id
        String xRequestId = requestHeader.header("X-Request-Id")
                .filter(this::isValidRequestId)
                .orElseGet(() -> UUID.randomUUID().toString());
        
        // Log request
        String method = requestHeader.method();
        String path = requestHeader.path();
        
        Logger.info("[" + instanceId + "] [" + xRequestId + "] " + method + " " + path);
        
        // Gọi handler tiếp theo
        Http.RequestHeader requestWithId = requestHeader.addAttr(GatewayController.REQUEST_ID, xRequestId);
        return next.apply(requestWithId).thenApply(result -> {
            // Thêm X-Served-By vào response (cho biết instance nào phục vụ)
            // Thêm X-Request-Id vào response (để client track request)
            return result
                    .withHeader("X-Served-By", instanceId)
                    .withHeader("X-Request-Id", xRequestId);
        });
    }

    private boolean isValidRequestId(String requestId) {
        return requestId.length() <= 128 && requestId.matches("[A-Za-z0-9._:-]+");
    }
}
