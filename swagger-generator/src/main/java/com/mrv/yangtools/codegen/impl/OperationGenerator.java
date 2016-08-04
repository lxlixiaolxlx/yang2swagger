package com.mrv.yangtools.codegen.impl;

import com.mrv.yangtools.codegen.DataObjectRepo;
import com.mrv.yangtools.codegen.PathSegment;
import io.swagger.models.Operation;
import io.swagger.models.Response;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;

/**
 * Simple command that generates operation
 * @author bartosz.michalik@amartus.com
 */
public abstract class OperationGenerator {

    protected final PathSegment path;
    private final DataObjectRepo repo;

    protected OperationGenerator(PathSegment path, DataObjectRepo repo) {
        java.util.Objects.requireNonNull(path);
        java.util.Objects.requireNonNull(repo);
        this.path = path;
        this.repo = repo;
    }

    /**
     * Create operation for node.
     * @param node YANG node
     * @return Swagger operation
     */
    public Operation execute(DataSchemaNode node) {
        return defaultOperation();
    }

    protected String getDefinitionId(DataSchemaNode node) {
        return repo.getDefinitionId(node);
    }

    protected String getName(DataSchemaNode node) {
        return repo.getName(node);
    }

    /**
     * Default empty operation that defines error response only
     * @return basic operation with 400 response pre-defined
     */
    protected Operation defaultOperation() {
        final Operation operation = new io.swagger.models.Operation();
        operation.response(400, new Response().description("Internal error"));
        operation.setParameters(path.params());
        return operation;
    }
}