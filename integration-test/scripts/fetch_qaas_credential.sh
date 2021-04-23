#!/usr/bin/env bash

set -ex

secret_version="e0b2f9be740e4cd9b4fe645b40a3a05a"

CREDENTIAL_LOCATION="./credential/qaas-credential.json"

if [[ -z $AZURE_CLIENT_ID ]]; then
  echo "Variable AZURE_CLIENT_ID not set, using az login."
  az login
else
  az login -u $AZURE_CLIENT_ID -p $AZURE_CLIENT_SECRET --service-principal --tenant $AZURE_TENANT_ID
fi

mkdir -p ./credential

echo "Executing secret fetching from Azure 'jenkins-secret' store"
az keyvault secret show --name "qaas-texas-service-account-credential" --vault-name "jenkins-secret" --version $secret_version --query 'value' -o tsv | jq '.' > $CREDENTIAL_LOCATION

echo "Checking if valid json file was fetched: $CREDENTIAL_LOCATION"
cat $CREDENTIAL_LOCATION | jq type


