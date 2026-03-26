# Example Repository Template

This repository serves as an **example template** to kick-start your projects with pre-configured files and folders for **OpenAPI**, **Helm**, **Gradle**, **Java**, and **JUnit testing**. It is designed to streamline the initial setup of new projects and ensure consistency in project structure.

---

## 📂 Repository Structure

Here is a quick overview of the files and directories included in this repository:

```plaintext
.
├── .devops/            # DevOps pipelines
├── .github/            # GitHub configuration files
├── gradle/             # Gradle wrapper files
├── helm/               # Helm charts for Kubernetes deployments
├── openapi/            # OpenAPI specification files
├── src/                # Source code for the Java application
│   ├── main/
│   └── test/
├── build.gradle.kts    # Gradle build file
├── Dockerfile          # Docker build file
├── README.md           # Project documentation
├── settings.gradle.kts # Gradle settings file
└── .gitignore          # Git ignore rules
```

## 🚀 Features

### 📜 OpenAPI

- Example OpenAPI specification file (`backoffice-bff-java-repository.openapi.yaml`) to document your RESTful APIs.
- Compatible with tools like Swagger and Postman.

### ⚙️ Helm

- Template Helm charts for deploying your Java application on Kubernetes.
- Includes `values.yaml` for parameter configuration and pre-defined deployment manifests.

### 🔧 Gradle

- `build.gradle` file with dependencies and plugins for building, testing, and running your Java application.
- Compatible with Java 21+.

### ☕ Java

- Example Java application structure with a simple `HelloWorld` class.

### ✅ JUnit

- Example JUnit test cases under the `test/` directory to help you get started with unit testing.

---

## 🛠️ Getting Started

### Prerequisites

Ensure the following tools are installed on your machine:

1. **Java 21+**
2. **Gradle** (or use the Gradle wrapper included in the repository)
3. **Docker** (for Helm-related tasks, optional)

### Building & Run

#### Build

```sh
./gradlew clean build
```

#### Run local

```sh
./gradlew bootRun
```

#### Write Locks

```sh
./gradlew dependencies --write-locks
```
