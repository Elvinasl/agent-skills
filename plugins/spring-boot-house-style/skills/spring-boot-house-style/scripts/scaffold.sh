#!/usr/bin/env bash
# Scaffolds a new Spring Boot 4.1 service from ../templates.
#
# Usage:
#   scaffold.sh --name order-sync --package com.acme.ordersync --db postgres|mysql \
#               [--java 21] [--with rabbit,jobrunr,sentry] [--db-port 11432] [--dir ./order-sync]
set -euo pipefail

SKILL_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TEMPLATES="$SKILL_DIR/templates"

NAME="" PACKAGE="" DB="" JAVA_VERSION="21" WITH="" DB_PORT="" DIR=""
ALL_MODULES=(postgres mysql rabbit jobrunr sentry)

usage() { sed -n '2,7p' "$0" | sed 's/^# \{0,1\}//'; exit 1; }

while [[ $# -gt 0 ]]; do
  case "$1" in
    --name) NAME="$2"; shift 2 ;;
    --package) PACKAGE="$2"; shift 2 ;;
    --db) DB="$2"; shift 2 ;;
    --java) JAVA_VERSION="$2"; shift 2 ;;
    --with) WITH="$2"; shift 2 ;;
    --db-port) DB_PORT="$2"; shift 2 ;;
    --dir) DIR="$2"; shift 2 ;;
    -h|--help) usage ;;
    *) echo "unknown option: $1" >&2; usage ;;
  esac
done

[[ -n "$NAME" && -n "$PACKAGE" && -n "$DB" ]] || usage
[[ "$NAME" =~ ^[a-z][a-z0-9-]*$ ]] || { echo "--name must be kebab-case, e.g. order-sync" >&2; exit 1; }
[[ "$PACKAGE" =~ ^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$ ]] || { echo "--package must look like com.acme.ordersync" >&2; exit 1; }
[[ "$DB" == postgres || "$DB" == mysql ]] || { echo "--db must be postgres or mysql" >&2; exit 1; }
[[ "$JAVA_VERSION" =~ ^[0-9]+$ && "$JAVA_VERSION" -ge 21 ]] || { echo "--java must be 21 or newer" >&2; exit 1; }

DIR="${DIR:-./$NAME}"
if [[ -e "$DIR" && -n "$(ls -A "$DIR" 2>/dev/null)" ]]; then
  echo "$DIR exists and is not empty; refusing to overwrite" >&2
  exit 1
fi

APP_CLASS="$(echo "$NAME" | awk -F- '{for (i = 1; i <= NF; i++) printf "%s", toupper(substr($i, 1, 1)) substr($i, 2)}')"
DB_NAME="${NAME//-/_}"
GROUP="${PACKAGE%.*}"
PACKAGE_DIR="${PACKAGE//.//}"
if [[ -z "$DB_PORT" ]]; then
  [[ "$DB" == postgres ]] && DB_PORT=15432 || DB_PORT=13306
fi

ENABLED=("$DB")
IFS=',' read -r -a EXTRA <<< "$WITH"
for m in "${EXTRA[@]}"; do
  [[ -z "$m" ]] && continue
  case "$m" in
    rabbit|jobrunr|sentry) ENABLED+=("$m") ;;
    *) echo "unknown module in --with: $m (expected rabbit, jobrunr, sentry)" >&2; exit 1 ;;
  esac
done
is_enabled() { local x; for x in "${ENABLED[@]}"; do [[ "$x" == "$1" ]] && return 0; done; return 1; }

mkdir -p "$DIR"
cp -R "$TEMPLATES/project/." "$DIR/"
for m in rabbit jobrunr; do
  if is_enabled "$m" && [[ -d "$TEMPLATES/modules/$m" ]]; then
    cp -R "$TEMPLATES/modules/$m/." "$DIR/"
  fi
done

mv "$DIR/gitignore" "$DIR/.gitignore"
mv "$DIR/env.example" "$DIR/.env.example"

for src in main test integrationTest; do
  base="$DIR/src/$src/java"
  if [[ -d "$base/__PACKAGE_DIR__" ]]; then
    mkdir -p "$base/$PACKAGE_DIR"
    cp -R "$base/__PACKAGE_DIR__/." "$base/$PACKAGE_DIR/"
    rm -rf "$base/__PACKAGE_DIR__"
  fi
done

find "$DIR" -depth -name '*__APP_CLASS__*' | while read -r path; do
  mv "$path" "$(dirname "$path")/$(basename "$path" | sed "s/__APP_CLASS__/$APP_CLASS/g")"
done

DISABLED=""
for m in "${ALL_MODULES[@]}"; do is_enabled "$m" || DISABLED="$DISABLED $m"; done

find "$DIR" -type f ! -name '*.jar' | while read -r file; do
  DISABLED="$DISABLED" \
  T_APP_NAME="$NAME" T_APP_CLASS="$APP_CLASS" T_PACKAGE="$PACKAGE" T_GROUP="$GROUP" \
  T_DB_NAME="$DB_NAME" T_DB_PORT="$DB_PORT" T_JAVA_VERSION="$JAVA_VERSION" \
  perl -i -ne '
    BEGIN { %off = map { $_ => 1 } split " ", $ENV{DISABLED}; $skip = 0; }
    if (/>>>mod:(\w+)/) { $skip++ if $off{$1}; next; }
    if (/<<<mod:(\w+)/) { $skip-- if $off{$1}; next; }
    next if $skip;
    s/__APP_NAME__/$ENV{T_APP_NAME}/g;
    s/__APP_CLASS__/$ENV{T_APP_CLASS}/g;
    s/__PACKAGE__/$ENV{T_PACKAGE}/g;
    s/__GROUP__/$ENV{T_GROUP}/g;
    s/__DB_NAME__/$ENV{T_DB_NAME}/g;
    s/__DB_PORT__/$ENV{T_DB_PORT}/g;
    s/__JAVA_VERSION__/$ENV{T_JAVA_VERSION}/g;
    print;
  ' "$file"
done

chmod +x "$DIR/gradlew"

if grep -rl '__[A-Z_]*__\|>>>mod:\|<<<mod:' "$DIR" --exclude='*.jar' >/dev/null 2>&1; then
  echo "warning: unresolved template markers remain:" >&2
  grep -rn '__[A-Z_]*__\|>>>mod:\|<<<mod:' "$DIR" --exclude='*.jar' >&2 || true
  exit 1
fi

echo "Created $NAME in $DIR"
echo "  package:  $PACKAGE"
echo "  database: $DB (local port $DB_PORT)"
echo "  modules:  ${ENABLED[*]}"
echo "  java:     $JAVA_VERSION"
echo
echo "Next: cd $DIR && docker compose up -d && ./gradlew build"
