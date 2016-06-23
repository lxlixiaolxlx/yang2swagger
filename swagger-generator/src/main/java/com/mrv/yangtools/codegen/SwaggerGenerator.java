package com.mrv.yangtools.codegen;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.swagger.models.*;
import io.swagger.models.parameters.BodyParameter;
import io.swagger.models.properties.RefProperty;
import org.opendaylight.yangtools.yang.model.api.*;

import java.io.IOException;
import java.io.Writer;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author bartosz.michalik@amartus.com
 */
public class SwaggerGenerator {
    private final SchemaContext ctx;
    private final Set<Module> modules;
    private final Swagger target;
    private final DataObjectsBuilder dataObjectsBuilder;
    private ObjectMapper mapper;


    public enum Format { YAML, JSON }

    public SwaggerGenerator(SchemaContext ctx, Set<Module> modulesToGenerate) {
        Objects.requireNonNull(ctx);
        Objects.requireNonNull(modulesToGenerate);
        this.ctx = ctx;
        this.modules = modulesToGenerate;
        target = new Swagger();
        dataObjectsBuilder = new DataObjectsBuilder(ctx);

        //no exposed swagger API
        target.info(new Info());

        //setting defaults
        this
            .host("localhost:8080")
            .basePath("/restconf")
            .consumes("application/json")
            .produces("application/json")
            .version("1.0.0-SNAPSHOT")
            .format(Format.YAML);
    }

    private SwaggerGenerator version(String version) {
        target.getInfo().version(version);
        return this;
    }


    public SwaggerGenerator format(Format f) {
        switch(f) {
            case YAML:
                mapper = new ObjectMapper(new YAMLFactory());
                break;
            case JSON:
            default:
                mapper = new ObjectMapper(new JsonFactory());
        }
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        return this;
    }

    public SwaggerGenerator host(String host) {
        target.host(host);
        return this;
    }


    public SwaggerGenerator basePath(String basePath) {
        target.basePath(basePath);
        return this;
    }

    public SwaggerGenerator consumes(String consumes) {
        Objects.requireNonNull(consumes);
        target.consumes(consumes);
        return this;
    }

    public SwaggerGenerator produces(String produces) {
        Objects.requireNonNull(produces);
        target.produces(produces);
        return this;
    }

    public void generate(Writer writer) throws IOException {
        if(writer == null) throw new NullPointerException();

        Swagger target = generate();

        mapper.writeValue(writer, target);
    }

    protected Swagger generate() throws IOException {

        ArrayList<String> mNames = new ArrayList<>();

        modules.stream().forEach(m -> {
            mNames.add(m.getName());
            dataObjectsBuilder.processModule(m);
            new ModuleGenerator(m).generate();

        });

        // update info with module names
        String modules = mNames.stream().collect(Collectors.joining(","));
        target.getInfo()
                .description(modules + " API generated from yang definitions")
                .title(modules + " API");
        return target;
    }


    private class ModuleGenerator {
        private final Module module;
        private PathSegment pathCtx;

        private ModuleGenerator(Module module) {
            this.module = module;
            pathCtx = new PathSegment("/data", module.getName(), ctx);

        }

        void generate() {
            module.getChildNodes().forEach(this::generate);
        }

        private void generate(DataSchemaNode node) {

            if(node instanceof ContainerSchemaNode) {
                final ContainerSchemaNode cN = (ContainerSchemaNode) node;
                pathCtx = pathCtx.attach(new PathSegment(cN.getQName().getLocalName(), module.getName(), ctx));

                addPath(cN);

                cN.getChildNodes().forEach(this::generate);
                target.addDefinition(dataObjectsBuilder.getName(cN), dataObjectsBuilder.build(cN));

                pathCtx = pathCtx.drop();
            } else if(node instanceof ListSchemaNode) {
                final ListSchemaNode lN = (ListSchemaNode) node;
                PathSegment child = new PathSegment(lN.getQName().getLocalName(), module.getName(), ctx);
                child.attachNode(lN);
                pathCtx = pathCtx.attach(child);

                addPath(lN);
                lN.getChildNodes().forEach(this::generate);
                target.addDefinition(dataObjectsBuilder.getName(lN), dataObjectsBuilder.build(lN));

                pathCtx = pathCtx.drop();
            }
        }

        private Operation defaultOperation() {
            final Operation operation = new Operation();
            operation.response(400, new Response().description("Internal error"));
            operation.setParameters(pathCtx.params());
            return operation;
        }

        private Operation listOperation() {
            final Operation operation = new Operation();
            operation.response(400, new Response().description("Internal error"));
            operation.setParameters(pathCtx.listParams());
            return operation;
        }


        protected Operation getOp(DataSchemaNode node) {
            final Operation get = defaultOperation();
            final RefModel definition = new RefModel(dataObjectsBuilder.getDefinitionId(node));
            get.description("returns " + dataObjectsBuilder.getName(node));
            get.response(200, new Response().schema(new RefProperty(definition.getReference())).description(dataObjectsBuilder.getName(node)));
            return get;
        }

        protected Operation deleteOp(DataSchemaNode node) {
            final Operation delete = defaultOperation();
            delete.description("removes " + dataObjectsBuilder.getName(node));
            delete.response(204, new Response().description("Object deleted"));
            return delete;
        }

        protected Operation putOp(DataSchemaNode node) {
            final Operation put = defaultOperation();
            final RefModel definition = new RefModel(dataObjectsBuilder.getDefinitionId(node));
            put.description("creates or updates " + dataObjectsBuilder.getName(node));
            put.parameter(new BodyParameter()
                            .name("body-param")
                            .schema(definition)
                            .description(dataObjectsBuilder.getName(node) + " to be added or updated"));

            put.response(201, new Response().description("Object created"));
            put.response(204, new Response().description("Object modified"));
            return put;
        }

        protected Operation postOp(DataSchemaNode node, boolean dropLastSegmentParams) {
            final Operation post = dropLastSegmentParams ? listOperation() : defaultOperation();
            final RefModel definition = new RefModel(dataObjectsBuilder.getDefinitionId(node));
            post.description("Creates" + dataObjectsBuilder.getName(node));
            post.parameter(new BodyParameter()
                    .name("body-param")
                    .schema(definition)
                    .description(dataObjectsBuilder.getName(node) + " to be added to list"));

            post.response(201, new Response().description("Object created"));
            post.response(409, new Response().description("Object already exists"));
            return post;
        }

        private void addPath(ListSchemaNode lN) {
            final Path path = new Path();

            path.get(getOp(lN));
            path.put(putOp(lN));
            path.post(postOp(lN, false));
            path.delete(deleteOp(lN));


            target.path(pathCtx.path(), path);

            //add list path
            final Path list = new Path();
            list.post(postOp(lN, true));

            target.path(pathCtx.listPath(), list);

        }

        private void addPath(ContainerSchemaNode cN) {
            final Path path = new Path();
            path.get(getOp(cN));
            path.put(putOp(cN));
            path.put(postOp(cN, false));
            path.delete(deleteOp(cN));
            target.path(pathCtx.path(), path);
        }
    }
}
