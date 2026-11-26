package repositories;

import models.Branch;
import models.Customer;
import models.Drug;
import models.DrugGroup;
import models.Manufacturer;
import models.Supplier;
import models.Unit;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/** Integration-style repository tests against the shared Catalog table shape. */
public class CatalogRepositoryTest extends CatalogTestSupport {

    @Test
    public void branchCrudMapsDatabaseTypesAndStatus() {
        BranchRepository repository = new BranchRepository(database);
        Branch branch = new Branch();
        branch.setCode("CN900");
        branch.setName("Test branch");
        branch.setType("WAREHOUSE");
        branch.setLicenseNumber("LIC-900");
        branch.setLicenseExpiry(java.time.LocalDate.of(2030, 12, 31));

        Branch created = repository.create(branch);
        assertTrue(created.getId() > 0);
        assertEquals("WAREHOUSE", repository.findById(created.getId()).get().getType());
        assertEquals("LIC-900", repository.findById(created.getId()).get().getLicenseNumber());

        created.setName("Renamed branch");
        assertTrue(repository.update(created));
        assertEquals("Renamed branch", repository.findById(created.getId()).get().getName());

        assertTrue(repository.setActive(created.getId(), false));
        assertTrue(repository.findAll().isEmpty());
        assertEquals("INACTIVE", repository.findById(created.getId()).get().getStatus());
    }

    @Test
    public void drugGroupsCanBeSoftDeletedButNotWhileUsed() {
        DrugGroupRepository groups = new DrugGroupRepository(database);
        DrugGroup group = new DrugGroup();
        group.setCode("GRP-1");
        group.setName("Pain relief");
        DrugGroup created = groups.create(group);
        assertEquals("ACTIVE", created.getStatus());

        addManufacturerAndUnit();
        Drug drug = baseDrug(created.getId(), 1, 1, "D-1", "Pain medicine", "Ingredient X", "123");
        new DrugRepository(database).create(drug);
        try {
            groups.softDelete(created.getId());
            fail("A group referenced by an active drug must not be deactivated");
        } catch (ConflictException expected) {
            assertTrue(expected.getMessage().contains("active drugs"));
        }
        assertEquals(1, groups.findAll().size());
    }

    @Test
    public void drugGroupsTrackParentPathAndChildren() {
        DrugGroupRepository groups = new DrugGroupRepository(database);
        DrugGroup parent = new DrugGroup();
        parent.setCode("GRP-ROOT");
        parent.setName("Medicines");
        DrugGroup createdParent = groups.create(parent);

        DrugGroup child = new DrugGroup();
        child.setCode("GRP-CHILD");
        child.setName("Pain relief");
        child.setParentId(createdParent.getId());
        DrugGroup createdChild = groups.create(child);

        assertEquals(Integer.valueOf(0), Integer.valueOf(createdParent.getLevel()));
        assertEquals(Integer.valueOf(1), Integer.valueOf(createdChild.getLevel()));
        assertEquals(createdParent.getPath() + createdChild.getId() + "/", createdChild.getPath());
        assertEquals(1, groups.findChildren(createdParent.getId()).size());
        assertEquals(1, groups.findRootGroups().size());
    }

    @Test
    public void manufacturerAndSupplierCrudPreserveLifecycleRules() {
        ManufacturerRepository manufacturers = new ManufacturerRepository(database);
        Manufacturer manufacturer = new Manufacturer();
        manufacturer.setName("Example Pharma");
        manufacturer.setCountry("Vietnam");
        Manufacturer createdManufacturer = manufacturers.create(manufacturer);
        createdManufacturer.setCountry("Updated");
        assertTrue(manufacturers.update(createdManufacturer));
        assertEquals("Updated", manufacturers.findById(createdManufacturer.getId()).get().getCountry());
        assertTrue(manufacturers.softDelete(createdManufacturer.getId()));
        assertTrue(manufacturers.findAll().isEmpty());

        SupplierRepository suppliers = new SupplierRepository(database);
        Supplier supplier = new Supplier();
        supplier.setCode("SUP-1");
        supplier.setName("Supplier One");
        supplier.setContactPerson("Contact One");
        supplier.setContactPhone("0900000099");
        supplier.setPaymentTermDays(30);
        Supplier createdSupplier = suppliers.create(supplier);
        assertEquals(1, suppliers.count("Supplier"));
        Supplier loadedSupplier = suppliers.findById(createdSupplier.getId()).get();
        assertEquals("Contact One", loadedSupplier.getContactPerson());
        assertEquals(Integer.valueOf(30), Integer.valueOf(loadedSupplier.getPaymentTermDays()));
        createdSupplier.setName("Supplier Updated");
        assertTrue(suppliers.update(createdSupplier));
        assertEquals("Supplier Updated", suppliers.findById(createdSupplier.getId()).get().getName());
        assertTrue(suppliers.softDelete(createdSupplier.getId()));
        assertEquals(0, suppliers.count(null));
    }

