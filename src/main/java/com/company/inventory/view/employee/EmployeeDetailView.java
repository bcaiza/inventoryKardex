package com.company.inventory.view.employee;

import com.company.inventory.entity.Employee;
import com.company.inventory.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.*;

@Route(value = "employees/:id", layout = MainView.class)
@ViewController(id = "Employee.detail")
@ViewDescriptor(path = "employee-detail-view.xml")
@EditedEntityContainer("employeeDc")
public class EmployeeDetailView extends StandardDetailView<Employee> {
}
