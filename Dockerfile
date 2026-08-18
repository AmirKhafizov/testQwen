# ---- build ----
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace

RUN apt-get update && apt-get install -y --no-install-recommends unzip curl \
    && curl -fsSL https://services.gradle.org/distributions/gradle-8.10.2-bin.zip -o /tmp/gradle.zip \
    && unzip -q /tmp/gradle.zip -d /opt \
    && ln -s /opt/gradle-8.10.2/bin/gradle /usr/local/bin/gradle \
    && rm -rf /var/lib/apt/lists/* /tmp/gradle.zip

COPY settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
COPY app app

RUN gradle :app:bootJar -x test --no-daemon

# ---- runtime ----
FROM eclipse-temurin:21-jre-jammy AS runtime

WORKDIR /app

# Chromium system dependencies for Playwright
RUN apt-get update && apt-get install -y --no-install-recommends \
      libnss3 libnspr4 libatk1.0-0 libatk-bridge2.0-0 libcups2 libdrm2 \
      libxkbcommon0 libxcomposite1 libxdamage1 libxfixes3 libxrandr2 \
      libgbm1 libasound2 libpango-1.0-0 libcairo2 libatspi2.0-0 \
      fonts-liberation fonts-noto-core \
      ca-certificates curl \
    && curl -fsSL https://deb.nodesource.com/setup_20.x | bash - \
    && apt-get install -y --no-install-recommends nodejs \
    && npx --yes playwright@1.47.0 install --with-deps chromium \
    && apt-get purge -y curl \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /workspace/app/build/libs/*.jar /app/app.jar

ENV PLAYWRIGHT_BROWSERS_PATH=/root/.cache/ms-playwright
ENV JAVA_OPTS="-Xms256m -Xmx512m"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
