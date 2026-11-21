/* =========================================================
   PHARMACY MANAGEMENT SOA - SQL SERVER
   01_create_tables.sql
   Database: PharmacyManagementSOA
   Logical ownership: auth, catalog, purchase, inventory, sales
   Note: A single SQL Server database is used for the student
   project; each service owns its schema and tables.
   ========================================================= */

IF DB_ID(N'PharmacyManagementSOA') IS NULL
    CREATE DATABASE PharmacyManagementSOA;
GO
USE PharmacyManagementSOA;
GO

IF NOT EXISTS (SELECT 1 FROM sys.schemas WHERE name = N'auth') EXEC(N'CREATE SCHEMA auth');
IF NOT EXISTS (SELECT 1 FROM sys.schemas WHERE name = N'catalog') EXEC(N'CREATE SCHEMA catalog');
IF NOT EXISTS (SELECT 1 FROM sys.schemas WHERE name = N'purchase') EXEC(N'CREATE SCHEMA purchase');
IF NOT EXISTS (SELECT 1 FROM sys.schemas WHERE name = N'inventory') EXEC(N'CREATE SCHEMA inventory');
IF NOT EXISTS (SELECT 1 FROM sys.schemas WHERE name = N'sales') EXEC(N'CREATE SCHEMA sales');
GO

/* ============================== AUTH SERVICE ============================== */
CREATE TABLE auth.Roles (
    RoleId INT IDENTITY(1,1) PRIMARY KEY,
    RoleCode VARCHAR(20) NOT NULL UNIQUE,
    RoleName NVARCHAR(100) NOT NULL,
    IsActive BIT NOT NULL CONSTRAINT DF_Roles_IsActive DEFAULT 1
);

CREATE TABLE auth.Permissions (
    PermissionId INT IDENTITY(1,1) PRIMARY KEY,
    PermissionCode VARCHAR(60) NOT NULL UNIQUE,
    PermissionName NVARCHAR(150) NOT NULL
);

CREATE TABLE auth.RolePermissions (
    RoleId INT NOT NULL,
    PermissionId INT NOT NULL,
    CONSTRAINT PK_RolePermissions PRIMARY KEY (RoleId, PermissionId),
    CONSTRAINT FK_RolePermissions_Roles FOREIGN KEY (RoleId) REFERENCES auth.Roles(RoleId),
    CONSTRAINT FK_RolePermissions_Permissions FOREIGN KEY (PermissionId) REFERENCES auth.Permissions(PermissionId)
);

CREATE TABLE auth.Users (
    UserId INT IDENTITY(1,1) PRIMARY KEY,
    RoleId INT NOT NULL,
    Username VARCHAR(50) NOT NULL UNIQUE,
    PasswordHash VARCHAR(255) NOT NULL,
    FullName NVARCHAR(150) NOT NULL,
    Phone VARCHAR(20) NULL,
    Email VARCHAR(150) NULL,
    IsLocked BIT NOT NULL CONSTRAINT DF_Users_IsLocked DEFAULT 0,
    FailedLoginCount INT NOT NULL CONSTRAINT DF_Users_FailedLogin DEFAULT 0,
    IsActive BIT NOT NULL CONSTRAINT DF_Users_IsActive DEFAULT 1,
    CreatedAt DATETIME2(0) NOT NULL CONSTRAINT DF_Users_CreatedAt DEFAULT SYSDATETIME(),
    UpdatedAt DATETIME2(0) NULL,
    CONSTRAINT FK_Users_Roles FOREIGN KEY (RoleId) REFERENCES auth.Roles(RoleId),
    CONSTRAINT CK_Users_FailedLogin CHECK (FailedLoginCount >= 0)
);

CREATE TABLE auth.UserBranches (
    UserId INT NOT NULL,
    BranchId INT NOT NULL,
    CONSTRAINT PK_UserBranches PRIMARY KEY (UserId, BranchId),
    CONSTRAINT FK_UserBranches_Users FOREIGN KEY (UserId) REFERENCES auth.Users(UserId)
    /* BranchId is resolved by Catalog Service through API; no cross-service FK. */
);

CREATE TABLE auth.RefreshTokens (
    RefreshTokenId BIGINT IDENTITY(1,1) PRIMARY KEY,
    UserId INT NOT NULL,
    TokenHash VARCHAR(255) NOT NULL UNIQUE,
    ExpiresAt DATETIME2(0) NOT NULL,
    RevokedAt DATETIME2(0) NULL,
    CreatedAt DATETIME2(0) NOT NULL CONSTRAINT DF_RefreshTokens_CreatedAt DEFAULT SYSDATETIME(),
    CONSTRAINT FK_RefreshTokens_Users FOREIGN KEY (UserId) REFERENCES auth.Users(UserId)
);

