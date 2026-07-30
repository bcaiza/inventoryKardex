package com.company.inventory.view.stockentry;

import com.company.inventory.entity.StockEntry;
import com.company.inventory.service.StockEntryService;
import com.company.inventory.view.main.MainView;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Set;

@Route(value = "stock-entries", layout = MainView.class)
@ViewController(id = "StockEntry.list")
@ViewDescriptor(path = "stock-entry-list-view.xml")
@DialogMode(width = "90em")
public class StockEntryListView extends StandardListView<StockEntry> {

    @Autowired
    private StockEntryService stockEntryService;

    @Autowired
    private Notifications notifications;

    @ViewComponent
    private DataGrid<StockEntry> stockEntriesDataGrid;

    @Subscribe("anularButton")
    public void onAnularButtonClick(ClickEvent<Button> event) {
        Set<StockEntry> selectedItems = stockEntriesDataGrid.getSelectedItems();
        if (selectedItems.isEmpty()) {
            notifications.create("Seleccione una entrada para anular").show();
            return;
        }
        if (selectedItems.size() > 1) {
            notifications.create("Seleccione solo una entrada para anular").show();
            return;
        }

        StockEntry entry = selectedItems.iterator().next();
        if (Boolean.TRUE.equals(entry.getCancelled())) {
            notifications.create("Esta entrada ya está anulada").show();
            return;
        }

        Dialog confirmDialog = new Dialog();
        confirmDialog.setHeaderTitle("Confirmar Anulación");

        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);
        content.setSpacing(false);
        content.add(new Span("¿Está seguro que desea anular esta entrada?"));
        content.add(new Span("Producto: " + entry.getProduct().getName()));
        content.add(new Span("Cantidad a revertir del stock: " + entry.getQuantity()));
        confirmDialog.add(content);

        Button confirmBtn = new Button("Anular", e -> {
            confirmDialog.close();
            anularEntry(entry);
        });
        confirmBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_PRIMARY);

        Button cancelBtn = new Button("Cancelar", e -> confirmDialog.close());
        confirmDialog.getFooter().add(cancelBtn, confirmBtn);
        confirmDialog.open();
    }

    private void anularEntry(StockEntry entry) {
        try {
            int newStock = stockEntryService.cancelEntry(entry.getId());

            getViewData().loadAll();

            if (newStock < 0) {
                notifications.create("Entrada anulada. AVISO: el stock quedó en 0 (saldo insuficiente para revertir completamente).")
                        .withType(Notifications.Type.WARNING)
                        .show();
            } else {
                notifications.create("Entrada anulada. Stock del producto actualizado correctamente.")
                        .withType(Notifications.Type.SUCCESS)
                        .show();
            }
        } catch (Exception ex) {
            notifications.create("Error al anular la entrada: " + ex.getMessage())
                    .withType(Notifications.Type.ERROR)
                    .show();
        }
    }

    @Subscribe("bulkImportButton")
    public void onBulkImportButtonClick(ClickEvent<Button> event) {
        Dialog dialog = new Dialog();
        dialog.setWidth("500px");
        dialog.setHeaderTitle("Carga Masiva de Entradas");

        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);

        Span instructions = new Span("Formato Excel: Col A = Código Producto, Col B = Fecha (dd/MM/yyyy), Col C = Cantidad, Col D = Motivo (fila 1 = encabezado, todas obligatorias)");
        instructions.getStyle().set("font-size", "var(--lumo-font-size-s)");
        instructions.getStyle().set("color", "var(--lumo-secondary-text-color)");

        MemoryBuffer buffer = new MemoryBuffer();
        Upload upload = new Upload(buffer);
        upload.setAcceptedFileTypes(".xlsx", ".xls");
        upload.setMaxFiles(1);

        Span resultSpan = new Span();

        upload.addSucceededListener(e -> {
            try {
                String result = stockEntryService.importEntries(buffer.getInputStream(), e.getFileName());
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
        dialog.getFooter().add(new Button("Cerrar", ev -> dialog.close()));
        dialog.open();
    }
}
