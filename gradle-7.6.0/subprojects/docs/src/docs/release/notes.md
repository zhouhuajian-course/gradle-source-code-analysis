
The Gradle team is excited to announce Gradle @version@.

This release includes [building and running code with Java 19](#java19),
a flag to [rerun tasks individually](#individual-rerun),
a new [strongly-typed dependencies block](#strongly-typed-dependencies) for JVM test suites,
and a [pluggable system for Java Toolchains provisioning](#toolchain-provision).

As always there are also performance improvements like enhancements to the [configuration cache](#configuration) and
[incremental compilation](#incremental-compilation-after-failure).

<!--
Include only their name, impactful features should be called out separately below.
 [Some person](https://github.com/some-person)

 THiS LIST SHOULD BE ALPHABETIZED BY [PERSON NAME] - the docs:updateContributorsInReleaseNotes task will enforce this ordering, which is case-insensitive.
-->
We would like to thank the following community members for their contributions to this release of Gradle:
[altrisi](https://github.com/altrisi),
[aSemy](https://github.com/aSemy),
[Ashwin Pankaj](https://github.com/ashwinpankaj),
[Aurimas](https://github.com/liutikas),
[BJ Hargrave](https://github.com/bjhargrave),
[Björn Kautler](https://github.com/Vampire),
[Bradley Turek](https://github.com/TurekBot),
[Craig Andrews](https://github.com/candrews),
[Daniel Lin](https://github.com/ephemient),
[David Morris](https://github.com/codefish1),
[Edmund Mok](https://github.com/edmundmok),
[Frosty-J](https://github.com/Frosty-J),
[Gabriel Feo](https://github.com/gabrielfeo),
[Ivan Gavrilovic](https://github.com/gavra0),
[Jendrik Johannes](https://github.com/jjohannes),
[John](https://github.com/goughy000),
[Joseph Woolf](https://github.com/jsmwoolf),
[Karl-Michael Schindler](https://github.com/kamischi),
[Konstantin Gribov](https://github.com/grossws),
[Leonardo Brondani Schenkel](https://github.com/lbschenkel),
[Martin d'Anjou](https://github.com/martinda),
[Michael Bailey](https://github.com/yogurtearl),
[Pete Bentley](https://github.com/prbprbprb),
[Rob Bavey](https://github.com/robbavey),
[Sam Snyder](https://github.com/sambsnyd),
[sll552](https://github.com/sll552),
[teawithbrownsugar](https://github.com/teawithbrownsugar),
[Thomas Broadley](https://github.com/tbroadley),
[urdak](https://github.com/urdak),
[Varun Sharma](https://github.com/varunsh-coder),
[Xin Wang](https://github.com/scaventz)

## Upgrade instructions

Switch your build to use Gradle @version@ by updating your wrapper:

`./gradlew wrapper --gradle-version=@version@`

See the [Gradle 7.x upgrade guide](userguide/upgrading_version_7.html#changes_@baseVersion@) to learn about deprecations, breaking changes and other considerations when upgrading to Gradle @version@.

For Java, Groovy, Kotlin and Android compatibility, see the [full compatibility notes](userguide/compatibility.html).

<!-- Do not add breaking changes or deprecations here! Add them to the upgrade guide instead. -->

<!--

================== TEMPLATE ==============================

<a name="FILL-IN-KEY-AREA"></a>
### FILL-IN-KEY-AREA improvements

<<<FILL IN CONTEXT FOR KEY AREA>>>
Example:
> The [configuration cache](userguide/configuration_cache.html) improves build performance by caching the result of
> the configuration phase. Using the configuration cache, Gradle can skip the configuration phase entirely when
> nothing that affects the build configuration has changed.

#### FILL-IN-FEATURE
> HIGHLIGHT the usecase or existing problem the feature solves
> EXPLAIN how the new release addresses that problem or use case
> PROVIDE a screenshot or snippet illustrating the new feature, if applicable
> LINK to the full documentation for more details

================== END TEMPLATE ==========================


==========================================================
ADD RELEASE FEATURES BELOW
vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv -->

## New features and usability improvements

<a name="jvm"></a>
### JVM

<a name="java19"></a>
#### Added Support for Java 19

Gradle 7.6 supports compiling, testing and running on Java 19.

<a name="strongly-typed-dependencies"></a>
#### Introduced strongly-typed `dependencies` block for JVM test suites

The [JVM test suite](userguide/jvm_test_suite_plugin.html) `dependencies` block now uses a [strongly-typed API](dsl/org.gradle.api.plugins.jvm.JvmComponentDependencies.html).
This makes the build logic cleaner and improves assistance in the IDEs, especially with the Kotlin DSL.

Previously, the JVM test suite `dependencies` block only accepted dependencies of type `Object`.

```kotlin
testing {
    suites {
        val test by getting(JvmTestSuite::class) {
            useJUnitJupiter()
            dependencies {
                implementation(project(":foo")) {
                    // Receiver (this) is of type Dependency
                    // To access ProjectDependency
                    // methods, smart-cast:
                    this as ProjectDependency
                    // Now available as a ProjectDependency
                    println(dependencyProject)
                }
            }
        }
    }
}
```

Now, each notation provides its `Dependency` subtype:

```kotlin
testing {
    suites {
        val test by getting(JvmTestSuite::class) {
            useJUnitJupiter()
            dependencies {
                implementation(project(":foo")) {
                    // `this` already of type ProjectDependency
                    println(dependencyProject)
                }
            }
        }
    }
}
```

For example, using a `String` provides an `ExternalModuleDependency`.
Using a `FileCollection` provides a `FileCollectionDependency`.
This allows Java and Kotlin to properly configure all types of dependencies
and improves IDE support for the Groovy DSL.

In addition, the Kotlin DSL now supports named arguments for external dependencies in this block:

```kotlin
testing {
    suites {
        "test"(JvmTestSuite::class) { 
            useJUnitJupiter()
            dependencies {
                implementation(module(group = "com.google.guava",
                               name = "guava",
                               version = "31.1-jre"))
            }
        }
    }
}
```

For more information about the test suite `dependencies` block, see
[Differences Between Test Suite and Top-Level Dependencies](userguide/jvm_test_suite_plugin.html#differences_between_the_test_suite_dependencies_and_the_top_level_dependencies_blocks).

<a name="toolchain-provision"></a>
#### Added support for Java Toolchain downloads from arbitrary repositories

Starting in Gradle 7.6, Gradle can download JVM [toolchains](userguide/toolchains.html) from arbitrary repositories.
By default, Gradle downloads toolchains from Adoptium/AdoptOpenJDK.
You can now override the default providers with repositories of your choice using a toolchain resolver plugin.

For example, the following uses custom plugins that provide `AzulResolver` and `AdoptiumResolver` to add custom toolchains for Adoptium and Azul:

```kotlin
toolchainManagement {
    jvm {
        javaRepositories {
            repository("azul") {
                resolverClass.set(AzulResolver::class.java)
                credentials {
                    username = "user"
                    password = "password"
                }
                authentication {
                    create<DigestAuthentication>("digest")
                }
            }
            repository("adoptium") {
                resolverClass.set(AdoptiumResolver::class.java)
            }
        }
    }
}
```

For more information about using custom toolchain resolvers, see the [Toolchain Download Repositories documentation](userguide/toolchains.html#sub:download_repositories).
For more information about writing custom toolchain resolvers, see the [Toolchain Resolver Plugins documentation](userguide/toolchain_plugins.html).

<a name="incremental-compilation-after-failure"></a>
#### Added support for incremental compilation following a compilation failure

Gradle supports [Java incremental compilation](userguide/java_plugin.html#sec:incremental_compile)
by default and [Groovy incremental compilation](userguide/groovy_plugin.html#sec:incremental_groovy_compilation)
as an opt-in experimental feature.

In previous versions, a compilation failure caused the next compilation to perform a full recompile.
Starting in Gradle 7.6, Java and Groovy incremental compilation can work even after a failure.

This feature is enabled by default when incremental compilation is enabled.
The feature can be disabled with the [`incrementalAfterFailure`](javadoc/org/gradle/api/tasks/compile/CompileOptions.html#getIncrementalAfterFailure--) compile option.

#### Introduced support for Java 9+ network debugging

You can run a Java test or application child process with [debugging options](userguide/java_testing.html#sec:debugging_java_tests) to accept debugger client connections over the network. If the debugging options only specify a port, but not a host address, the set of accepted connections depends on your version of Java:

- Before Java 9, the debugger client accepts connections from any machine.
- Starting in Java 9, the debugger client accepts connections originating from the host machine *only*.

This release adds a new property to [`JavaDebugOptions`](javadoc/org/gradle/process/JavaDebugOptions.html): `host`.
This allows you to specify the debugger host address along with the port.

Similarly, the new Gradle property `org.gradle.debug.host` now enables [running the Gradle process with the debugger server](userguide/troubleshooting.html#sec:troubleshooting_build_logic) accepting connections via network on Java 9 and above.

On Java 9 and above, use the special host address value `*` to make the debugger server listen on all network interfaces.
Otherwise, use the address of one of the machine's network interfaces.

#### Revised dependencies generated from `init` Maven conversions from `implementation` to `api`

The `init` task now adds compile-time Maven dependencies to Gradle's `api` configuration when converting a Maven project.
This sharply reduces the number of compilation errors.
It is still recommended to use [`implementation`](userguide/java_library_plugin.html#sec:java_library_separation) where possible. 

For more information about Maven conversions, see the [Build Init Plugin](userguide/build_init_plugin.html#sec:pom_maven_conversion).

<a name="general-improvements"></a>
### General Improvements

<a name="individual-rerun"></a>
#### Introduced flag for individual task `rerun`

All tasks can now use the `--rerun` option. This option works like `--rerun-tasks`,
except `--rerun` only affects a single task. For example, you can force tests to
ignore up-to-date checks like this:

```
gradle test --rerun
```

For more information about the rerun option, see [Built-in Task Options](userguide/command_line_interface.html#sec:builtin_task_options).

<a name="init"></a>
#### Relocated convention plugins in projects generated with `init`

> 🐣 *This feature is incubating*.

When generating builds with the `init` task and opting in to incubating features,
Gradle now places convention plugins under the `build-logic` directory instead of in `buildSrc`.

Convention plugins are Gradle’s recommended way of organizing build logic where you can compose custom build logic by applying and configuring both core and external plugins. 

For more information about convention plugins, see [Convention Plugins](userguide/sharing_build_logic_between_subprojects.html#sec:convention_plugins).

#### Introduced network timeout configuration for wrapper download

It is now possible to configure the network timeout for downloading Gradle wrapper files.
The default value is 10000ms and can be changed in several ways:

From the command line:

```shell
$ ./gradlew wrapper --network-timeout=30000
```

In your build scripts or convention plugins:

```kotlin
tasks.wrapper {
    networkTimeout.set(30000)
}
```

Or in `gradle/wrapper/gradle-wrapper.properties`:

```properties
networkTimeout=30000
```

For more information about the Gradle wrapper, see [Gradle Wrapper](userguide/gradle_wrapper.html#sec:adding_wrapper).

#### Introduced ability to explain why a task was skipped with a message

You can now provide a reason message when conditionally disabling a task using the
[`Task.onlyIf` predicate](userguide/more_about_tasks.html#sec:using_a_predicate):

```groovy
tasks.register("slowBenchmark") {
    onlyIf("slow benchmarks not enabled") {
        false
    }
}
```

Gradle outputs reason messages at log level `INFO`.
To output reason messages to the console, use the `--info` or `--debug` [log levels](userguide/logging.html).

<a name="dependency-management"></a>
### Dependency Management


#### Clarified the ordering of disambiguation rule checks in `resolvableConfigurations` reports

Attribute disambiguation rules control the variant of a dependency selected by
Gradle when:

- multiple variants of a dependency exist with different compatible values for a
  requested attribute
- no variant exactly matches that attribute

Attribute disambiguation rules select a single matching dependency variant in
such cases. The `resolvableConfigurations` reporting task now prints the order
of these rules:

```shell
$ ./gradlew resolvableConfigurations
```

```
--------------------------------------------------
Disambiguation Rules
--------------------------------------------------
The following Attributes have disambiguation rules defined.

    - flavor
    - org.gradle.category (1)
    - org.gradle.dependency.bundling (5)
    - org.gradle.jvm.environment (6)
    - org.gradle.jvm.version (3)
    - org.gradle.libraryelements (4)
    - org.gradle.plugin.api-version
    - org.gradle.usage (2)

(#): Attribute disambiguation precedence
```

For more information, see [Attribute Disambiguation Rules](userguide/variant_attributes.html#sec:abm_disambiguation_rules).



<a name="configuration"></a>
### Configuration Cache

The [configuration cache](https://docs.gradle.org/7.5/userguide/configuration_cache.html) improves build time by caching the result of the configuration phase and reusing this for subsequent builds.

#### Improved configuration cache failure recovery

In previous Gradle versions, it was possible to leave a configuration cache entry in a permanently broken state after a dependency resolution failure. Following builds would simply reproduce the failure without any attempt to recover from it.

Starting with Gradle 7.6, this is no longer the case. Gradle recovers from dependency resolution failures in exactly the same way whether the configuration cache is enabled or not.

#### Extended configuration cache task compatibility

The `dependencies`, `buildEnvironment`, `projects` and `properties` tasks are now compatible with the configuration cache.

#### Added configuration cache support to the Maven Publish Plugin

The [Maven Publish Plugin](userguide/publishing_maven.html) is now compatible with the configuration cache.

Note that when using credentials, the configuration cache requires [safe credential containers](userguide/configuration_cache.html#config_cache:requirements:safe_credentials).

#### Improved handling of `--offline` option

Gradle now stores configuration caches for online and offline modes separately.
This change supports builds and plugins that need to behave differently during configuration depending on whether the `--offline` option is in effect.

For more information, see [the `--offline` CLI option](userguide/command_line_interface.html#sec:command_line_execution_options).

<a name="plugin"></a>
### Plugin Development

#### Introduced support for task options of type `Integer`

You can now pass integer task options declared as `Property<Integer>` from the command line.

For example, the following task option:

```java
@Option(option = "integer-option", description = "description")
public abstract Property<Integer> getIntegerOption();
```

can be passed from the command line as follows:

```shell
gradle myCustomTask --integer-option=123
```

<a name="ide"></a>
### IDE Integration

These improvements are for IDE integrators and are not directly for end-users until their specific IDE implements the integration. 

#### Enhanced test events to distinguish between assertion and framework failures

Gradle 7.6 introduces new failure types for the `Failure` interface returned by
[`FailureResult.getFailures()`](javadoc/org/gradle/tooling/events/FailureResult.html#getFailures--): `TestAssertionFailure` and `TestFrameworkFailure`.
IDEs can now distinguish between assertion and framework failures using progress event listeners.
For test frameworks that expose expected and actual values, `TestAssertionFailure` contains those values.

<a name="testlauncher"></a>
#### Introduced `TestLauncher` task execution

The [`TestLauncher`](javadoc/org/gradle/tooling/TestLauncher.html) interface now allows Tooling API clients
to execute any tasks along with the selected tests:

```
ProjectConnection connection = ...
connection.newTestLauncher()
          .withTaskAndTestClasses("integTest", ["org.MyTest"])
          .forTasks("startDB")
          .run()
```

#### Introduced class, method, package, and pattern test selection via `TestLauncher`

The [TestLauncher](javadoc/org/gradle/tooling/TestLauncher.html) interface now allows Tooling API clients
to select test classes, methods, packages and patterns with a new API.

```
TestLauncher testLauncher = projectConnection.newTestLauncher();
testLauncher.withTestsFor(spec -> {
    spec.forTaskPath(":test")
        .includePackage("org.pkg")
        .includeClass("com.TestClass")
        .includeMethod("com.TestClass")
        .includePattern("io.*")
}).run();
```

<a name="pass-system-properties-to-build"></a>
#### Added support for passing system properties to the build with the Tooling API

Before 7.6, the Tooling API started builds with the system properties from the host JVM. This leaked configuration from the IDE to the build.
Starting in Gradle 7.6, `LongRunningOperation.withSystemProperties(Map)` provides an isolated set of build system properties.
For more information, see [`LongRunningOperation`](javadoc/org/gradle/tooling/LongRunningOperation.html#withSystemProperties-java.util.Map-).

<!-- ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
ADD RELEASE FEATURES ABOVE
==========================================================

-->

## Promoted features

Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backwards compatibility.
See the User Manual section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

This Gradle release promotes the following features to stable:

- `getTestSources` and `getTestResources`
- `getDestinationDirectory` and `getTestResults`

## Fixed issues

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.

## External contributions

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Reporting problems

If you find a problem with this release, please file a bug on [GitHub Issues](https://github.com/gradle/gradle/issues) adhering to our issue guidelines.
If you're not sure you're encountering a bug, please use the [forum](https://discuss.gradle.org/c/help-discuss).

We hope you will build happiness with Gradle, and we look forward to your feedback via [Twitter](https://twitter.com/gradle) or on [GitHub](https://github.com/gradle).
