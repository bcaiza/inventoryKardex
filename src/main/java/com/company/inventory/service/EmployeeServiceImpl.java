package com.company.inventory.service;

import com.company.inventory.entity.Employee;
import io.jmix.core.DataManager;
import io.jmix.core.SaveContext;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.company.inventory.service.ExcelCellUtils.getCellString;

@Service
public class EmployeeServiceImpl implements EmployeeService {

    @Autowired
    private DataManager dataManager;

    @Override
    public String importEmployees(InputStream inputStream, String filename) throws Exception {
        Workbook workbook = filename.toLowerCase().endsWith(".xlsx")
                ? new XSSFWorkbook(inputStream)
                : new HSSFWorkbook(inputStream);

        Sheet sheet = workbook.getSheetAt(0);
        Map<String, Employee> toSave = new LinkedHashMap<>();
        int created = 0, updated = 0, errors = 0;

        for (Row row : sheet) {
            if (row.getRowNum() == 0) continue;

            String documentId = getCellString(row.getCell(0));
            if (documentId == null || documentId.isBlank()) continue;
            documentId = documentId.trim();

            String name = getCellString(row.getCell(1));
            if (name == null || name.isBlank()) { errors++; continue; }

            String cargo = getCellString(row.getCell(2));
            String note1 = getCellString(row.getCell(3));
            String note2 = getCellString(row.getCell(4));

            String normalizedDoc = documentId.toLowerCase();
            Employee employee = toSave.get(normalizedDoc);
            boolean isNew = false;

            if (employee == null) {
                List<Employee> existing = dataManager.load(Employee.class)
                        .query("select e from Employee e where lower(trim(e.documentId)) = :doc")
                        .parameter("doc", normalizedDoc)
                        .list();
                employee = existing.isEmpty() ? dataManager.create(Employee.class) : existing.get(0);
                isNew = existing.isEmpty();
                if (isNew) created++; else updated++;
            }

            employee.setDocumentId(documentId);
            employee.setName(name);
            employee.setCargo(cargo);
            employee.setNote1(note1);
            employee.setNote2(note2);

            toSave.put(normalizedDoc, employee);
        }

        workbook.close();

        if (!toSave.isEmpty()) {
            SaveContext ctx = new SaveContext();
            toSave.values().forEach(ctx::saving);
            dataManager.save(ctx);
        }

        return String.format("Importación completada: %d creadas, %d actualizadas, %d errores", created, updated, errors);
    }
}
