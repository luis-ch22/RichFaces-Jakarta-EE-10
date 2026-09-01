package org.richfaces.demo.dropdownmenu;

import java.io.Serializable;

import jakarta.inject.Named;
import jakarta.faces.view.ViewScoped;

@Named
@ViewScoped
public class DropDownMenuBean implements Serializable {
    private static final long serialVersionUID = 1L;
    private String current;

    public String getCurrent() {
        return this.current;
    }

    public void setCurrent(String current) {
        this.current = current;
    }

    public String doNew() {
        this.current = "New";
        return null;
    }

    public String doOpen() {
        this.current = "Open";
        return null;
    }

    public String doClose() {
        this.current = "Close";
        return null;
    }

    public String doSave() {
        this.current = "Save";
        return null;
    }

    public String doSaveAll() {
        this.current = "Save All";
        return null;
    }

    public String doExit() {
        this.current = "Exit";
        return null;
    }
}
