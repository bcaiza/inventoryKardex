package com.company.inventory.view.product;

import com.company.inventory.entity.Product;
import com.company.inventory.entity.StockEntry;
import com.company.inventory.entity.StockExit;
import com.company.inventory.service.ProductService;
import com.company.inventory.view.main.MainView;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.view.*;

import org.springframework.beans.factory.annotation.Autowired;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Route(value = "products", layout = MainView.class)
@ViewController(id = "Product.list")
@ViewDescriptor(path = "product-list-view.xml")
@LookupComponent("productsDataGrid")
@DialogMode(width = "80em")
public class ProductListView extends StandardListView<Product> {

    @Autowired
    private ProductService productService;

    @Autowired
    private Notifications notifications;

    @ViewComponent
    private DataGrid<Product> productsDataGrid;

    @ViewComponent
    private Span alertBanner;

    @Subscribe
    public void onReady(ReadyEvent event) {
        loadAlertBanner();

        Grid.Column<Product> stockColumn = productsDataGrid.getColumnByKey("stock");
        if (stockColumn != null) {
            stockColumn.setRenderer(new ComponentRenderer<>(product -> {
                int stock = product.getStock() != null ? product.getStock() : 0;
                Integer minStock = product.getMinStock();
                Span span = new Span(String.valueOf(stock));
                span.getStyle().set("font-weight", "bold");
                String color;
                if (stock == 0) {
                    color = "var(--lumo-error-color)";
                } else if (minStock != null && minStock > 0 && stock <= minStock) {
                    color = "var(--lumo-warning-text-color)";
                } else {
                    color = "var(--lumo-success-color)";
                }
                span.getStyle().set("color", color);
                return span;
            }));
        }
        addEntryExitColumns();
    }

    private void loadAlertBanner() {
        try {
            long count = productService.countActiveLowStock();
            if (count > 0) {
                alertBanner.setText("⚠  " + count + " producto(s) con stock igual o por debajo del mínimo configurado");
                alertBanner.getStyle()
                        .set("background-color", "var(--lumo-warning-color-10pct)")
                        .set("color", "var(--lumo-warning-text-color)")
                        .set("padding", "8px 16px")
                        .set("border-radius", "4px")
                        .set("font-weight", "bold")
                        .set("display", "block")
                        .set("width", "100%")
                        .set("box-sizing", "border-box")
                        .set("margin-bottom", "8px");
            } else {
                alertBanner.setText("");
                alertBanner.getStyle().remove("display");
            }
        } catch (Exception ignored) {}
    }

    private void addEntryExitColumns() {
        Map<UUID, Long> entryTotals = new HashMap<>();
        Map<UUID, Long> exitTotals = new HashMap<>();

        try {
            entryTotals.putAll(productService.sumEntryQuantitiesByProduct());
            exitTotals.putAll(productService.sumExitQuantitiesByProduct());
        } catch (Exception ignored) {}

        Grid.Column<Product> entradasCol = productsDataGrid.addColumn(new ComponentRenderer<>(p -> {
            long val = entryTotals.getOrDefault(p.getId(), 0L);
            Span s = new Span("" + val);
            s.getStyle().set("color", "var(--lumo-success-color)").set("font-weight", "bold");
            return s;
        })).setHeader("Entradas").setWidth("100px").setFlexGrow(0).setSortable(false);

        Grid.Column<Product> salidasCol = productsDataGrid.addColumn(new ComponentRenderer<>(p -> {
            long val = exitTotals.getOrDefault(p.getId(), 0L);
            Span s = new Span("" + val);
            s.getStyle().set("color", "var(--lumo-error-color)").set("font-weight", "bold");
            return s;
        })).setHeader("Salidas").setWidth("100px").setFlexGrow(0).setSortable(false);

        Grid.Column<Product> codeCol = productsDataGrid.getColumnByKey("code");
        Grid.Column<Product> nameCol = productsDataGrid.getColumnByKey("name");
        Grid.Column<Product> categoryCol = productsDataGrid.getColumnByKey("category");
        Grid.Column<Product> stockCol = productsDataGrid.getColumnByKey("stock");
        Grid.Column<Product> minStockCol = productsDataGrid.getColumnByKey("minStock");
        Grid.Column<Product> activeCol = productsDataGrid.getColumnByKey("active");

        if (codeCol != null && nameCol != null && categoryCol != null && stockCol != null && activeCol != null) {
            if (minStockCol != null) {
                productsDataGrid.setColumnOrder(codeCol, nameCol, categoryCol, entradasCol, salidasCol, stockCol, minStockCol, activeCol);
            } else {
                productsDataGrid.setColumnOrder(codeCol, nameCol, categoryCol, entradasCol, salidasCol, stockCol, activeCol);
            }
        }
    }

    @Subscribe("bulkImportButton")
    public void onBulkImportButtonClick(ClickEvent<Button> event) {
        openImportDialog(false);
    }

    @Subscribe("bulkStockButton")
    public void onBulkStockButtonClick(ClickEvent<Button> event) {
        openImportDialog(true);
    }

