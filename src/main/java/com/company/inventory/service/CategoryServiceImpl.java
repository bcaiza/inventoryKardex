package com.company.inventory.service;

import com.company.inventory.entity.Category;
import io.jmix.core.DataManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CategoryServiceImpl implements CategoryService {

    @Autowired
    private DataManager dataManager;

    @Override
    public Category findOrCreateByName(String name) {
        List<Category> existing = dataManager.load(Category.class)
                .query("select e from Category e where lower(e.name) = lower(:name)")
                .parameter("name", name)
                .list();

        if (!existing.isEmpty()) {
            return existing.get(0);
        }

        Category category = dataManager.create(Category.class);
        category.setName(name);
        return dataManager.save(category);
    }
}
