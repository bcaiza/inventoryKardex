package com.company.inventory.service;

import com.company.inventory.entity.Category;
import com.company.inventory.entity.Employee;
import com.company.inventory.entity.Product;
import com.company.inventory.entity.StockEntry;
import com.company.inventory.entity.StockExit;
import com.company.inventory.view.kardex.KardexRow;
import io.jmix.core.DataManager;
import io.jmix.core.FetchPlan;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class KardexServiceImpl implements KardexService {

    @Autowired
    private DataManager dataManager;

    @Override
    public List<Product> resolveProducts(Product product, Category category) {
        if (product != null) {
            // El producto seleccionado manda; si además hay categoría, debe coincidir.
            if (category != null
                    && (product.getCategory() == null || !category.getId().equals(product.getCategory().getId()))) {
                return new ArrayList<>();
            }
            return List.of(product);
        }
        if (category == null) {
            return new ArrayList<>();
        }
        return dataManager.load(Product.class)
                .query("select e from Product e where e.active = true and e.category.id = :catId order by e.code")
                .parameter("catId", category.getId())
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE))
                .list();
    }

    @Override
    public KardexResult build(List<Product> products, LocalDate from, LocalDate to, Employee employeeFilter) {
        Date dateFrom = Date.from(from.atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date dateTo = Date.from(to.atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant());
        List<UUID> productIds = products.stream().map(Product::getId).toList();

        // Stock real actual por producto: es la única fuente de verdad, porque el historial de
        // movimientos puede estar incompleto o mal fechado (ej. cargas iniciales registradas después
        // de salidas que en realidad las precedían).
        List<Product> freshProducts = dataManager.load(Product.class)
                .query("select e from Product e where e.id in :ids")
                .parameter("ids", productIds)
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE))
                .list();
        Map<UUID, Integer> stockActualPorProducto = new HashMap<>();
        for (Product p : freshProducts) stockActualPorProducto.put(p.getId(), p.getStock() != null ? p.getStock() : 0);
        int stockActualTotal = stockActualPorProducto.values().stream().mapToInt(Integer::intValue).sum();

        // Se cargan TODOS los movimientos del periodo (sin filtrar por persona) para que el saldo
        // corrido sea siempre el stock real; el filtro de persona se aplica después, solo a la vista.
        List<StockEntry> entries = dataManager.load(StockEntry.class)
                .query("select e from StockEntry e where e.product.id in :ids and e.entryDate >= :from and e.entryDate <= :to order by e.entryDate, e.createdDate")
                .parameter("ids", productIds)
                .parameter("from", dateFrom)
                .parameter("to", dateTo)
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE).add("product", FetchPlan.BASE))
                .list();

        List<StockExit> exits = dataManager.load(StockExit.class)
                .query("select e from StockExit e where e.product.id in :ids and e.exitDate >= :from and e.exitDate <= :to order by e.exitDate, e.createdDate")
                .parameter("ids", productIds)
                .parameter("from", dateFrom)
                .parameter("to", dateTo)
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE).add("product", FetchPlan.BASE).add("employee", FetchPlan.BASE))
                .list();

        // Movimientos posteriores al periodo: se usan para "descontar" del stock real actual y llegar
        // al saldo que había justo antes de que empezara el periodo consultado.
        List<StockEntry> afterEntries = dataManager.load(StockEntry.class)
                .query("select e from StockEntry e where e.product.id in :ids and e.entryDate > :to")
                .parameter("ids", productIds)
                .parameter("to", dateTo)
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE).add("product", FetchPlan.BASE))
                .list();

        List<StockExit> afterExits = dataManager.load(StockExit.class)
                .query("select e from StockExit e where e.product.id in :ids and e.exitDate > :to")
                .parameter("ids", productIds)
                .parameter("to", dateTo)
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE).add("product", FetchPlan.BASE))
                .list();

        Map<UUID, Integer> netPeriodoPorProducto = new HashMap<>();
        for (Product p : products) netPeriodoPorProducto.put(p.getId(), 0);
        for (StockEntry e : entries) {
            if (Boolean.TRUE.equals(e.getCancelled())) continue;
            netPeriodoPorProducto.merge(e.getProduct().getId(), e.getQuantity() != null ? e.getQuantity() : 0, Integer::sum);
        }
        for (StockExit e : exits) {
            if (Boolean.TRUE.equals(e.getCancelled())) continue;
            netPeriodoPorProducto.merge(e.getProduct().getId(), -(e.getQuantity() != null ? e.getQuantity() : 0), Integer::sum);
        }

        Map<UUID, Integer> netDespuesPorProducto = new HashMap<>();
        for (Product p : products) netDespuesPorProducto.put(p.getId(), 0);
        for (StockEntry e : afterEntries) {
            if (Boolean.TRUE.equals(e.getCancelled())) continue;
            netDespuesPorProducto.merge(e.getProduct().getId(), e.getQuantity() != null ? e.getQuantity() : 0, Integer::sum);
        }
        for (StockExit e : afterExits) {
            if (Boolean.TRUE.equals(e.getCancelled())) continue;
            netDespuesPorProducto.merge(e.getProduct().getId(), -(e.getQuantity() != null ? e.getQuantity() : 0), Integer::sum);
        }

        // Saldo inicial del periodo = stock real actual, menos lo que pasó después del periodo, menos
        // lo que pasó dentro del periodo. Así el saldo corrido siempre cuadra con el stock real de hoy,
        // sin depender de que el historial anterior al periodo esté completo o bien fechado.
        Map<UUID, Integer> saldoPorProducto = new HashMap<>();
        for (Product p : products) {
            int actual = stockActualPorProducto.getOrDefault(p.getId(), 0);
            int despues = netDespuesPorProducto.getOrDefault(p.getId(), 0);
            int enPeriodo = netPeriodoPorProducto.getOrDefault(p.getId(), 0);
            saldoPorProducto.put(p.getId(), actual - despues - enPeriodo);
        }
        int saldoInicialTotal = saldoPorProducto.values().stream().mapToInt(Integer::intValue).sum();

        List<KardexRow> rows = new ArrayList<>();

        for (StockEntry e : entries) {
            KardexRow row = new KardexRow();
            row.setProductoId(e.getProduct().getId());
            row.setProducto(e.getProduct().getCode() + " - " + e.getProduct().getName());
            row.setFecha(e.getEntryDate());
            row.setFechaCreacion(e.getCreatedDate());
            row.setTipo("ENTRADA");
            row.setComprobante(e.getVoucher() != null ? e.getVoucher() : "");
            row.setCantidad(e.getQuantity() != null ? e.getQuantity() : 0);
            row.setDescripcion(e.getDescription() != null ? e.getDescription() : "");
            row.setNota1(e.getNote1() != null ? e.getNote1() : "");
            row.setNota2(e.getNote2() != null ? e.getNote2() : "");
            row.setMotivo(e.getReason() != null ? e.getReason() : "");
            row.setPersona("");
            row.setCreadoPor(e.getCreatedBy() != null ? e.getCreatedBy() : "");
            row.setFechaModificacion(e.getLastModifiedDate());
            row.setModificadoPor(e.getLastModifiedBy() != null ? e.getLastModifiedBy() : "");
            row.setCancelada(Boolean.TRUE.equals(e.getCancelled()));
            rows.add(row);
        }

        for (StockExit e : exits) {
            KardexRow row = new KardexRow();
            row.setProductoId(e.getProduct().getId());
            row.setProducto(e.getProduct().getCode() + " - " + e.getProduct().getName());
            row.setFecha(e.getExitDate());
            row.setFechaCreacion(e.getCreatedDate());
            row.setTipo("SALIDA");
            row.setComprobante(e.getVoucher() != null ? e.getVoucher() : "");
            row.setCantidad(e.getQuantity() != null ? e.getQuantity() : 0);
            row.setDescripcion(e.getDescription() != null ? e.getDescription() : "");
            row.setNota1(e.getNote1() != null ? e.getNote1() : "");
            row.setNota2(e.getNote2() != null ? e.getNote2() : "");
            row.setMotivo(e.getReason() != null ? e.getReason() : "");
            row.setPersonaId(e.getEmployee() != null ? e.getEmployee().getId() : null);
            row.setPersona(e.getEmployee() != null ? e.getEmployee().getName() : "");
            row.setCreadoPor(e.getCreatedBy() != null ? e.getCreatedBy() : "");
            row.setFechaModificacion(e.getLastModifiedDate());
            row.setModificadoPor(e.getLastModifiedBy() != null ? e.getLastModifiedBy() : "");
            row.setCancelada(Boolean.TRUE.equals(e.getCancelled()));
            rows.add(row);
        }

        // Se ordena por fecha de creación (orden real en que se registraron los movimientos) en vez de
        // por la fecha del movimiento, porque esta última puede quedar mal cargada (ej. una carga inicial
        // fechada atrás pero registrada después de una salida que en la realidad la precedía), lo que
        // generaba saldos negativos transitorios en el saldo corrido aunque el total final fuera correcto.
        rows.sort(Comparator
                .comparing((KardexRow r) -> r.getFechaCreacion() != null ? r.getFechaCreacion() : new Date(0))
                .thenComparing(r -> r.getFecha() != null ? r.getFecha() : new Date(0)));

        // El saldo corre de forma independiente por producto (relevante en modo categoría, con varios productos),
        // sobre TODOS los movimientos, para que el stock mostrado sea siempre el real.
        Map<UUID, Integer> balancePorProducto = new HashMap<>(saldoPorProducto);
        for (KardexRow row : rows) {
            int balance = balancePorProducto.getOrDefault(row.getProductoId(), 0);
            if (!row.isCancelada()) {
                balance += "ENTRADA".equals(row.getTipo()) ? row.getCantidad() : -row.getCantidad();
            }
            balancePorProducto.put(row.getProductoId(), balance);
            row.setStock(balance);
        }

        // El filtro de persona solo determina qué filas se muestran (salidas de esa persona);
        // el stock de cada fila ya refleja el saldo real calculado arriba.
        List<KardexRow> visibleRows = employeeFilter == null ? rows :
                rows.stream()
                        .filter(r -> "SALIDA".equals(r.getTipo()) && employeeFilter.getId().equals(r.getPersonaId()))
                        .toList();

        return new KardexResult(visibleRows, stockActualTotal, saldoInicialTotal);
    }

    @Override
    public byte[] buildExcel(List<KardexRow> rows) {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Kardex");

            CellStyle headerStyle = wb.createCellStyle();
            Font hFont = wb.createFont();
            hFont.setBold(true);
            hFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(hFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            String[] headers = {"Fecha", "Fecha Creación", "Producto", "Tipo", "Estado", "Comprobante", "Cantidad", "Motivo",
                    "Descripción", "Nota 1", "Nota 2", "Persona", "Registrado por", "Fecha Modificación", "Modificado por", "Stock"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell c = headerRow.createCell(i);
                c.setCellValue(headers[i]);
                c.setCellStyle(headerStyle);
            }

            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
            SimpleDateFormat sdfCreacion = new SimpleDateFormat("dd/MM/yyyy HH:mm");
            int rowIdx = 1;
            for (KardexRow r : rows) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(r.getFecha() != null ? sdf.format(r.getFecha()) : "");
                row.createCell(1).setCellValue(r.getFechaCreacion() != null ? sdfCreacion.format(r.getFechaCreacion()) : "");
                row.createCell(2).setCellValue(r.getProducto());
                row.createCell(3).setCellValue(r.getTipo());
                row.createCell(4).setCellValue(r.isCancelada() ? "ANULADO" : "Vigente");
                row.createCell(5).setCellValue(r.getComprobante());
                row.createCell(6).setCellValue("SALIDA".equals(r.getTipo()) ? -r.getCantidad() : r.getCantidad());
                row.createCell(7).setCellValue(r.getMotivo());
                row.createCell(8).setCellValue(r.getDescripcion());
                row.createCell(9).setCellValue(r.getNota1());
                row.createCell(10).setCellValue(r.getNota2());
                row.createCell(11).setCellValue(r.getPersona());
                row.createCell(12).setCellValue(r.getCreadoPor());
                row.createCell(13).setCellValue(r.getFechaModificacion() != null ? sdfCreacion.format(r.getFechaModificacion()) : "");
                row.createCell(14).setCellValue(r.getModificadoPor());
                row.createCell(15).setCellValue(r.getStock());
            }

            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Error generando Excel del kardex", e);
        }
    }
}