CREATE TABLE auth.AuditLogs (
    AuditLogId BIGINT IDENTITY(1,1) PRIMARY KEY,
    UserId INT NULL,
    BranchId INT NULL,
    ActionCode VARCHAR(80) NOT NULL,
    EntityName VARCHAR(80) NULL,
    EntityId BIGINT NULL,
    Details NVARCHAR(1000) NULL,
    CreatedAt DATETIME2(0) NOT NULL CONSTRAINT DF_AuditLogs_CreatedAt DEFAULT SYSDATETIME(),
    CONSTRAINT FK_AuditLogs_Users FOREIGN KEY (UserId) REFERENCES auth.Users(UserId)
);

/* ============================= CATALOG SERVICE ============================ */
CREATE TABLE catalog.Branches (
    BranchId INT IDENTITY(1,1) PRIMARY KEY,
    BranchCode VARCHAR(20) NOT NULL UNIQUE,
    BranchName NVARCHAR(150) NOT NULL,
    Address NVARCHAR(250) NULL,
    Phone VARCHAR(20) NULL,
    BranchType VARCHAR(20) NOT NULL, -- CENTRAL_WAREHOUSE / PHARMACY
    IsActive BIT NOT NULL CONSTRAINT DF_Branches_IsActive DEFAULT 1,
    CreatedAt DATETIME2(0) NOT NULL CONSTRAINT DF_Branches_CreatedAt DEFAULT SYSDATETIME(),
    CONSTRAINT CK_Branches_Type CHECK (BranchType IN ('CENTRAL_WAREHOUSE','PHARMACY'))
);

CREATE TABLE catalog.DrugGroups (
    DrugGroupId INT IDENTITY(1,1) PRIMARY KEY,
    GroupName NVARCHAR(120) NOT NULL UNIQUE,
    Description NVARCHAR(300) NULL,
    IsActive BIT NOT NULL CONSTRAINT DF_DrugGroups_IsActive DEFAULT 1
);

CREATE TABLE catalog.Manufacturers (
    ManufacturerId INT IDENTITY(1,1) PRIMARY KEY,
    ManufacturerName NVARCHAR(150) NOT NULL UNIQUE,
    Country NVARCHAR(80) NULL,
    IsActive BIT NOT NULL CONSTRAINT DF_Manufacturers_IsActive DEFAULT 1
);

CREATE TABLE catalog.Units (
    UnitId INT IDENTITY(1,1) PRIMARY KEY,
    UnitName NVARCHAR(50) NOT NULL UNIQUE,
    UnitCode VARCHAR(20) NOT NULL UNIQUE
);

CREATE TABLE catalog.Drugs (
    DrugId INT IDENTITY(1,1) PRIMARY KEY,
    DrugCode VARCHAR(30) NOT NULL UNIQUE,
    Barcode VARCHAR(50) NULL,
    DrugName NVARCHAR(200) NOT NULL,
    ActiveIngredient NVARCHAR(200) NULL,
    DrugGroupId INT NOT NULL,
    ManufacturerId INT NOT NULL,
    BaseUnitId INT NOT NULL,
    PrescriptionRequired BIT NOT NULL CONSTRAINT DF_Drugs_Prescription DEFAULT 0,
    ControlledDrug BIT NOT NULL CONSTRAINT DF_Drugs_Controlled DEFAULT 0,
    RegistrationNumber VARCHAR(60) NULL,
    Description NVARCHAR(500) NULL,
    IsActive BIT NOT NULL CONSTRAINT DF_Drugs_IsActive DEFAULT 1,
    CreatedAt DATETIME2(0) NOT NULL CONSTRAINT DF_Drugs_CreatedAt DEFAULT SYSDATETIME(),
    CONSTRAINT FK_Drugs_Groups FOREIGN KEY (DrugGroupId) REFERENCES catalog.DrugGroups(DrugGroupId),
    CONSTRAINT FK_Drugs_Manufacturers FOREIGN KEY (ManufacturerId) REFERENCES catalog.Manufacturers(ManufacturerId),
    CONSTRAINT FK_Drugs_BaseUnits FOREIGN KEY (BaseUnitId) REFERENCES catalog.Units(UnitId)
);
CREATE UNIQUE INDEX UX_Drugs_Barcode ON catalog.Drugs(Barcode) WHERE Barcode IS NOT NULL;

CREATE TABLE catalog.DrugUnitConversions (
    ConversionId INT IDENTITY(1,1) PRIMARY KEY,
    DrugId INT NOT NULL,
    UnitId INT NOT NULL,
    ConversionToBase DECIMAL(18,4) NOT NULL,
    IsSaleUnit BIT NOT NULL CONSTRAINT DF_Conversions_IsSaleUnit DEFAULT 1,
    CONSTRAINT FK_Conversions_Drugs FOREIGN KEY (DrugId) REFERENCES catalog.Drugs(DrugId),
    CONSTRAINT FK_Conversions_Units FOREIGN KEY (UnitId) REFERENCES catalog.Units(UnitId),
    CONSTRAINT UQ_Conversions_Drug_Unit UNIQUE (DrugId, UnitId),
    CONSTRAINT CK_Conversions_Factor CHECK (ConversionToBase > 0)
);

