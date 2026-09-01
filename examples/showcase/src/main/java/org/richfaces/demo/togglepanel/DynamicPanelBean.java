package org.richfaces.demo.togglepanel;

import java.io.Serializable;

import jakarta.inject.Named;
import jakarta.enterprise.context.SessionScoped;

@SessionScoped
@Named
public class DynamicPanelBean implements Serializable {
    private static final long serialVersionUID = 1L;

    private String activeTab;

    public String getActiveTab() {
        return activeTab;
    }

    public void setActiveTab(String activeTab) {
        this.activeTab = activeTab;
    }
}
