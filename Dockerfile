# ---- build ----
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace

COPY settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
COPY gradlew gradlew
COPY app app

# Wrapper may be missing in repo — generate if needed
RUN if [ ! -f gradlew ]; then \
      apt-get update && apt-get install -y --no-install-recommends unzip curl \
      && curl -fsSL https://services.gradle.org/distributions/gradle-8.10.2-bin.zip -o /tmp/gradle.zip \
      && unzip -q /tmp/gradle.zip -d /opt \
      && /opt/gradle-8.10.2/bin/gradle wrapper --gradle-version 8.10.2; \
    fi \
 && chmod +x gradlew \
 && ./gradlew :app:bootJar -x test --no-daemon

# ---- runtime ----
# Playwright Java needs browser deps; use jammy + install chromium via playwright CLI
FROM eclipse-temurin:21-jre-jammy AS runtime

WORKDIR /app

# System libs for headless Chromium (Playwright)
RUN apt-get update && apt-get install -y --no-install-recommends \
      libnss3 libnspr4 libatk1.0-0 libatk-bridge2.0-0 libcups2 libdrm2 \
      libxkbcommon0 libxcomposite1 libxdamage1 libxfixes3 libxrandr2 \
      libgbm1 libasound2 libpango-1.0-0 libcairo2 libatspi2.0-0 \
      fonts-liberation fonts-noto-core \
      ca-certificates \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /workspace/app/build/libs/cost-monitor-*.jar /app/app.jar

# Install Playwright browsers into image (version must match dependency)
# Extract playwright from the fat jar is awkward; install via npm is simpler for Chromium
RUN apt-get update && apt-get install -y --no-install-recommends curl ca-certificates \
    && curl -fsSL https://deb.nodesource.com/setup_20.x | bash - \
    && apt-get install -y --no-install-recommends nodejs \
    && npx --yes playwright@1.47.0 install --with-deps chromium \
    && apt-get purge -y curl \
    && rm -rf /var/lib/apt/lists/*

ENV PLAYWRIGHT_BROWSERS_PATH=/root/.cache/ms-playwright
ENV JAVA_OPTS="-Xms256m -Xmx512m"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
