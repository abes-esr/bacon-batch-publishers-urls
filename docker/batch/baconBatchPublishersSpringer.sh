LANG=fr_FR.UTF-8
if [[ $(pgrep -cf "SPRINGER") -lt 1 ]];
then
    java -XX:MaxRAMPercentage=80 -jar /scripts/bacon-batch-publishers.jar --spring.batch.job.name=jobRecuperationKbart --editeur=SPRINGER --logPath=./local/editeur/logs/
fi