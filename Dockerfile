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
RUN java -Djarmode=layertools -jar bacon-batch-publishers.jar extract

###
# Socle commun aux images api et batch.
# IMPORTANT: tout ce qui est commun aux deux images doit rester ici, sinon les
# deux images divergent et chaque paquet est installé (et téléchargé) 2 fois.
# Les dnf sont regroupés en une seule transaction: chaque appel à dnf recharge
# les métadonnées des dépôts rockylinux, ce qui coûte plus cher que l'install.
FROM rockylinux:8 as runtime-base
WORKDIR scripts

# Les locales fr_FR
ENV LANG fr_FR.UTF-8
ENV LANGUAGE fr_FR:fr
ENV LC_ALL fr_FR.UTF-8

# Configuration du fuseau horaire
# Pour résoudre le problème de décalage horaire dans les logs
# de votre conteneur item-batch, vous devez ajouter ces instructions dans la partie batch-image de votre Dockerfile.
ENV TZ=Europe/Paris

# Activer le dépôt EPEL pour avoir plus de paquets, puis installer le socle:
# locales fr, fuseau horaire, java, chromium (pour le scraping)
RUN dnf install -y epel-release \
    && dnf install -y --nodocs \
         langpacks-fr \
         tzdata \
         java-17-openjdk \
         chromium \
         chromium-headless \
         wget \
         unzip \
    && dnf clean all && rm -rf /var/cache/dnf \
    && ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# Installer ChromeDriver compatible
ARG CHROMEDRIVER_VERSION=114.0.5735.90
RUN wget -q -O /tmp/chromedriver.zip \
       https://chromedriver.storage.googleapis.com/${CHROMEDRIVER_VERSION}/chromedriver_linux64.zip \
    && unzip /tmp/chromedriver.zip -d /usr/local/bin/ \
    && chmod +x /usr/local/bin/chromedriver \
    && rm /tmp/chromedriver.zip

# Pointer vers le binaire Chromium
ENV CHROME_BINARY=/usr/bin/chromium-browser

###
# Image pour le module web (api)
FROM runtime-base as api-image

COPY --from=batch-builder application/dependencies/ ./batch/
COPY --from=batch-builder application/spring-boot-loader/ ./batch/
COPY --from=batch-builder application/snapshot-dependencies/ ./batch/
COPY --from=batch-builder application/application/ ./batch/

COPY --from=batch-builder /application/*.jar /scripts/batch/bacon-batch-publishers.jar
COPY --from=build-image build/web/target/*.jar /scripts/bacon-web-publishers.jar
RUN chmod +x /scripts/batch/bacon-batch-publishers.jar \
    && chmod +x /scripts/bacon-web-publishers.jar \
    && mkdir /scripts/local/ \
    && chmod 776 /scripts/local/

EXPOSE 8082

ENTRYPOINT ["java", "-jar", "bacon-web-publishers.jar"]

###
# Image pour le module batch
FROM runtime-base as batch-image

# Les paquets d'abord, les artefacts applicatifs ensuite : sinon chaque
# changement de code invalide le cache des installations.
# systeme pour les crontab
# cronie: remplacant de crond qui support le CTRL+C dans docker (sans ce système c'est compliqué de stopper le conteneur)
# gettext: pour avoir envsubst qui permet de gérer le template tasks.tmpl
RUN dnf install -y --nodocs \
      procps \
      cronie \
      gettext \
      nss \
      GConf2 \
      libXScrnSaver \
      redhat-lsb-core \
    && crond -V && rm -rf /etc/cron.*/* \
    && dnf clean all && rm -rf /var/cache/dnf
COPY ./docker/batch/tasks.tmpl /etc/cron.d/tasks.tmpl

COPY --from=batch-builder application/dependencies/ ./
COPY --from=batch-builder application/spring-boot-loader/ ./
COPY --from=batch-builder application/snapshot-dependencies/ ./
COPY --from=batch-builder application/application/ ./

# Les scripts de lancement des batchs par editeur
COPY ./docker/batch/baconBatchPublishers*.sh /scripts/
COPY --from=batch-builder /application/bacon-batch-publishers.jar /scripts/bacon-batch-publishers.jar
RUN chmod +x /scripts/baconBatchPublishers*.sh \
    && chmod +x /scripts/bacon-batch-publishers.jar \
    && mkdir /scripts/local/ \
    && chmod 776 /scripts/local/

COPY ./docker/batch/docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod +x /docker-entrypoint.sh
ENTRYPOINT ["/docker-entrypoint.sh"]
CMD ["crond", "-n"]
