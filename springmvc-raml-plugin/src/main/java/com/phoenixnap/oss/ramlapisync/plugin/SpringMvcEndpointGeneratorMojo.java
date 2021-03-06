/*
 * Copyright 2002-2016 the original author or authors.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.phoenixnap.oss.ramlapisync.plugin;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.jsonschema2pojo.Annotator;
import org.jsonschema2pojo.GenerationConfig;
import org.jsonschema2pojo.Jackson1Annotator;
import org.raml.model.Raml;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import com.phoenixnap.oss.ramlapisync.data.ApiBodyMetadata;
import com.phoenixnap.oss.ramlapisync.data.ApiControllerMetadata;
import com.phoenixnap.oss.ramlapisync.generation.RamlParser;
import com.phoenixnap.oss.ramlapisync.generation.rules.ConfigurableRule;
import com.phoenixnap.oss.ramlapisync.generation.rules.Rule;
import com.phoenixnap.oss.ramlapisync.generation.rules.Spring4ControllerStubRule;
import com.phoenixnap.oss.ramlapisync.naming.NamingHelper;
import com.phoenixnap.oss.ramlapisync.naming.SchemaHelper;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JDefinedClass;

/**
 * Maven Plugin MOJO specific to Generation of Spring MVC Endpoints from RAML documents.
 *
 * @author Kurt Paris
 * @since 0.2.1
 */
@Mojo(name = "generate-springmvc-endpoints", requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, threadSafe = true)
public class SpringMvcEndpointGeneratorMojo extends AbstractMojo {

	/**
	 * Maven project - required for project info
	 */
	@Parameter(defaultValue = "${project}", required = true, readonly = true)
	protected MavenProject project;

	@Component
	private PluginDescriptor descriptor;

	/**
	 * Path to the raml document to be verified
	 */
	@Parameter(required = true, readonly = true, defaultValue = "")
	protected String ramlPath;

	/**
	 * Relative file path where the Java files will be saved to
	 */
	@Parameter(required = false, readonly = true, defaultValue = "")
	protected String outputRelativePath;

	/**
	 * IF this is set to true, we will only parse methods that consume, produce or accept the requested defaultMediaType
	 */
	@Parameter(required = false, readonly = true, defaultValue = "false")
	protected Boolean addTimestampFolder;
	
	/**
	 * IF this is set to true, we will pass on this configuration to the jsonschema2pojo library for creation of Longs instead of Ints
	 */
	@Parameter(required = false, readonly = true, defaultValue = "false")
	protected Boolean schemaUseLongIntegers;

	/**
	 * Java package to be applied to the generated files
	 */
	@Parameter(required = true, readonly = true, defaultValue = "")
	protected String basePackage;

	/**
	 * The explicit base path under which the rest endpoints should be located.
	 * If overrules the baseUri setting in the raml spec.
	 */
	@Parameter(required = false, readonly = true)
	protected String baseUri;

	/**
	 * If set to true, we will generate seperate methods for different content types in the RAML
	 */
	@Parameter(required = false, readonly = true, defaultValue = "false")
	protected Boolean seperateMethodsByContentType;

	/**
	 * If set to true, we will generate Jackson 1 annotations inside the model objects
	 */
	@Parameter(required = false, readonly = true, defaultValue = "false")
	protected Boolean useJackson1xCompatibility;

	/**
	 * The full qualified name of the Rule that should be used for code generation.
	 */
	@Parameter(required = false, readonly = true, defaultValue = "com.phoenixnap.oss.ramlapisync.generation.rules.Spring4ControllerStubRule")
	protected String rule;
	
	/**
	 * Map of key/value configuration parameters that can be used to modify behaviour or certain rules
	 */
	@Parameter(required = false, readonly = true)
	protected Map<String, String> ruleConfiguration = new LinkedHashMap<>();

	private ClassRealm classRealm;

	protected void generateEndpoints() throws MojoExecutionException, MojoFailureException, IOException {

		String resolvedPath = project.getBasedir().getAbsolutePath();
		if (resolvedPath.endsWith(File.separator) || resolvedPath.endsWith("/")) {
			resolvedPath = resolvedPath.substring(0, resolvedPath.length()-1);
		}
		String resolvedRamlPath = project.getBasedir().getAbsolutePath();
		if (!ramlPath.startsWith(File.separator) && !ramlPath.startsWith("/")) {
			resolvedRamlPath += File.separator + ramlPath;
		} else {
			resolvedRamlPath += ramlPath;
		}
		Raml loadRamlFromFile = RamlParser.loadRamlFromFile( "file:"+resolvedRamlPath );
		RamlParser par = new RamlParser(basePackage, getBasePath(loadRamlFromFile), seperateMethodsByContentType);
		Set<ApiControllerMetadata> controllers = par.extractControllers(loadRamlFromFile);

		if (StringUtils.hasText(outputRelativePath)) {
			if (!outputRelativePath.startsWith(File.separator) && !outputRelativePath.startsWith("/")) {
				resolvedPath += File.separator;
			}
			resolvedPath += outputRelativePath;
		} else {
			resolvedPath += "/target/generated-sources/spring-mvc";
		}

		File rootDir = new File (resolvedPath + (addTimestampFolder == true ? System.currentTimeMillis() : "") + "/");

		if (!rootDir.exists() && !rootDir.mkdirs()) {
			throw new IOException("Could not create directory:" + rootDir.getAbsolutePath());
		}
		generateCode(controllers, rootDir);
	}

