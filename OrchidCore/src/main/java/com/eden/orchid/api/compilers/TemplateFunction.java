package com.eden.orchid.api.compilers;

import com.eden.orchid.api.options.OptionsHolder;
import com.eden.orchid.api.options.annotations.Description;
import com.eden.orchid.api.server.annotations.Extensible;
import com.eden.orchid.api.theme.pages.OrchidPage;
import lombok.Getter;
import lombok.Setter;

/**
 * Template Functions add methods that can be called by themselves or as a "filter" in an expression within the primary
 * template language.
 *
 * @since v1.0.0
 * @orchidApi extensible
 */
@Extensible
@Description(value = "A function that can be called from your templates.", name = "Template Functions")
public abstract class TemplateFunction implements OptionsHolder {

    @Getter
    protected final String name;

    @Getter
    protected final boolean isSafe;

    @Getter
    @Setter
    protected OrchidPage page;

    /**
     * Initialize the Function with the name which it should be called with in the template., and whether Whether this
     * Function returns "safe" HTML that should not be escaped by default. For filters that return anything other than a
     * String, `isSafe` should always be false. The actual implementation of a Function should have a single constructor
     * annotated with {@link javax.inject.Inject }.
     *
     * @param name the name which to call this Function
     */
    public TemplateFunction(String name, boolean isSafe) {
        this.name = name;
        this.isSafe = isSafe;
    }

    /**
     * The sequential parameters of this function
     *
     * @return the sequential parameters
     */
    public abstract String[] parameters();

    /**
     * Apply the filter or function here, returning the created or modified data.
     *
     * @return the result
     */
    public abstract Object apply();

}
