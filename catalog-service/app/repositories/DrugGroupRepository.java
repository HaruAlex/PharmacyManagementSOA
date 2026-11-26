package repositories;

import models.DrugGroup;
import play.db.Database;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** JDBC access for drug groups and their parent-child hierarchy. */
@Singleton
public class DrugGroupRepository extends CatalogRepositorySupport {
    @Inject
    public DrugGroupRepository(Database db) {
        super(db);
    }

    public List<DrugGroup> findAll() {
        return db.withConnection(connection -> {
            List<DrugGroup> groups = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT DrugGroupId, GroupCode, GroupName, Description, ParentDrugGroupId, Level, Path, " +
                            "DisplayOrder, IsActive FROM catalog.DrugGroups " +
                            "WHERE IsActive = 1 ORDER BY Path, DisplayOrder, GroupName");
                 ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    groups.add(map(rows));
                }
            }
            return groups;
        });
    }

    public List<DrugGroup> findRootGroups() {
        return db.withConnection(connection -> {
            List<DrugGroup> roots = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT DrugGroupId, GroupCode, GroupName, Description, ParentDrugGroupId, Level, Path, " +
                            "DisplayOrder, IsActive FROM catalog.DrugGroups " +
                            "WHERE ParentDrugGroupId IS NULL AND IsActive = 1 ORDER BY DisplayOrder, GroupName");
                 ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    roots.add(map(rows));
                }
            }
            return roots;
        });
    }

    public Optional<DrugGroup> findById(long id) {
        return db.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT DrugGroupId, GroupCode, GroupName, Description, ParentDrugGroupId, Level, Path, " +
                            "DisplayOrder, IsActive FROM catalog.DrugGroups WHERE DrugGroupId = ?")) {
                statement.setLong(1, id);
                try (ResultSet rows = statement.executeQuery()) {
                    return rows.next() ? Optional.of(map(rows)) : Optional.empty();
                }
            }
        });
    }

    public Optional<DrugGroup> findByName(String name) {
        return db.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT DrugGroupId, GroupCode, GroupName, Description, ParentDrugGroupId, Level, Path, " +
                            "DisplayOrder, IsActive FROM catalog.DrugGroups WHERE GroupName = ?")) {
                statement.setString(1, name);
                try (ResultSet rows = statement.executeQuery()) {
                    return rows.next() ? Optional.of(map(rows)) : Optional.empty();
                }
            }
        });
    }

    public Optional<DrugGroup> findByCode(String code) {
        return db.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT DrugGroupId, GroupCode, GroupName, Description, ParentDrugGroupId, Level, Path, " +
                            "DisplayOrder, IsActive FROM catalog.DrugGroups WHERE GroupCode = ?")) {
                statement.setString(1, code);
                try (ResultSet rows = statement.executeQuery()) {
                    return rows.next() ? Optional.of(map(rows)) : Optional.empty();
                }
            }
        });
    }

    public List<DrugGroup> findChildren(long parentId) {
        return db.withConnection(connection -> {
            List<DrugGroup> children = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT DrugGroupId, GroupCode, GroupName, Description, ParentDrugGroupId, Level, Path, " +
                            "DisplayOrder, IsActive FROM catalog.DrugGroups " +
                            "WHERE ParentDrugGroupId = ? AND IsActive = 1 ORDER BY DisplayOrder, GroupName")) {
                statement.setLong(1, parentId);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        children.add(map(rows));
                    }
                }
            }
            return children;
        });
    }

    public DrugGroup create(DrugGroup group) {
        return db.withConnection(connection -> {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                String parentPath = "/";
                int level = 0;
                if (group.getParentId() != null) {
                    DrugGroup parent = findByIdOnConnection(connection, group.getParentId())
                            .orElseThrow(() -> new IllegalArgumentException("parentId does not exist"));
                    if (!"ACTIVE".equals(parent.getStatus())) {
                        throw new IllegalArgumentException("parentId must reference an active drug group");
                    }
                    parentPath = parent.getPath();
                    level = parent.getLevel() + 1;
                }

                String sql = "INSERT INTO catalog.DrugGroups " +
                        "(GroupCode, GroupName, Description, ParentDrugGroupId, Level, Path, DisplayOrder, IsActive) " +
                        "VALUES (?, ?, ?, ?, ?, NULL, ?, 1)";
                try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    statement.setString(1, group.getCode());
                    statement.setString(2, group.getName());
                    setNullableText(statement, 3, group.getDescription());
                    if (group.getParentId() == null) {
                        statement.setNull(4, java.sql.Types.INTEGER);
                    } else {
                        statement.setLong(4, group.getParentId());
                    }
                    statement.setInt(5, level);
                    statement.setInt(6, group.getDisplayOrder());
                    group.setId(insertAndGetId(statement));
                }
                String path = parentPath + group.getId() + "/";
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE catalog.DrugGroups SET Path = ? WHERE DrugGroupId = ?")) {
                    statement.setString(1, path);
                    statement.setLong(2, group.getId());
                    statement.executeUpdate();
                }
                connection.commit();
                group.setLevel(level);
                group.setPath(path);
                group.setStatus("ACTIVE");
            } catch (Exception e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
            return group;
        });
    }

    public boolean update(DrugGroup group) {
        return db.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE catalog.DrugGroups SET GroupName = ?, Description = ?, DisplayOrder = ? " +
                            "WHERE DrugGroupId = ? AND IsActive = 1")) {
                statement.setString(1, group.getName());
                setNullableText(statement, 2, group.getDescription());
                statement.setInt(3, group.getDisplayOrder());
                statement.setLong(4, group.getId());
                return statement.executeUpdate() == 1;
            }
        });
    }

    public boolean softDelete(long id) {
        return db.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT COUNT(*) FROM catalog.DrugGroups WHERE ParentDrugGroupId = ? AND IsActive = 1")) {
                statement.setLong(1, id);
                try (ResultSet rows = statement.executeQuery()) {
                    if (rows.next() && rows.getLong(1) > 0) {
                        throw new ConflictException("Cannot delete a drug group that has active child groups");
                    }
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT COUNT(*) FROM catalog.Drugs WHERE DrugGroupId = ? AND IsActive = 1")) {
                statement.setLong(1, id);
                try (ResultSet rows = statement.executeQuery()) {
                    if (rows.next() && rows.getLong(1) > 0) {
                        throw new ConflictException("Cannot delete a drug group that is assigned to active drugs");
                    }
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE catalog.DrugGroups SET IsActive = 0 WHERE DrugGroupId = ? AND IsActive = 1")) {
                statement.setLong(1, id);
                return statement.executeUpdate() == 1;
            }
        });
    }

    private DrugGroup map(ResultSet row) throws java.sql.SQLException {
        DrugGroup group = new DrugGroup();
        group.setId(row.getLong("DrugGroupId"));
        group.setCode(row.getString("GroupCode"));
        group.setName(row.getString("GroupName"));
        group.setDescription(row.getString("Description"));
        long parentId = row.getLong("ParentDrugGroupId");
        if (!row.wasNull()) {
            group.setParentId(parentId);
        }
        group.setLevel(row.getInt("Level"));
        group.setPath(row.getString("Path"));
        group.setDisplayOrder(row.getInt("DisplayOrder"));
        group.setStatus(row.getBoolean("IsActive") ? "ACTIVE" : "INACTIVE");
        return group;
    }

    private Optional<DrugGroup> findByIdOnConnection(Connection connection, long id) throws java.sql.SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT DrugGroupId, GroupCode, GroupName, Description, ParentDrugGroupId, Level, Path, " +
                        "DisplayOrder, IsActive FROM catalog.DrugGroups WHERE DrugGroupId = ?")) {
            statement.setLong(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(map(rows)) : Optional.empty();
            }
        }
    }
}
