package com.mrv.yangtools.maven.gen.swagger;

import com.google.common.base.Preconditions;
import com.mrv.yangtools.codegen.SwaggerGenerator;
import com.mrv.yangtools.codegen.impl.SegmentTagGenerator;
import org.apache.maven.project.MavenProject;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang2sources.spi.BasicCodeGenerator;
import org.opendaylight.yangtools.yang2sources.spi.BuildContextAware;
import org.opendaylight.yangtools.yang2sources.spi.MavenProjectAware;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author bartosz.michalik@amartus.com
 */
public class MavenSwaggerGenerator implements BasicCodeGenerator, BuildContextAware, MavenProjectAware {

    private static final Logger log = LoggerFactory.getLogger(MavenSwaggerGenerator.class);
    private MavenProject mavenProject;


    public static final String DEFAULT_OUTPUT_BASE_DIR_PATH = "target" + File.separator + "generated-sources"
            + File.separator + "swagger-maven-api-gen";
    private File projectBaseDir;
    private Map<String, String> additionalConfig;
    private File resourceBaseDir;

    @Override
    public Collection<File> generateSources(SchemaContext schemaContext, File outputDir, Set<Module> modules) throws IOException {
        final File outputBaseDir = outputDir == null ? getDefaultOutputBaseDir() : outputDir;

        if(! outputBaseDir.exists()) {
            if(! outputBaseDir.mkdirs()) {
                throw new IllegalStateException("cannot create " + outputBaseDir);
            }
        }

        File output = new File(outputBaseDir, "yang.swagger");


        List<String> mimes = Arrays.asList(System.getProperty("generator-mime", "json,xml").split(","));
        List<SwaggerGenerator.Elements> elements = Arrays.stream(System.getProperty("generator-elements", "DATA,RCP").split(","))
                .filter(e -> {try{ SwaggerGenerator.Elements.valueOf(e); return true;} catch(IllegalArgumentException ex) {return false;}})
                .map(SwaggerGenerator.Elements::valueOf).collect(Collectors.toList());



        try(FileWriter fileWriter = new FileWriter(output)) {
            SwaggerGenerator generator = new SwaggerGenerator(schemaContext, modules)
                    .tagGenerator(new SegmentTagGenerator());

            mimes.forEach(m -> { generator.consumes("application/"+ m); generator.produces("application/"+ m);});
            generator.elements(elements.toArray(new SwaggerGenerator.Elements[elements.size()]));
            generator.generate(fileWriter);
        };

        return Collections.singleton(output);
    }

    @Override
    public void setAdditionalConfig(Map<String, String> additionalConfiguration) {
        this.additionalConfig = additionalConfiguration;
    }

    @Override
    public void setResourceBaseDir(File resourceBaseDir) {
        this.resourceBaseDir = resourceBaseDir;
    }

    @Override
    public void setBuildContext(org.sonatype.plexus.build.incremental.BuildContext buildContext) {
        log.debug("bc {}", buildContext);
    }

    @Override
    public void setMavenProject(org.apache.maven.project.MavenProject project) {
        this.mavenProject = project;
        this.projectBaseDir = project.getBasedir();

    }
    private File getDefaultOutputBaseDir() {
        File outputBaseDir;
        outputBaseDir = new File(DEFAULT_OUTPUT_BASE_DIR_PATH);
        setOutputBaseDirAsSourceFolder(outputBaseDir, mavenProject);
        log.debug("Adding " + outputBaseDir.getPath() + " as compile source root");
        return outputBaseDir;
    }

    private static void setOutputBaseDirAsSourceFolder(final File outputBaseDir, final MavenProject mavenProject) {
        Preconditions.checkNotNull(mavenProject, "Maven project needs to be set in this phase");
        mavenProject.addCompileSourceRoot(outputBaseDir.getPath());
    }

}
