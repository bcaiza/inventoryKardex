package com.company.inventory.service;

import com.company.inventory.entity.Product;
import com.company.inventory.entity.StockEntry;
import io.jmix.core.DataManager;
import io.jmix.core.SaveContext;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.company.inventory.service.ExcelCellUtils.getCellString;
import static com.company.inventory.service.ExcelCellUtils.isBlank;
import static com.company.inventory.service.ExcelCellUtils.parseDate;

@Service
public class StockEntryServiceImpl implements StockEntryService {

    @Autowired
    private DataManager dataManager;

    @Autowired
    private ProductService productService;

    @Override
    public Product incrementStock(UUID productId, int quantity) {
        Product freshProduct = productService.loadFresh(productId);
        int currentStock = freshProduct.getStock() != null ? freshProduct.getStock() : 0;
        freshProduct.setStock(currentStock + quantity);
        return dataManager.save(freshProduct);
    }

    @Override
    public int cancelEntry(UUID entryId) {
        StockEntry freshEntry = dataManager.load(StockEntry.class)
                .id(entryId)
                .fetchPlan("_local")
                .one();

        Product product = dataManager.load(Product.class)
                .id(freshEntry.getProduct().getId())
                .fetchPlan("_local")
                .one();

        int currentStock = product.getStock() != null ? product.getStock() : 0;
        int qty = freshEntry.getQuantity() != null ? freshEntry.getQuantity() : 0;
        int newStock = currentStock - qty;

        product.setStock(Math.max(0, newStock));
        freshEntry.setCancelled(true);

        SaveContext ctx = new SaveContext();
        ctx.saving(freshEntry);
        ctx.saving(product);
        dataManager.save(ctx);

        return newStock;
    }

    @Override
    public String importEntries(InputStream inputStream, String filename) throws Exception {
        Workbook workbook = filename.toLowerCase().endsWith(".xlsx")
                ? new XSSFWorkbook(inputStream)
                : new HSSFWorkbook(inputStream);

        Sheet sheet = workbook.getSheetAt(0);
        List<StockEntry> entriesToSave = new ArrayList<>();
        List<Product> productsToSave = new ArrayList<>();
        int created = 0, errors = 0;
        StringBuilder errorDetail = new StringBuilder();

        for (Row row : sheet) {
            if (row.getRowNum() == 0) continue;

            int rowNum = row.getRowNum() + 1;
            String code = getCellString(row.getCell(0));
            String dateStr = getCellString(row.getCell(1));
            String qtyStr = getCellString(row.getCell(2));
            String reason = getCellString(row.getCell(3));

            if (isBlank(code) && isBlank(dateStr) && isBlank(qtyStr) && isBlank(reason)) continue;

            if (isBlank(code)) { errors++; errorDetail.append(" | Fila ").append(rowNum).append(": código vacío"); continue; }
            if (isBlank(dateStr)) { errors++; errorDetail.append(" | Fila ").append(rowNum).append(": fecha vacía"); continue; }
            if (isBlank(qtyStr)) { errors++; errorDetail.append(" | Fila ").append(rowNum).append(": cantidad vacía"); continue; }
            if (isBlank(reason)) { errors++; errorDetail.append(" | Fila ").append(rowNum).append(": motivo vacío"); continue; }

            Date entryDate = parseDate(row.getCell(1), dateStr);
            if (entryDate == null) { errors++; errorDetail.append(" | Fila ").append(rowNum).append(": fecha inválida '").append(dateStr).append("'"); continue; }

            int quantity;
            try {
                quantity = Integer.parseInt(qtyStr);
                if (quantity <= 0) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                errors++; errorDetail.append(" | Fila ").append(rowNum).append(": cantidad inválida '").append(qtyStr).append("'"); continue;
            }

            Optional<Product> found = productService.findByCodeIgnoreCase(code);
            if (found.isEmpty()) { errors++; errorDetail.append(" | Fila ").append(rowNum).append(": producto no encontrado '").append(code).append("'"); continue; }

            Product product = found.get();
            int currentStock = product.getStock() != null ? product.getStock() : 0;
            product.setStock(currentStock + quantity);

            StockEntry entry = dataManager.create(StockEntry.class);
            entry.setProduct(product);
            entry.setEntryDate(entryDate);
            entry.setQuantity(quantity);
            entry.setReason(reason.trim());

            entriesToSave.add(entry);
            productsToSave.add(product);
            created++;
        }

        workbook.close();

        if (!entriesToSave.isEmpty()) {
            SaveContext ctx = new SaveContext();
            entriesToSave.forEach(ctx::saving);
            productsToSave.forEach(ctx::saving);
            dataManager.save(ctx);
        }

        String msg = String.format("Importación completada: %d entradas creadas, %d errores", created, errors);
        return errors > 0 ? msg + errorDetail : msg;
    }
}
