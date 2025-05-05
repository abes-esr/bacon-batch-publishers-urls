LANG=fr_FR.UTF-8
if [[ $(pgrep -cf "SPRINGER") < 1 ]];
then
    java -XX:MaxRAMPercentage=80 org.springframework.boot.loader.JarLauncher --spring.batch.job.name=jobRecuperationKbart --editeur=SPRINGER
fi
