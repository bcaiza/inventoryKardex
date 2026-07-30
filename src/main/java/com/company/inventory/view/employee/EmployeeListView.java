package com.company.inventory.view.employee;

import com.company.inventory.entity.Employee;
import com.company.inventory.service.EmployeeService;
import com.company.inventory.view.main.MainView;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

@Route(value = "employees", layout = MainView.class)
@ViewController(id = "Employee.list")
@ViewDescriptor(path = "employee-list-view.xml")
@LookupComponent("employeesDataGrid")
@DialogMode(width = "60em")
public class EmployeeListView extends StandardListView<Employee> {

    @Autowired
    private EmployeeService employeeService;

    @ViewComponent
    private DataGrid<Employee> employeesDataGrid;

    @Subscribe("bulkImportButton")
    public void onBulkImportButtonClick(ClickEvent<Button> event) {
        openImportDialog();
    }

    private void openImportDialog() {
        Dialog dialog = new Dialog();
        dialog.setWidth("520px");
        dialog.setHeaderTitle("Carga Masiva de Personas");

        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);

        Span instructions = new Span(
                "Formato Excel: Col A = Cédula*, Col B = Nombre*, Col C = Cargo, Col D = Nota1, Col E = Nota2 (fila 1 = encabezado)");
        instructions.getStyle().set("font-size", "var(--lumo-font-size-s)");
        instructions.getStyle().set("color", "var(--lumo-secondary-text-color)");

        MemoryBuffer buffer = new MemoryBuffer();
        Upload upload = new Upload(buffer);
        upload.setAcceptedFileTypes(".xlsx", ".xls");
        upload.setMaxFiles(1);

        Span resultSpan = new Span();

        upload.addSucceededListener(e -> {
            try {
                String result = employeeService.importEmployees(buffer.getInputStream(), e.getFileName());
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
