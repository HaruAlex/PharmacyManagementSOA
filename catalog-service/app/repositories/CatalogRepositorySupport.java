package repositories;

import play.db.Database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/** Common JDBC helpers used by Catalog repositories. */
abstract class CatalogRepositorySupport {
    protected final Database db;

    CatalogRepositorySupport(Database db) {
        this.db = db;
    }

    protected long insertAndGetId(PreparedStatement statement) throws SQLException {
        statement.executeUpdate();
        try (ResultSet keys = statement.getGeneratedKeys()) {
            if (!keys.next()) {
                throw new SQLException("Insert completed without returning a generated key");
            }
            return keys.getLong(1);
        }
    }

    protected void setNullableText(PreparedStatement statement, int index, String value) throws SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.VARCHAR);
        } else {
            statement.setString(index, value);
        }
    }

    protected void setNullableLong(PreparedStatement statement, int index, Long value) throws SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.BIGINT);
        } else {
            statement.setLong(index, value);
        }
    }

    protected static String activeStatus(boolean active) {
        return active ? "ACTIVE" : "INACTIVE";
    }

    protected static boolean isActive(String status) {
        return !"INACTIVE".equalsIgnoreCase(status) && !"DISCONTINUED".equalsIgnoreCase(status);
    }

    protected static boolean exists(ResultSet resultSet) throws SQLException {
        return resultSet.next() && resultSet.getLong(1) > 0;
    }

    protected static void bindPage(PreparedStatement statement, int offset, int size, int firstIndex)
            throws SQLException {
        statement.setInt(firstIndex, offset);
        statement.setInt(firstIndex + 1, size);
    }

    protected static int offset(int page, int size) {
        try {
            return Math.multiplyExact(page - 1, size);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("page is too large");
        }
    }
}
