package org.richfaces.demo.autocomplete;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.enterprise.context.RequestScoped;

import org.richfaces.demo.tables.model.capitals.Capital;

@Named
@RequestScoped
public class AutocompleteBean {
    private String value;
    private List<String> autocompleteList;
    @Inject
    private List<Capital> capitals;

    public AutocompleteBean() {
    }

    @PostConstruct
    public void init() {
        autocompleteList = new ArrayList<String>();
        for (Capital cap : capitals) {
            autocompleteList.add(cap.getState());
        }
    }

    public List<String> autocomplete(String prefix) {
        ArrayList<String> result = new ArrayList<String>();
        if ((prefix == null) || (prefix.length() == 0)) {
            for (int i = 0; i < 10; i++) {
                result.add(capitals.get(i).getState());
            }
        } else {
            Iterator<Capital> iterator = capitals.iterator();
            while (iterator.hasNext()) {
                Capital elem = ((Capital) iterator.next());
                if ((elem.getState() != null && elem.getState().toLowerCase().indexOf(prefix.toLowerCase()) == 0)
                    || "".equals(prefix)) {
                    result.add(elem.getState());
                }
            }
        }

        return result;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public List<String> getAutocompleteList() {
        return autocompleteList;
    }

    public void setAutocompleteList(List<String> autocompleteList) {
        this.autocompleteList = autocompleteList;
    }

    public List<Capital> getCapitals() {
        return capitals;
    }

    public void setCapitals(List<Capital> capitals) {
        this.capitals = capitals;
    }
}
