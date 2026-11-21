#!/usr/bin/env bash
BROKER="${1:-localhost}"
PORT="${2:-1883}"

echo "Écoute des alertes sur carbon/alerts (tcp://$BROKER:$PORT)..."
mosquitto_sub -h "$BROKER" -p "$PORT" -t "carbon/alerts" -v