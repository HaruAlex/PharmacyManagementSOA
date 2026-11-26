package repositories;

import models.Unit;
import play.db.Database;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** JDBC access for units of measure, including soft-deactivation. */
@Singleton
public class UnitRepository extends CatalogRepositorySupport {
    @Inject
    public UnitRepository(Database db) {
        super(db);
    }

    public List<Unit> findAll() {
        return db.withConnection(connection -> {
            List<Unit> units = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT UnitId, UnitCode, UnitName, Description, IsActive FROM catalog.Units " +
                            "WHERE IsActive = 1 ORDER BY UnitName");
                 ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    units.add(map(rows));
                }
            }
            return units;
        });
    }

    public Optional<Unit> findById(long id) {
        return db.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT UnitId, UnitCode, UnitName, Description, IsActive FROM catalog.Units WHERE UnitId = ?")) {
                statement.setLong(1, id);
                try (ResultSet rows = statement.executeQuery()) {
                    return rows.next() ? Optional.of(map(rows)) : Optional.empty();
                }
            }
        });
    }

    public Optional<Unit> findByCode(String code) {
        return db.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT UnitId, UnitCode, UnitName, Description, IsActive FROM catalog.Units WHERE UnitCode = ?")) {
                statement.setString(1, code);
                try (ResultSet rows = statement.executeQuery()) {
                    return rows.next() ? Optional.of(map(rows)) : Optional.empty();
                }
            }
        });
    }

    public Optional<Unit> findByName(String name) {
        return db.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT UnitId, UnitCode, UnitName, Description, IsActive FROM catalog.Units WHERE UnitName = ?")) {
                statement.setString(1, name);
                try (ResultSet rows = statement.executeQuery()) {
                    return rows.next() ? Optional.of(map(rows)) : Optional.empty();
                }
            }
        });
    }

    public Unit create(Unit unit) {
        return db.withConnection(connection -> {
            String sql = "INSERT INTO catalog.Units (UnitName, UnitCode, Description, IsActive) VALUES (?, ?, ?, 1)";
            try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, unit.getName());
                statement.setString(2, unit.getCode());
                setNullableText(statement, 3, unit.getDescription());
                unit.setId(insertAndGetId(statement));
                unit.setStatus("ACTIVE");
            }
            return unit;
        });
    }

    public boolean update(Unit unit) {
        return db.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE catalog.Units SET UnitName = ?, UnitCode = ?, Description = ? " +
                            "WHERE UnitId = ? AND IsActive = 1")) {
                statement.setString(1, unit.getName());
                statement.setString(2, unit.getCode());
                setNullableText(statement, 3, unit.getDescription());
                statement.setLong(4, unit.getId());
                return statement.executeUpdate() == 1;
            }
        });
    }

    public boolean delete(long id) {
        return db.withConnection(connection -> {
            String usageSql = "SELECT " +
                    "(SELECT COUNT(*) FROM catalog.Drugs WHERE BaseUnitId = ? AND IsActive = 1) + " +
                    "(SELECT COUNT(*) FROM catalog.DrugUnitConversions WHERE UnitId = ?)";
            try (PreparedStatement statement = connection.prepareStatement(usageSql)) {
                statement.setLong(1, id);
                statement.setLong(2, id);
                try (ResultSet rows = statement.executeQuery()) {
                    if (rows.next() && rows.getLong(1) > 0) {
                        throw new ConflictException("Cannot delete a unit that is used by a drug or conversion");
                    }
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE catalog.Units SET IsActive = 0 WHERE UnitId = ? AND IsActive = 1")) {
                statement.setLong(1, id);
                return statement.executeUpdate() == 1;
            }
        });
    }

    private Unit map(ResultSet row) throws java.sql.SQLException {
        Unit unit = new Unit();
        unit.setId(row.getLong("UnitId"));
        unit.setCode(row.getString("UnitCode"));
        unit.setName(row.getString("UnitName"));
        unit.setDescription(row.getString("Description"));
        unit.setStatus(row.getBoolean("IsActive") ? "ACTIVE" : "INACTIVE");
        return unit;
    }
}
