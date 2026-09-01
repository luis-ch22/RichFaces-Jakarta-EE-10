package org.richfaces.demo.tables;

import java.io.Serializable;
import java.util.List;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.enterprise.context.SessionScoped;

import org.richfaces.demo.tables.model.capitals.Capital;

@Named
@SessionScoped
public class CapitalsBean implements Serializable {
    private static final long serialVersionUID = -1509108399715814302L;
    @Inject
    private List<Capital> capitals;

    public List<Capital> getCapitals() {
        return capitals;
    }

    public void setCapitals(List<Capital> capitals) {
        this.capitals = capitals;
    }
}
