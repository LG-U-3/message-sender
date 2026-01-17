# 1) Build stage: 소스코드 + resources(db/migration, application-docker.yml 포함)로 JAR 생성
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

# gradle 캐시 효율을 위해 먼저 설정 파일만 복사
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./

# gradlew 실행 권한(리눅스 컨테이너에서 필요)
RUN chmod +x gradlew

# 의존성 먼저 받아두기(캐시)
RUN ./gradlew dependencies --no-daemon || true

# 나머지 소스 복사 (여기 안에 src/main/resources 포함됨)
COPY src src

# 빌드 (테스트는 도커 빌드 시간 줄이려고 보통 제외)
RUN ./gradlew clean bootJar -x test --no-daemon


# 2) Runtime stage: 실행만 담당 (가벼운 JRE)
FROM eclipse-temurin:17-jre
WORKDIR /app

# build stage에서 만든 jar를 복사
COPY --from=build /app/build/libs/*.jar app.jar

# 실행
ENTRYPOINT ["java","-Xms128m","-Xmx384m","-jar","/app/app.jar"]
