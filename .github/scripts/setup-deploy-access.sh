#!/usr/bin/env bash
# Lets GitHub Actions deploy to Firebase without a stored key (Workload Identity Federation).
#
#   .github/scripts/setup-deploy-access.sh <firebase-project-id> <github-owner/repo>
#
# Creates a "github-deploy" service account with the roles a Firebase deploy needs, and lets only
# the Deliver workflow on the repository's main branch act as it. Safe to run again. Needs gcloud,
# signed in as a project owner, and curl. Prints the repository variables to set at the end.
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
  roles/browser # reads the project's IAM policy, for backend/check-rules-access.cjs
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

# Functions run as the Compute Engine default service account, and the deploy must be allowed to
# use it: that account only, not every service account in the project.
NUMBER="$(gcloud projects describe "$PROJECT" --format 'value(projectNumber)')"
RUNTIME="$NUMBER-compute@developer.gserviceaccount.com"
echo "Letting it deploy functions that run as $RUNTIME"
gcloud iam service-accounts add-iam-policy-binding "$RUNTIME" --project "$PROJECT" \
  --member "serviceAccount:$ACCOUNT" --role roles/iam.serviceAccountUser > /dev/null
# Earlier versions of this script granted it on the whole project.
gcloud projects remove-iam-policy-binding "$PROJECT" --member "serviceAccount:$ACCOUNT" \
  --role roles/iam.serviceAccountUser --condition None --quiet > /dev/null 2>&1 || true

# storage.rules reads Firestore, which Storage may do only with this role. `firebase deploy` offers
# it only when a person runs it, and CI can't grant roles.
echo "Letting Storage rules read Firestore"
gcloud beta services identity create --service firebasestorage.googleapis.com --project "$PROJECT" > /dev/null
gcloud projects add-iam-policy-binding "$PROJECT" \
  --member "serviceAccount:service-$NUMBER@gcp-sa-firebasestorage.iam.gserviceaccount.com" \
  --role roles/firebaserules.firestoreServiceAgent --condition None --quiet > /dev/null

# The deploy also sets the Storage bucket's lifecycle rule (backend/apply-lifecycle.cjs), so it
# may manage the default bucket: that bucket only, not every bucket in the project.
BUCKET=""
for name in "$PROJECT.firebasestorage.app" "$PROJECT.appspot.com"; do
  if gcloud storage buckets describe "gs://$name" --project "$PROJECT" > /dev/null 2>&1; then
    BUCKET="$name"
    break
  fi
done
if [ -n "$BUCKET" ]; then
  echo "Letting it set the lifecycle rule on gs://$BUCKET"
  gcloud storage buckets add-iam-policy-binding "gs://$BUCKET" --member "serviceAccount:$ACCOUNT" \
    --role roles/storage.admin > /dev/null
else
  echo "No default Storage bucket yet: create it (docs/self-hosting.md, 2.5), then run this again." >&2
  exit 1
fi

if ! gcloud iam workload-identity-pools describe "$POOL" --project "$PROJECT" --location global > /dev/null 2>&1; then
  echo "Creating the workload identity pool"
  gcloud iam workload-identity-pools create "$POOL" --project "$PROJECT" --location global \
    --display-name "GitHub Actions"
fi

# Only the Deliver workflow on this repository's main branch may sign in: pull requests, other
# branches and other workflows can't deploy. The repository's numeric ID also pins it, so a
# repository created later under the same name couldn't either.
REPO_ID="$(curl -sSf "https://api.github.com/repos/$REPO" | sed -n 's/^  "id": \([0-9]*\),$/\1/p' || true)"
CONDITION="assertion.repository == '$REPO' && assertion.ref == 'refs/heads/main'"
CONDITION="$CONDITION && assertion.workflow_ref == '$REPO/.github/workflows/deliver.yml@refs/heads/main'"
if [ -n "$REPO_ID" ]; then
  CONDITION="$CONDITION && assertion.repository_id == '$REPO_ID'"
else
  echo "Couldn't look up $REPO on GitHub (private?), so its numeric ID isn't checked." >&2
fi
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
