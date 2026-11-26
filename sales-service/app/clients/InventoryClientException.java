package clients;

public class InventoryClientException extends RuntimeException {
    private final int statusCode;
    private final String responseBody;

    public InventoryClientException(int statusCode, String responseBody) {
        super("Inventory Service returned HTTP " + statusCode);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getResponseBody() {
        return responseBody;
    }
}
