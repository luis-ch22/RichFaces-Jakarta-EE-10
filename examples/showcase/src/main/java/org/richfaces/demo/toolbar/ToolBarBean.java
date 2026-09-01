package org.richfaces.demo.toolbar;

import java.io.Serializable;

import jakarta.inject.Named;
import jakarta.faces.view.ViewScoped;

@Named
@ViewScoped
public class ToolBarBean implements Serializable {
    private static final long serialVersionUID = 1L;
    private String groupSeparator;
    private String groupItemSeparator;

    public String getGroupItemSeparator() {
        return groupItemSeparator;
    }

    public void setGroupItemSeparator(String groupItemSeparator) {
        this.groupItemSeparator = groupItemSeparator;
    }

    public String getGroupSeparator() {
        return groupSeparator;
    }

    public void setGroupSeparator(String groupSeparator) {
        this.groupSeparator = groupSeparator;
    }
}