CREATE TABLE catalog.Suppliers (
    SupplierId INT IDENTITY(1,1) PRIMARY KEY,
    SupplierCode VARCHAR(30) NOT NULL UNIQUE,
    SupplierName NVARCHAR(180) NOT NULL,
    Phone VARCHAR(20) NULL,
    Email VARCHAR(150) NULL,
    Address NVARCHAR(250) NULL,
    TaxCode VARCHAR(30) NULL,
    IsActive BIT NOT NULL CONSTRAINT DF_Suppliers_IsActive DEFAULT 1
);

CREATE TABLE catalog.Customers (
    CustomerId INT IDENTITY(1,1) PRIMARY KEY,
    CustomerCode VARCHAR(30) NOT NULL UNIQUE,
    CustomerName NVARCHAR(150) NOT NULL,
    Phone VARCHAR(20) NULL,
    Email VARCHAR(150) NULL,
    LoyaltyPoints INT NOT NULL CONSTRAINT DF_Customers_Points DEFAULT 0,
    CreatedAt DATETIME2(0) NOT NULL CONSTRAINT DF_Customers_CreatedAt DEFAULT SYSDATETIME(),
    CONSTRAINT CK_Customers_Points CHECK (LoyaltyPoints >= 0)
);

CREATE TABLE catalog.BranchDrugSettings (
    BranchId INT NOT NULL,
    DrugId INT NOT NULL,
    MinimumStock DECIMAL(18,2) NOT NULL CONSTRAINT DF_BranchDrug_Min DEFAULT 0,
    SalePrice DECIMAL(18,2) NOT NULL,
    IsAvailable BIT NOT NULL CONSTRAINT DF_BranchDrug_Available DEFAULT 1,
    CONSTRAINT PK_BranchDrugSettings PRIMARY KEY (BranchId, DrugId),
    CONSTRAINT FK_BranchDrug_Drugs FOREIGN KEY (DrugId) REFERENCES catalog.Drugs(DrugId),
    CONSTRAINT CK_BranchDrug_Min CHECK (MinimumStock >= 0),
    CONSTRAINT CK_BranchDrug_Price CHECK (SalePrice >= 0)
    /* BranchId belongs to Catalog itself; checked by service/application. */
);

/* ============================ PURCHASE SERVICE ============================ */
CREATE TABLE purchase.PurchaseOrders (
    PurchaseOrderId BIGINT IDENTITY(1,1) PRIMARY KEY,
    OrderCode VARCHAR(30) NOT NULL UNIQUE,
    SupplierId INT NOT NULL,
    BranchId INT NOT NULL,
    CreatedBy INT NOT NULL,
    OrderDate DATETIME2(0) NOT NULL CONSTRAINT DF_PurchaseOrders_Date DEFAULT SYSDATETIME(),
    Status VARCHAR(20) NOT NULL CONSTRAINT DF_PurchaseOrders_Status DEFAULT 'DRAFT',
    Note NVARCHAR(500) NULL,
    CONSTRAINT FK_PurchaseOrders_Users FOREIGN KEY (CreatedBy) REFERENCES auth.Users(UserId),
    CONSTRAINT CK_PurchaseOrders_Status CHECK (Status IN ('DRAFT','SUBMITTED','APPROVED','CANCELLED'))
);

CREATE TABLE purchase.PurchaseOrderDetails (
    PurchaseOrderDetailId BIGINT IDENTITY(1,1) PRIMARY KEY,
    PurchaseOrderId BIGINT NOT NULL,
    DrugId INT NOT NULL,
    QuantityBase DECIMAL(18,2) NOT NULL,
    ExpectedUnitCost DECIMAL(18,2) NOT NULL,
    CONSTRAINT FK_POD_Order FOREIGN KEY (PurchaseOrderId) REFERENCES purchase.PurchaseOrders(PurchaseOrderId),
    CONSTRAINT CK_POD_Quantity CHECK (QuantityBase > 0),
    CONSTRAINT CK_POD_Cost CHECK (ExpectedUnitCost >= 0)
);

CREATE TABLE purchase.PurchaseReceipts (
    PurchaseReceiptId BIGINT IDENTITY(1,1) PRIMARY KEY,
    ReceiptCode VARCHAR(30) NOT NULL UNIQUE,
    PurchaseOrderId BIGINT NULL,
    SupplierId INT NOT NULL,
    BranchId INT NOT NULL,
    CreatedBy INT NOT NULL,
    ApprovedBy INT NULL,
    ReceiptDate DATETIME2(0) NOT NULL CONSTRAINT DF_PurchaseReceipts_Date DEFAULT SYSDATETIME(),
    Status VARCHAR(20) NOT NULL CONSTRAINT DF_PurchaseReceipts_Status DEFAULT 'DRAFT',
    TotalAmount DECIMAL(18,2) NOT NULL CONSTRAINT DF_PurchaseReceipts_Total DEFAULT 0,
    Note NVARCHAR(500) NULL,
    CONSTRAINT FK_Receipts_Order FOREIGN KEY (PurchaseOrderId) REFERENCES purchase.PurchaseOrders(PurchaseOrderId),
    CONSTRAINT FK_Receipts_CreatedBy FOREIGN KEY (CreatedBy) REFERENCES auth.Users(UserId),
    CONSTRAINT FK_Receipts_ApprovedBy FOREIGN KEY (ApprovedBy) REFERENCES auth.Users(UserId),
    CONSTRAINT CK_Receipts_Status CHECK (Status IN ('DRAFT','SUBMITTED','APPROVED','CANCELLED')),
    CONSTRAINT CK_Receipts_Total CHECK (TotalAmount >= 0)
);

