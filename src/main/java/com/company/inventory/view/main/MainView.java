package com.company.inventory.view.main;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.router.Route;
import io.jmix.core.security.CurrentAuthentication;
import io.jmix.flowui.DialogWindows;
import io.jmix.flowui.app.main.StandardMainView;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.view.DialogWindow;
import io.jmix.flowui.view.Subscribe;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;
import io.jmix.securityflowui.view.changepassword.ChangePasswordView;
import org.springframework.beans.factory.annotation.Autowired;

@Route("")
@ViewController(id = "MainView")
@ViewDescriptor(path = "main-view.xml")
public class MainView extends StandardMainView {


    @Autowired
    private DialogWindows dialogWindows;
    @Autowired
    private CurrentAuthentication currentAuthentication;

    @Subscribe("passwordButton")
    public void onPasswordButtonClick(final ClickEvent<JmixButton> event) {
        DialogWindow<ChangePasswordView> window =
                dialogWindows.view(this, ChangePasswordView.class).build();
        window.getView().setUsername(currentAuthentication.getUser().getUsername());
        window.getView().setCurrentPasswordRequired(false);
        window.open();
    }
}
