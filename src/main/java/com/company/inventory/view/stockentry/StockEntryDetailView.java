package com.company.inventory.view.stockentry;

import com.company.inventory.entity.Product;
import com.company.inventory.entity.StockEntry;
import com.company.inventory.service.ProductService;
import com.company.inventory.service.StockEntryService;
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

@Route(value = "stock-entries/:id", layout = MainView.class)
@ViewController(id = "StockEntry.detail")
@ViewDescriptor(path = "stock-entry-detail-view.xml")
@EditedEntityContainer("stockEntryDc")
public class StockEntryDetailView extends StandardDetailView<StockEntry> {

    @Autowired
    private StockEntryService stockEntryService;

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
    public void onInitEntity(InitEntityEvent<StockEntry> event) {
        event.getEntity().setEntryDate(new Date());
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
            notifications.create("Esta entrada ya fue registrada y no se puede editar. " +
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
                String unidad = fresh.getMedida() != null ? fresh.getMedida().getName() : "";
                currentStockSpan.setText("Stock actual: " + stock + " " + unidad);
                currentStockSpan.getStyle().set("background-color",
                        stock > 0 ? "var(--lumo-success-color-10pct)" : "var(--lumo-error-color-10pct)");
                currentStockSpan.getStyle().set("color",
                        stock > 0 ? "var(--lumo-success-color)" : "var(--lumo-error-color)");
            }
        } else {
            currentStockSpan.setText("");
        }
    }

    @Subscribe
    public void onBeforeSave(BeforeSaveEvent event) {
        StockEntry entry = getEditedEntity();

        if (!entityStates.isNew(entry)) {
            notifications.create("No se puede editar una entrada ya guardada. Anúlela y registre una nueva.")
                    .withType(Notifications.Type.ERROR)
                    .show();
            event.preventSave();
            return;
        }

        if (entry.getProduct() == null) {
            notifications.create("Debe seleccionar un producto")
                    .withType(Notifications.Type.WARNING)
                    .show();
            event.preventSave();
            return;
        }

        if (entry.getQuantity() == null || entry.getQuantity() <= 0) {
            notifications.create("La cantidad debe ser mayor a 0")
                    .withType(Notifications.Type.WARNING)
                    .show();
            event.preventSave();
            return;
        }

        stockEntryService.incrementStock(entry.getProduct().getId(), entry.getQuantity());
    }

}
