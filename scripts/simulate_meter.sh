#!/usr/bin/env bash
BROKER="${1:-localhost}"
PORT="${2:-1883}"

echo "Envoi des relevés sur home/energy (tcp://$BROKER:$PORT)..."
while true; do
  KWH=$(awk -v min=0.5 -v max=4.5 'BEGIN{srand(); printf "%.2f", min+rand()*(max-min)}')
  GAS=$(awk -v min=0.1 -v max=1.5 'BEGIN{srand(); printf "%.2f", min+rand()*(max-min)}')
  mosquitto_pub -h "$BROKER" -p "$PORT" -t "home/energy" -m "{\"electricity_kwh\": $KWH, \"gas_m3\": $GAS}"
  sleep 5
done