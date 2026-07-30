package com.company.inventory.service;

import com.company.inventory.entity.Medida;

public interface MedidaService {

    /**
     * Busca una medida por nombre (insensible a mayúsculas). Si no existe, la crea y la guarda.
     */
    Medida findOrCreateByName(String name);
}
