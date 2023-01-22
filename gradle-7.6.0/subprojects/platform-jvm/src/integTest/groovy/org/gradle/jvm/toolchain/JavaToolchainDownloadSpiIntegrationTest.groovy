/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.jvm.toolchain

import net.rubygrapefruit.platform.SystemInfo
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.internal.nativeintegration.services.NativeServices
import org.gradle.internal.os.OperatingSystem

class JavaToolchainDownloadSpiIntegrationTest extends AbstractJavaToolchainDownloadSpiIntegrationTest {

    @ToBeFixedForConfigurationCache(because = "Fails the build with an additional error")
    def "can inject custom toolchain registry via settings plugin"() {
        settingsFile << """
            ${applyToolchainResolverPlugin("CustomToolchainResolver", customToolchainResolverCode())}               
            toolchainManagement {
                jvm {
                    javaRepositories {
                        repository('custom') {
                            resolverClass = CustomToolchainResolver
                        }
                    }
                }
            }
        """

        buildFile << """
            apply plugin: "java"

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(99)
                    vendor = JvmVendorSpec.matching("exotic")
                }
            }
        """

        file("src/main/java/Foo.java") << "public class Foo {}"

        when:
        failure = executer
                .withTasks("compileJava")
                .requireOwnGradleUserHomeDir()
                .withToolchainDownloadEnabled()
                .runWithFailure()

        then:
        failure.assertHasDescription("Execution failed for task ':compileJava'.")
                .assertHasCause("Failed to calculate the value of task ':compileJava' property 'javaCompiler'.")
                .assertHasCause("Unable to download toolchain matching the requirements ({languageVersion=99, vendor=matching('exotic'), implementation=vendor-specific}) from 'https://exoticJavaToolchain.com/java-99'.")
                .assertHasCause("Could not HEAD 'https://exoticJavaToolchain.com/java-99'.")
    }

    @ToBeFixedForConfigurationCache(because = "Fails the build with an additional error")
    def "downloaded JDK is checked against the spec"() {
        settingsFile << """
            ${applyToolchainResolverPlugin("BrokenToolchainResolver", brokenToolchainResolverCode())}               
            toolchainManagement {
                jvm {
                    javaRepositories {
                        repository('broken') {
                            resolverClass = BrokenToolchainResolver
                        }
                    }
                }
            }
        """

        buildFile << """
            apply plugin: "java"

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(11)
                }
            }
        """

        file("src/main/java/Foo.java") << "public class Foo {}"

        when:
        failure = executer
                .withTasks("compileJava")
                .requireOwnGradleUserHomeDir()
                .withToolchainDownloadEnabled()
                .runWithFailure()

        then:
        failure.assertHasDescription("Execution failed for task ':compileJava'.")
                .assertHasCause("Error while evaluating property 'javaCompiler' of task ':compileJava'.")
                .assertHasCause("Failed to calculate the value of task ':compileJava' property 'javaCompiler'.")
                .assertHasCause("Unable to download toolchain matching the requirements ({languageVersion=11, vendor=any, implementation=vendor-specific}) from 'https://api.adoptium.net/v3/binary/latest/17/ga/${os()}/${architecture()}/jdk/hotspot/normal/eclipse'.")
                .assertHasCause("Toolchain provisioned from 'https://api.adoptium.net/v3/binary/latest/17/ga/${os()}/${architecture()}/jdk/hotspot/normal/eclipse' doesn't satisfy the specification: {languageVersion=11, vendor=any, implementation=vendor-specific}.")
    }

    @ToBeFixedForConfigurationCache(because = "Fails the build with an additional error")
    def "custom toolchain registries are consulted in order"() {
        settingsFile << """
            ${applyToolchainResolverPlugin("CustomToolchainResolver", customToolchainResolverCode())}
            ${applyToolchainResolverPlugin("UselessToolchainResolver", uselessToolchainResolverCode("UselessToolchainResolver"))}            
            toolchainManagement {
                jvm {
                    javaRepositories {
                        repository('useless') {
                            resolverClass = UselessToolchainResolver
                        }
                        repository('custom') {
                            resolverClass = CustomToolchainResolver
                        }
                    }
                }
            }
        """

        buildFile << """
            apply plugin: "java"

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(99)
                    vendor = JvmVendorSpec.matching("exotic")
                }
            }
        """

        file("src/main/java/Foo.java") << "public class Foo {}"

        when:
        failure = executer
                .withTasks("compileJava")
                .requireOwnGradleUserHomeDir()
                .withToolchainDownloadEnabled()
                .runWithFailure()

        then:
        failure.assertHasDescription("Execution failed for task ':compileJava'.")
                .assertHasCause("Failed to calculate the value of task ':compileJava' property 'javaCompiler'.")
                .assertHasCause("Unable to download toolchain matching the requirements ({languageVersion=99, vendor=matching('exotic'), implementation=vendor-specific}) from 'https://exoticJavaToolchain.com/java-99'.")
                .assertHasCause("Could not HEAD 'https://exoticJavaToolchain.com/java-99'.")
    }