CREATE TABLE purchase.PurchaseReceiptDetails (
    PurchaseReceiptDetailId BIGINT IDENTITY(1,1) PRIMARY KEY,
    PurchaseReceiptId BIGINT NOT NULL,
    DrugId INT NOT NULL,
    LotNumber VARCHAR(60) NOT NULL,
    ExpiryDate DATE NOT NULL,
    ManufactureDate DATE NULL,
    QuantityBase DECIMAL(18,2) NOT NULL,
    UnitCostBase DECIMAL(18,2) NOT NULL,
    CONSTRAINT FK_PRD_Receipt FOREIGN KEY (PurchaseReceiptId) REFERENCES purchase.PurchaseReceipts(PurchaseReceiptId),
    CONSTRAINT CK_PRD_Quantity CHECK (QuantityBase > 0),
    CONSTRAINT CK_PRD_Cost CHECK (UnitCostBase >= 0),
    CONSTRAINT CK_PRD_Expiry CHECK (ManufactureDate IS NULL OR ExpiryDate > ManufactureDate)
);

CREATE TABLE purchase.SupplierReturns (
    SupplierReturnId BIGINT IDENTITY(1,1) PRIMARY KEY,
    ReturnCode VARCHAR(30) NOT NULL UNIQUE,
    SupplierId INT NOT NULL,
    BranchId INT NOT NULL,
    CreatedBy INT NOT NULL,
    ReturnDate DATETIME2(0) NOT NULL CONSTRAINT DF_SupplierReturns_Date DEFAULT SYSDATETIME(),
    Status VARCHAR(20) NOT NULL CONSTRAINT DF_SupplierReturns_Status DEFAULT 'DRAFT',
    Reason NVARCHAR(300) NULL,
    CONSTRAINT FK_SupplierReturns_Users FOREIGN KEY (CreatedBy) REFERENCES auth.Users(UserId),
    CONSTRAINT CK_SupplierReturns_Status CHECK (Status IN ('DRAFT','APPROVED','CANCELLED'))
);

CREATE TABLE purchase.SupplierReturnDetails (
    SupplierReturnDetailId BIGINT IDENTITY(1,1) PRIMARY KEY,
    SupplierReturnId BIGINT NOT NULL,
    LotId BIGINT NOT NULL,
    QuantityBase DECIMAL(18,2) NOT NULL,
    UnitCostBase DECIMAL(18,2) NOT NULL,
    CONSTRAINT FK_SupplierReturnDetails_Return FOREIGN KEY (SupplierReturnId) REFERENCES purchase.SupplierReturns(SupplierReturnId),
    CONSTRAINT CK_SupplierReturnDetails_Qty CHECK (QuantityBase > 0),
    CONSTRAINT CK_SupplierReturnDetails_Cost CHECK (UnitCostBase >= 0)
);

CREATE TABLE purchase.SupplierPayments (
    SupplierPaymentId BIGINT IDENTITY(1,1) PRIMARY KEY,
    PaymentCode VARCHAR(30) NOT NULL UNIQUE,
    SupplierId INT NOT NULL,
    BranchId INT NULL,
    PaidBy INT NOT NULL,
    PaymentDate DATETIME2(0) NOT NULL CONSTRAINT DF_SupplierPayments_Date DEFAULT SYSDATETIME(),
    Amount DECIMAL(18,2) NOT NULL,
    PaymentMethod VARCHAR(20) NOT NULL,
    Note NVARCHAR(300) NULL,
    CONSTRAINT FK_SupplierPayments_Users FOREIGN KEY (PaidBy) REFERENCES auth.Users(UserId),
    CONSTRAINT CK_SupplierPayments_Amount CHECK (Amount > 0),
    CONSTRAINT CK_SupplierPayments_Method CHECK (PaymentMethod IN ('CASH','BANK_TRANSFER'))
);