    @Subscribe("bulkBodegaButton")
    public void onBulkBodegaButtonClick(ClickEvent<Button> event) {
        Dialog dialog = new Dialog();
        dialog.setWidth("520px");
        dialog.setHeaderTitle("Importar Productos con Stock");

        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);

        Span instructions = new Span(
                "Formato BASE DE DATOS BODEGA: fila 4 en adelante — CODIGO | DESCRIPCION | CATEGORIA | UNIDAD | CANTIDAD");
        instructions.getStyle().set("font-size", "var(--lumo-font-size-s)")
                .set("color", "var(--lumo-secondary-text-color)");

        MemoryBuffer buffer = new MemoryBuffer();
        Upload upload = new Upload(buffer);
        upload.setAcceptedFileTypes(".xlsx", ".xls");
        upload.setMaxFiles(1);

        Span resultSpan = new Span();

        upload.addSucceededListener(e -> {
            try {
                String result = productService.importProductsWithStock(buffer.getInputStream(), e.getFileName());
                resultSpan.setText(result);
                resultSpan.getStyle().set("color", "var(--lumo-success-color)");
                getViewData().loadAll();
            } catch (Exception ex) {
                resultSpan.setText("Error: " + ex.getMessage());
                resultSpan.getStyle().set("color", "var(--lumo-error-color)");
            }
        });

        upload.addFailedListener(e -> {
            resultSpan.setText("Error al cargar: " + e.getReason().getMessage());
            resultSpan.getStyle().set("color", "var(--lumo-error-color)");
        });

