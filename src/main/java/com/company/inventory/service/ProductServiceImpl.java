package com.company.inventory.service;

import com.company.inventory.entity.Category;
import com.company.inventory.entity.Medida;
import com.company.inventory.entity.Product;
import com.company.inventory.entity.StockEntry;
import com.company.inventory.entity.StockExit;
import io.jmix.core.DataManager;
import io.jmix.core.FetchPlan;
import io.jmix.core.SaveContext;
import io.jmix.core.entity.KeyValueEntity;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.company.inventory.service.ExcelCellUtils.getCellString;

@Service
public class ProductServiceImpl implements ProductService {

    @Autowired
    private DataManager dataManager;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private MedidaService medidaService;

    @Override
    public Optional<Product> findFreshWithMedida(UUID productId) {
        return dataManager.load(Product.class)
                .id(productId)
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE).add("medida", FetchPlan.BASE))
                .optional();
    }

    @Override
    public Product loadFresh(UUID productId) {
        return dataManager.load(Product.class)
                .id(productId)
                .one();
    }

    @Override
    public Optional<Product> findByCodeIgnoreCase(String code) {
        return dataManager.load(Product.class)
                .query("select e from Product e where lower(trim(e.code)) = :code")
                .parameter("code", code.toLowerCase().trim())
                .list()
                .stream()
                .findFirst();
    }

    @Override
    public long countActiveLowStock() {
        Optional<KeyValueEntity> result = dataManager.loadValues(
                        "select count(e) from Product e where e.active = true " +
                        "and e.minStock is not null and e.minStock > 0 and e.stock <= e.minStock")
                .properties("cnt")
                .optional();
        return result.map(kve -> kve.<Number>getValue("cnt"))
                .map(Number::longValue)
                .orElse(0L);
    }

    @Override
    public List<Product> findLowStockProducts() {
        List<Product> all = dataManager.load(Product.class)
                .query("select e from Product e where e.active = true and e.minStock > 0 order by e.name asc")
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE)
                        .add("category", FetchPlan.BASE)
                        .add("medida", FetchPlan.BASE))
                .list();

        return all.stream()
                .filter(p -> {
                    int stock = p.getStock() != null ? p.getStock() : 0;
                    int min = p.getMinStock() != null ? p.getMinStock() : 0;
                    int margin = p.getAlertMargin() != null ? p.getAlertMargin() : 0;
                    return stock <= min + margin;
                })
                .sorted(Comparator.comparingInt(p -> p.getStock() != null ? p.getStock() : 0))
                .toList();
    }

    @Override
    public Map<UUID, Long> sumEntryQuantitiesByProduct() {
        Map<UUID, Long> totals = new HashMap<>();
        dataManager.loadValues(
                        "select e.product.id, sum(e.quantity) from StockEntry e where e.cancelled is null or e.cancelled = false group by e.product.id")
                .properties("pid", "total")
                .list()
                .forEach(kve -> {
                    UUID id = kve.getValue("pid");
                    Number total = kve.getValue("total");
                    if (id != null) totals.put(id, total != null ? total.longValue() : 0L);
                });
        return totals;
    }

    @Override
    public Map<UUID, Long> sumExitQuantitiesByProduct() {
        Map<UUID, Long> totals = new HashMap<>();
        dataManager.loadValues(
                        "select e.product.id, sum(e.quantity) from StockExit e where e.cancelled is null or e.cancelled = false group by e.product.id")
                .properties("pid", "total")
                .list()
                .forEach(kve -> {
                    UUID id = kve.getValue("pid");
                    Number total = kve.getValue("total");
                    if (id != null) totals.put(id, total != null ? total.longValue() : 0L);
                });
        return totals;
    }

    @Override
    public List<StockEntry> findEntriesByProduct(UUID productId) {
        return dataManager.load(StockEntry.class)
                .query("select e from StockEntry e where e.product.id = :pid order by e.entryDate, e.createdDate")
                .parameter("pid", productId)
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE))
                .list();
    }

    @Override
    public List<StockExit> findExitsByProduct(UUID productId) {
        return dataManager.load(StockExit.class)
                .query("select e from StockExit e where e.product.id = :pid order by e.exitDate, e.createdDate")
                .parameter("pid", productId)
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE))
                .list();
    }

    @Override
    public void deleteCascade(Product product, List<StockEntry> entries, List<StockExit> exits) {
        SaveContext ctx = new SaveContext();
        entries.forEach(ctx::removing);
        exits.forEach(ctx::removing);
        ctx.removing(product);
        dataManager.save(ctx);
    }

    @Override
    public String reconcileOpeningStocks() {
        List<Product> products = dataManager.load(Product.class).all().list();

        List<StockEntry> allEntries = dataManager.load(StockEntry.class)
                .all()
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE).add("product", FetchPlan.BASE))
                .list();
        List<StockExit> allExits = dataManager.load(StockExit.class)
                .all()
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE).add("product", FetchPlan.BASE))
                .list();

        Map<UUID, Integer> registradoPorProducto = new HashMap<>();
        for (Product p : products) registradoPorProducto.put(p.getId(), 0);

        for (StockEntry e : allEntries) {
            if (Boolean.TRUE.equals(e.getCancelled())) continue;
            registradoPorProducto.merge(e.getProduct().getId(), e.getQuantity() != null ? e.getQuantity() : 0, Integer::sum);
        }
        for (StockExit e : allExits) {
            if (Boolean.TRUE.equals(e.getCancelled())) continue;
            registradoPorProducto.merge(e.getProduct().getId(), -(e.getQuantity() != null ? e.getQuantity() : 0), Integer::sum);
        }

        List<StockEntry> entriesToSave = new ArrayList<>();
        List<StockExit> exitsToSave = new ArrayList<>();
        int corregidos = 0;

        for (Product p : products) {
            int registrado = registradoPorProducto.getOrDefault(p.getId(), 0);
            int real = p.getStock() != null ? p.getStock() : 0;
            int diferencia = real - registrado;
            if (diferencia == 0) continue;

            Date fecha = p.getCreatedDate() != null ? p.getCreatedDate() : new Date();

            if (diferencia > 0) {
                StockEntry se = dataManager.create(StockEntry.class);
                se.setProduct(p);
                se.setEntryDate(fecha);
                se.setQuantity(diferencia);
                se.setReason("CARGA INICIAL");
                se.setDescription("CARGA INICIAL");
                entriesToSave.add(se);
            } else {
                StockExit sx = dataManager.create(StockExit.class);
                sx.setProduct(p);
                sx.setExitDate(fecha);
                sx.setQuantity(-diferencia);
                sx.setReason("CARGA INICIAL");
                sx.setDescription("CARGA INICIAL");
                exitsToSave.add(sx);
            }
            corregidos++;
        }

        if (corregidos > 0) {
            SaveContext ctx = new SaveContext();
            entriesToSave.forEach(ctx::saving);
            exitsToSave.forEach(ctx::saving);
            dataManager.save(ctx);
        }

        return String.format("Corrección completada: %d de %d producto(s) ajustado(s).", corregidos, products.size());
    }

    @Override
    public String importProducts(InputStream inputStream, String filename) throws Exception {
        Workbook workbook = filename.toLowerCase().endsWith(".xlsx")
                ? new XSSFWorkbook(inputStream)
                : new HSSFWorkbook(inputStream);

        Sheet sheet = workbook.getSheetAt(0);
        Map<String, Product> toSave = new LinkedHashMap<>();
        int created = 0, updated = 0, errors = 0;

        for (Row row : sheet) {
            if (row.getRowNum() == 0) continue;

            String code = getCellString(row.getCell(0));
            if (code == null || code.isBlank()) continue;
            code = code.trim();

            String name = getCellString(row.getCell(1));
            if (name == null || name.isBlank()) { errors++; continue; }

            String description = getCellString(row.getCell(2));
            String note1 = getCellString(row.getCell(3));
            String note2 = getCellString(row.getCell(4));
            String categoryName = getCellString(row.getCell(5));
            String medidaName = getCellString(row.getCell(6));

            String normalizedCode = code.toLowerCase();

            // Primero busca en el mapa (duplicados dentro del Excel)
            Product product = toSave.get(normalizedCode);

            if (product == null) {
                Optional<Product> existing = findByCodeIgnoreCase(normalizedCode);
                product = existing.orElseGet(() -> dataManager.create(Product.class));
                if (existing.isEmpty()) created++; else updated++;
            }

            product.setCode(code);
            product.setName(name);
            product.setDescription(description);
            product.setNote1(note1);
            product.setNote2(note2);
            product.setUnit("UNIDAD");

            if (categoryName != null && !categoryName.isBlank()) {
                product.setCategory(categoryService.findOrCreateByName(categoryName));
            }

            String resolvedMedida = (medidaName != null && !medidaName.isBlank()) ? medidaName : "UNIDAD";
            product.setMedida(medidaService.findOrCreateByName(resolvedMedida));

            toSave.put(normalizedCode, product);
        }

        workbook.close();

        if (!toSave.isEmpty()) {
            SaveContext ctx = new SaveContext();
            toSave.values().forEach(ctx::saving);
            dataManager.save(ctx);
        }

        return String.format("Importación completada: %d creados, %d actualizados, %d errores", created, updated, errors);
    }

    @Override
    public String importProductsWithStock(InputStream inputStream, String filename) throws Exception {
        Workbook workbook = filename.toLowerCase().endsWith(".xlsx")
                ? new XSSFWorkbook(inputStream)
                : new HSSFWorkbook(inputStream);

        Sheet sheet = workbook.getSheetAt(0);
        Map<String, Product> toSave = new LinkedHashMap<>();
        Map<String, Integer> stockDeltas = new LinkedHashMap<>();
        Set<String> newCodes = new HashSet<>();
        int created = 0, updated = 0, errors = 0;

        for (Row row : sheet) {
            // Saltar las 3 primeras filas (título, subtítulo, encabezado)
            if (row.getRowNum() < 3) continue;

            // Col A (0) = Nº, Col B (1) = CODIGO, Col C (2) = DESCRIPCION,
            // Col D (3) = CATEGORIA, Col E (4) = UNIDAD, Col F (5) = CANTIDAD
            String numStr = getCellString(row.getCell(0));
            if (numStr == null || numStr.isBlank()) continue;

            String code = getCellString(row.getCell(1));
            if (code == null || code.isBlank()) { errors++; continue; }
            code = code.trim();

            String name = getCellString(row.getCell(2));
            if (name == null || name.isBlank()) name = code;

            String categoryName = getCellString(row.getCell(3));
            String medidaName = getCellString(row.getCell(4));
            String cantidadStr = getCellString(row.getCell(5));

            int stock = 0;
            if (cantidadStr != null && !cantidadStr.isBlank()) {
                try { stock = Math.max(0, (int) Double.parseDouble(cantidadStr)); } catch (NumberFormatException ignored) {}
            }

            String normalizedCode = code.toLowerCase();
            Product product = toSave.get(normalizedCode);

            if (product == null) {
                Optional<Product> existing = findByCodeIgnoreCase(normalizedCode);
                if (existing.isEmpty()) {
                    product = dataManager.create(Product.class);
                    newCodes.add(normalizedCode);
                    created++;
                } else {
                    product = existing.get();
                    updated++;
                }
            }

            int oldStock = product.getStock() != null ? product.getStock() : 0;

            product.setCode(code);
            product.setName(name);
            product.setStock(stock);

            if (categoryName != null && !categoryName.isBlank()) {
                product.setCategory(categoryService.findOrCreateByName(categoryName));
            }

            String resolvedMedida = (medidaName != null && !medidaName.isBlank()) ? medidaName : "UNIDAD";
            product.setMedida(medidaService.findOrCreateByName(resolvedMedida));

            stockDeltas.put(normalizedCode, stock - oldStock);
            toSave.put(normalizedCode, product);
        }

        workbook.close();

        if (!toSave.isEmpty()) {
            SaveContext ctx = new SaveContext();
            toSave.values().forEach(ctx::saving);

            // Registra cada cambio de stock como movimiento en el Kardex: "CARGA INICIAL" para
            // productos nuevos, ajuste con motivo explícito cuando se reimporta un producto existente.
            Date now = new Date();
            for (Map.Entry<String, Product> entry : toSave.entrySet()) {
                Integer delta = stockDeltas.get(entry.getKey());
                if (delta == null || delta == 0) continue;

                Product product = entry.getValue();
                boolean isNew = newCodes.contains(entry.getKey());
                String reason = isNew ? "CARGA INICIAL" : "AJUSTE CARGA BODEGA";

                if (delta > 0) {
                    StockEntry se = dataManager.create(StockEntry.class);
                    se.setProduct(product);
                    se.setEntryDate(now);
                    se.setQuantity(delta);
                    se.setReason(reason);
                    se.setDescription("Importación bodega: " + filename);
                    ctx.saving(se);
                } else {
                    StockExit sx = dataManager.create(StockExit.class);
                    sx.setProduct(product);
                    sx.setExitDate(now);
                    sx.setQuantity(-delta);
                    sx.setReason(reason);
                    sx.setDescription("Ajuste importación bodega: " + filename);
                    ctx.saving(sx);
                }
            }

            dataManager.save(ctx);
        }

        return String.format("Bodega importada: %d creados, %d actualizados, %d errores", created, updated, errors);
    }

    @Override
    public String updateStockFromExcel(InputStream inputStream, String filename) throws Exception {
        Workbook workbook = filename.toLowerCase().endsWith(".xlsx")
                ? new XSSFWorkbook(inputStream)
                : new HSSFWorkbook(inputStream);

        Sheet sheet = workbook.getSheetAt(0);
        List<Product> toSave = new ArrayList<>();
        Map<UUID, Integer> stockDeltas = new LinkedHashMap<>();
        int updated = 0, notFound = 0;

        for (Row row : sheet) {
            if (row.getRowNum() == 0) continue;

            String code = getCellString(row.getCell(0));
            if (code == null || code.isBlank()) continue;

            String stockStr = getCellString(row.getCell(1));
            if (stockStr == null || stockStr.isBlank()) continue;

            int newStock;
            try {
                newStock = (int) Double.parseDouble(stockStr);
                if (newStock < 0) newStock = 0;
            } catch (NumberFormatException e) {
                continue;
            }

            Optional<Product> existing = findByCodeIgnoreCase(code);
            if (existing.isEmpty()) { notFound++; continue; }

            Product product = existing.get();
            int oldStock = product.getStock() != null ? product.getStock() : 0;
            int delta = newStock - oldStock;
            if (delta == 0) continue;

            product.setStock(newStock);
            stockDeltas.put(product.getId(), delta);
            toSave.add(product);
            updated++;
        }

        workbook.close();

        if (!toSave.isEmpty()) {
            SaveContext ctx = new SaveContext();
            toSave.forEach(ctx::saving);

            // Toda actualización de stock, incluida la carga por Excel, debe quedar registrada
            // como movimiento de Entrada/Salida para que el Kardex refleje el cambio.
            Date now = new Date();
            for (Product product : toSave) {
                Integer delta = stockDeltas.get(product.getId());
                if (delta == null || delta == 0) continue;

                if (delta > 0) {
                    StockEntry se = dataManager.create(StockEntry.class);
                    se.setProduct(product);
                    se.setEntryDate(now);
                    se.setQuantity(delta);
                    se.setReason("AJUSTE STOCK EXCEL");
                    se.setDescription("Actualización masiva de stock: " + filename);
                    ctx.saving(se);
                } else {
                    StockExit sx = dataManager.create(StockExit.class);
                    sx.setProduct(product);
                    sx.setExitDate(now);
                    sx.setQuantity(-delta);
                    sx.setReason("AJUSTE STOCK EXCEL");
                    sx.setDescription("Actualización masiva de stock: " + filename);
                    ctx.saving(sx);
                }
            }

            dataManager.save(ctx);
        }

        return String.format("Stock actualizado: %d productos, %d no encontrados", updated, notFound);
    }
}
