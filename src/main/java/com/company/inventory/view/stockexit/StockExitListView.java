package com.company.inventory.view.stockexit;

import com.company.inventory.entity.StockExit;
import com.company.inventory.service.StockExitService;
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

@Route(value = "stock-exits", layout = MainView.class)
@ViewController(id = "StockExit.list")
@ViewDescriptor(path = "stock-exit-list-view.xml")
@DialogMode(width = "90em")
public class StockExitListView extends StandardListView<StockExit> {

    @Autowired
    private StockExitService stockExitService;

    @Autowired
    private Notifications notifications;

    @ViewComponent
    private DataGrid<StockExit> stockExitsDataGrid;

    @Subscribe("anularButton")
    public void onAnularButtonClick(ClickEvent<Button> event) {
        Set<StockExit> selectedItems = stockExitsDataGrid.getSelectedItems();
        if (selectedItems.isEmpty()) {
            notifications.create("Seleccione una salida para anular").show();
            return;
        }
        if (selectedItems.size() > 1) {
            notifications.create("Seleccione solo una salida para anular").show();
            return;
        }

        StockExit exit = selectedItems.iterator().next();
        if (Boolean.TRUE.equals(exit.getCancelled())) {
            notifications.create("Esta salida ya está anulada").show();
            return;
        }

        Dialog confirmDialog = new Dialog();
        confirmDialog.setHeaderTitle("Confirmar Anulación");

        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);
        content.setSpacing(false);
        content.add(new Span("¿Está seguro que desea anular esta salida?"));
        content.add(new Span("Producto: " + exit.getProduct().getName()));
        content.add(new Span("Cantidad que se devolverá al stock: " + exit.getQuantity()));
        confirmDialog.add(content);

        Button confirmBtn = new Button("Anular", e -> {
            confirmDialog.close();
            anularExit(exit);
        });
        confirmBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_PRIMARY);

        Button cancelBtn = new Button("Cancelar", e -> confirmDialog.close());
        confirmDialog.getFooter().add(cancelBtn, confirmBtn);
        confirmDialog.open();
    }

    private void anularExit(StockExit exit) {
        try {
            stockExitService.cancelExit(exit.getId());

            getViewData().loadAll();
            notifications.create("Salida anulada. Stock del producto restaurado correctamente.")
                    .withType(Notifications.Type.SUCCESS)
                    .show();
        } catch (Exception ex) {
            notifications.create("Error al anular la salida: " + ex.getMessage())
                    .withType(Notifications.Type.ERROR)
                    .show();
        }
    }

    @Subscribe("bulkImportButton")
    public void onBulkImportButtonClick(ClickEvent<Button> event) {
        Dialog dialog = new Dialog();
        dialog.setWidth("500px");
        dialog.setHeaderTitle("Carga Masiva de Salidas");

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
                String result = stockExitService.importExits(buffer.getInputStream(), e.getFileName());
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
