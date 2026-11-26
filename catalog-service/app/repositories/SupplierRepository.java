package repositories;

import models.Supplier;
import play.db.Database;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** JDBC access for suppliers in the shared Catalog schema. */
@Singleton
public class SupplierRepository extends CatalogRepositorySupport {
    @Inject
    public SupplierRepository(Database db) {
        super(db);
    }

    public List<Supplier> search(String query, int page, int size) {
        return db.withConnection(connection -> {
            List<Supplier> suppliers = new ArrayList<>();
            StringBuilder sql = new StringBuilder(
                    "SELECT SupplierId, SupplierCode, SupplierName, Phone, Email, Address, TaxCode, " +
                            "ContactPerson, ContactPhone, PaymentTermDays, IsActive " +
                            "FROM catalog.Suppliers WHERE IsActive = 1 ");
            if (query != null && !query.trim().isEmpty()) {
                sql.append("AND (SupplierName LIKE ? OR SupplierCode LIKE ? OR TaxCode LIKE ?) ");
            }
            sql.append("ORDER BY SupplierName OFFSET ? ROWS FETCH NEXT ? ROWS ONLY");
            try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
                int index = 1;
                if (query != null && !query.trim().isEmpty()) {
                    String pattern = "%" + query.trim() + "%";
                    statement.setString(index++, pattern);
                    statement.setString(index++, pattern);
                    statement.setString(index++, pattern);
                }
                bindPage(statement, offset(page, size), size, index);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        suppliers.add(map(rows));
                    }
                }
            }
            return suppliers;
        });
    }

    public long count(String query) {
        return db.withConnection(connection -> {
            String sql = "SELECT COUNT(*) FROM catalog.Suppliers WHERE IsActive = 1 " +
                    (query == null || query.trim().isEmpty()
                            ? ""
                            : "AND (SupplierName LIKE ? OR SupplierCode LIKE ? OR TaxCode LIKE ?)");
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                if (query != null && !query.trim().isEmpty()) {
                    String pattern = "%" + query.trim() + "%";
                    statement.setString(1, pattern);
                    statement.setString(2, pattern);
                    statement.setString(3, pattern);
                }
                try (ResultSet rows = statement.executeQuery()) {
                    rows.next();
                    return rows.getLong(1);
                }
            }
        });
    }

    public Optional<Supplier> findById(long id) {
        return db.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT SupplierId, SupplierCode, SupplierName, Phone, Email, Address, TaxCode, " +
                            "ContactPerson, ContactPhone, PaymentTermDays, IsActive " +
                            "FROM catalog.Suppliers WHERE SupplierId = ?")) {
                statement.setLong(1, id);
                try (ResultSet rows = statement.executeQuery()) {
                    return rows.next() ? Optional.of(map(rows)) : Optional.empty();
                }
            }
        });
    }

    public Optional<Supplier> findByCode(String code) {
        return db.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT SupplierId, SupplierCode, SupplierName, Phone, Email, Address, TaxCode, " +
                            "ContactPerson, ContactPhone, PaymentTermDays, IsActive " +
                            "FROM catalog.Suppliers WHERE SupplierCode = ?")) {
                statement.setString(1, code);
                try (ResultSet rows = statement.executeQuery()) {
                    return rows.next() ? Optional.of(map(rows)) : Optional.empty();
                }
            }
        });
    }

    public Supplier create(Supplier supplier) {
        return db.withConnection(connection -> {
            String sql = "INSERT INTO catalog.Suppliers " +
                    "(SupplierCode, SupplierName, Phone, Email, Address, TaxCode, ContactPerson, ContactPhone, " +
                    "PaymentTermDays, IsActive) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1)";
            try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, supplier.getCode());
                statement.setString(2, supplier.getName());
                setNullableText(statement, 3, supplier.getPhone());
                setNullableText(statement, 4, supplier.getEmail());
                setNullableText(statement, 5, supplier.getAddress());
                setNullableText(statement, 6, supplier.getTaxCode());
                setNullableText(statement, 7, supplier.getContactPerson());
                setNullableText(statement, 8, supplier.getContactPhone());
                statement.setInt(9, supplier.getPaymentTermDays());
                supplier.setId(insertAndGetId(statement));
                supplier.setStatus("ACTIVE");
            }
            return supplier;
        });
    }

    public boolean update(Supplier supplier) {
        return db.withConnection(connection -> {
            String sql = "UPDATE catalog.Suppliers SET SupplierName = ?, Phone = ?, Email = ?, " +
                    "Address = ?, TaxCode = ?, ContactPerson = ?, ContactPhone = ?, PaymentTermDays = ? " +
            "WHERE SupplierId = ? AND IsActive = 1";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, supplier.getName());
                setNullableText(statement, 2, supplier.getPhone());
                setNullableText(statement, 3, supplier.getEmail());
                setNullableText(statement, 4, supplier.getAddress());
                setNullableText(statement, 5, supplier.getTaxCode());
                setNullableText(statement, 6, supplier.getContactPerson());
                setNullableText(statement, 7, supplier.getContactPhone());
                statement.setInt(8, supplier.getPaymentTermDays());
                statement.setLong(9, supplier.getId());
                return statement.executeUpdate() == 1;
            }
        });
    }

    public boolean softDelete(long id) {
        return db.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE catalog.Suppliers SET IsActive = 0 WHERE SupplierId = ? AND IsActive = 1")) {
                statement.setLong(1, id);
                return statement.executeUpdate() == 1;
            }
        });
    }

    private Supplier map(ResultSet row) throws java.sql.SQLException {
        Supplier supplier = new Supplier();
        supplier.setId(row.getLong("SupplierId"));
        supplier.setCode(row.getString("SupplierCode"));
        supplier.setName(row.getString("SupplierName"));
        supplier.setPhone(row.getString("Phone"));
        supplier.setEmail(row.getString("Email"));
        supplier.setAddress(row.getString("Address"));
        supplier.setTaxCode(row.getString("TaxCode"));
        supplier.setContactPerson(row.getString("ContactPerson"));
        supplier.setContactPhone(row.getString("ContactPhone"));
        supplier.setPaymentTermDays(row.getInt("PaymentTermDays"));
        supplier.setStatus(row.getBoolean("IsActive") ? "ACTIVE" : "INACTIVE");
        return supplier;
    }
}