    @ToBeFixedForConfigurationCache(because = "Fails the build with an additional error")
    def "will use default if no custom toolchain registry requested"() {
        buildFile << """
            apply plugin: "java"

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(99)
                }
            }
        """

        file("src/main/java/Foo.java") << "public class Foo {}"

        when:
        failure = executer
                .withTasks("compileJava")
                .requireOwnGradleUserHomeDir()
                .withToolchainDownloadEnabled()
                .expectDocumentedDeprecationWarning("Java toolchain auto-provisioning needed, but no java toolchain repositories declared by the build. Will rely on the built-in repository. " +
                        "This behaviour has been deprecated and is scheduled to be removed in Gradle 8.0. " +
                        "In order to declare a repository for java toolchains, you must edit your settings script and add one via the toolchainManagement block. " +
                        "See https://docs.gradle.org/current/userguide/toolchains.html#sec:provisioning for more details.")
                .runWithFailure()

        then:
        failure.assertHasDescription("Execution failed for task ':compileJava'.")
                .assertHasCause("Error while evaluating property 'javaCompiler' of task ':compileJava'.")
                .assertHasCause("Failed to calculate the value of task ':compileJava' property 'javaCompiler'.")
                .assertHasCause("Unable to download toolchain matching the requirements ({languageVersion=99, vendor=any, implementation=vendor-specific}) from 'https://api.adoptium.net/v3/binary/latest/99/ga/${os()}/${architecture()}/jdk/hotspot/normal/eclipse'.")
                .assertHasCause("Could not read 'https://api.adoptium.net/v3/binary/latest/99/ga/${os()}/${architecture()}/jdk/hotspot/normal/eclipse' as it does not exist.")
    }

    @ToBeFixedForConfigurationCache(because = "Fails the build with an additional error")
    def "if toolchain registries are explicitly requested, then the default is NOT automatically added to the request"() {
        settingsFile << """
            ${applyToolchainResolverPlugin("UselessToolchainResolver", uselessToolchainResolverCode("UselessToolchainResolver"))}            
            toolchainManagement {
                jvm {
                    javaRepositories {
                        repository('useless') {
                            resolverClass = UselessToolchainResolver
                        }
                    }
                }
            }
        """

        buildFile << """
            apply plugin: "java"

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(99)
                }
            }
        """

        file("src/main/java/Foo.java") << "public class Foo {}"

        when:
        failure = executer
                .withTasks("compileJava")
                .requireOwnGradleUserHomeDir()
                .withToolchainDownloadEnabled()
                .runWithFailure()

        then:
        failure.assertHasDescription("Execution failed for task ':compileJava'.")
                .assertHasCause("Failed to calculate the value of task ':compileJava' property 'javaCompiler'.")
                .assertHasCause("No compatible toolchains found for request specification: {languageVersion=99, vendor=any, implementation=vendor-specific} (auto-detect false, auto-download true).")
    }

    def "fails on registration collision"() {
        settingsFile << """
            ${applyToolchainResolverPlugin("UselessPlugin1", "UselessToolchainResolver", uselessToolchainResolverCode("UselessToolchainResolver"))}
            ${applyToolchainResolverPlugin("UselessPlugin2", "UselessToolchainResolver", "")}
            toolchainManagement {
                jvm {
                    javaRepositories {
                        repository('useless') {
                            resolverClass = UselessToolchainResolver1
                        }
                        repository('useless') {
                            resolverClass = UselessToolchainResolver2
                        }
                    }
                }
            }
        """

        buildFile << """
            apply plugin: "java"

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(99)
                }
            }
        """

        file("src/main/java/Foo.java") << "public class Foo {}"

        when:
        failure = executer
                .withTasks("compileJava")
                .requireOwnGradleUserHomeDir()
                .withToolchainDownloadEnabled()
                .runWithFailure()

        then:
        failure.assertHasCause("Failed to apply plugin class 'UselessPlugin2'.")
                .assertHasCause("Duplicate registration for 'UselessToolchainResolver'.")
    }

    @ToBeFixedForConfigurationCache(because = "Fails the build with an additional error")
    def "fails on implementation class collision"() {
        settingsFile << """
            ${applyToolchainResolverPlugin("UselessToolchainResolver", uselessToolchainResolverCode("UselessToolchainResolver"))}            
            toolchainManagement {
                jvm {
                    javaRepositories {
                        repository('useless1') {
                            resolverClass = UselessToolchainResolver
                        }
                        repository('useless2') {
                            resolverClass = UselessToolchainResolver
                        }
                    }
                }
            }
        """

        buildFile << """
            apply plugin: "java"

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(99)
                }
            }
        """

        file("src/main/java/Foo.java") << "public class Foo {}"

        when:
        failure = executer
                .withTasks("compileJava")
                .requireOwnGradleUserHomeDir()
                .withToolchainDownloadEnabled()
                .runWithFailure()

        then:
        failure.assertHasCause("Duplicate configuration for repository implementation 'UselessToolchainResolver'.")
    }

