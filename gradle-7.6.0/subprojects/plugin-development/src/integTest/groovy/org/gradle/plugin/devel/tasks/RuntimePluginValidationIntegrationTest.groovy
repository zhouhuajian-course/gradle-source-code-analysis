/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.plugin.devel.tasks

import org.gradle.internal.reflect.problems.ValidationProblemId
import org.gradle.internal.reflect.validation.ValidationTestFor
import org.gradle.test.fixtures.file.TestFile

import static org.gradle.internal.reflect.validation.Severity.ERROR
import static org.gradle.internal.reflect.validation.Severity.WARNING

class RuntimePluginValidationIntegrationTest extends AbstractPluginValidationIntegrationSpec {

    @Override
    def setup() {
        expectReindentedValidationMessage()
        buildFile << """
            tasks.register("run", MyTask)
        """
    }

    String iterableSymbol = '.$0'

    @Override
    String getNameSymbolFor(String name) {
        ".$name\$0"
    }

    @Override
    String getKeySymbolFor(String name) {
        ".$name"
    }

    @Override
    void assertValidationSucceeds() {
        succeeds "run"
        result.assertTaskNotSkipped(":run")
    }

    void assertValidationFailsWith(List<DocumentedProblem> messages) {
        def expectedDeprecations = messages
            .findAll { problem -> problem.severity == WARNING }
        def expectedFailures = messages
            .findAll { problem -> problem.severity == ERROR }

        expectedDeprecations.forEach { warning ->
            expectThatExecutionOptimizationDisabledWarningIsDisplayed(executer, warning.message, warning.id, warning.section)
        }
        if (expectedFailures) {
            fails "run"
        } else {
            succeeds "run"
        }

        switch (expectedFailures.size()) {
            case 0:
                break
            case 1:
                failure.assertHasDescription("A problem was found with the configuration of task ':run' (type 'MyTask').")
                break
            default:
                failure.assertHasDescription("Some problems were found with the configuration of task ':run' (type 'MyTask').")
                break
        }
        expectedFailures.forEach { error ->
            failureDescriptionContains(error.message)
        }
    }

    @Override
    TestFile source(String path) {
        return file("buildSrc/$path")
    }

    @ValidationTestFor(
        ValidationProblemId.MISSING_ANNOTATION
    )
    def "supports recursive types"() {
        groovyTaskSource << """
            import org.gradle.api.*
            import org.gradle.api.tasks.*

            class MyTask extends DefaultTask {
                @Nested
                Tree tree = new Tree(
                        left: new Tree([:]),
                        right: new Tree([:])
                    )

                public static class Tree {
                    @Optional @Nested
                    Tree left

                    @Optional @Nested
                    Tree right

                    String nonAnnotated
                }

                @TaskAction void execute() {}
            }
        """

        expect:
        assertValidationFailsWith([
                error(missingAnnotationMessage { type('MyTask').property('tree.nonAnnotated').missingInputOrOutput() }, 'validation_problems', 'missing_annotation'),
                error(missingAnnotationMessage { type('MyTask').property('tree.left.nonAnnotated').missingInputOrOutput() }, 'validation_problems', 'missing_annotation'),
                error(missingAnnotationMessage { type('MyTask').property('tree.right.nonAnnotated').missingInputOrOutput() }, 'validation_problems', 'missing_annotation'),
        ])
    }

    @ValidationTestFor([
            ValidationProblemId.MISSING_NORMALIZATION_ANNOTATION,
            ValidationProblemId.INCORRECT_USE_OF_INPUT_ANNOTATION
    ])
    def "detects problems with file inputs"() {
        file("input.txt").text = "input"
        file("input").createDir()

        javaTaskSource << """
            import org.gradle.api.*;
            import org.gradle.api.file.*;
            import org.gradle.api.tasks.*;
            import java.util.Set;
            import java.util.Collections;
            import java.io.File;
            import java.nio.file.Path;

            @CacheableTask
            public abstract class MyTask extends DefaultTask {
                @javax.inject.Inject
                org.gradle.api.internal.file.FileResolver fileResolver;

                @InputDirectory
                @Optional
                public File getInputDirectory() {
                    return new File("input");
                }

                @InputFile
                public File getInputFile() {
                    return new File("input.txt");
                }

                @InputFiles
                public Set<File> getInputFiles() {
                    return Collections.emptySet();
                }

                @Input
                public File getFile() {
                    return new File("some-file");
                }

                @Input
                public FileCollection getFileCollection() {
                    return getProject().files();
                }

                @Input
                public Path getFilePath() {
                    return new File("some-file").toPath();
                }

                @Input
                public FileTree getFileTree() {
                    return getProject().files().getAsFileTree();
                }

                @TaskAction
                public void doStuff() { }
            }
        """

        expect:
        executer.withArgument("-Dorg.gradle.internal.max.validation.errors=10")
        assertValidationFailsWith([
                error(incorrectUseOfInputAnnotation { type('MyTask').property('file').propertyType('File') }, 'validation_problems', 'incorrect_use_of_input_annotation'),
                error(incorrectUseOfInputAnnotation { type('MyTask').property('fileCollection').propertyType('FileCollection') }, 'validation_problems', 'incorrect_use_of_input_annotation'),
                error(incorrectUseOfInputAnnotation { type('MyTask').property('filePath').propertyType('Path') }, 'validation_problems', 'incorrect_use_of_input_annotation'),
                error(incorrectUseOfInputAnnotation { type('MyTask').property('fileTree').propertyType('FileTree') }, 'validation_problems', 'incorrect_use_of_input_annotation'),
        ])
    }

