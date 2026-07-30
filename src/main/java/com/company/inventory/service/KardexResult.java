package com.company.inventory.service;

import com.company.inventory.view.kardex.KardexRow;

import java.util.List;

/**
 * Resultado del cálculo de Kardex: filas visibles (ya filtradas por persona si aplica) y los
 * totales del periodo, listos para que la vista los muestre.
 */
public class KardexResult {

    private final List<KardexRow> rows;
    private final int stockActualTotal;
    private final int saldoInicialTotal;

    public KardexResult(List<KardexRow> rows, int stockActualTotal, int saldoInicialTotal) {
        this.rows = rows;
        this.stockActualTotal = stockActualTotal;
        this.saldoInicialTotal = saldoInicialTotal;
    }

    public List<KardexRow> getRows() {
        return rows;
    }

    public int getStockActualTotal() {
        return stockActualTotal;
    }

    public int getSaldoInicialTotal() {
        return saldoInicialTotal;
    }
}
