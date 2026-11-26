package repositories;

import models.Branch;
import play.db.Database;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** JDBC access for branches in the shared {@code catalog} schema. */
@Singleton
public class BranchRepository extends CatalogRepositorySupport {
    @Inject
    public BranchRepository(Database db) {
        super(db);
    }

    public List<Branch> findAll() {
        return db.withConnection(connection -> {
            List<Branch> branches = new ArrayList<>();
            String sql = "SELECT BranchId, BranchCode, BranchName, Address, Phone, BranchType, " +
                    "LicenseNumber, LicenseExpiry, IsActive " +
                    "FROM catalog.Branches WHERE IsActive = 1 ORDER BY BranchCode";
            try (PreparedStatement statement = connection.prepareStatement(sql);
                 ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    branches.add(map(rows));
                }
            }
            return branches;
        });
    }

    public Optional<Branch> findById(long id) {
        return db.withConnection(connection -> {
            String sql = "SELECT BranchId, BranchCode, BranchName, Address, Phone, BranchType, " +
                    "LicenseNumber, LicenseExpiry, IsActive " +
                    "FROM catalog.Branches WHERE BranchId = ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, id);
                try (ResultSet rows = statement.executeQuery()) {
                    return rows.next() ? Optional.of(map(rows)) : Optional.empty();
                }
            }
        });
    }

    public Optional<Branch> findByCode(String code) {
        return db.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT BranchId, BranchCode, BranchName, Address, Phone, BranchType, " +
                            "LicenseNumber, LicenseExpiry, IsActive " +
                            "FROM catalog.Branches WHERE BranchCode = ?")) {
                statement.setString(1, code);
                try (ResultSet rows = statement.executeQuery()) {
                    return rows.next() ? Optional.of(map(rows)) : Optional.empty();
                }
            }
        });
    }

    public Branch create(Branch branch) {
        return db.withConnection(connection -> {
            String sql = "INSERT INTO catalog.Branches " +
                    "(BranchCode, BranchName, Address, Phone, BranchType, LicenseNumber, LicenseExpiry, IsActive) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, 1)";
            try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, branch.getCode());
                statement.setString(2, branch.getName());
                setNullableText(statement, 3, branch.getAddress());
                setNullableText(statement, 4, branch.getPhone());
                statement.setString(5, toDatabaseType(branch.getType()));
                setNullableText(statement, 6, branch.getLicenseNumber());
                if (branch.getLicenseExpiry() == null) {
                    statement.setNull(7, java.sql.Types.DATE);
                } else {
                    statement.setDate(7, Date.valueOf(branch.getLicenseExpiry()));
                }
                branch.setId(insertAndGetId(statement));
                branch.setStatus("ACTIVE");
            }
            return branch;
        });
    }

    public boolean update(Branch branch) {
        return db.withConnection(connection -> {
            String sql = "UPDATE catalog.Branches SET BranchName = ?, Address = ?, Phone = ?, BranchType = ?, " +
                    "LicenseNumber = ?, LicenseExpiry = ? WHERE BranchId = ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, branch.getName());
                setNullableText(statement, 2, branch.getAddress());
                setNullableText(statement, 3, branch.getPhone());
                statement.setString(4, toDatabaseType(branch.getType()));
                setNullableText(statement, 5, branch.getLicenseNumber());
                if (branch.getLicenseExpiry() == null) {
                    statement.setNull(6, java.sql.Types.DATE);
                } else {
                    statement.setDate(6, Date.valueOf(branch.getLicenseExpiry()));
                }
                statement.setLong(7, branch.getId());
                return statement.executeUpdate() == 1;
            }
        });
    }

    public boolean setActive(long id, boolean active) {
        return db.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE catalog.Branches SET IsActive = ? WHERE BranchId = ?")) {
                statement.setBoolean(1, active);
                statement.setLong(2, id);
                return statement.executeUpdate() == 1;
            }
        });
    }

    private Branch map(ResultSet row) throws java.sql.SQLException {
        Branch branch = new Branch();
        branch.setId(row.getLong("BranchId"));
        branch.setCode(row.getString("BranchCode"));
        branch.setName(row.getString("BranchName"));
        branch.setAddress(row.getString("Address"));
        branch.setPhone(row.getString("Phone"));
        branch.setType("CENTRAL_WAREHOUSE".equals(row.getString("BranchType")) ? "WAREHOUSE" : "STORE");
        branch.setLicenseNumber(row.getString("LicenseNumber"));
        Date expiry = row.getDate("LicenseExpiry");
        if (expiry != null) {
            branch.setLicenseExpiry(expiry.toLocalDate());
        }
        branch.setStatus(row.getBoolean("IsActive") ? "ACTIVE" : "INACTIVE");
        return branch;
    }

    private String toDatabaseType(String type) {
        return "WAREHOUSE".equalsIgnoreCase(type) || "CENTRAL_WAREHOUSE".equalsIgnoreCase(type)
                ? "CENTRAL_WAREHOUSE" : "PHARMACY";
    }
}
