package com.company.inventory.view.kardex;

import com.company.inventory.entity.Category;
import com.company.inventory.entity.Employee;
import com.company.inventory.entity.Product;
import com.company.inventory.service.KardexResult;
import com.company.inventory.service.KardexService;
import com.company.inventory.view.main.MainView;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.UiProperties;
import io.jmix.flowui.component.combobox.EntityComboBox;
import io.jmix.flowui.component.datepicker.TypedDatePicker;
import io.jmix.flowui.download.DownloadFormat;
import io.jmix.flowui.download.Downloader;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Route(value = "kardex", layout = MainView.class)
@ViewController(id = "Kardex.view")
@ViewDescriptor(path = "kardex-view.xml")
public class KardexView extends StandardView {

    @Autowired
    private KardexService kardexService;

    @Autowired
    private Downloader downloader;

    @Autowired
    private UiProperties uiProperties;

    @ViewComponent
    private EntityComboBox<Product> productPicker;

    @ViewComponent
    private EntityComboBox<Category> categoryPicker;

    @ViewComponent
    private TypedDatePicker<LocalDate> fechaInicial;

    @ViewComponent
    private TypedDatePicker<LocalDate> fechaFinal;

    @ViewComponent
    private EntityComboBox<Employee> personaPicker;

    @ViewComponent
    private Span stockSummary;

    @ViewComponent
    private VerticalLayout gridContainer;

    private Grid<KardexRow> kardexGrid;
    private List<KardexRow> currentRows = new ArrayList<>();

    @Subscribe
    public void onReady(ReadyEvent event) {
        kardexGrid = buildGrid();
        gridContainer.add(kardexGrid);
        kardexGrid.setWidthFull();
        kardexGrid.setMinHeight("400px");

        fechaInicial.setTypedValue(LocalDate.now());
        fechaFinal.setTypedValue(LocalDate.now());
        productPicker.addValueChangeListener(e -> tryLoadKardex());
        categoryPicker.addValueChangeListener(e -> tryLoadKardex());
        fechaInicial.addValueChangeListener(e -> tryLoadKardex());
        fechaFinal.addValueChangeListener(e -> tryLoadKardex());
        personaPicker.addValueChangeListener(e -> tryLoadKardex());
    }

    @Subscribe("exportExcelBtn")
    public void onExportExcelBtnClick(ClickEvent<Button> event) {
        if (currentRows.isEmpty()) return;
        Product product = productPicker.getValue();
        Category category = categoryPicker.getValue();
        String filename;
        if (product != null) {
            filename = "kardex_" + product.getCode() + ".xlsx";
        } else if (category != null) {
            filename = "kardex_" + category.getName().replaceAll("[^a-zA-Z0-9]+", "_") + ".xlsx";
        } else {
            filename = "kardex.xlsx";
        }
        byte[] bytes = kardexService.buildExcel(currentRows);
        downloader.download(bytes, filename, DownloadFormat.XLSX);
    }

    private void tryLoadKardex() {
        Product product = productPicker.getValue();
        Category category = categoryPicker.getValue();
        LocalDate from = fechaInicial.getTypedValue();
        LocalDate to = fechaFinal.getTypedValue();

        if ((product == null && category == null) || from == null || to == null) {
            clearKardex();
            return;
        }

        List<Product> products = kardexService.resolveProducts(product, category);
        if (products.isEmpty()) {
            clearKardex();
            return;
        }

        loadKardex(products, from, to, product, category);
    }

    private void loadKardex(List<Product> products, LocalDate from, LocalDate to, Product selectedProduct, Category selectedCategory) {
        Employee empleado = personaPicker.getValue();
        KardexResult result = kardexService.build(products, from, to, empleado);

        currentRows = result.getRows();
        kardexGrid.setItems(currentRows);

        int stockActualTotal = result.getStockActualTotal();
        int saldoInicialTotal = result.getSaldoInicialTotal();
        int movimientosCount = currentRows.size();

        if (selectedProduct != null) {
            String nombre = selectedProduct.getCode() + " - " + selectedProduct.getName();
            stockSummary.setText(nombre
                    + " | Stock actual: " + stockActualTotal
                    + "  |  Saldo inicial periodo: " + saldoInicialTotal
                    + "  |  Movimientos: " + movimientosCount);
        } else {
            stockSummary.setText("Categoría: " + selectedCategory.getName()
                    + " | Productos: " + products.size()
                    + "  |  Stock actual total: " + stockActualTotal
                    + "  |  Saldo inicial periodo: " + saldoInicialTotal
                    + "  |  Movimientos: " + movimientosCount);
        }
        stockSummary.getStyle()
                .set("font-weight", "bold")
                .set("color", stockActualTotal > 0 ? "var(--lumo-success-color)" : "var(--lumo-error-color)")
                .set("background", "var(--lumo-contrast-5pct)")
                .set("padding", "var(--lumo-space-s)")
                .set("border-radius", "var(--lumo-border-radius-s)");
    }