/* ============================ INVENTORY SERVICE ============================ */
CREATE TABLE inventory.DrugLots (
    LotId BIGINT IDENTITY(1,1) PRIMARY KEY,
    BranchId INT NOT NULL,
    DrugId INT NOT NULL,
    PurchaseReceiptDetailId BIGINT NULL,
    LotNumber VARCHAR(60) NOT NULL,
    ManufactureDate DATE NULL,
    ExpiryDate DATE NOT NULL,
    UnitCostBase DECIMAL(18,2) NOT NULL,
    ReceivedAt DATETIME2(0) NOT NULL CONSTRAINT DF_DrugLots_ReceivedAt DEFAULT SYSDATETIME(),
    IsBlocked BIT NOT NULL CONSTRAINT DF_DrugLots_Blocked DEFAULT 0,
    CONSTRAINT FK_DrugLots_ReceiptDetail FOREIGN KEY (PurchaseReceiptDetailId) REFERENCES purchase.PurchaseReceiptDetails(PurchaseReceiptDetailId),
    CONSTRAINT UQ_DrugLots_Branch_Drug_Lot UNIQUE (BranchId, DrugId, LotNumber),
    CONSTRAINT CK_DrugLots_Cost CHECK (UnitCostBase >= 0),
    CONSTRAINT CK_DrugLots_Expiry CHECK (ManufactureDate IS NULL OR ExpiryDate > ManufactureDate)
);

CREATE TABLE inventory.StockByLot (
    LotId BIGINT PRIMARY KEY,
    QuantityBase DECIMAL(18,2) NOT NULL CONSTRAINT DF_StockByLot_Qty DEFAULT 0,
    UpdatedAt DATETIME2(0) NOT NULL CONSTRAINT DF_StockByLot_Updated DEFAULT SYSDATETIME(),
    CONSTRAINT FK_StockByLot_Lots FOREIGN KEY (LotId) REFERENCES inventory.DrugLots(LotId),
    CONSTRAINT CK_StockByLot_Qty CHECK (QuantityBase >= 0)
);

CREATE TABLE inventory.StockLedger (
    StockLedgerId BIGINT IDENTITY(1,1) PRIMARY KEY,
    BranchId INT NOT NULL,
    DrugId INT NOT NULL,
    LotId BIGINT NOT NULL,
    TransactionType VARCHAR(25) NOT NULL,
    ReferenceType VARCHAR(25) NOT NULL,
    ReferenceId BIGINT NOT NULL,
    QuantityChange DECIMAL(18,2) NOT NULL,
    UnitCostBase DECIMAL(18,2) NULL,
    CreatedBy INT NULL,
    CreatedAt DATETIME2(0) NOT NULL CONSTRAINT DF_StockLedger_Created DEFAULT SYSDATETIME(),
    Note NVARCHAR(300) NULL,
    CONSTRAINT FK_StockLedger_Lots FOREIGN KEY (LotId) REFERENCES inventory.DrugLots(LotId),
    CONSTRAINT FK_StockLedger_Users FOREIGN KEY (CreatedBy) REFERENCES auth.Users(UserId),
    CONSTRAINT CK_StockLedger_Type CHECK (TransactionType IN ('PURCHASE_IN','SALE_OUT','SALE_RETURN','SUPPLIER_RETURN','TRANSFER_OUT','TRANSFER_IN','STOCKTAKE','DISPOSAL')),
    CONSTRAINT CK_StockLedger_Quantity CHECK (QuantityChange <> 0)
);
CREATE INDEX IX_StockLedger_Branch_Drug_Date ON inventory.StockLedger(BranchId, DrugId, CreatedAt);

CREATE TABLE inventory.StockTransfers (
    TransferId BIGINT IDENTITY(1,1) PRIMARY KEY,
    TransferCode VARCHAR(30) NOT NULL UNIQUE,
    FromBranchId INT NOT NULL,
    ToBranchId INT NOT NULL,
    CreatedBy INT NOT NULL,
    ApprovedBy INT NULL,
    SentAt DATETIME2(0) NULL,
    ReceivedAt DATETIME2(0) NULL,
    Status VARCHAR(20) NOT NULL CONSTRAINT DF_StockTransfers_Status DEFAULT 'DRAFT',
    Note NVARCHAR(300) NULL,
    CONSTRAINT FK_StockTransfers_CreatedBy FOREIGN KEY (CreatedBy) REFERENCES auth.Users(UserId),
    CONSTRAINT FK_StockTransfers_ApprovedBy FOREIGN KEY (ApprovedBy) REFERENCES auth.Users(UserId),
    CONSTRAINT CK_StockTransfers_Branches CHECK (FromBranchId <> ToBranchId),
    CONSTRAINT CK_StockTransfers_Status CHECK (Status IN ('DRAFT','APPROVED','IN_TRANSIT','RECEIVED','CANCELLED'))
);

CREATE TABLE inventory.StockTransferDetails (
    TransferDetailId BIGINT IDENTITY(1,1) PRIMARY KEY,
    TransferId BIGINT NOT NULL,
    FromLotId BIGINT NOT NULL,
    QuantitySent DECIMAL(18,2) NOT NULL,
    QuantityReceived DECIMAL(18,2) NULL,
    ToLotId BIGINT NULL,
    CONSTRAINT FK_TransferDetails_Transfer FOREIGN KEY (TransferId) REFERENCES inventory.StockTransfers(TransferId),
    CONSTRAINT FK_TransferDetails_FromLot FOREIGN KEY (FromLotId) REFERENCES inventory.DrugLots(LotId),
    CONSTRAINT FK_TransferDetails_ToLot FOREIGN KEY (ToLotId) REFERENCES inventory.DrugLots(LotId),
    CONSTRAINT CK_TransferDetails_Sent CHECK (QuantitySent > 0),
    CONSTRAINT CK_TransferDetails_Received CHECK (QuantityReceived IS NULL OR (QuantityReceived >= 0 AND QuantityReceived <= QuantitySent))
);

