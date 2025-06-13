LANG=fr_FR.UTF-8
if [[ $(pgrep -cf "DUKE") -lt 1 ]];
then
    java -XX:MaxRAMPercentage=80 -jar /scripts/bacon-batch-publishers.jar --spring.batch.job.name=jobRecuperationKbart --editeur=DUKE --logPath=./local/editeur/logs/
fi