package repositories;

/** Signals a valid request that conflicts with existing Catalog data. */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
