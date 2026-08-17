plugins {
    java
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Database
    runtimeOnly("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    // Utils
    implementation("org.apache.commons:commons-lang3:3.14.0")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    // Parsing (LemanaPro) - placeholders for now
    implementation("org.jsoup:jsoup:1.18.1")
    // Playwright can be added later when implementing real parser
    // implementation("com.microsoft.playwright:playwright:1.47.0")

    // Dev
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.mockito:mockito-core")
}

tasks.bootJar {
    archiveBaseName.set("cost-monitor")
}
