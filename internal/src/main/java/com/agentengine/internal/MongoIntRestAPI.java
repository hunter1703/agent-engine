package com.agentengine.internal;

import com.agentengine.util.mongodb.mongo.AbstractMongoReadRepository;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Operational endpoints for MongoDB. Served under {@code /internal}, which comes from
 * {@code quarkus.http.root-path} rather than being repeated in every path here.
 */
@Path("/mongo")
@Produces(MediaType.APPLICATION_JSON)
public class MongoIntRestAPI {

    private static final int MULTI_STATUS = 207;

    private static final Logger LOG = LoggerFactory.getLogger(MongoIntRestAPI.class);

    private final Instance<AbstractMongoReadRepository<?>> repositories;

    @Inject
    public MongoIntRestAPI(final Instance<AbstractMongoReadRepository<?>> repositories) {
        this.repositories = repositories;
    }

    @POST
    @Path("/ensure-index")
    public Response ensureIndex() {
        final List<EnsureIndexesResult> results = new ArrayList<>();
        for (final AbstractMongoReadRepository<?> repository : repositories) {
            results.add(ensureFor(repository));
        }
        final boolean anyFailed = results.stream().anyMatch(result -> result.error() != null);
        return Response.status(anyFailed ? MULTI_STATUS : Response.Status.OK.getStatusCode())
                .entity(results)
                .build();
    }

    private EnsureIndexesResult ensureFor(final AbstractMongoReadRepository<?> repository) {
        try {
            return EnsureIndexesResult.succeeded(repository.collectionName(), repository.ensureIndexes());
        } catch (final RuntimeException exception) {
            LOG.error("Failed to ensure indexes for collection {}", repository.collectionName(), exception);
            return EnsureIndexesResult.failed(repository.collectionName(), exception.toString());
        }
    }

    public record EnsureIndexesResult(String collection, List<String> indexes, String error) {

        static EnsureIndexesResult succeeded(final String collection, final List<String> indexes) {
            return new EnsureIndexesResult(collection, indexes, null);
        }

        static EnsureIndexesResult failed(final String collection, final String error) {
            return new EnsureIndexesResult(collection, List.of(), error);
        }
    }
}
