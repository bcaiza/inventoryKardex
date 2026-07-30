package com.company.inventory.view.alertas;

import com.company.inventory.entity.Product;
import com.company.inventory.service.ProductService;
import com.company.inventory.view.main.MainView;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

@Route(value = "alertas", layout = MainView.class)
@ViewController(id = "Alertas.view")
@ViewDescriptor(path = "alertas-view.xml")
public class AlertasView extends StandardView {

    @Autowired
    private ProductService productService;

    @ViewComponent
    private DataGrid<Product> alertasGrid;

    @ViewComponent
    private CollectionContainer<Product> alertasDc;

    @ViewComponent
    private Span sinStockBadge;

    @ViewComponent
    private Span bajoMinimoBadge;

    @ViewComponent
    private Span proximoBadge;

    @Subscribe
    public void onReady(ReadyEvent event) {
        setupStatusColumn();
        setupStockRenderer();
        loadData();
    }

    private void loadData() {
        List<Product> filtered = productService.findLowStockProducts();
        alertasDc.setItems(filtered);
        updateBadges();
    }

    private void setupStatusColumn() {
        alertasGrid.addColumn(new ComponentRenderer<>(product -> {
            int stock = product.getStock() != null ? product.getStock() : 0;
            int min = product.getMinStock() != null ? product.getMinStock() : 0;
            Span badge = new Span();
            badge.getStyle()
                    .set("padding", "2px 8px")
                    .set("border-radius", "4px")
                    .set("font-weight", "bold")
                    .set("font-size", "var(--lumo-font-size-s)");
            if (stock == 0) {
                badge.setText("SIN STOCK");
                badge.getStyle()
                        .set("background-color", "var(--lumo-error-color-10pct)")
                        .set("color", "var(--lumo-error-color)");
            } else if (stock <= min) {
                badge.setText("BAJO MÍNIMO");
                badge.getStyle()
                        .set("background-color", "var(--lumo-warning-color-10pct)")
                        .set("color", "var(--lumo-warning-text-color)");
            } else {
                badge.setText("PRÓXIMO");
                badge.getStyle()
                        .set("background-color", "var(--lumo-contrast-5pct)")
                        .set("color", "var(--lumo-secondary-text-color)");
            }
            return badge;
        })).setHeader("Estado").setWidth("140px").setFlexGrow(0).setSortable(false);

        Grid.Column<Product> estadoCol = alertasGrid.getColumns().get(alertasGrid.getColumns().size() - 1);
        Grid.Column<Product> codeCol = alertasGrid.getColumnByKey("code");
        Grid.Column<Product> nameCol = alertasGrid.getColumnByKey("name");
        Grid.Column<Product> categoryCol = alertasGrid.getColumnByKey("category");
        Grid.Column<Product> stockCol = alertasGrid.getColumnByKey("stock");
        Grid.Column<Product> minStockCol = alertasGrid.getColumnByKey("minStock");
        if (codeCol != null) {
            alertasGrid.setColumnOrder(estadoCol, codeCol, nameCol, categoryCol, stockCol, minStockCol);
        }
    }

    private void setupStockRenderer() {
        Grid.Column<Product> stockCol = alertasGrid.getColumnByKey("stock");
        if (stockCol != null) {
            stockCol.setRenderer(new ComponentRenderer<>(product -> {
                int stock = product.getStock() != null ? product.getStock() : 0;
                int min = product.getMinStock() != null ? product.getMinStock() : 0;
                Span span = new Span(String.valueOf(stock));
                span.getStyle().set("font-weight", "bold");
                if (stock == 0) {
                    span.getStyle().set("color", "var(--lumo-error-color)");
                } else if (stock <= min) {
                    span.getStyle().set("color", "var(--lumo-warning-text-color)");
                } else {
                    span.getStyle().set("color", "var(--lumo-secondary-text-color)");
                }
                return span;
            }));
        }
    }

    private void updateBadges() {
        long sinStock = 0, bajoMinimo = 0, proximo = 0;
        for (Product p : alertasDc.getItems()) {
            int stock = p.getStock() != null ? p.getStock() : 0;
            int min = p.getMinStock() != null ? p.getMinStock() : 0;
            if (stock == 0) sinStock++;
            else if (stock <= min) bajoMinimo++;
            else proximo++;
        }
        styleBadge(sinStockBadge, sinStock + " sin stock",
                "var(--lumo-error-color-10pct)", "var(--lumo-error-color)");
        styleBadge(bajoMinimoBadge, bajoMinimo + " bajo mínimo",
                "var(--lumo-warning-color-10pct)", "var(--lumo-warning-text-color)");
        styleBadge(proximoBadge, proximo + " próximos al mínimo",
                "var(--lumo-contrast-5pct)", "var(--lumo-secondary-text-color)");
    }

    private void styleBadge(Span badge, String text, String bg, String color) {
        badge.setText(text);
        badge.getStyle()
                .set("background-color", bg)
                .set("color", color)
                .set("padding", "6px 14px")
                .set("border-radius", "20px")
                .set("font-weight", "bold")
                .set("font-size", "var(--lumo-font-size-s)")
                .set("margin-right", "8px");
    }

    @Subscribe("refreshButton")
    public void onRefreshButtonClick(ClickEvent<Button> event) {
        loadData();
    }
}
