/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.smoketests

import org.gradle.internal.reflect.validation.Severity
import org.gradle.internal.reflect.validation.ValidationMessageChecker
import spock.lang.Issue

import static org.gradle.internal.reflect.validation.Severity.ERROR
import static org.gradle.internal.reflect.validation.Severity.WARNING
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class SpringBootPluginSmokeTest extends AbstractPluginValidatingSmokeTest implements ValidationMessageChecker {

    @Issue('https://mvnrepository.com/artifact/org.springframework.boot/spring-boot-gradle-plugin')
    def 'spring boot plugin'() {
        given:
        buildFile << """
            plugins {
                id "application"
                id "org.springframework.boot" version "${TestedVersions.springBoot}"
                id "io.spring.dependency-management" version "${TestedVersions.springDependencyManagement}"
            }

            ${mavenCentralRepository()}

            dependencies {
                implementation 'org.springframework.boot:spring-boot-starter'
                testImplementation 'org.springframework.boot:spring-boot-starter-test'
            }
            
            tasks.named('test') {
                useJUnitPlatform()
            }
        """.stripIndent()

        file('src/main/java/example/Application.java') << """
            package example;
            
            import org.springframework.boot.SpringApplication;
            import org.springframework.boot.autoconfigure.SpringBootApplication;
            
            @SpringBootApplication
            public class Application {
                public static void main(String[] args) {
                    SpringApplication.run(Application.class, args);
                }
            }
        """.stripIndent()
        file("src/test/java/example/ApplicationTest.java") << """
            package example;
            
            import org.junit.jupiter.api.Test;
            import org.springframework.boot.test.context.SpringBootTest;
            
            @SpringBootTest
            class ApplicationTest {
                @Test
                void contextLoads() {
                }
            }
        """

        when:
        def buildResult = runner('assembleBootDist', 'check').build()

        then:
        buildResult.task(':assembleBootDist').outcome == SUCCESS
        buildResult.task(':check').outcome == SUCCESS

        when:
        def runResult = runner('bootRun').build()

        then:
        runResult.task(':bootRun').outcome == SUCCESS
    }

    @Override
    Map<String, Versions> getPluginsToValidate() {
        [
            'org.springframework.boot': Versions.of(TestedVersions.springBoot)
        ]
    }

    @Override
    void configureValidation(String pluginId, String version) {
        Map<String, Severity> messages = [:]

        validatePlugins {
            onPlugin(pluginId) {
                // This is not a problem, since this task type is only used for Gradle versions < 6.4.
                // See https://github.com/spring-projects/spring-boot/blob/038ae9340644f0128ed6f29d9e5eb7e6c359f291/spring-boot-project/spring-boot-tools/spring-boot-gradle-plugin/src/main/java/org/springframework/boot/gradle/plugin/ApplicationPluginAction.java#L85
                messages[incompatibleAnnotations {
                    type'org.springframework.boot.gradle.tasks.application.CreateBootStartScripts'
                    property 'mainClassName'
                    annotatedWith 'Optional'
                    incompatibleWith 'ReplacedBy'
                    includeLink()
                }] = ERROR

                messages[incorrectUseOfInputAnnotation {
                    type'org.springframework.boot.gradle.tasks.bundling.BootBuildImage'
                    property 'archiveFile'
                    propertyType 'RegularFileProperty'
                    includeLink()
                }] = WARNING
                messages[incorrectUseOfInputAnnotation {
                    type'org.springframework.boot.gradle.tasks.bundling.BootBuildImage'
                    property 'jar'
                    propertyType 'RegularFileProperty'
                    includeLink()
                }] = WARNING

                failsWith messages
            }
        }
    }
}
