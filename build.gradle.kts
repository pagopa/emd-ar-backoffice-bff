import com.github.jk1.license.filter.SpdxLicenseBundleNormalizer
import com.github.jk1.license.render.XmlReportRenderer
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
  java
  id("org.springframework.boot") version "4.0.3"
  id("io.spring.dependency-management") version "1.1.7"
  jacoco
  id("org.sonarqube") version "7.2.3.7755"
  id("com.github.ben-manes.versions") version "0.53.0"
  id("org.openapi.generator") version "7.20.0"
  id("com.gorylenko.gradle-git-properties") version "2.5.7"
  id("com.github.jk1.dependency-license-report") version "3.1.1"
}

group = "it.gov.pagopa.emd.ar"
version = "0.7.0" // x-release-please-version
description = "emd-ar-backoffice-bff"

java {
  toolchain {
    languageVersion = JavaLanguageVersion.of(21)
  }
}

configurations {
  compileOnly {
    extendsFrom(configurations.annotationProcessor.get())
  }
  compileClasspath {
    resolutionStrategy.activateDependencyLocking()
  }
}

licenseReport {
  renderers =
    arrayOf(XmlReportRenderer("third-party-libs.xml", "Back-End Libraries"))
  outputDir = "$projectDir/dependency-licenses"
  filters = arrayOf(SpdxLicenseBundleNormalizer())
}
tasks.classes {
  finalizedBy(tasks.generateLicenseReport)
}

repositories {
  mavenCentral()
}

val springDocOpenApiVersion = "3.0.2"
val janinoVersion = "3.1.12"
val openApiToolsVersion = "0.2.9"
val micrometerVersion = "1.6.3"
val httpClientVersion = "5.6"
val httpCoreVersion = "5.4.1"

// fix cve
val jackson2CoreVersion = "2.21.1"
val jackson3CoreVersion = "3.1.0"

dependencies {

  // Webflux
  implementation("org.springframework.boot:spring-boot-starter-webflux")

  // OpenAPI
  implementation("org.springdoc:springdoc-openapi-starter-webflux-ui:$springDocOpenApiVersion")
  implementation("org.openapitools:jackson-databind-nullable:$openApiToolsVersion")


  // JWT token
  implementation("com.auth0:java-jwt:4.4.0")
  implementation("com.auth0:jwks-rsa:0.22.1")


  // Monitoring
  implementation("org.springframework.boot:spring-boot-starter-actuator")
  implementation("org.springframework.boot:spring-boot-starter-opentelemetry")
  implementation("io.micrometer:micrometer-tracing-bridge-otel:$micrometerVersion")
  implementation("io.micrometer:micrometer-registry-prometheus")

  implementation("org.springframework.data:spring-data-commons")

  // Utilities
  implementation("org.springframework.boot:spring-boot-starter-validation")
  implementation("org.codehaus.janino:janino:$janinoVersion")
  
  

  // CVE fix
  implementation("tools.jackson.core:jackson-core:$jackson3CoreVersion")
  implementation("com.fasterxml.jackson.core:jackson-core:$jackson2CoreVersion")

  implementation("io.micrometer:context-propagation")
  implementation("io.micrometer:micrometer-tracing-bridge-otel")

  compileOnly("org.projectlombok:lombok")
  annotationProcessor("org.projectlombok:lombok")
  testAnnotationProcessor("org.projectlombok:lombok")

  //	Testing
  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testImplementation("io.projectreactor:reactor-test")
  testImplementation("org.projectlombok:lombok")
}

tasks.withType<Test> {
  useJUnitPlatform()
  finalizedBy(tasks.jacocoTestReport)
}

val mockitoAgent = configurations.create("mockitoAgent")
dependencies {
  mockitoAgent("org.mockito:mockito-core") { isTransitive = false }
}
tasks {
  test {
    jvmArgs("-javaagent:${mockitoAgent.asPath}")
    testLogging.events = setOf(TestLogEvent.FAILED)
    testLogging.exceptionFormat = TestExceptionFormat.FULL
  }
}

tasks.jacocoTestReport {
  dependsOn(tasks.test)
  reports {
    xml.required = true
  }
}

val projectInfo = mapOf(
  "artifactId" to project.name,
  "version" to project.version
)

tasks {
  val processResources by getting(ProcessResources::class) {
    filesMatching("**/application.yml") {
      expand(projectInfo)
    }
  }
}

tasks.compileJava {
  dependsOn("dependenciesBuild")
}

tasks.register("dependenciesBuild") {
  group = "AutomaticallyGeneratedCode"
  description = "grouping all together automatically generate code tasks"

  dependsOn(
    "openApiGenerate"
  )
}

configure<SourceSetContainer> {
  named("main") {
    java.srcDir("$projectDir/build/generated/src/main/java")
  }
}

springBoot {
  buildInfo()
  mainClass.value("it.gov.pagopa.emd.ar.backoffice.BackofficeApplication")
}

openApiGenerate {
  generatorName.set("spring")
  inputSpec.set("$rootDir/openapi/backoffice-bff-java-repository.openapi.yaml")
  outputDir.set("$projectDir/build/generated")
  apiPackage.set("it.gov.pagopa.emd.ar.backoffice.controller.generated")
  modelPackage.set("it.gov.pagopa.emd.ar.backoffice.dto.generated")
  configOptions.set(
    mapOf(
      "reactive" to "true",
      "dateLibrary" to "java8",
      "requestMappingMode" to "api_interface",
      "useSpringBoot3" to "true",
      "interfaceOnly" to "true",
      "useTags" to "true",
      "useBeanValidation" to "true",
      "generateConstructorWithAllArgs" to "true",
      "generatedConstructorWithRequiredArgs" to "true",
      "enumPropertyNaming" to "original",
      "additionalModelTypeAnnotations" to "@lombok.experimental.SuperBuilder(toBuilder = true)"
    )
  )
}
