#!/usr/bin/env bash
set -euo pipefail

# Ephemeral CI identity, never an official release or a matching-host identity.
test ! -e sign.properties
test ! -e app/ci-contract.jks
keytool -genkeypair -noprompt -storetype PKCS12 \
  -keystore app/ci-contract.jks -storepass android -keypass android \
  -alias ci-contract -keyalg RSA -keysize 2048 -validity 2 \
  -dname 'CN=AutoJs6 Contract CI,OU=Ephemeral,O=CI,L=CI,ST=CI,C=ZZ'
printf '%s\n' 'storeFile=ci-contract.jks' 'storePassword=android' \
  'keyAlias=ci-contract' 'keyPassword=android' > sign.properties
