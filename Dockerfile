###
# Image pour la compilation
FROM maven:3-eclipse-temurin-17 as build-image
WORKDIR /build/

# On lance la compilation Java
# On débute par une mise en cache docker des dépendances Java
# cf https://www.baeldung.com/ops/docker-cache-maven-dependencies
COPY ./pom.xml /build/pom.xml
RUN mvn verify --fail-never

COPY .   /build/
RUN mvn --batch-mode \
        -Dmaven.test.skip=false \
        -Duser.timezone=Europe/Paris \
        -Duser.language=fr \
        package

###
# Image pour le module batch
# Remarque: l'image openjdk:17 n'est pas utilisée car nous avons besoin de cronie
#           qui n'est que disponible sous centos/rockylinux.
FROM maven:3-eclipse-temurin-17 as batch-builder
WORKDIR application
COPY --from=build-image build/batch/target/*.jar bacon-batch-publishers.jar
COPY --from=build-image build/web/target/*.jar bacon-web-publishers.jar
RUN java -Djarmode=layertools -jar bacon-batch-publishers.jar extract
RUN java -Djarmode=layertools -jar bacon-web-publishers.jar extract

FROM rockylinux:8 as api-image
WORKDIR scripts
#locales fr
# Les locales fr_FR
RUN dnf install langpacks-fr glibc-all-langpacks -y
ENV LANG fr_FR.UTF-8
ENV LANGUAGE fr_FR:fr
ENV LC_ALL fr_FR.UTF-8

# Configuration du fuseau horaire
# Pour résoudre le problème de décalage horaire dans les logs
# de votre conteneur item-batch, vous devez ajouter ces instructions dans la partie batch-image de votre Dockerfile.
ENV TZ=Europe/Paris
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

COPY --from=batch-builder application/dependencies/ ./
COPY --from=batch-builder application/spring-boot-loader/ ./
COPY --from=batch-builder application/snapshot-dependencies/ ./
COPY --from=batch-builder application/application/ ./
# systeme pour les crontab
# cronie: remplacant de crond qui support le CTRL+C dans docker (sans ce système c'est compliqué de stopper le conteneur)
# gettext: pour avoir envsubst qui permet de gérer le template tasks.tmpl

# Le JAR et le script pour le batch de LN
RUN dnf install -y java-17-openjdk

RUN dnf install -y tzdata && \
    ln -fs /usr/share/zoneinfo/Europe/Paris /etc/localtime && \
    echo "Europe/London" > /etc/timezone


COPY --from=batch-builder /application/bacon-batch-publishers.jar /scripts/bacon-batch-publishers.jar
COPY --from=batch-builder /application/bacon-web-publishers.jar /scripts/bacon-web-publishers.jar
RUN chmod +x /scripts/bacon-batch-publishers.jar
RUN chmod +x /scripts/bacon-web-publishers.jar

RUN mkdir /scripts/local/
RUN chmod 776 /scripts/local/

ENTRYPOINT ["java", "-jar", "bacon-web-publishers.jar"]

FROM rockylinux:8 as batch-image
WORKDIR scripts
#locales fr
# Les locales fr_FR
RUN dnf install langpacks-fr glibc-all-langpacks -y
ENV LANG fr_FR.UTF-8
ENV LANGUAGE fr_FR:fr
ENV LC_ALL fr_FR.UTF-8

# Configuration du fuseau horaire
# Pour résoudre le problème de décalage horaire dans les logs
# de votre conteneur item-batch, vous devez ajouter ces instructions dans la partie batch-image de votre Dockerfile.
ENV TZ=Europe/Paris
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

COPY --from=batch-builder application/dependencies/ ./
COPY --from=batch-builder application/spring-boot-loader/ ./
COPY --from=batch-builder application/snapshot-dependencies/ ./
COPY --from=batch-builder application/application/ ./
# systeme pour les crontab
# cronie: remplacant de crond qui support le CTRL+C dans docker (sans ce système c'est compliqué de stopper le conteneur)
# gettext: pour avoir envsubst qui permet de gérer le template tasks.tmpl
RUN yum install -y procps
RUN dnf install -y cronie gettext && \
    crond -V && rm -rf /etc/cron.*/*
COPY ./docker/batch/tasks.tmpl /etc/cron.d/tasks.tmpl
# Le JAR et le script pour le batch de LN
RUN dnf install -y java-17-openjdk

RUN dnf install -y tzdata && \
    ln -fs /usr/share/zoneinfo/Europe/Paris /etc/localtime && \
    echo "Europe/London" > /etc/timezone

COPY ./docker/batch/baconBatchPublishersSpringer.sh /scripts/baconBatchPublishersSpringer.sh
RUN chmod +x /scripts/baconBatchPublishersSpringer.sh

COPY --from=batch-builder /application/bacon-batch-publishers.jar /scripts/bacon-batch-publishers.jar
COPY --from=batch-builder /application/bacon-web-publishers.jar /scripts/bacon-web-publishers.jar
RUN chmod +x /scripts/bacon-batch-publishers.jar
RUN chmod +x /scripts/bacon-web-publishers.jar

RUN mkdir /scripts/local/
RUN chmod 776 /scripts/local/

COPY ./docker/batch/docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod +x /docker-entrypoint.sh
ENTRYPOINT ["/docker-entrypoint.sh"]
CMD ["crond", "-n"]