CREATE TABLE inventory.Stocktakes (
    StocktakeId BIGINT IDENTITY(1,1) PRIMARY KEY,
    StocktakeCode VARCHAR(30) NOT NULL UNIQUE,
    BranchId INT NOT NULL,
    CreatedBy INT NOT NULL,
    ApprovedBy INT NULL,
    StartedAt DATETIME2(0) NOT NULL CONSTRAINT DF_Stocktakes_Started DEFAULT SYSDATETIME(),
    CompletedAt DATETIME2(0) NULL,
    Status VARCHAR(20) NOT NULL CONSTRAINT DF_Stocktakes_Status DEFAULT 'DRAFT',
    CONSTRAINT FK_Stocktakes_CreatedBy FOREIGN KEY (CreatedBy) REFERENCES auth.Users(UserId),
    CONSTRAINT FK_Stocktakes_ApprovedBy FOREIGN KEY (ApprovedBy) REFERENCES auth.Users(UserId),
    CONSTRAINT CK_Stocktakes_Status CHECK (Status IN ('DRAFT','COMPLETED','APPROVED','CANCELLED'))
);

CREATE TABLE inventory.StocktakeDetails (
    StocktakeDetailId BIGINT IDENTITY(1,1) PRIMARY KEY,
    StocktakeId BIGINT NOT NULL,
    LotId BIGINT NOT NULL,
    SystemQuantity DECIMAL(18,2) NOT NULL,
    ActualQuantity DECIMAL(18,2) NULL,
    CONSTRAINT FK_StocktakeDetails_Stocktake FOREIGN KEY (StocktakeId) REFERENCES inventory.Stocktakes(StocktakeId),
    CONSTRAINT FK_StocktakeDetails_Lot FOREIGN KEY (LotId) REFERENCES inventory.DrugLots(LotId),
    CONSTRAINT CK_StocktakeDetails_Qty CHECK (SystemQuantity >= 0 AND (ActualQuantity IS NULL OR ActualQuantity >= 0))
);

CREATE TABLE inventory.Disposals (
    DisposalId BIGINT IDENTITY(1,1) PRIMARY KEY,
    DisposalCode VARCHAR(30) NOT NULL UNIQUE,
    BranchId INT NOT NULL,
    CreatedBy INT NOT NULL,
    ApprovedBy INT NULL,
    DisposalDate DATETIME2(0) NOT NULL CONSTRAINT DF_Disposals_Date DEFAULT SYSDATETIME(),
    Status VARCHAR(20) NOT NULL CONSTRAINT DF_Disposals_Status DEFAULT 'DRAFT',
    Reason NVARCHAR(300) NOT NULL,
    CONSTRAINT FK_Disposals_CreatedBy FOREIGN KEY (CreatedBy) REFERENCES auth.Users(UserId),
    CONSTRAINT FK_Disposals_ApprovedBy FOREIGN KEY (ApprovedBy) REFERENCES auth.Users(UserId),
    CONSTRAINT CK_Disposals_Status CHECK (Status IN ('DRAFT','APPROVED','CANCELLED'))
);

CREATE TABLE inventory.DisposalDetails (
    DisposalDetailId BIGINT IDENTITY(1,1) PRIMARY KEY,
    DisposalId BIGINT NOT NULL,
    LotId BIGINT NOT NULL,
    QuantityBase DECIMAL(18,2) NOT NULL,
    CONSTRAINT FK_DisposalDetails_Disposal FOREIGN KEY (DisposalId) REFERENCES inventory.Disposals(DisposalId),
    CONSTRAINT FK_DisposalDetails_Lot FOREIGN KEY (LotId) REFERENCES inventory.DrugLots(LotId),
    CONSTRAINT CK_DisposalDetails_Qty CHECK (QuantityBase > 0)
);

/* ============================== SALES SERVICE ============================== */
CREATE TABLE sales.Shifts (
    ShiftId BIGINT IDENTITY(1,1) PRIMARY KEY,
    BranchId INT NOT NULL,
    UserId INT NOT NULL,
    OpenedAt DATETIME2(0) NOT NULL CONSTRAINT DF_Shifts_Opened DEFAULT SYSDATETIME(),
    ClosedAt DATETIME2(0) NULL,
    OpeningCash DECIMAL(18,2) NOT NULL CONSTRAINT DF_Shifts_OpeningCash DEFAULT 0,
    ActualClosingCash DECIMAL(18,2) NULL,
    Status VARCHAR(15) NOT NULL CONSTRAINT DF_Shifts_Status DEFAULT 'OPEN',
    CONSTRAINT FK_Shifts_Users FOREIGN KEY (UserId) REFERENCES auth.Users(UserId),
    CONSTRAINT CK_Shifts_Status CHECK (Status IN ('OPEN','CLOSED')),
    CONSTRAINT CK_Shifts_Cash CHECK (OpeningCash >= 0 AND (ActualClosingCash IS NULL OR ActualClosingCash >= 0))
);
CREATE UNIQUE INDEX UX_Shifts_OneOpenPerUserBranch ON sales.Shifts(UserId, BranchId) WHERE Status = 'OPEN';