        content.add(instructions, upload, resultSpan);
        dialog.add(content);
        dialog.getFooter().add(new Button("Cerrar", ev -> dialog.close()));
        dialog.open();
    }

    @Subscribe("reconcileStockButton")
    public void onReconcileStockButtonClick(ClickEvent<Button> event) {
        Dialog confirmDialog = new Dialog();
        confirmDialog.setHeaderTitle("Confirmar corrección de saldo inicial");

        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);
        content.setSpacing(false);
        content.add(new Span("Esto revisa TODOS los productos y compara su stock real contra la suma de sus"
                + " entradas y salidas vigentes registradas."));
        content.add(new Span("Donde haya diferencia, crea un único movimiento de ajuste (fechado a la creación"
                + " del producto) que explica esa diferencia en el Kardex."));
        content.add(new Span("No modifica el stock actual de ningún producto."));
        confirmDialog.add(content);

        Button confirmBtn = new Button("Corregir", e -> {
            confirmDialog.close();
            String result = productService.reconcileOpeningStocks();
            notifications.create(result).withType(Notifications.Type.SUCCESS).show();
            getViewData().loadAll();
        });
        confirmBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancelBtn = new Button("Cancelar", e -> confirmDialog.close());
        confirmDialog.getFooter().add(cancelBtn, confirmBtn);
        confirmDialog.open();
    }

    @Subscribe("deleteProductButton")
    public void onDeleteProductButtonClick(ClickEvent<Button> event) {
        Set<Product> selected = productsDataGrid.getSelectedItems();
        if (selected.isEmpty()) {
            notifications.create("Seleccione un producto para eliminar").withType(Notifications.Type.WARNING).show();
            return;
        }
        if (selected.size() > 1) {
            notifications.create("Seleccione un único producto para eliminar").withType(Notifications.Type.WARNING).show();
            return;
        }
        openDeleteProductDialog(selected.iterator().next());
    }

    private void openDeleteProductDialog(Product product) {
        List<StockEntry> entries = productService.findEntriesByProduct(product.getId());
        List<StockExit> exits = productService.findExitsByProduct(product.getId());

        List<MovementRow> movementRows = new ArrayList<>();
        for (StockEntry e : entries) {
            movementRows.add(new MovementRow(e.getEntryDate(), "ENTRADA",
                    e.getQuantity() != null ? e.getQuantity() : 0, e.getReason(), e.getVoucher()));
        }
        for (StockExit e : exits) {
            movementRows.add(new MovementRow(e.getExitDate(), "SALIDA",
                    e.getQuantity() != null ? e.getQuantity() : 0, e.getReason(), e.getVoucher()));
        }
        movementRows.sort(Comparator.comparing(r -> r.fecha != null ? r.fecha : new Date(0)));

        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Eliminar Producto");
        dialog.setWidth("900px");

        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);

        Span productInfo = new Span(product.getCode() + " - " + product.getName()
                + "  |  Stock actual: " + (product.getStock() != null ? product.getStock() : 0));
        productInfo.getStyle().set("font-weight", "bold");
        content.add(productInfo);

        if (movementRows.isEmpty()) {
            content.add(new Span("Este producto no tiene movimientos registrados en el Kardex."));
        } else {
            Span kardexLabel = new Span("Kardex de movimientos (" + movementRows.size() + "):");
            kardexLabel.getStyle().set("font-weight", "bold");
            content.add(kardexLabel);

            Grid<MovementRow> kardexGrid = new Grid<>();
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
            kardexGrid.addColumn(r -> r.fecha != null ? sdf.format(r.fecha) : "")
                    .setHeader("Fecha").setWidth("100px").setFlexGrow(0);
            kardexGrid.addColumn(new ComponentRenderer<>(r -> {
                Span s = new Span(r.tipo);
                s.getStyle().set("color", "ENTRADA".equals(r.tipo) ? "var(--lumo-success-color)" : "var(--lumo-error-color)")
                        .set("font-weight", "bold");
                return s;
            })).setHeader("Tipo").setWidth("90px").setFlexGrow(0);
            kardexGrid.addColumn(r -> ("ENTRADA".equals(r.tipo) ? "+" : "-") + r.cantidad)
                    .setHeader("Cantidad").setWidth("90px").setFlexGrow(0);
            kardexGrid.addColumn(r -> r.comprobante != null ? r.comprobante : "").setHeader("Comprobante").setWidth("120px").setFlexGrow(0);
            kardexGrid.addColumn(r -> r.motivo != null ? r.motivo : "").setHeader("Motivo").setFlexGrow(1);
            kardexGrid.setItems(movementRows);
            kardexGrid.setWidthFull();
            kardexGrid.setMaxHeight("280px");
            content.add(kardexGrid);
        }

        Span warning = new Span("⚠ Esta acción eliminará el producto de forma definitiva junto con "
                + entries.size() + " entrada(s) y " + exits.size() + " salida(s) del Kardex. Esta acción no se puede deshacer.");
        warning.getStyle()
                .set("color", "var(--lumo-error-text-color)")
                .set("background-color", "var(--lumo-error-color-10pct)")
                .set("padding", "8px 12px")
                .set("border-radius", "4px")
                .set("font-weight", "bold")
                .set("display", "block")
                .set("margin-top", "8px");
        content.add(warning);

        dialog.add(content);

        Button cancelBtn = new Button("Cancelar", e -> dialog.close());
        Button confirmBtn = new Button("Eliminar Definitivamente", e -> {
            productService.deleteCascade(product, entries, exits);
            dialog.close();
            getViewData().loadAll();
            loadAlertBanner();
            notifications.create("Producto '" + product.getCode() + "' eliminado junto con sus movimientos")
                    .withType(Notifications.Type.SUCCESS).show();
        });
        confirmBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_PRIMARY);

        dialog.getFooter().add(cancelBtn, confirmBtn);
        dialog.open();
    }

    private static class MovementRow {
        final Date fecha;
        final String tipo;
        final int cantidad;
        final String motivo;
        final String comprobante;

        MovementRow(Date fecha, String tipo, int cantidad, String motivo, String comprobante) {
            this.fecha = fecha;
            this.tipo = tipo;
            this.cantidad = cantidad;
            this.motivo = motivo;
            this.comprobante = comprobante;
        }
    }

    private void openImportDialog(boolean stockOnly) {
        Dialog dialog = new Dialog();
        dialog.setWidth("500px");

        if (stockOnly) {
            dialog.setHeaderTitle("Actualizar Stock Masivo");
        } else {
            dialog.setHeaderTitle("Carga Masiva de Productos");
        }

        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);

        Span instructions = new Span();
        if (stockOnly) {
            instructions.setText("Formato Excel: Col A = Código, Col B = Nuevo Stock (fila 1 = encabezado)");
        } else {
            instructions.setText("Formato Excel: Col A = Código, Col B = Nombre, Col C = Descripción, Col D = Nota1, Col E = Nota2, Col F = Categoría, Col G = Medida (fila 1 = encabezado)");
        }
        instructions.getStyle().set("font-size", "var(--lumo-font-size-s)");
        instructions.getStyle().set("color", "var(--lumo-secondary-text-color)");

        MemoryBuffer buffer = new MemoryBuffer();
        Upload upload = new Upload(buffer);
        upload.setAcceptedFileTypes(".xlsx", ".xls");
        upload.setMaxFiles(1);

        Span resultSpan = new Span();

        upload.addSucceededListener(e -> {
            try {
                String result;
                if (stockOnly) {
                    result = productService.updateStockFromExcel(buffer.getInputStream(), e.getFileName());
                } else {
                    result = productService.importProducts(buffer.getInputStream(), e.getFileName());
                }
                resultSpan.setText(result);
                resultSpan.getStyle().set("color", "var(--lumo-success-color)");
                getViewData().loadAll();
            } catch (Exception ex) {
                resultSpan.setText("Error: " + ex.getMessage());
                resultSpan.getStyle().set("color", "var(--lumo-error-color)");
            }
        });

        upload.addFailedListener(e -> {
            resultSpan.setText("Error al cargar el archivo: " + e.getReason().getMessage());
            resultSpan.getStyle().set("color", "var(--lumo-error-color)");
        });

        content.add(instructions, upload, resultSpan);
        dialog.add(content);

        Button closeButton = new Button("Cerrar", ev -> dialog.close());
        dialog.getFooter().add(closeButton);

        dialog.open();
    }
}
