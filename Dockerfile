FROM registry.access.redhat.com/ubi8/openjdk-11:1.14-12.1675788288
USER root
WORKDIR /tmp/src
ADD . /tmp/src
RUN ./gradlew :assemble

FROM registry.access.redhat.com/ubi8/openjdk-11:1.14-12.1675788288
COPY --from=0 /tmp/src/build/libs/* /deployments/
