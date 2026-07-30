package com.company.inventory.service;

import com.company.inventory.entity.Product;

import java.io.InputStream;
import java.util.UUID;

public interface StockExitService {

    /**
     * Resta la cantidad del stock del producto (carga fresca) y guarda. Usado al registrar una nueva salida.
     *
     * @throws InsufficientStockException si el stock disponible es menor a la cantidad solicitada.
     * @return el producto actualizado y guardado.
     */
    Product decrementStock(UUID productId, int quantity);

    /**
     * Anula una salida: devuelve la cantidad al stock del producto y marca la salida como anulada,
     * en una única transacción.
     *
     * @return el stock restaurado del producto.
     */
    int cancelExit(UUID exitId);

    /**
     * Importa salidas masivas desde un Excel: Código Producto, Fecha (dd/MM/yyyy), Cantidad, Motivo
     * (fila 1 = encabezado, todas obligatorias). Actualiza el stock de cada producto afectado y
     * rechaza filas cuya cantidad supere el stock disponible.
     */
    String importExits(InputStream inputStream, String filename) throws Exception;
}