    @Test
    public void unitCannotBeDeletedWhileUsedByDrug() {
        UnitRepository units = new UnitRepository(database);
        Unit unit = new Unit();
        unit.setCode("TAB");
        unit.setName("Tablet");
        Unit createdUnit = units.create(unit);

        DrugGroup group = new DrugGroup();
        group.setCode("GRP-2");
        group.setName("Group");
        long groupId = new DrugGroupRepository(database).create(group).getId();
        Manufacturer manufacturer = new Manufacturer();
        manufacturer.setName("Manufacturer");
        long manufacturerId = new ManufacturerRepository(database).create(manufacturer).getId();
        new DrugRepository(database).create(
                baseDrug(groupId, manufacturerId, createdUnit.getId(), "D-2", "Drug", "A", null));

        try {
            units.delete(createdUnit.getId());
            fail("A unit referenced by a drug must not be deleted");
        } catch (ConflictException expected) {
            assertTrue(expected.getMessage().contains("used"));
        }
        assertTrue(units.findById(createdUnit.getId()).isPresent());
    }

    @Test
    public void customerSearchUsesNameAndExactPhoneAndProtectsHistory() {
        CustomerRepository customers = new CustomerRepository(database);
        Customer customer = new Customer();
        customer.setCode("C-1");
        customer.setName("Nguyen An");
        customer.setPhone("0900000001");
        customer.setAllergies("Penicillin");
        customers.create(customer);
        assertEquals("Penicillin", customers.findById(customer.getId()).get().getAllergies());

        assertEquals(1, customers.count("Nguyen", null));
        assertEquals(1, customers.search(null, "0900000001", 1, 20).size());
        assertTrue(customers.search(null, "090000000", 1, 20).isEmpty());

        execute("INSERT INTO sales.Invoices(CustomerId) SELECT CustomerId FROM catalog.Customers WHERE CustomerCode='C-1'");
        try {
            customers.delete(customer.getId());
            fail("Customer with sales history must be retained");
        } catch (ConflictException expected) {
            assertTrue(expected.getMessage().contains("sales history"));
        }
    }

    @Test
    public void drugSearchMatchesNameIngredientBarcodeAndClassifiesFlags() {
        addManufacturerAndUnit();
        DrugGroup group = new DrugGroup();
        group.setCode("GRP-3");
        group.setName("Analgesics");
        long groupId = new DrugGroupRepository(database).create(group).getId();

        DrugRepository drugs = new DrugRepository(database);
        drugs.create(baseDrug(groupId, 1, 1, "D-3", "Paracetamol", "Acetaminophen", "8930001"));
        Drug prescription = baseDrug(groupId, 1, 1, "D-4", "Amoxicillin", "Amoxicillin", "8930002");
        prescription.setPrescriptionRequired(true);
        drugs.create(prescription);

        assertEquals(1, drugs.search("Para", null, null, 1, 20).size());
        assertEquals(1, drugs.search("Acetaminophen", null, null, 1, 20).size());
        assertEquals("D-3", drugs.findByBarcode("8930001").get().getCode());
        assertEquals(1, drugs.search(null, groupId, "RX", 1, 20).size());
        assertEquals(1, drugs.search(null, null, "OTC", 1, 20).size());

        Drug target = drugs.findByCode("D-3").get();
        assertTrue(drugs.discontinue(target.getId()));
        assertTrue(drugs.search(null, null, null, 1, 20).stream()
                .noneMatch(value -> "D-3".equals(value.getCode())));
    }

    @Test
    public void invalidDrugTypesAreRejectedBeforeQuerying() {
        try {
            new DrugRepository(database).search(null, null, "UNKNOWN", 1, 20);
            fail("Unsupported type must fail instead of returning a misleading empty result");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("type must be"));
        }
    }

    private void addManufacturerAndUnit() {
        Manufacturer manufacturer = new Manufacturer();
        manufacturer.setName("Test manufacturer");
        new ManufacturerRepository(database).create(manufacturer);
        Unit unit = new Unit();
        unit.setCode("EA");
        unit.setName("Each");
        new UnitRepository(database).create(unit);
    }

    private Drug baseDrug(long groupId, long manufacturerId, long unitId, String code,
                          String name, String activeIngredient, String barcode) {
        Drug drug = new Drug();
        drug.setCode(code);
        drug.setName(name);
        drug.setActiveIngredient(activeIngredient);
        drug.setBarcode(barcode);
        drug.setGroupId(groupId);
        drug.setManufacturerId(manufacturerId);
        drug.setPrimaryUnitId(unitId);
        return drug;
    }
}
