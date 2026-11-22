package app.neuland.adapters.inbound.rest.error;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

@Provider
public class MealNotFoundExceptionMapper extends AbstractProblemExceptionMapper<MealNotFoundException> {

    @Override
    protected Problem problem(MealNotFoundException exception) {
        return problem(Response.Status.NOT_FOUND, exception.getMessage());
    }
}
