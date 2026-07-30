package com.company.inventory.service;

/**
 * Se lanza cuando se intenta registrar una salida por una cantidad mayor al stock disponible.
 */
public class InsufficientStockException extends RuntimeException {

    private final int available;
    private final int requested;

    public InsufficientStockException(int available, int requested) {
        super("Stock insuficiente. Disponible: " + available + " | Solicitado: " + requested);
        this.available = available;
        this.requested = requested;
    }

    public int getAvailable() {
        return available;
    }

    public int getRequested() {
        return requested;
    }
}
