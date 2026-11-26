package controllers;

import com.ecwid.consul.v1.ConsulClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.Before;
import org.junit.Test;
import play.db.Database;
import play.libs.Json;
import play.mvc.Result;

import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static play.mvc.Http.Status.OK;
import static play.mvc.Http.Status.SERVICE_UNAVAILABLE;
import static play.test.Helpers.contentAsString;

/**
 * Unit Test cho HealthController (BE-27).
 * Giả lập Database và ConsulClient bằng Mockito để kiểm thử độc lập,
 * không phụ thuộc vào SQL Server hay Consul đang chạy thật.
 *
 * @author Nhóm 4 - Đại học Quy Nhơn
 */
public class HealthControllerTest {

    private Database mockDatabase;
    private Connection mockConnection;
    private ConsulClient mockConsulClient;
    private HealthController healthController;

    /**
     * Khởi tạo mock trước mỗi test case để tránh rò rỉ trạng thái giữa các bài test.
     */
    @Before
    public void setUp() {
        mockDatabase = mock(Database.class);
        mockConnection = mock(Connection.class);
        mockConsulClient = mock(ConsulClient.class);
        healthController = new HealthController(mockDatabase, mockConsulClient);
    }

    /**
     * Kịch bản 1: Database kết nối thành công và hợp lệ (timeout 3s).
     * Kỳ vọng: HTTP 200 OK, status = "healthy" và đóng kết nối DB sau khi kiểm tra.
     */
    @Test
    public void testHealth_WhenDatabaseIsHealthy_ReturnsOk() throws SQLException {
        // Arrange: Giả lập DB trả về connection hợp lệ trong 3s
        when(mockDatabase.getConnection()).thenReturn(mockConnection);
        when(mockConnection.isValid(3)).thenReturn(true);

        // Act
        Result result = healthController.health();

        // Assert
        assertEquals(OK, result.status());
        JsonNode body = Json.parse(contentAsString(result));
        assertEquals("healthy", body.get("status").asText());
        assertEquals("inventory-service", body.get("service").asText());
        assertNotNull(body.get("timestamp"));
        verify(mockConnection, times(1)).close();
    }

    /**
     * Kịch bản 2: Lỗi kết nối Database (ném exception khi lấy connection).
     * Kỳ vọng: HTTP 503 Service Unavailable và status = "unhealthy".
     */
    @Test
    public void testHealth_WhenDatabaseThrowsException_Returns503() {
        // Arrange: Giả lập lỗi mất kết nối tới SQL Server
        when(mockDatabase.getConnection()).thenThrow(new RuntimeException("Connection refused"));

        // Act
        Result result = healthController.health();

        // Assert
        assertEquals(SERVICE_UNAVAILABLE, result.status());
        JsonNode body = Json.parse(contentAsString(result));
        assertEquals("unhealthy", body.get("status").asText());
        assertEquals("Database connection failed", body.get("reason").asText());
    }

    /**
     * Kịch bản 3: Connection không còn hợp lệ (isValid(3) trả về false).
     * Kỳ vọng: HTTP 503 Service Unavailable.
     */
    @Test
    public void testHealth_WhenConnectionIsInvalid_Returns503() throws SQLException {
        // Arrange
        when(mockDatabase.getConnection()).thenReturn(mockConnection);
        when(mockConnection.isValid(3)).thenReturn(false);

        // Act
        Result result = healthController.health();

        // Assert
        assertEquals(SERVICE_UNAVAILABLE, result.status());
        JsonNode body = Json.parse(contentAsString(result));
        assertEquals("unhealthy", body.get("status").asText());
        assertEquals("Database connection failed", body.get("reason").asText());
    }

    /**
     * Kịch bản 4: Kiểm tra endpoint detailedHealth trả về đủ thông tin database, memory và jvm.
     * Kỳ vọng: HTTP 200 OK và có các object con database, memory, jvm.
     */
    @Test
    public void testDetailedHealth_ReturnsMetricsPayload() throws SQLException {
        // Arrange
        when(mockDatabase.getConnection()).thenReturn(mockConnection);
        when(mockConnection.isValid(3)).thenReturn(true);

        // Act
        Result result = healthController.detailedHealth();

        // Assert
        assertEquals(OK, result.status());
        JsonNode body = Json.parse(contentAsString(result));
        assertEquals("healthy", body.get("status").asText());
        assertTrue(body.get("database").get("connected").asBoolean());
        assertNotNull(body.get("memory"));
        assertNotNull(body.get("jvm"));
    }
}