    //@IgnoreRest
    @ValidationTestFor(
            ValidationProblemId.MISSING_ANNOTATION
    )
    def "detects problems on nested collections"() {
        javaTaskSource << """
            import org.gradle.api.*;
            import org.gradle.api.provider.*;
            import org.gradle.api.tasks.*;
            import org.gradle.work.*;
            import java.util.*;
            import java.io.File;

            @DisableCachingByDefault(because = "test task")
            public class MyTask extends DefaultTask {
                @Nested
                public Options getOptions() {
                    return new Options();
                }

                @Nested
                public List<Options> getOptionsList() {
                    return Arrays.asList(new Options());
                }

                @Nested
                public Iterable<Options> getIterableOptions() {
                    return Arrays.asList(new Options());
                }

                @Nested
                public Iterable<Iterable<Options>> getDoubleIterableOptions() {
                    return Arrays.asList(Arrays.asList(new Options()));
                }

                @Nested
                public Map<String, Options> getMappedOptions() {
                    return Collections.singletonMap("alma", new Options());
                }

                @Nested
                public Iterable<Map<String, Iterable<Options>>> getIterableMappedOptions() {
                    return Arrays.asList(Collections.singletonMap("alma", Arrays.asList(new Options())));
                }

                @Nested
                public Provider<Options> getProvidedOptions() {
                    return getProject().getObjects().property(Options.class).convention(new Options());
                }

                @Nested
                public Iterable<NamedBean> getNamedIterable() {
                    return Arrays.asList(new NamedBean());
                }

                @Nested
                public AnnotatedList getAnnotatedList() {
                    return new AnnotatedList();
                }

                public static class Options {
                    @Input
                    public String getGood() {
                        return "good";
                    }

                    public String getNotAnnotated() {
                        return null;
                    }
                }

                public static class NamedBean implements Named {
                    @Input
                    public String getGood() {
                        return "good";
                    }

                    public String getNotAnnotated() {
                        return null;
                    }

                    @Internal
                    public String getName() {
                        return "tibor";
                    }
                }

                // Does not validate the type parameter of extended collection
                // because it has annotated properties
                public static class AnnotatedList extends ArrayList<Options> {
                    public AnnotatedList() {
                        add(new Options());
                    }

                    @Input
                    public String getGood() {
                        return "good";
                    }
                }

                @TaskAction
                public void doStuff() { }
            }
        """

        expect:
        executer.withArgument("-Dorg.gradle.internal.max.validation.errors=10")
        assertValidationFailsWith([
                error(missingAnnotationMessage { type('MyTask').property("doubleIterableOptions${iterableSymbol}${iterableSymbol}.notAnnotated").missingInputOrOutput() }, 'validation_problems', 'missing_annotation'),
                error(missingAnnotationMessage { type('MyTask').property("iterableMappedOptions${iterableSymbol}${getKeySymbolFor("alma")}${iterableSymbol}.notAnnotated").missingInputOrOutput() }, 'validation_problems', 'missing_annotation'),
                error(missingAnnotationMessage { type('MyTask').property("iterableOptions${iterableSymbol}.notAnnotated").missingInputOrOutput() }, 'validation_problems', 'missing_annotation'),
                error(missingAnnotationMessage { type('MyTask').property("mappedOptions${getKeySymbolFor("alma")}.notAnnotated").missingInputOrOutput() }, 'validation_problems', 'missing_annotation'),
                error(missingAnnotationMessage { type('MyTask').property("namedIterable${getNameSymbolFor("tibor")}.notAnnotated").missingInputOrOutput() }, 'validation_problems', 'missing_annotation'),
                error(missingAnnotationMessage { type('MyTask').property("options.notAnnotated").missingInputOrOutput() }, 'validation_problems', 'missing_annotation'),
                error(missingAnnotationMessage { type('MyTask').property("optionsList${iterableSymbol}.notAnnotated").missingInputOrOutput() }, 'validation_problems', 'missing_annotation'),
                error(missingAnnotationMessage { type('MyTask').property("providedOptions.notAnnotated").missingInputOrOutput() }, 'validation_problems', 'missing_annotation'),
        ])
    }
}
