package repositories;

import models.Drug;
import models.DrugUnitConversion;
import play.db.Database;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** JDBC access for drug CRUD and name/ingredient/barcode search. */
@Singleton
public class DrugRepository extends CatalogRepositorySupport {
    @Inject
    public DrugRepository(Database db) {
        super(db);
    }

    public List<Drug> search(String query, Long groupId, String type, int page, int size) {
        return db.withConnection(connection -> {
            List<Drug> drugs = new ArrayList<>();
            StringBuilder sql = new StringBuilder(selectColumns() + " FROM catalog.Drugs WHERE IsActive = 1 ");
            List<Object> parameters = addFilters(sql, query, groupId, type);
            sql.append("ORDER BY DrugName OFFSET ? ROWS FETCH NEXT ? ROWS ONLY");
            parameters.add(offset(page, size));
            parameters.add(size);
            try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
                bind(statement, parameters);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        drugs.add(map(rows));
                    }
                }
            }
            return drugs;
        });
    }

    public long count(String query, Long groupId, String type) {
        return db.withConnection(connection -> {
            StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM catalog.Drugs WHERE IsActive = 1 ");
            List<Object> parameters = addFilters(sql, query, groupId, type);
            try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
                bind(statement, parameters);
                try (ResultSet rows = statement.executeQuery()) {
                    rows.next();
                    return rows.getLong(1);
                }
            }
        });
    }

    public Optional<Drug> findById(long id) {
        return findOne("DrugId", id);
    }

    public Optional<Drug> findByBarcode(String barcode) {
        return db.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    selectColumns() + " FROM catalog.Drugs WHERE Barcode = ? AND IsActive = 1")) {
                statement.setString(1, barcode);
                try (ResultSet rows = statement.executeQuery()) {
                    return rows.next() ? Optional.of(map(rows)) : Optional.empty();
                }
            }
        });
    }

    public Optional<Drug> findByCode(String code) {
        return findOne("DrugCode", code);
    }

    public Optional<Drug> findByBarcodeIncludingInactive(String barcode) {
        return findOne("Barcode", barcode);
    }

    public Drug create(Drug drug) {
        return db.withConnection(connection -> {
            String sql = "INSERT INTO catalog.Drugs " +
                    "(DrugCode, Barcode, DrugName, ActiveIngredient, Concentration, DosageForm, StorageCondition, " +
                    "DrugType, DrugGroupId, ManufacturerId, BaseUnitId, PrescriptionRequired, ControlledDrug, " +
                    "RegistrationNumber, Description, IsActive) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)";
            try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, drug.getCode());
                setNullableText(statement, 2, drug.getBarcode());
                statement.setString(3, drug.getName());
                setNullableText(statement, 4, drug.getActiveIngredient());
                setNullableText(statement, 5, drug.getConcentration());
                setNullableText(statement, 6, drug.getDosageForm());
                setNullableText(statement, 7, drug.getStorageCondition());
                statement.setString(8, drug.getDrugType());
                statement.setLong(9, drug.getGroupId());
                statement.setLong(10, drug.getManufacturerId());
                statement.setLong(11, drug.getPrimaryUnitId());
                statement.setBoolean(12, drug.isPrescriptionRequired());
                statement.setBoolean(13, drug.isControlledDrug());
                setNullableText(statement, 14, drug.getRegistrationNumber());
                setNullableText(statement, 15, drug.getDescription());
                drug.setId(insertAndGetId(statement));
                drug.setStatus("ACTIVE");
            }
            return drug;
        });
    }

    public boolean update(Drug drug) {
        return db.withConnection(connection -> {
            String sql = "UPDATE catalog.Drugs SET Barcode = ?, DrugName = ?, ActiveIngredient = ?, " +
                    "Concentration = ?, DosageForm = ?, StorageCondition = ?, DrugType = ?, " +
                    "DrugGroupId = ?, ManufacturerId = ?, BaseUnitId = ?, PrescriptionRequired = ?, " +
                    "ControlledDrug = ?, RegistrationNumber = ?, Description = ? WHERE DrugId = ? AND IsActive = 1";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                setNullableText(statement, 1, drug.getBarcode());
                statement.setString(2, drug.getName());
                setNullableText(statement, 3, drug.getActiveIngredient());
                setNullableText(statement, 4, drug.getConcentration());
                setNullableText(statement, 5, drug.getDosageForm());
                setNullableText(statement, 6, drug.getStorageCondition());
                statement.setString(7, drug.getDrugType());
                statement.setLong(8, drug.getGroupId());
                statement.setLong(9, drug.getManufacturerId());
                statement.setLong(10, drug.getPrimaryUnitId());
                statement.setBoolean(11, drug.isPrescriptionRequired());
                statement.setBoolean(12, drug.isControlledDrug());
                setNullableText(statement, 13, drug.getRegistrationNumber());
                setNullableText(statement, 14, drug.getDescription());
                statement.setLong(15, drug.getId());
                return statement.executeUpdate() == 1;
            }
        });
    }

    public boolean discontinue(long id) {
        return db.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE catalog.Drugs SET IsActive = 0 WHERE DrugId = ? AND IsActive = 1")) {
                statement.setLong(1, id);
                return statement.executeUpdate() == 1;
            }
        });
    }

    public List<DrugUnitConversion> findConversions(long drugId) {
        return db.withConnection(connection -> {
            List<DrugUnitConversion> conversions = new ArrayList<>();
            String sql = "SELECT c.UnitId, u.UnitCode, u.UnitName, c.ConversionToBase, c.IsSaleUnit, " +
                    "c.SalePrice, c.IsDefault FROM catalog.DrugUnitConversions c " +
                    "JOIN catalog.Units u ON u.UnitId = c.UnitId " +
                    "WHERE c.DrugId = ? ORDER BY c.IsDefault DESC, u.UnitName";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, drugId);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        DrugUnitConversion conversion = new DrugUnitConversion();
                        conversion.setUnitId(rows.getLong("UnitId"));
                        conversion.setUnitCode(rows.getString("UnitCode"));
                        conversion.setUnitName(rows.getString("UnitName"));
                        conversion.setConversionToBase(rows.getBigDecimal("ConversionToBase"));
                        conversion.setSaleUnit(rows.getBoolean("IsSaleUnit"));
                        conversion.setSalePrice(rows.getBigDecimal("SalePrice"));
                        conversion.setDefaultUnit(rows.getBoolean("IsDefault"));
                        conversions.add(conversion);
                    }
                }
            }
            return conversions;
        });
    }

    public void replaceConversions(long drugId, List<DrugUnitConversion> conversions) {
        db.withConnection(connection -> {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement delete = connection.prepareStatement(
                        "DELETE FROM catalog.DrugUnitConversions WHERE DrugId = ?")) {
                    delete.setLong(1, drugId);
                    delete.executeUpdate();
                }
                String insertSql = "INSERT INTO catalog.DrugUnitConversions " +
                        "(DrugId, UnitId, ConversionToBase, IsSaleUnit, SalePrice, IsDefault) " +
                        "VALUES (?, ?, ?, ?, ?, ?)";
                try (PreparedStatement insert = connection.prepareStatement(insertSql)) {
                    for (DrugUnitConversion conversion : conversions) {
                        insert.setLong(1, drugId);
                        insert.setLong(2, conversion.getUnitId());
                        insert.setBigDecimal(3, conversion.getConversionToBase());
                        insert.setBoolean(4, conversion.isSaleUnit());
                        if (conversion.getSalePrice() == null) {
                            insert.setNull(5, java.sql.Types.DECIMAL);
                        } else {
                            insert.setBigDecimal(5, conversion.getSalePrice());
                        }
                        insert.setBoolean(6, conversion.isDefaultUnit());
                        insert.addBatch();
                    }
                    insert.executeBatch();
                }
                connection.commit();
            } catch (java.sql.SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
            return null;
        });
    }

    private Optional<Drug> findOne(String column, Object value) {
        if (!"DrugId".equals(column) && !"DrugCode".equals(column) && !"Barcode".equals(column)) {
            throw new IllegalArgumentException("Unsupported drug lookup column");
        }
        return db.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    selectColumns() + " FROM catalog.Drugs WHERE " + column + " = ?")) {
                statement.setObject(1, value);
                try (ResultSet rows = statement.executeQuery()) {
                    return rows.next() ? Optional.of(map(rows)) : Optional.empty();
                }
            }
        });
    }

    private List<Object> addFilters(StringBuilder sql, String query, Long groupId, String type) {
        List<Object> parameters = new ArrayList<>();
        if (query != null && !query.trim().isEmpty()) {
            sql.append("AND (DrugName LIKE ? OR ActiveIngredient LIKE ? OR Barcode = ?) ");
            String pattern = "%" + query.trim() + "%";
            parameters.add(pattern);
            parameters.add(pattern);
            parameters.add(query.trim());
        }
        if (groupId != null) {
            sql.append("AND DrugGroupId = ? ");
            parameters.add(groupId);
        }
        if (type != null && !type.trim().isEmpty()) {
            switch (type.toUpperCase(Locale.ROOT)) {
                case "OTC":
                    sql.append("AND PrescriptionRequired = 0 AND ControlledDrug = 0 ");
                    break;
                case "RX":
                case "PRESCRIPTION":
                    sql.append("AND PrescriptionRequired = 1 AND ControlledDrug = 0 ");
                    break;
                case "CONTROLLED":
                    sql.append("AND ControlledDrug = 1 ");
                    break;
                default:
                    throw new IllegalArgumentException("type must be OTC, RX, or CONTROLLED");
            }
        }
        return parameters;
    }

    private void bind(PreparedStatement statement, List<Object> parameters) throws java.sql.SQLException {
        for (int i = 0; i < parameters.size(); i++) {
            statement.setObject(i + 1, parameters.get(i));
        }
    }

    private String selectColumns() {
        return "SELECT DrugId, DrugCode, Barcode, DrugName, ActiveIngredient, Concentration, DosageForm, " +
                "StorageCondition, DrugType, DrugGroupId, ManufacturerId, BaseUnitId, PrescriptionRequired, " +
                "ControlledDrug, RegistrationNumber, Description, IsActive";
    }

    private Drug map(ResultSet row) throws java.sql.SQLException {
        Drug drug = new Drug();
        drug.setId(row.getLong("DrugId"));
        drug.setCode(row.getString("DrugCode"));
        drug.setBarcode(row.getString("Barcode"));
        drug.setName(row.getString("DrugName"));
        drug.setActiveIngredient(row.getString("ActiveIngredient"));
        drug.setConcentration(row.getString("Concentration"));
        drug.setDosageForm(row.getString("DosageForm"));
        drug.setStorageCondition(row.getString("StorageCondition"));
        drug.setDrugType(row.getString("DrugType"));
        drug.setGroupId(row.getLong("DrugGroupId"));
        drug.setManufacturerId(row.getLong("ManufacturerId"));
        drug.setPrimaryUnitId(row.getLong("BaseUnitId"));
        drug.setPrescriptionRequired(row.getBoolean("PrescriptionRequired"));
        drug.setControlledDrug(row.getBoolean("ControlledDrug"));
        drug.setRegistrationNumber(row.getString("RegistrationNumber"));
        drug.setDescription(row.getString("Description"));
        drug.setStatus(row.getBoolean("IsActive") ? "ACTIVE" : "DISCONTINUED");
        return drug;
    }
}
