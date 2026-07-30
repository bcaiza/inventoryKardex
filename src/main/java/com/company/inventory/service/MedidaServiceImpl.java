package com.company.inventory.service;

import com.company.inventory.entity.Medida;
import io.jmix.core.DataManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MedidaServiceImpl implements MedidaService {

    @Autowired
    private DataManager dataManager;

    @Override
    public Medida findOrCreateByName(String name) {
        List<Medida> existing = dataManager.load(Medida.class)
                .query("select e from Medida e where lower(e.name) = lower(:name)")
                .parameter("name", name)
                .list();

        if (!existing.isEmpty()) {
            return existing.get(0);
        }

        Medida medida = dataManager.create(Medida.class);
        medida.setName(name);
        return dataManager.save(medida);
    }
}
