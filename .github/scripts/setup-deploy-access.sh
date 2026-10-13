#!/usr/bin/env bash
# Lets GitHub Actions deploy to Firebase without a stored key (Workload Identity Federation).
#
#   .github/scripts/setup-deploy-access.sh <firebase-project-id> <github-owner/repo>
#
# Creates a "github-deploy" service account with the roles a Firebase deploy needs, and lets only
# workflows on the repository's main branch act as it. Safe to run again. Needs gcloud, signed in
# as a project owner. Prints the repository variables to set at the end.
set -euo pipefail

if [ $# -ne 2 ]; then
  echo "Usage: $0 <firebase-project-id> <github-owner/repo>" >&2
  exit 1
fi
PROJECT="$1"
REPO="$2" # exactly as GitHub shows it, e.g. PCOffline/meadow
ACCOUNT_NAME="github-deploy"
ACCOUNT="$ACCOUNT_NAME@$PROJECT.iam.gserviceaccount.com"
POOL="github"
PROVIDER="github"

# Roles for deploying rules, indexes, functions (with their triggers and schedules) and hosting.
ROLES=(
  roles/firebase.admin
  roles/cloudfunctions.admin
  roles/run.admin
  roles/eventarc.admin
  roles/pubsub.admin
  roles/cloudscheduler.admin
  roles/artifactregistry.admin
  roles/datastore.indexAdmin
  roles/serviceusage.serviceUsageConsumer
  # To deploy functions that run as the project's default service accounts.
  roles/iam.serviceAccountUser
)

echo "Turning on the APIs that keyless sign-in needs"
gcloud services enable iam.googleapis.com iamcredentials.googleapis.com sts.googleapis.com \
  cloudresourcemanager.googleapis.com --project "$PROJECT"

if ! gcloud iam service-accounts describe "$ACCOUNT" --project "$PROJECT" > /dev/null 2>&1; then
  echo "Creating the $ACCOUNT_NAME service account"
  gcloud iam service-accounts create "$ACCOUNT_NAME" --project "$PROJECT" \
    --display-name "GitHub Actions deploys"
fi

echo "Granting it the roles a deploy needs"
for role in "${ROLES[@]}"; do
  gcloud projects add-iam-policy-binding "$PROJECT" --member "serviceAccount:$ACCOUNT" \
    --role "$role" --condition None --quiet > /dev/null
done

if ! gcloud iam workload-identity-pools describe "$POOL" --project "$PROJECT" --location global > /dev/null 2>&1; then
  echo "Creating the workload identity pool"
  gcloud iam workload-identity-pools create "$POOL" --project "$PROJECT" --location global \
    --display-name "GitHub Actions"
fi

# Only this repository's main branch may sign in: pull requests and other branches can't deploy.
CONDITION="assertion.repository == '$REPO' && assertion.ref == 'refs/heads/main'"
if ! gcloud iam workload-identity-pools providers describe "$PROVIDER" --project "$PROJECT" \
  --location global --workload-identity-pool "$POOL" > /dev/null 2>&1; then
  echo "Creating the GitHub provider"
  gcloud iam workload-identity-pools providers create-oidc "$PROVIDER" --project "$PROJECT" \
    --location global --workload-identity-pool "$POOL" --display-name "GitHub" \
    --issuer-uri "https://token.actions.githubusercontent.com" \
    --attribute-mapping "google.subject=assertion.sub,attribute.repository=assertion.repository,attribute.ref=assertion.ref" \
    --attribute-condition "$CONDITION"
else
  gcloud iam workload-identity-pools providers update-oidc "$PROVIDER" --project "$PROJECT" \
    --location global --workload-identity-pool "$POOL" --attribute-condition "$CONDITION" > /dev/null
fi

# A pool or provider created a moment ago can take a minute to be found, so ask a few times.
retry() {
  local attempt
  for attempt in 1 2 3 4 5 6 7 8 9 10 11 12; do
    if "$@" 2> /dev/null; then return 0; fi
    echo "Waiting for Google Cloud to catch up ($attempt)…" >&2
    sleep 10
  done
  "$@"
}
POOL_NAME="$(retry gcloud iam workload-identity-pools describe "$POOL" --project "$PROJECT" --location global --format 'value(name)')"
PROVIDER_NAME="$(retry gcloud iam workload-identity-pools providers describe "$PROVIDER" --project "$PROJECT" \
  --location global --workload-identity-pool "$POOL" --format 'value(name)')"

echo "Letting $REPO act as the service account"
retry gcloud iam service-accounts add-iam-policy-binding "$ACCOUNT" --project "$PROJECT" \
  --role roles/iam.workloadIdentityUser \
  --member "principalSet://iam.googleapis.com/$POOL_NAME/attribute.repository/$REPO" > /dev/null

cat <<EOF

Done. In GitHub, open Settings → Secrets and variables → Actions → Variables and add:

  FIREBASE_PROJECT                 $PROJECT
  GCP_WORKLOAD_IDENTITY_PROVIDER   $PROVIDER_NAME
  GCP_SERVICE_ACCOUNT              $ACCOUNT
  AUTO_DEPLOY                      true    (to deploy after every passing push to main)
EOF