CREATE TABLE sales.Prescriptions (
    PrescriptionId BIGINT IDENTITY(1,1) PRIMARY KEY,
    PrescriptionCode VARCHAR(40) NOT NULL UNIQUE,
    CustomerId INT NULL,
    PrescriberName NVARCHAR(150) NOT NULL,
    FacilityName NVARCHAR(150) NULL,
    PrescriptionDate DATE NOT NULL,
    Notes NVARCHAR(500) NULL,
    CreatedBy INT NOT NULL,
    CreatedAt DATETIME2(0) NOT NULL CONSTRAINT DF_Prescriptions_Created DEFAULT SYSDATETIME(),
    CONSTRAINT FK_Prescriptions_CreatedBy FOREIGN KEY (CreatedBy) REFERENCES auth.Users(UserId)
);

CREATE TABLE sales.Invoices (
    InvoiceId BIGINT IDENTITY(1,1) PRIMARY KEY,
    InvoiceCode VARCHAR(30) NOT NULL UNIQUE,
    BranchId INT NOT NULL,
    UserId INT NOT NULL,
    ShiftId BIGINT NULL,
    CustomerId INT NULL,
    PrescriptionId BIGINT NULL,
    InvoiceDate DATETIME2(0) NOT NULL CONSTRAINT DF_Invoices_Date DEFAULT SYSDATETIME(),
    Status VARCHAR(20) NOT NULL CONSTRAINT DF_Invoices_Status DEFAULT 'PROCESSING',
    Subtotal DECIMAL(18,2) NOT NULL CONSTRAINT DF_Invoices_Subtotal DEFAULT 0,
    DiscountAmount DECIMAL(18,2) NOT NULL CONSTRAINT DF_Invoices_Discount DEFAULT 0,
    TotalAmount DECIMAL(18,2) NOT NULL CONSTRAINT DF_Invoices_Total DEFAULT 0,
    PaymentMethod VARCHAR(20) NULL,
    Note NVARCHAR(300) NULL,
    CONSTRAINT FK_Invoices_Users FOREIGN KEY (UserId) REFERENCES auth.Users(UserId),
    CONSTRAINT FK_Invoices_Shifts FOREIGN KEY (ShiftId) REFERENCES sales.Shifts(ShiftId),
    CONSTRAINT FK_Invoices_Prescriptions FOREIGN KEY (PrescriptionId) REFERENCES sales.Prescriptions(PrescriptionId),
    CONSTRAINT CK_Invoices_Status CHECK (Status IN ('DRAFT','PROCESSING','COMPLETED','CANCELLED')),
    CONSTRAINT CK_Invoices_Amounts CHECK (Subtotal >= 0 AND DiscountAmount >= 0 AND TotalAmount >= 0 AND DiscountAmount <= Subtotal),
    CONSTRAINT CK_Invoices_Payment CHECK (PaymentMethod IS NULL OR PaymentMethod IN ('CASH','BANK_TRANSFER','CARD'))
);

CREATE TABLE sales.InvoiceDetails (
    InvoiceDetailId BIGINT IDENTITY(1,1) PRIMARY KEY,
    InvoiceId BIGINT NOT NULL,
    DrugId INT NOT NULL,
    UnitId INT NOT NULL,
    Quantity DECIMAL(18,2) NOT NULL,
    QuantityBase DECIMAL(18,2) NOT NULL,
    UnitPrice DECIMAL(18,2) NOT NULL,
    DiscountAmount DECIMAL(18,2) NOT NULL CONSTRAINT DF_InvoiceDetails_Discount DEFAULT 0,
    LineTotal DECIMAL(18,2) NOT NULL,
    CONSTRAINT FK_InvoiceDetails_Invoice FOREIGN KEY (InvoiceId) REFERENCES sales.Invoices(InvoiceId),
    CONSTRAINT FK_InvoiceDetails_Unit FOREIGN KEY (UnitId) REFERENCES catalog.Units(UnitId),
    CONSTRAINT CK_InvoiceDetails_Qty CHECK (Quantity > 0 AND QuantityBase > 0),
    CONSTRAINT CK_InvoiceDetails_Amounts CHECK (UnitPrice >= 0 AND DiscountAmount >= 0 AND LineTotal >= 0)
);

