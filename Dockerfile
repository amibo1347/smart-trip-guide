# ── 빌드 단계: JDK 21 + Node 20 (gradle 이 프론트까지 빌드) ──
FROM eclipse-temurin:21-jdk-jammy AS build

# Node 20 설치 (gradle frontendBuild 가 npm 을 호출하므로 필요)
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl ca-certificates gnupg \
 && curl -fsSL https://deb.nodesource.com/setup_20.x | bash - \
 && apt-get install -y --no-install-recommends nodejs \
 && apt-get clean && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY . .
# bootJar = 프론트 빌드(npm install + vite) → static 복사 → 실행 가능한 단일 jar
RUN chmod +x gradlew && ./gradlew bootJar --no-daemon -x test

# ── 실행 단계: JRE 만 ──
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar

# 운영 프로파일 기본 활성(로그/에러 노출 최소화). 포트는 PORT(플랫폼 주입) → SERVER_PORT → 8082 순.
ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8082

ENTRYPOINT ["sh", "-c", "java -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom -jar app.jar"]
