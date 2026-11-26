package repositories;

import models.Customer;
import play.db.Database;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** JDBC access for customers, including allergy data and lifecycle status. */
@Singleton
public class CustomerRepository extends CatalogRepositorySupport {
    private static final String CUSTOMER_COLUMNS =
            "CustomerId, CustomerCode, CustomerName, Phone, Email, LoyaltyPoints, Allergies, IsActive";

    @Inject
    public CustomerRepository(Database db) {
        super(db);
    }

    public List<Customer> search(String query, String phone, int page, int size) {
        return db.withConnection(connection -> {
            List<Customer> customers = new ArrayList<>();
            StringBuilder sql = new StringBuilder(
                    "SELECT " + CUSTOMER_COLUMNS + " FROM catalog.Customers WHERE IsActive = 1 ");
            List<Object> parameters = new ArrayList<>();
            appendFilters(sql, parameters, query, phone);
            sql.append("ORDER BY CustomerName OFFSET ? ROWS FETCH NEXT ? ROWS ONLY");
            parameters.add(offset(page, size));
            parameters.add(size);
            try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
                bind(statement, parameters);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        customers.add(map(rows));
                    }
                }
            }
            return customers;
        });
    }

    public long count(String query, String phone) {
        return db.withConnection(connection -> {
            StringBuilder sql = new StringBuilder(
                    "SELECT COUNT(*) FROM catalog.Customers WHERE IsActive = 1 ");
            List<Object> parameters = new ArrayList<>();
            appendFilters(sql, parameters, query, phone);
            try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
                bind(statement, parameters);
                try (ResultSet rows = statement.executeQuery()) {
                    rows.next();
                    return rows.getLong(1);
                }
            }
        });
    }

    public Optional<Customer> findById(long id) {
        return findOne("CustomerId", id);
    }

    public Optional<Customer> findByCode(String code) {
        return findOne("CustomerCode", code);
    }

    public Optional<Customer> findByPhone(String phone) {
        return db.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT " + CUSTOMER_COLUMNS + " FROM catalog.Customers WHERE Phone = ?")) {
                statement.setString(1, phone);
                try (ResultSet rows = statement.executeQuery()) {
                    return rows.next() ? Optional.of(map(rows)) : Optional.empty();
                }
            }
        });
    }

    public Customer create(Customer customer) {
        return db.withConnection(connection -> {
            String sql = "INSERT INTO catalog.Customers " +
                    "(CustomerCode, CustomerName, Phone, Email, LoyaltyPoints, Allergies, IsActive) " +
                    "VALUES (?, ?, ?, ?, ?, ?, 1)";
            try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, customer.getCode());
                statement.setString(2, customer.getName());
                setNullableText(statement, 3, customer.getPhone());
                setNullableText(statement, 4, customer.getEmail());
                statement.setInt(5, customer.getLoyaltyPoints() == null ? 0 : customer.getLoyaltyPoints());
                setNullableText(statement, 6, customer.getAllergies());
                customer.setId(insertAndGetId(statement));
                if (customer.getLoyaltyPoints() == null) {
                    customer.setLoyaltyPoints(0);
                }
                customer.setStatus("ACTIVE");
            }
            return customer;
        });
    }

    public boolean update(Customer customer) {
        return db.withConnection(connection -> {
            String sql = "UPDATE catalog.Customers SET CustomerName = ?, Phone = ?, Email = ?, LoyaltyPoints = ?, " +
                    "Allergies = ? WHERE CustomerId = ? AND IsActive = 1";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, customer.getName());
                setNullableText(statement, 2, customer.getPhone());
                setNullableText(statement, 3, customer.getEmail());
                statement.setInt(4, customer.getLoyaltyPoints() == null ? 0 : customer.getLoyaltyPoints());
                setNullableText(statement, 5, customer.getAllergies());
                statement.setLong(6, customer.getId());
                return statement.executeUpdate() == 1;
            }
        });
    }

    public boolean delete(long id) {
        return db.withConnection(connection -> {
            String referenceSql = "SELECT " +
                    "(SELECT COUNT(*) FROM sales.Invoices WHERE CustomerId = ?) + " +
                    "(SELECT COUNT(*) FROM sales.Prescriptions WHERE CustomerId = ?)";
            try (PreparedStatement statement = connection.prepareStatement(referenceSql)) {
                statement.setLong(1, id);
                statement.setLong(2, id);
                try (ResultSet rows = statement.executeQuery()) {
                    if (rows.next() && rows.getLong(1) > 0) {
                        throw new ConflictException("Cannot deactivate a customer with sales history or prescriptions");
                    }
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE catalog.Customers SET IsActive = 0 WHERE CustomerId = ? AND IsActive = 1")) {
                statement.setLong(1, id);
                return statement.executeUpdate() == 1;
            }
        });
    }

    private Optional<Customer> findOne(String column, Object value) {
        if (!"CustomerId".equals(column) && !"CustomerCode".equals(column)) {
            throw new IllegalArgumentException("Unsupported customer lookup column");
        }
        return db.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT " + CUSTOMER_COLUMNS + " FROM catalog.Customers WHERE " + column + " = ?")) {
                statement.setObject(1, value);
                try (ResultSet rows = statement.executeQuery()) {
                    return rows.next() ? Optional.of(map(rows)) : Optional.empty();
                }
            }
        });
    }

    private void appendFilters(StringBuilder sql, List<Object> parameters, String query, String phone) {
        if (query != null && !query.trim().isEmpty()) {
            sql.append("AND (CustomerName LIKE ? OR CustomerCode LIKE ?) ");
            String pattern = "%" + query.trim() + "%";
            parameters.add(pattern);
            parameters.add(pattern);
        }
        if (phone != null && !phone.trim().isEmpty()) {
            sql.append("AND Phone = ? ");
            parameters.add(phone.trim());
        }
    }

    private void bind(PreparedStatement statement, List<Object> parameters) throws java.sql.SQLException {
        for (int i = 0; i < parameters.size(); i++) {
            statement.setObject(i + 1, parameters.get(i));
        }
    }

    private Customer map(ResultSet row) throws java.sql.SQLException {
        Customer customer = new Customer();
        customer.setId(row.getLong("CustomerId"));
        customer.setCode(row.getString("CustomerCode"));
        customer.setName(row.getString("CustomerName"));
        customer.setPhone(row.getString("Phone"));
        customer.setEmail(row.getString("Email"));
        customer.setLoyaltyPoints(row.getInt("LoyaltyPoints"));
        customer.setAllergies(row.getString("Allergies"));
        customer.setStatus(row.getBoolean("IsActive") ? "ACTIVE" : "INACTIVE");
        return customer;
    }
}
