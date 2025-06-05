#!/bin/sh

# Paramètres par défaut du conteneur
export BACON_BATCH_PUBLISHERS_AT_STARTUP=${BACON_BATCH_PUBLISHERS_AT_STARTUP:='1'}

# Réglage de /etc/environment pour que les crontab s'exécutent avec les bonnes variables d'env
echo "$(env)
LANG=fr_FR.UTF-8" > /etc/environment

# Charge la crontab depuis le template
if [ "$BACON_BATCH_PUBLISHERS_DEPLOY_CRON" = "1" ]; then
  envsubst < /etc/cron.d/tasks.tmpl > /etc/cron.d/tasks
  echo "-> Installation des crontab :"
  cat /etc/cron.d/tasks
  crontab /etc/cron.d/tasks
fi

# Force le démarrage du batch au démarrage du conteneur
if [ "$BACON_BATCH_PUBLISHERS_AT_STARTUP" = "1" ]; then
  echo "-> Lancement de baconBatchPublishersSpringer.sh au démarrage du conteneur"
  /scripts/baconBatchPublishersSpringer.sh
  echo "-> Lancement de baconBatchPublishersEmerald.sh au démarrage du conteneur"
  /scripts/baconBatchPublishersEmerald.sh
  echo "-> Lancement de baconBatchPublishersEbsco.sh au démarrage du conteneur"
  /scripts/baconBatchPublishersEbsco.sh
  echo "-> Lancement de baconBatchPublishersDegruyter.sh au démarrage du conteneur"
  /scripts/baconBatchPublishersDegruyter.sh
fi

exec "$@"
