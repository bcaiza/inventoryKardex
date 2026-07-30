package com.company.inventory.view.medida;

import com.company.inventory.entity.Medida;
import com.company.inventory.view.main.MainView;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamResource;
import io.jmix.core.DataManager;
import io.jmix.flowui.download.Downloader;
import io.jmix.flowui.view.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

@Route(value = "medidas", layout = MainView.class)
@ViewController(id = "Medida.list")
@ViewDescriptor(path = "medida-list-view.xml")
@LookupComponent("medidasDataGrid")
@DialogMode(width = "50em")
public class MedidaListView extends StandardListView<Medida> {

}
