package repositories;

import models.Manufacturer;
import play.db.Database;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** JDBC access for drug manufacturers in the shared Catalog schema. */
@Singleton
public class ManufacturerRepository extends CatalogRepositorySupport {
    @Inject
    public ManufacturerRepository(Database db) {
        super(db);
    }

    public List<Manufacturer> findAll() {
        return db.withConnection(connection -> {
            List<Manufacturer> manufacturers = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT ManufacturerId, ManufacturerName, Country, IsActive FROM catalog.Manufacturers " +
                            "WHERE IsActive = 1 ORDER BY ManufacturerName");
                 ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    manufacturers.add(map(rows));
                }
            }
            return manufacturers;
        });
    }

    public Optional<Manufacturer> findById(long id) {
        return db.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT ManufacturerId, ManufacturerName, Country, IsActive FROM catalog.Manufacturers " +
                            "WHERE ManufacturerId = ?")) {
                statement.setLong(1, id);
                try (ResultSet rows = statement.executeQuery()) {
                    return rows.next() ? Optional.of(map(rows)) : Optional.empty();
                }
            }
        });
    }

    public Optional<Manufacturer> findByName(String name) {
        return db.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT ManufacturerId, ManufacturerName, Country, IsActive FROM catalog.Manufacturers " +
                            "WHERE ManufacturerName = ?")) {
                statement.setString(1, name);
                try (ResultSet rows = statement.executeQuery()) {
                    return rows.next() ? Optional.of(map(rows)) : Optional.empty();
                }
            }
        });
    }

    public Manufacturer create(Manufacturer manufacturer) {
        return db.withConnection(connection -> {
            String sql = "INSERT INTO catalog.Manufacturers (ManufacturerName, Country, IsActive) VALUES (?, ?, 1)";
            try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, manufacturer.getName());
                setNullableText(statement, 2, manufacturer.getCountry());
                manufacturer.setId(insertAndGetId(statement));
                manufacturer.setStatus("ACTIVE");
            }
            return manufacturer;
        });
    }

    public boolean update(Manufacturer manufacturer) {
        return db.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE catalog.Manufacturers SET ManufacturerName = ?, Country = ? " +
                            "WHERE ManufacturerId = ? AND IsActive = 1")) {
                statement.setString(1, manufacturer.getName());
                setNullableText(statement, 2, manufacturer.getCountry());
                statement.setLong(3, manufacturer.getId());
                return statement.executeUpdate() == 1;
            }
        });
    }

    public boolean softDelete(long id) {
        return db.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT COUNT(*) FROM catalog.Drugs WHERE ManufacturerId = ? AND IsActive = 1")) {
                statement.setLong(1, id);
                try (ResultSet rows = statement.executeQuery()) {
                    if (rows.next() && rows.getLong(1) > 0) {
                        throw new ConflictException("Cannot deactivate a manufacturer assigned to active drugs");
                    }
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE catalog.Manufacturers SET IsActive = 0 WHERE ManufacturerId = ? AND IsActive = 1")) {
                statement.setLong(1, id);
                return statement.executeUpdate() == 1;
            }
        });
    }

    private Manufacturer map(ResultSet row) throws java.sql.SQLException {
        Manufacturer manufacturer = new Manufacturer();
        manufacturer.setId(row.getLong("ManufacturerId"));
        manufacturer.setName(row.getString("ManufacturerName"));
        manufacturer.setCountry(row.getString("Country"));
        manufacturer.setStatus(row.getBoolean("IsActive") ? "ACTIVE" : "INACTIVE");
        return manufacturer;
    }
}
