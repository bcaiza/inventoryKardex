package com.company.inventory.service;

import com.company.inventory.entity.Category;

public interface CategoryService {

    /**
     * Busca una categoría por nombre (insensible a mayúsculas). Si no existe, la crea y la guarda.
     */
    Category findOrCreateByName(String name);
}
