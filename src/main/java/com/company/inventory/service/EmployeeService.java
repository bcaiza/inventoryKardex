package com.company.inventory.service;

import java.io.InputStream;

public interface EmployeeService {

    /**
     * Importa/actualiza personas desde un Excel con columnas: Cédula*, Nombre*, Cargo, Nota1, Nota2
     * (fila 1 = encabezado).
     */
    String importEmployees(InputStream inputStream, String filename) throws Exception;
}
