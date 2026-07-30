package com.company.inventory.service;

import com.company.inventory.entity.Category;
import com.company.inventory.entity.Employee;
import com.company.inventory.entity.Product;
import com.company.inventory.view.kardex.KardexRow;

import java.time.LocalDate;
import java.util.List;

public interface KardexService {

    /**
     * Resuelve la lista de productos a consultar: el producto seleccionado si lo hay (validando
     * que coincida con la categoría si ambos están seleccionados), o todos los productos activos
     * de la categoría seleccionada.
     */
    List<Product> resolveProducts(Product product, Category category);

    /**
     * Calcula las filas de movimientos (entradas y salidas) de los productos dados dentro del
     * periodo, con el saldo corrido calculado a partir del stock real actual (no del historial),
     * y los totales de stock actual / saldo inicial del periodo. Si se indica un empleado, las
     * filas visibles se filtran a solo sus salidas (el saldo sigue reflejando todos los movimientos).
     */
    KardexResult build(List<Product> products, LocalDate from, LocalDate to, Employee employeeFilter);

    /**
     * Genera el archivo Excel del Kardex para las filas dadas.
     */
    byte[] buildExcel(List<KardexRow> rows);
}
