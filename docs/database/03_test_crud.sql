/* PHARMACY MANAGEMENT SOA - 03_test_crud.sql
   Run after 01_create_tables.sql and 02_seed_data.sql.
   Each section uses rollback so demo data is not permanently changed.
*/
USE PharmacyManagementSOA;
GO
SET NOCOUNT ON;

/* TEST 1: READ - danh sách thuốc kèm nhóm và nhà sản xuất */
PRINT N'TEST 1 - READ danh sách thuốc';
SELECT d.DrugCode,d.DrugName,d.ActiveIngredient,g.GroupName,m.ManufacturerName,
       d.PrescriptionRequired,d.ControlledDrug,d.IsActive
FROM catalog.Drugs d
JOIN catalog.DrugGroups g ON g.DrugGroupId=d.DrugGroupId
JOIN catalog.Manufacturers m ON m.ManufacturerId=d.ManufacturerId
ORDER BY d.DrugId;

/* TEST 2: SEARCH - tìm theo tên/hoạt chất */
PRINT N'TEST 2 - SEARCH thuốc';
DECLARE @Keyword NVARCHAR(100)=N'Paracetamol';
SELECT DrugId,DrugCode,DrugName,ActiveIngredient
FROM catalog.Drugs
WHERE DrugName LIKE N'%'+@Keyword+N'%'
   OR ActiveIngredient LIKE N'%'+@Keyword+N'%';

/* TEST 3: CREATE - thêm khách hàng, sau đó rollback */
PRINT N'TEST 3 - CREATE khách hàng (ROLLBACK)';
BEGIN TRANSACTION;
INSERT INTO catalog.Customers(CustomerCode,CustomerName,Phone)
VALUES ('KH-TEST-001',N'Khách hàng kiểm thử','0999999999');
SELECT * FROM catalog.Customers WHERE CustomerCode='KH-TEST-001';
ROLLBACK TRANSACTION;

/* TEST 4: UPDATE - cập nhật giá bán, sau đó rollback */
PRINT N'TEST 4 - UPDATE giá bán (ROLLBACK)';
BEGIN TRANSACTION;
UPDATE bds
SET SalePrice=SalePrice+100
FROM catalog.BranchDrugSettings bds
JOIN catalog.Branches b ON b.BranchId=bds.BranchId
JOIN catalog.Drugs d ON d.DrugId=bds.DrugId
WHERE b.BranchCode='CN001' AND d.DrugCode='THUOC001';
SELECT b.BranchCode,d.DrugCode,bds.SalePrice
FROM catalog.BranchDrugSettings bds
JOIN catalog.Branches b ON b.BranchId=bds.BranchId
JOIN catalog.Drugs d ON d.DrugId=bds.DrugId
WHERE b.BranchCode='CN001' AND d.DrugCode='THUOC001';
ROLLBACK TRANSACTION;

/* TEST 5: SOFT DELETE - ngừng kinh doanh thuốc, không xóa vật lý */
PRINT N'TEST 5 - SOFT DELETE thuốc (ROLLBACK)';
BEGIN TRANSACTION;
UPDATE catalog.Drugs SET IsActive=0 WHERE DrugCode='THUOC002';
SELECT DrugCode,DrugName,IsActive FROM catalog.Drugs WHERE DrugCode='THUOC002';
ROLLBACK TRANSACTION;

/* TEST 6: UNIQUE - kiểm tra mã thuốc trùng (dự kiến lỗi 2601/2627).
   Bỏ comment để chạy riêng nếu muốn xem SQL Server từ chối dữ liệu trùng.
*/
/*
INSERT INTO catalog.Drugs(DrugCode,DrugName,DrugGroupId,ManufacturerId,BaseUnitId)
SELECT DrugCode,N'Thuốc trùng mã',DrugGroupId,ManufacturerId,BaseUnitId
FROM catalog.Drugs WHERE DrugCode='THUOC001';
*/

/* TEST 7: REPORT - tổng tồn khả dụng theo chi nhánh và thuốc */
PRINT N'TEST 7 - REPORT tồn kho';
SELECT b.BranchCode,d.DrugCode,d.DrugName,
       SUM(s.QuantityBase) AS QuantityBase
FROM inventory.DrugLots l
JOIN inventory.StockByLot s ON s.LotId=l.LotId
JOIN catalog.Branches b ON b.BranchId=l.BranchId
JOIN catalog.Drugs d ON d.DrugId=l.DrugId
WHERE l.IsBlocked=0 AND l.ExpiryDate>=CAST(GETDATE() AS DATE)
GROUP BY b.BranchCode,d.DrugCode,d.DrugName
ORDER BY b.BranchCode,d.DrugCode;
GO