	private void generateCode(Set<ApiControllerMetadata> controllers, File rootDir) {
		for (ApiControllerMetadata met :controllers) {
			this.getLog().debug("");
			this.getLog().debug("-----------------------------------------------------------");
			this.getLog().debug(met.getName());
			this.getLog().debug("");

			Set<ApiBodyMetadata> dependencies = met.getDependencies();
			for (ApiBodyMetadata body : dependencies) {
				GenerationConfig config = SchemaHelper.getGenerationConfig(null, null, null, this.schemaUseLongIntegers);
				generateModelSources(met, body, rootDir, config, useJackson1xCompatibility == true ? new Jackson1Annotator() : null );
			}

			generateControllerSource(met, rootDir);
		}
	}

	/*
	 * @return The configuration property <baseUri> (if set) or the baseUri from the RAML spec.
     */
	private String getBasePath(Raml loadRamlFromFile) {
		// we take the given baseUri from raml spec by default.
		String basePath = loadRamlFromFile.getBaseUri();

		// If the baseUri is explicitly set by the plugin configuration we take it.
		if(baseUri != null) {
			basePath = baseUri;
		}

		// Because we can't load an empty string parameter value from maven config
		// the user needs to set a single "/", to overrule the raml spec.
		if(basePath != null && basePath.equals("/")) {
			// We remove a single "/" cause the leading slash will be generated by the raml endpoints.
			basePath = "";
		}

		return basePath;
	}

	@SuppressWarnings("unchecked")
	private Rule<JCodeModel, JDefinedClass, ApiControllerMetadata> loadRule() {
		Rule<JCodeModel, JDefinedClass, ApiControllerMetadata> ruleInstance = new Spring4ControllerStubRule();
		try {
			ruleInstance = (Rule<JCodeModel, JDefinedClass, ApiControllerMetadata>) getClassRealm().loadClass(rule).newInstance();
			System.out.println(StringUtils.collectionToCommaDelimitedString(ruleConfiguration.keySet()));
			System.out.println(StringUtils.collectionToCommaDelimitedString(ruleConfiguration.values()));
			
			if (ruleInstance instanceof ConfigurableRule<?,?,?> && !CollectionUtils.isEmpty(ruleConfiguration)) {
				System.out.println("SETTING CONFIG");
				((ConfigurableRule<?, ?, ?>)ruleInstance).applyConfiguration(ruleConfiguration);
			}
		} catch (Exception e) {
			getLog().error("Could not instantiate Rule "+ this.rule +". The default Rule will be used for code generation.", e);
		}
		return ruleInstance;
	}

	private ClassRealm getClassRealm() throws DependencyResolutionRequiredException, MalformedURLException {
		if(classRealm == null) {
			List<String> runtimeClasspathElements = project.getRuntimeClasspathElements();
			classRealm = descriptor.getClassRealm();
			for (String element : runtimeClasspathElements)
			{
				File elementFile = new File(element);
				classRealm.addURL(elementFile.toURI().toURL());
			}
		}
		return classRealm;
	}

	private void generateModelSources(ApiControllerMetadata met, ApiBodyMetadata body, File rootDir, GenerationConfig config, Annotator annotator) {
		try {
            JCodeModel codeModel;
            if (config ==null && annotator == null) {
            	codeModel = body.getCodeModel();
            } else {
            	codeModel = body.getCodeModel(basePackage + NamingHelper.getDefaultModelPackage(), config, annotator);
            }
            if (codeModel != null) {
                codeModel.build(rootDir);
            }
        } catch (IOException e) {
            e.printStackTrace();
            this.getLog().error("Could not build code model for " + met.getName(), e);
        }
	}

	private void generateControllerSource(ApiControllerMetadata met, File dir) {
		JCodeModel codeModel = new JCodeModel();
		loadRule().apply(met, codeModel);
		try {
			codeModel.build(dir);
		} catch (IOException e) {
			e.printStackTrace();
			this.getLog().error("Could not build code model for " + met.getName(), e);
		}
	}

	public void execute() throws MojoExecutionException, MojoFailureException {
		long startTime = System.currentTimeMillis();

		try {
			generateEndpoints();
		} catch (IOException e) {
			ClassLoaderUtils.restoreOriginalClassLoader();
			throw new MojoExecutionException(e, "Unexpected exception while executing Spring MVC Endpoint Generation Plugin.",
					e.toString());
		}

		this.getLog().info("Endpoint Generation Complete in:" + (System.currentTimeMillis() - startTime) + "ms");
	}

}
