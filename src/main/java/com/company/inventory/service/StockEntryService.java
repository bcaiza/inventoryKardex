package com.company.inventory.service;

import com.company.inventory.entity.Product;

import java.io.InputStream;
import java.util.UUID;

public interface StockEntryService {

    /**
     * Suma la cantidad al stock del producto (carga fresca) y guarda. Usado al registrar una nueva entrada.
     *
     * @return el producto actualizado y guardado.
     */
    Product incrementStock(UUID productId, int quantity);

    /**
     * Anula una entrada: revierte la cantidad del stock del producto (sin bajar de 0) y marca
     * la entrada como anulada, en una única transacción.
     *
     * @return el stock resultante sin recortar en 0, para que la UI pueda advertir si quedó negativo.
     */
    int cancelEntry(UUID entryId);

    /**
     * Importa entradas masivas desde un Excel: Código Producto, Fecha (dd/MM/yyyy), Cantidad, Motivo
     * (fila 1 = encabezado, todas obligatorias). Actualiza el stock de cada producto afectado.
     */
    String importEntries(InputStream inputStream, String filename) throws Exception;
}
