/* Additive fields used by Catalog BE-09 through BE-17.
   Apply after 01_create_tables.sql and 02_seed_data.sql.
   This script is idempotent and does not modify the shared base schema. */
USE PharmacyManagementSOA;
GO

/* Branch licensing information. */
IF COL_LENGTH(N'catalog.Branches', N'LicenseNumber') IS NULL
    ALTER TABLE catalog.Branches ADD LicenseNumber NVARCHAR(100) NULL;
IF COL_LENGTH(N'catalog.Branches', N'LicenseExpiry') IS NULL
    ALTER TABLE catalog.Branches ADD LicenseExpiry DATE NULL;
GO

/* Drug-group hierarchy and stable codes required by the Catalog screens. */
IF COL_LENGTH(N'catalog.DrugGroups', N'GroupCode') IS NULL
    ALTER TABLE catalog.DrugGroups ADD GroupCode VARCHAR(30) NULL;
IF COL_LENGTH(N'catalog.DrugGroups', N'ParentDrugGroupId') IS NULL
    ALTER TABLE catalog.DrugGroups ADD ParentDrugGroupId INT NULL;
IF COL_LENGTH(N'catalog.DrugGroups', N'Level') IS NULL
    ALTER TABLE catalog.DrugGroups ADD Level INT NOT NULL
        CONSTRAINT DF_CatalogDrugGroups_Level DEFAULT 0;
IF COL_LENGTH(N'catalog.DrugGroups', N'Path') IS NULL
    ALTER TABLE catalog.DrugGroups ADD Path NVARCHAR(500) NULL;
IF COL_LENGTH(N'catalog.DrugGroups', N'DisplayOrder') IS NULL
    ALTER TABLE catalog.DrugGroups ADD DisplayOrder INT NOT NULL
        CONSTRAINT DF_CatalogDrugGroups_DisplayOrder DEFAULT 0;
GO

UPDATE catalog.DrugGroups
SET GroupCode = CONCAT('GRP', RIGHT(CONCAT('000000', DrugGroupId), 6))
WHERE GroupCode IS NULL;
UPDATE catalog.DrugGroups
SET Path = CONCAT('/', DrugGroupId, '/')
WHERE Path IS NULL AND ParentDrugGroupId IS NULL;
IF EXISTS (SELECT 1 FROM catalog.DrugGroups WHERE GroupCode IS NULL)
    THROW 51002, 'Unable to assign a code to every drug group.', 1;
ALTER TABLE catalog.DrugGroups ALTER COLUMN GroupCode VARCHAR(30) NOT NULL;

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID(N'catalog.DrugGroups') AND name = N'UX_CatalogDrugGroups_GroupCode'
)
    CREATE UNIQUE INDEX UX_CatalogDrugGroups_GroupCode ON catalog.DrugGroups(GroupCode);
IF NOT EXISTS (
    SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_CatalogDrugGroups_Parent'
)
    ALTER TABLE catalog.DrugGroups ADD CONSTRAINT FK_CatalogDrugGroups_Parent
        FOREIGN KEY (ParentDrugGroupId) REFERENCES catalog.DrugGroups(DrugGroupId);
GO

/* Units need lifecycle and optional display text; referenced units are retained. */
IF COL_LENGTH(N'catalog.Units', N'Description') IS NULL
    ALTER TABLE catalog.Units ADD Description NVARCHAR(300) NULL;
IF COL_LENGTH(N'catalog.Units', N'IsActive') IS NULL
    ALTER TABLE catalog.Units ADD IsActive BIT NOT NULL
        CONSTRAINT DF_CatalogUnits_IsActive DEFAULT 1;
GO

/* Drug attributes not represented in the shared base schema. */
IF COL_LENGTH(N'catalog.Drugs', N'Concentration') IS NULL
    ALTER TABLE catalog.Drugs ADD Concentration NVARCHAR(100) NULL;
IF COL_LENGTH(N'catalog.Drugs', N'DosageForm') IS NULL
    ALTER TABLE catalog.Drugs ADD DosageForm NVARCHAR(100) NULL;
IF COL_LENGTH(N'catalog.Drugs', N'StorageCondition') IS NULL
    ALTER TABLE catalog.Drugs ADD StorageCondition NVARCHAR(500) NULL;
IF COL_LENGTH(N'catalog.Drugs', N'DrugType') IS NULL
    ALTER TABLE catalog.Drugs ADD DrugType VARCHAR(20) NOT NULL
        CONSTRAINT DF_CatalogDrugs_DrugType DEFAULT 'OTC';
GO
UPDATE catalog.Drugs
SET DrugType = CASE
    WHEN ControlledDrug = 1 THEN 'CONTROLLED'
    WHEN PrescriptionRequired = 1 THEN 'RX'
    ELSE 'OTC'
END;
GO

/* Per-drug unit prices are stored alongside the existing conversion factor. */
IF COL_LENGTH(N'catalog.DrugUnitConversions', N'SalePrice') IS NULL
    ALTER TABLE catalog.DrugUnitConversions ADD SalePrice DECIMAL(18,2) NULL;
IF COL_LENGTH(N'catalog.DrugUnitConversions', N'IsDefault') IS NULL
    ALTER TABLE catalog.DrugUnitConversions ADD IsDefault BIT NOT NULL
        CONSTRAINT DF_CatalogConversions_IsDefault DEFAULT 0;
GO

/* Supplier contact and payment terms used by purchase/catalog screens. */
IF COL_LENGTH(N'catalog.Suppliers', N'ContactPerson') IS NULL
    ALTER TABLE catalog.Suppliers ADD ContactPerson NVARCHAR(100) NULL;
IF COL_LENGTH(N'catalog.Suppliers', N'ContactPhone') IS NULL
    ALTER TABLE catalog.Suppliers ADD ContactPhone NVARCHAR(30) NULL;
IF COL_LENGTH(N'catalog.Suppliers', N'PaymentTermDays') IS NULL
    ALTER TABLE catalog.Suppliers ADD PaymentTermDays INT NOT NULL
        CONSTRAINT DF_CatalogSuppliers_PaymentTermDays DEFAULT 0;
GO

/* Customer health profile and soft-delete support. */
IF COL_LENGTH(N'catalog.Customers', N'Allergies') IS NULL
    ALTER TABLE catalog.Customers ADD Allergies NVARCHAR(1000) NULL;
IF COL_LENGTH(N'catalog.Customers', N'IsActive') IS NULL
    ALTER TABLE catalog.Customers ADD IsActive BIT NOT NULL
        CONSTRAINT DF_CatalogCustomers_IsActive DEFAULT 1;
GO

/* Enforce one customer per supplied phone number after detecting bad legacy rows. */
IF EXISTS (
    SELECT Phone FROM catalog.Customers
    WHERE Phone IS NOT NULL
    GROUP BY Phone
    HAVING COUNT(*) > 1
)
    THROW 51001, 'Duplicate customer phone numbers must be resolved before Catalog migration.', 1;
IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID(N'catalog.Customers') AND name = N'UX_CatalogCustomers_Phone'
)
    CREATE UNIQUE INDEX UX_CatalogCustomers_Phone
        ON catalog.Customers(Phone) WHERE Phone IS NOT NULL;
GO
