package integration.consul;

public final class ServiceDiscoveryException extends RuntimeException {
    public ServiceDiscoveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
