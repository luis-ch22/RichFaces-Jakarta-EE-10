package org.richfaces.demo.focus;

import jakarta.inject.Named;
import jakarta.enterprise.context.RequestScoped;

import org.richfaces.application.ServiceTracker;
import org.richfaces.focus.FocusManager;

@RequestScoped
@Named
public class FocusManagerBean {

    public void preRenderView() {
        FocusManager focusManager = ServiceTracker.getService(FocusManager.class);
        focusManager.focus("input2");
    }
}
