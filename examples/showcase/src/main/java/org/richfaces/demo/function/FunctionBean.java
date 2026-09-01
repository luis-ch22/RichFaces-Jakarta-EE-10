package org.richfaces.demo.function;

import jakarta.inject.Named;
import jakarta.enterprise.context.RequestScoped;

@Named("functionBean")
@RequestScoped
public class FunctionBean {
    private String text;

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