    def "fails on repository name collision"() {
        settingsFile << """
            ${applyToolchainResolverPlugin("UselessToolchainResolver1", uselessToolchainResolverCode("UselessToolchainResolver1"))}            
            ${applyToolchainResolverPlugin("UselessToolchainResolver2", uselessToolchainResolverCode("UselessToolchainResolver2"))}            
            toolchainManagement {
                jvm {
                    javaRepositories {
                        repository('useless') {
                            resolverClass = UselessToolchainResolver1
                        }
                        repository('useless') {
                            resolverClass = UselessToolchainResolver2
                        }
                    }
                }
            }
        """

        buildFile << """
            apply plugin: "java"

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(99)
                }
            }
        """

        file("src/main/java/Foo.java") << "public class Foo {}"

        when:
        failure = executer
                .withTasks("compileJava")
                .requireOwnGradleUserHomeDir()
                .withToolchainDownloadEnabled()
                .runWithFailure()

        then:
        failure.assertHasCause("Duplicate configuration for repository 'useless'.")
    }

    @ToBeFixedForConfigurationCache(because = "Fails the build with an additional error")
    def "list of requested repositories can be queried"() {
        settingsFile << """
            ${applyToolchainResolverPlugin("UselessToolchainResolver1", uselessToolchainResolverCode("UselessToolchainResolver1"))}            
            ${applyToolchainResolverPlugin("UselessToolchainResolver2", uselessToolchainResolverCode("UselessToolchainResolver2"))}            
            ${applyToolchainResolverPlugin("UselessToolchainResolver3", uselessToolchainResolverCode("UselessToolchainResolver3"))}            
            toolchainManagement {
                jvm {
                    javaRepositories {
                        repository('useless3') {
                            resolverClass = UselessToolchainResolver3
                        }
                        repository('useless1') {
                            resolverClass = UselessToolchainResolver1
                        }
                    }
                }
            }
            
            println(\"\"\"Explicitly requested toolchains: \${toolchainManagement.jvm.getJavaRepositories().collect { it.getName() }}.\"\"\")
        """

        buildFile << """
            apply plugin: "java"

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(99)
                }
            }
        """

        file("src/main/java/Foo.java") << "public class Foo {}"

        when:
        failure = executer
                .withTasks("compileJava")
                .requireOwnGradleUserHomeDir()
                .withToolchainDownloadEnabled()
                .runWithFailure()

        then:
        failure.getOutput().contains("Explicitly requested toolchains: [useless3, useless1].")
    }

    private static String customToolchainResolverCode() {
        """
            import java.util.Optional;
            import org.gradle.platform.BuildPlatform;

            public abstract class CustomToolchainResolver implements JavaToolchainResolver {
                @Override
                public Optional<JavaToolchainDownload> resolve(JavaToolchainRequest request) {
                    URI uri = URI.create("https://exoticJavaToolchain.com/java-" + request.getJavaToolchainSpec().getLanguageVersion().get());
                    return Optional.of(JavaToolchainDownload.fromUri(uri));
                }
            }
            """
    }

    private static String uselessToolchainResolverCode(String className) {
        """
            import java.util.Optional;
            import org.gradle.platform.BuildPlatform;

            public abstract class ${className} implements JavaToolchainResolver {
                @Override
                public Optional<JavaToolchainDownload> resolve(JavaToolchainRequest request) {
                    return Optional.empty();
                }
            }
            """
    }

    private static String brokenToolchainResolverCode() {
        """
            import java.util.Optional;
            import org.gradle.platform.BuildPlatform;

            public abstract class BrokenToolchainResolver implements JavaToolchainResolver {
                @Override
                public Optional<JavaToolchainDownload> resolve(JavaToolchainRequest request) {
                    URI uri = URI.create("https://api.adoptium.net/v3/binary/latest/17/ga/${os()}/${architecture()}/jdk/hotspot/normal/eclipse");
                    return Optional.of(JavaToolchainDownload.fromUri(uri));
                }
            }
            """
    }

    private static String os() {
        OperatingSystem os = OperatingSystem.current()
        if (os.isWindows()) {
            return "windows"
        } else if (os.isMacOsX()) {
            return "mac"
        } else if (os.isLinux()) {
            return "linux"
        }
        return os.getFamilyName()
    }

    private static String architecture() {
        SystemInfo systemInfo = NativeServices.getInstance().get(SystemInfo.class)
        switch (systemInfo.architecture) {
            case SystemInfo.Architecture.i386:
                return "x32";
            case SystemInfo.Architecture.amd64:
                return "x64";
            case SystemInfo.Architecture.aarch64:
                return "aarch64";
            default:
                return "unknown";
        }
    }

}
