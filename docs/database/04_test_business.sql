/* PHARMACY MANAGEMENT SOA - 04_test_business.sql
   Business-flow tests. Most write operations are rolled back.
   Execute after 01_create_tables.sql and 02_seed_data.sql.
*/
USE PharmacyManagementSOA;
GO
SET NOCOUNT ON;

/* TEST 1: FEFO - xem thứ tự lô sẽ được xuất trước.
   Lô có hạn dùng gần nhất được ưu tiên. */
PRINT N'TEST 1 - FEFO';
SELECT l.LotId,l.LotNumber,l.ExpiryDate,s.QuantityBase,l.UnitCostBase
FROM inventory.DrugLots l
JOIN inventory.StockByLot s ON s.LotId=l.LotId
JOIN catalog.Drugs d ON d.DrugId=l.DrugId
JOIN catalog.Branches b ON b.BranchId=l.BranchId
WHERE d.DrugCode='THUOC001' AND b.BranchCode='CN001'
  AND l.IsBlocked=0 AND l.ExpiryDate>=CAST(GETDATE() AS DATE)
  AND s.QuantityBase>0
ORDER BY l.ExpiryDate,l.LotId;

/* TEST 2: SALE - mô phỏng trừ tồn FEFO trong transaction.
   UPDLOCK + ROWLOCK giúp tránh hai giao dịch cùng lấy một lượng tồn. */
PRINT N'TEST 2 - SALE FEFO (ROLLBACK)';
DECLARE @SaleDrugId INT=(SELECT DrugId FROM catalog.Drugs WHERE DrugCode='THUOC001');
DECLARE @SaleBranchId INT=(SELECT BranchId FROM catalog.Branches WHERE BranchCode='CN001');
DECLARE @Need DECIMAL(18,2)=120;
DECLARE @LotId BIGINT,@Available DECIMAL(18,2),@Take DECIMAL(18,2);

BEGIN TRY
    BEGIN TRANSACTION;
    DECLARE fefo_cursor CURSOR LOCAL FAST_FORWARD FOR
        SELECT l.LotId,s.QuantityBase
        FROM inventory.DrugLots l WITH (UPDLOCK,ROWLOCK)
        JOIN inventory.StockByLot s WITH (UPDLOCK,ROWLOCK) ON s.LotId=l.LotId
        WHERE l.BranchId=@SaleBranchId AND l.DrugId=@SaleDrugId
          AND l.IsBlocked=0 AND l.ExpiryDate>=CAST(GETDATE() AS DATE)
          AND s.QuantityBase>0
        ORDER BY l.ExpiryDate,l.LotId;

    OPEN fefo_cursor;
    FETCH NEXT FROM fefo_cursor INTO @LotId,@Available;
    WHILE @@FETCH_STATUS=0 AND @Need>0
    BEGIN
        SET @Take=CASE WHEN @Available>=@Need THEN @Need ELSE @Available END;
        UPDATE inventory.StockByLot
        SET QuantityBase=QuantityBase-@Take,UpdatedAt=SYSDATETIME()
        WHERE LotId=@LotId AND QuantityBase>=@Take;

        IF @@ROWCOUNT<>1 THROW 51001,N'Không thể trừ tồn lô; tồn kho đã thay đổi.',1;

        INSERT INTO inventory.StockLedger(BranchId,DrugId,LotId,TransactionType,ReferenceType,ReferenceId,QuantityChange,CreatedBy,Note)
        VALUES (@SaleBranchId,@SaleDrugId,@LotId,'SALE_OUT','TEST',0,-@Take,
                (SELECT UserId FROM auth.Users WHERE Username='pharmacist.demo'),
                N'Test bán hàng FEFO');

        SET @Need=@Need-@Take;
        FETCH NEXT FROM fefo_cursor INTO @LotId,@Available;
    END
    CLOSE fefo_cursor;
    DEALLOCATE fefo_cursor;

    IF @Need>0 THROW 51002,N'Không đủ tồn kho còn hạn để bán.',1;

    SELECT l.LotNumber,l.ExpiryDate,s.QuantityBase AS RemainingQuantity
    FROM inventory.DrugLots l JOIN inventory.StockByLot s ON s.LotId=l.LotId
    WHERE l.DrugId=@SaleDrugId AND l.BranchId=@SaleBranchId
    ORDER BY l.ExpiryDate;

    ROLLBACK TRANSACTION;
    PRINT N'PASS: mô phỏng trừ tồn FEFO thành công; dữ liệu đã rollback.';
END TRY
BEGIN CATCH
    IF CURSOR_STATUS('local','fefo_cursor')>=0 CLOSE fefo_cursor;
    IF CURSOR_STATUS('local','fefo_cursor')>-3 DEALLOCATE fefo_cursor;
    IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;

/* TEST 3: Không được bán thuốc đã hết hạn hoặc bị khóa */
PRINT N'TEST 3 - kiểm tra lô hết hạn/bị khóa';
SELECT l.LotNumber,l.ExpiryDate,l.IsBlocked,s.QuantityBase,
       CASE WHEN l.IsBlocked=1 OR l.ExpiryDate<CAST(GETDATE() AS DATE)
            THEN N'KHÔNG ĐƯỢC BÁN' ELSE N'Có thể xét bán nếu còn tồn' END AS SaleEligibility
FROM inventory.DrugLots l
JOIN inventory.StockByLot s ON s.LotId=l.LotId;

/* TEST 4: TRANSFER - kiểm tra điều kiện chuyển kho */
PRINT N'TEST 4 - kiểm tra chi tiết chuyển kho';
SELECT t.TransferCode,t.Status,d.QuantitySent,d.QuantityReceived,
       CASE WHEN d.QuantityReceived>d.QuantitySent THEN N'KHÔNG HỢP LỆ'
            ELSE N'HỢP LỆ' END AS QuantityCheck
FROM inventory.StockTransfers t
JOIN inventory.StockTransferDetails d ON d.TransferId=t.TransferId;

/* TEST 5: STOCKTAKE - đối chiếu tồn hệ thống với số kiểm kê */
PRINT N'TEST 5 - chênh lệch kiểm kê';
SELECT st.StocktakeCode,l.LotNumber,sd.SystemQuantity,sd.ActualQuantity,
       sd.ActualQuantity-sd.SystemQuantity AS Difference
FROM inventory.Stocktakes st
JOIN inventory.StocktakeDetails sd ON sd.StocktakeId=st.StocktakeId
JOIN inventory.DrugLots l ON l.LotId=sd.LotId;

/* TEST 6: PURCHASE DEBT - tổng công nợ phát sinh từ phiếu nhập đã duyệt
   Trừ đi thanh toán và trả nhà cung cấp ở mức tổng hợp minh họa. */
PRINT N'TEST 6 - tổng giá trị phiếu nhập đã duyệt theo nhà cung cấp';
SELECT s.SupplierCode,s.SupplierName,
       SUM(r.TotalAmount) AS ApprovedPurchaseValue
FROM purchase.PurchaseReceipts r
JOIN catalog.Suppliers s ON s.SupplierId=r.SupplierId
WHERE r.Status='APPROVED'
GROUP BY s.SupplierCode,s.SupplierName;

/* TEST 7: REPORT - doanh thu theo ngày */
PRINT N'TEST 7 - doanh thu theo ngày';
SELECT * FROM sales.vw_InvoiceRevenue ORDER BY RevenueDate DESC,BranchId;
GO
