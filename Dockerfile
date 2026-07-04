# 운영 배포용 멀티스테이지 빌드. docker-compose.prod.yml 의 `app` 서비스가 이 파일로 빌드한다.
# 1) 프론트(Vite) 빌드 → 2) 그 산출물을 포함해 Gradle bootJar → 3) JRE만 있는 런타임 이미지.

# ---- 1. 프론트엔드 빌드 ----
FROM node:20-alpine AS frontend-build
WORKDIR /workspace/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build
# vite.config.js 의 build.outDir("../src/main/resources/static") 에 의해
# 산출물은 /workspace/src/main/resources/static 에 생긴다.

# ---- 2. 백엔드 빌드 (프론트 산출물 포함) ----
FROM eclipse-temurin:21-jdk AS backend-build
WORKDIR /workspace
# KR1-Lite 저사양 환경 빌드 시 Gradle 자체 메모리 상한(스왑 2GB로 커버되지만 상한을 둬 안전).
ENV GRADLE_OPTS="-Dorg.gradle.daemon=false -Dorg.gradle.jvmargs=-Xmx512m"
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew
COPY src ./src
COPY --from=frontend-build /workspace/src/main/resources/static ./src/main/resources/static
# 배포 이미지 빌드에는 테스트를 포함하지 않는다(테스트는 CI/로컬에서 별도 실행 — 여기서 돌리면
# 이미지 빌드가 테스트 DB 연결 여부에 좌우돼 배포가 불안정해진다).
RUN ./gradlew bootJar --no-daemon -x test

# ---- 3. 런타임 ----
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=backend-build /workspace/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
