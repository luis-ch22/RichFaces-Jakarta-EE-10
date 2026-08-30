package org.richfaces.component;

import jakarta.faces.bean.ManagedBean;
import jakarta.faces.bean.SessionScoped;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@SessionScoped
@ManagedBean(name="graphBean")
public class GraphBean {
    public static final String FOO_MSG = "Foo";

    public static final String SHORT_MSG = "Short";

    public static final String PATTERN_MSG = "Pattern";

    public static final String FOO_VALUE = "fooValue";

    private String value = FOO_VALUE;

    /**
     * @return the value
     */
    @Size(min = 1, message = SHORT_MSG)
    @Pattern(regexp = ".*Value", message = PATTERN_MSG, groups = Group.class)
    public String getValue() {
        return value;
    }

    /**
     * @param value the value to set
     */
    public void setValue(String value) {
        this.value = value;
    }

    @AssertTrue(message = FOO_MSG)
    public boolean isValid() {
        return value.startsWith("foo");
    }
}
