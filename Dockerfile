# Faza 1: build jara (zavisnosti u zasebnom sloju radi kesiranja)
# Bazne slike sa AWS ogledala zvaničnih Docker slika (iste slike kao na Docker Hub-u): anonimni pull sa Docker Hub-a
# deli limit po IP adresi GitHub runnera i ume da obori CI pre build-a.
FROM public.ecr.aws/docker/library/eclipse-temurin:21-jdk-jammy AS build
WORKDIR /build
COPY pom.xml mvnw ./
COPY .mvn/ .mvn/
# chmod za svaki slučaj: checkout sa Windowsa može izgubiti exec bit
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline
COPY src/ src/
RUN ./mvnw -q -B -DskipTests package

# Faza 2: runtime, bez root-a
FROM public.ecr.aws/docker/library/eclipse-temurin:21-jre-jammy
RUN groupadd -g 1000 app && useradd -u 1000 -g app -m app
WORKDIR /app
COPY --from=build /build/target/*.jar /app/app.jar
USER app
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java","-jar","/app/app.jar"]