    private void clearKardex() {
        currentRows = new ArrayList<>();
        kardexGrid.setItems(currentRows);
        stockSummary.setText("");
        stockSummary.removeClassNames();
    }

    private Grid<KardexRow> buildGrid() {
        Grid<KardexRow> grid = new Grid<>();
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
        SimpleDateFormat sdfCreacion = new SimpleDateFormat("dd/MM/yyyy HH:mm");

        grid.addColumn(r -> r.getFecha() != null ? sdf.format(r.getFecha()) : "")
                .setHeader("Fecha").setWidth("110px").setFlexGrow(0).setSortable(true).setResizable(true);

        grid.addColumn(r -> r.getFechaCreacion() != null ? sdfCreacion.format(r.getFechaCreacion()) : "")
                .setHeader("Fecha Creación").setWidth("150px").setFlexGrow(0).setSortable(true).setResizable(true);

        grid.addColumn(r -> r.getProducto())
                .setHeader("Producto").setWidth("200px").setFlexGrow(1).setSortable(true).setResizable(true);

        grid.addColumn(new ComponentRenderer<>(r -> {
            Span s = new Span(r.getTipo());
            s.getStyle()
                    .set("color", tipoColor(r.getTipo()))
                    .set("font-weight", "bold")
                    .set("opacity", r.isCancelada() ? "0.5" : "1");
            return s;
        })).setHeader("Tipo").setWidth("95px").setFlexGrow(0).setResizable(true);

        grid.addColumn(new ComponentRenderer<>(r -> {
            Span s = new Span(r.isCancelada() ? "ANULADO" : "Vigente");
            s.getStyle()
                    .set("color", r.isCancelada() ? "var(--lumo-error-color)" : "var(--lumo-secondary-text-color)")
                    .set("font-weight", r.isCancelada() ? "bold" : "normal");
            return s;
        })).setHeader("Estado").setWidth("100px").setFlexGrow(0).setResizable(true);

        grid.addColumn(r -> r.getComprobante()).setHeader("Comprobante").setWidth("130px").setFlexGrow(0).setResizable(true);

        grid.addColumn(new ComponentRenderer<>(r -> {
            String prefix = "ENTRADA".equals(r.getTipo()) ? "+" : "-";
            Span s = new Span(prefix + r.getCantidad());
            s.getStyle()
                    .set("color", tipoColor(r.getTipo()))
                    .set("font-weight", "bold")
                    .set("opacity", r.isCancelada() ? "0.5" : "1")
                    .set("text-decoration", r.isCancelada() ? "line-through" : "none");
            return s;
        })).setHeader("Cantidad").setWidth("90px").setFlexGrow(0).setResizable(true);

        grid.addColumn(r -> r.getMotivo()).setHeader("Motivo").setFlexGrow(2).setResizable(true);
        grid.addColumn(r -> r.getDescripcion()).setHeader("Descripción").setFlexGrow(2).setResizable(true);
        grid.addColumn(r -> r.getNota1()).setHeader("Nota 1").setFlexGrow(1).setResizable(true);
        grid.addColumn(r -> r.getNota2()).setHeader("Nota 2").setFlexGrow(1).setResizable(true);
        grid.addColumn(r -> r.getPersona()).setHeader("Persona").setWidth("140px").setFlexGrow(0).setResizable(true);
        grid.addColumn(r -> r.getCreadoPor()).setHeader("Registrado por").setWidth("130px").setFlexGrow(0).setResizable(true);

        grid.addColumn(r -> r.getFechaModificacion() != null ? sdfCreacion.format(r.getFechaModificacion()) : "")
                .setHeader("Fecha Modificación").setWidth("150px").setFlexGrow(0).setSortable(true).setResizable(true);

        grid.addColumn(r -> r.getModificadoPor()).setHeader("Modificado por").setWidth("130px").setFlexGrow(0).setResizable(true);

        grid.addColumn(new ComponentRenderer<>(r -> {
            Span s = new Span(String.valueOf(r.getStock()));
            s.getStyle()
                    .set("color", r.getStock() > 0 ? "var(--lumo-success-color)" : "var(--lumo-error-color)")
                    .set("font-weight", "bold");
            return s;
        })).setHeader("Stock").setWidth("85px").setFlexGrow(0).setResizable(true);

        grid.setColumnReorderingAllowed(true);
        return grid;
    }

    private String tipoColor(String tipo) {
        return "ENTRADA".equals(tipo) ? "var(--lumo-success-color)" : "var(--lumo-error-color)";
    }
}
