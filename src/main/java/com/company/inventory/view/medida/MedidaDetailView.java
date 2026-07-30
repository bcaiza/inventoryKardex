package com.company.inventory.view.medida;

import com.company.inventory.entity.Medida;
import com.company.inventory.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.*;

@Route(value = "medidas/:id", layout = MainView.class)
@ViewController(id = "Medida.detail")
@ViewDescriptor(path = "medida-detail-view.xml")
@EditedEntityContainer("medidaDc")
public class MedidaDetailView extends StandardDetailView<Medida> {
}
