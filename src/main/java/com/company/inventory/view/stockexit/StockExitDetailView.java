package com.company.inventory.view.stockexit;

import com.company.inventory.entity.Product;
import com.company.inventory.entity.StockExit;
import com.company.inventory.service.InsufficientStockException;
import com.company.inventory.service.ProductService;
import com.company.inventory.service.StockExitService;
import com.company.inventory.view.main.MainView;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasValue;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.Route;
import io.jmix.core.EntityStates;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.component.combobox.EntityComboBox;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;

@Route(value = "stock-exits/:id", layout = MainView.class)
@ViewController(id = "StockExit.detail")
@ViewDescriptor(path = "stock-exit-detail-view.xml")
@EditedEntityContainer("stockExitDc")
public class StockExitDetailView extends StandardDetailView<StockExit> {

    @Autowired
    private StockExitService stockExitService;

    @Autowired
    private ProductService productService;

    @Autowired
    private Notifications notifications;

    @Autowired
    private EntityStates entityStates;

    @ViewComponent
    private EntityComboBox<Product> productField;

    @ViewComponent
    private Span currentStockSpan;

    @ViewComponent
    private FormLayout form;

    @ViewComponent
    private Button saveAndCloseButton;

    @Subscribe
    public void onInit(InitEvent event) {
        productField.addValueChangeListener(e -> updateStockDisplay(e.getValue()));
    }

    @Subscribe
    public void onInitEntity(InitEntityEvent<StockExit> event) {
        event.getEntity().setExitDate(new Date());
    }

    @Subscribe
    public void onReady(ReadyEvent event) {
        if (!entityStates.isNew(getEditedEntity())) {
            for (Component c : form.getChildren().toList()) {
                if (c instanceof HasValue<?, ?> hv) {
                    hv.setReadOnly(true);
                }
            }
            saveAndCloseButton.setEnabled(false);
            notifications.create("Esta salida ya fue registrada y no se puede editar. " +
                            "Si necesita corregirla, anúlela desde la lista y registre una nueva.")
                    .withType(Notifications.Type.WARNING)
                    .show();
        }
    }

    private void updateStockDisplay(Product product) {
        if (product != null && product.getId() != null) {
            Product fresh = productService.findFreshWithMedida(product.getId()).orElse(null);
            if (fresh != null) {
                int stock = fresh.getStock() != null ? fresh.getStock() : 0;
                Integer minStock = fresh.getMinStock();
                String unidad = fresh.getMedida() != null ? fresh.getMedida().getName() : "";
                String minInfo = (minStock != null && minStock > 0) ? "  |  Mínimo: " + minStock : "";
                currentStockSpan.setText("Stock disponible: " + stock + " " + unidad + minInfo);
                String bgColor, textColor;
                if (stock == 0) {
                    bgColor = "var(--lumo-error-color-10pct)";
                    textColor = "var(--lumo-error-color)";
                } else if (minStock != null && minStock > 0 && stock <= minStock) {
                    bgColor = "var(--lumo-warning-color-10pct)";
                    textColor = "var(--lumo-warning-text-color)";
                } else {
                    bgColor = "var(--lumo-success-color-10pct)";
                    textColor = "var(--lumo-success-color)";
                }
                currentStockSpan.getStyle().set("background-color", bgColor);
                currentStockSpan.getStyle().set("color", textColor);
            }
        } else {
            currentStockSpan.setText("");
        }
    }

    @Subscribe
    public void onBeforeSave(BeforeSaveEvent event) {
        StockExit exit = getEditedEntity();

        if (!entityStates.isNew(exit)) {
            notifications.create("No se puede editar una salida ya guardada. Anúlela y registre una nueva.")
                    .withType(Notifications.Type.ERROR)
                    .show();
            event.preventSave();
            return;
        }

        if (exit.getProduct() == null) {
            notifications.create("Debe seleccionar un producto")
                    .withType(Notifications.Type.WARNING)
                    .show();
            event.preventSave();
            return;
        }

        if (exit.getQuantity() == null || exit.getQuantity() <= 0) {
            notifications.create("La cantidad debe ser mayor a 0")
                    .withType(Notifications.Type.WARNING)
                    .show();
            event.preventSave();
            return;
        }

        Product freshProduct;
        try {
            freshProduct = stockExitService.decrementStock(exit.getProduct().getId(), exit.getQuantity());
        } catch (InsufficientStockException ex) {
            notifications.create(ex.getMessage())
                    .withType(Notifications.Type.ERROR)
                    .show();
            event.preventSave();
            return;
        }

        int newStock = freshProduct.getStock() != null ? freshProduct.getStock() : 0;
        Integer minStock = freshProduct.getMinStock();
        Integer alertMargin = freshProduct.getAlertMargin();
        int margin = alertMargin != null ? alertMargin : 0;
        if (minStock != null && minStock > 0 && newStock <= minStock + margin) {
            String nivel = newStock <= minStock ? "BAJO MÍNIMO" : "PRÓXIMO AL MÍNIMO";
            notifications.create(
                    "⚠ " + nivel + ": " + freshProduct.getDisplayName() +
                    " — quedan " + newStock + " unidades (mínimo: " + minStock + ", alerta: " + (minStock + margin) + ")")
                    .withType(Notifications.Type.WARNING)
                    .show();
        }
    }
}
