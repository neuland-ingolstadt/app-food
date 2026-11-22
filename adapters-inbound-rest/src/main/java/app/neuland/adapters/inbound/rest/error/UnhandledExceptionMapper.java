package app.neuland.adapters.inbound.rest.error;

import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

@Provider
public class UnhandledExceptionMapper
        extends AbstractProblemExceptionMapper<Throwable> {

    private static final Logger LOG = Logger.getLogger(UnhandledExceptionMapper.class);

    @Override
    protected Problem problem(Throwable exception) {
        // JAX-RS reports query parameters that cannot be converted (e.g. an unknown restaurant) as 404
        if (exception instanceof NotFoundException
                && exception.getCause() instanceof IllegalArgumentException cause) {
            return problem(Response.Status.BAD_REQUEST, "Invalid request parameter: " + cause.getMessage());
        }
        if (exception instanceof WebApplicationException webApplicationException) {
            return problem(statusOf(webApplicationException));
        }

        LOG.error("Unhandled exception", exception);

        return problem(Response.Status.INTERNAL_SERVER_ERROR, "Unexpected error");
    }

    private Response.Status statusOf(WebApplicationException exception) {
        Response.Status status = Response.Status.fromStatusCode(
                exception.getResponse()
                        .getStatus()
        );
        return status != null ? status : Response.Status.INTERNAL_SERVER_ERROR;
    }
}
