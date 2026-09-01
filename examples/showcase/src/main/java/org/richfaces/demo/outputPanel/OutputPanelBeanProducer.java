package org.richfaces.demo.outputPanel;

import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;

/**
 * CDI producers replacing the two JSF managed-bean declarations (opBean1, opBean2)
 * that previously lived in faces-config.xml. The outputPanel demo needs two
 * independent request-scoped instances of {@link OutputPanelBean} under different
 * EL names, which is why {@code OutputPanelBean} itself is not annotated.
 */
public class OutputPanelBeanProducer {

    @Produces
    @Named("opBean1")
    @RequestScoped
    public OutputPanelBean createOpBean1() {
        return new OutputPanelBean();
    }

    @Produces
    @Named("opBean2")
    @RequestScoped
    public OutputPanelBean createOpBean2() {
        return new OutputPanelBean();
    }
}
