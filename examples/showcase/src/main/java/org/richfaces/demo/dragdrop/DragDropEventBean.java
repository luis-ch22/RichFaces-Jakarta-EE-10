package org.richfaces.demo.dragdrop;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.enterprise.context.RequestScoped;

import org.richfaces.event.DropEvent;
import org.richfaces.event.DropListener;

@Named
@RequestScoped
public class DragDropEventBean implements DropListener {
    @Inject
    private DragDropBean dragDropBean;

    public void setDragDropBean(DragDropBean dragDropBean) {
        this.dragDropBean = dragDropBean;
    }

    public void processDrop(DropEvent event) {
        dragDropBean.moveFramework((Framework) event.getDragValue());
    }
}