CREATE TABLE sales.InvoiceDetailLots (
    InvoiceDetailLotId BIGINT IDENTITY(1,1) PRIMARY KEY,
    InvoiceDetailId BIGINT NOT NULL,
    LotId BIGINT NOT NULL,
    QuantityBase DECIMAL(18,2) NOT NULL,
    CostBaseAtSale DECIMAL(18,2) NOT NULL,
    CONSTRAINT FK_InvoiceDetailLots_Detail FOREIGN KEY (InvoiceDetailId) REFERENCES sales.InvoiceDetails(InvoiceDetailId),
    CONSTRAINT FK_InvoiceDetailLots_Lot FOREIGN KEY (LotId) REFERENCES inventory.DrugLots(LotId),
    CONSTRAINT CK_InvoiceDetailLots_Qty CHECK (QuantityBase > 0),
    CONSTRAINT CK_InvoiceDetailLots_Cost CHECK (CostBaseAtSale >= 0)
);

CREATE TABLE sales.SalesReturns (
    SalesReturnId BIGINT IDENTITY(1,1) PRIMARY KEY,
    ReturnCode VARCHAR(30) NOT NULL UNIQUE,
    InvoiceId BIGINT NOT NULL,
    CreatedBy INT NOT NULL,
    ReturnDate DATETIME2(0) NOT NULL CONSTRAINT DF_SalesReturns_Date DEFAULT SYSDATETIME(),
    Reason NVARCHAR(300) NOT NULL,
    Status VARCHAR(20) NOT NULL CONSTRAINT DF_SalesReturns_Status DEFAULT 'APPROVED',
    CONSTRAINT FK_SalesReturns_Invoice FOREIGN KEY (InvoiceId) REFERENCES sales.Invoices(InvoiceId),
    CONSTRAINT FK_SalesReturns_User FOREIGN KEY (CreatedBy) REFERENCES auth.Users(UserId),
    CONSTRAINT CK_SalesReturns_Status CHECK (Status IN ('DRAFT','APPROVED','REJECTED'))
);

CREATE TABLE sales.SalesReturnDetails (
    SalesReturnDetailId BIGINT IDENTITY(1,1) PRIMARY KEY,
    SalesReturnId BIGINT NOT NULL,
    InvoiceDetailLotId BIGINT NOT NULL,
    QuantityBase DECIMAL(18,2) NOT NULL,
    RefundAmount DECIMAL(18,2) NOT NULL,
    CONSTRAINT FK_SalesReturnDetails_Return FOREIGN KEY (SalesReturnId) REFERENCES sales.SalesReturns(SalesReturnId),
    CONSTRAINT FK_SalesReturnDetails_InvoiceLot FOREIGN KEY (InvoiceDetailLotId) REFERENCES sales.InvoiceDetailLots(InvoiceDetailLotId),
    CONSTRAINT CK_SalesReturnDetails_Values CHECK (QuantityBase > 0 AND RefundAmount >= 0)
);

CREATE TABLE sales.Promotions (
    PromotionId INT IDENTITY(1,1) PRIMARY KEY,
    PromotionCode VARCHAR(30) NOT NULL UNIQUE,
    PromotionName NVARCHAR(150) NOT NULL,
    DiscountPercent DECIMAL(5,2) NOT NULL,
    StartDate DATE NOT NULL,
    EndDate DATE NOT NULL,
    IsActive BIT NOT NULL CONSTRAINT DF_Promotions_Active DEFAULT 1,
    CONSTRAINT CK_Promotions_Discount CHECK (DiscountPercent BETWEEN 0 AND 100),
    CONSTRAINT CK_Promotions_Dates CHECK (EndDate >= StartDate)
);
GO

/* Common indexes used by lookup/report queries. */
CREATE INDEX IX_Drugs_Name ON catalog.Drugs(DrugName);
CREATE INDEX IX_DrugLots_FEFO ON inventory.DrugLots(BranchId, DrugId, ExpiryDate) INCLUDE (LotNumber, UnitCostBase);
CREATE INDEX IX_Invoices_Branch_Date ON sales.Invoices(BranchId, InvoiceDate, Status);
CREATE INDEX IX_PurchaseReceipts_Branch_Date ON purchase.PurchaseReceipts(BranchId, ReceiptDate, Status);
GO

/* Report Service: read-only views over service-owned data. */
CREATE OR ALTER VIEW sales.vw_InvoiceRevenue
AS
SELECT BranchId, CAST(InvoiceDate AS DATE) AS RevenueDate,
       COUNT_BIG(*) AS InvoiceCount, SUM(TotalAmount) AS Revenue
FROM sales.Invoices
WHERE Status = 'COMPLETED'
GROUP BY BranchId, CAST(InvoiceDate AS DATE);
GO

CREATE OR ALTER VIEW inventory.vw_CurrentStock
AS
SELECT l.BranchId, l.DrugId, SUM(s.QuantityBase) AS QuantityBase
FROM inventory.DrugLots l
JOIN inventory.StockByLot s ON s.LotId = l.LotId
WHERE l.IsBlocked = 0 AND l.ExpiryDate >= CAST(GETDATE() AS DATE)
GROUP BY l.BranchId, l.DrugId;
GO
