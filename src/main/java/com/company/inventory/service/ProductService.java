package com.company.inventory.service;

import com.company.inventory.entity.Product;
import com.company.inventory.entity.StockEntry;
import com.company.inventory.entity.StockExit;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface ProductService {

    /**
     * Carga el producto fresco desde la base de datos, incluyendo su medida (para mostrar la unidad).
     */
    Optional<Product> findFreshWithMedida(UUID productId);

    /**
     * Carga el producto fresco desde la base de datos con el fetch plan por defecto.
     */
    Product loadFresh(UUID productId);

    /**
     * Busca un producto por código, insensible a mayúsculas y espacios.
     */
    Optional<Product> findByCodeIgnoreCase(String code);

    /**
     * Cuenta los productos activos cuyo stock está en o por debajo del mínimo configurado.
     */
    long countActiveLowStock();

    /**
     * Devuelve los productos activos con mínimo configurado cuyo stock está en o por debajo
     * del mínimo + margen de alerta, ordenados por stock ascendente.
     */
    List<Product> findLowStockProducts();

    /**
     * Suma de cantidades de entradas vigentes (no anuladas), agrupadas por id de producto.
     */
    Map<UUID, Long> sumEntryQuantitiesByProduct();

    /**
     * Suma de cantidades de salidas vigentes (no anuladas), agrupadas por id de producto.
     */
    Map<UUID, Long> sumExitQuantitiesByProduct();

    /**
     * Entradas de un producto ordenadas por fecha de movimiento y creación.
     */
    List<StockEntry> findEntriesByProduct(UUID productId);

    /**
     * Salidas de un producto ordenadas por fecha de movimiento y creación.
     */
    List<StockExit> findExitsByProduct(UUID productId);

    /**
     * Elimina el producto junto con todas sus entradas y salidas del Kardex, en una única transacción.
     */
    void deleteCascade(Product product, List<StockEntry> entries, List<StockExit> exits);

    /**
     * Revisa todos los productos y, donde el stock real difiera de la suma de sus movimientos
     * vigentes, crea un único movimiento de ajuste (fechado a la creación del producto) que
     * explica la diferencia en el Kardex. No modifica el stock actual de ningún producto.
     *
     * @return mensaje resumen del resultado, listo para mostrar al usuario.
     */
    String reconcileOpeningStocks();

    /**
     * Importa/actualiza productos desde un Excel con columnas:
     * Código, Nombre, Descripción, Nota1, Nota2, Categoría, Medida (fila 1 = encabezado).
     */
    String importProducts(InputStream inputStream, String filename) throws Exception;

    /**
     * Importa/actualiza productos junto con su stock desde el formato "BASE DE DATOS BODEGA"
     * (Nº, Código, Descripción, Categoría, Unidad, Cantidad desde la fila 4), registrando el
     * delta de stock como movimiento de entrada o salida en el Kardex.
     */
    String importProductsWithStock(InputStream inputStream, String filename) throws Exception;

    /**
     * Actualiza el stock de productos existentes desde un Excel (Código, Nuevo Stock), registrando
     * el delta como movimiento de entrada o salida en el Kardex.
     */
    String updateStockFromExcel(InputStream inputStream, String filename) throws Exception;
}
