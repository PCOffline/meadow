#!/usr/bin/env bash
# Lets GitHub Actions deploy to Firebase without a stored key (Workload Identity Federation).
#
#   .github/scripts/setup-deploy-access.sh <firebase-project-id> <github-owner/repo>
#
# Creates a "github-deploy" service account that may deploy Meadow's backend and read none of its
# data, and lets only the Deliver workflow on the repository's main branch act as it. Also sets up
# the accounts the backend runs as (setup-backend-accounts.sh). Safe to run again. Needs gcloud
# with its beta component (`gcloud components install beta`), signed in as a project owner, and
# curl. Prints the repository variables to set at the end.
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

DEPLOY_ROLE="meadowDeploy"
LIFECYCLE_ROLE="meadowBucketLifecycle"

# What a deploy changes: the functions with their triggers and schedules, the Firestore and Storage
# rules, the indexes and hosting. None of these roles reads or writes Meadow's data.
ROLES=(
  roles/cloudfunctions.developer
  roles/run.developer
  roles/eventarc.developer
  roles/cloudscheduler.admin
  roles/firebaserules.admin
  roles/firebasehosting.admin
  roles/firebasestorage.viewer          # finds the default bucket for the Storage rules
  roles/datastore.indexAdmin
  roles/serviceusage.serviceUsageAdmin  # turns on APIs and their service accounts
  "projects/$PROJECT/roles/$DEPLOY_ROLE"
)
# What a deploy also needs and no narrow role gives.
DEPLOY_PERMISSIONS=(
  firebase.projects.get                 # every Firebase command
  resourcemanager.projects.get          # the project number
  resourcemanager.projects.getIamPolicy # the deploy's own checks, and backend/check-rules-access.cjs
  datastore.databases.getMetadata       # the Firestore database
  cloudfunctions.functions.setIamPolicy # anyone may call a new callable function,
  run.services.setIamPolicy             # and a schedule calls its function as meadow-functions
)
# Earlier versions of this script granted these. Each gives far more than a deploy needs.
OLD_ROLES=(
  roles/firebase.admin
  roles/cloudfunctions.admin
  roles/run.admin
  roles/eventarc.admin
  roles/pubsub.admin
  roles/artifactregistry.admin
  roles/serviceusage.serviceUsageConsumer
  roles/browser
)

echo "Turning on the APIs that keyless sign-in needs"
gcloud services enable iam.googleapis.com iamcredentials.googleapis.com sts.googleapis.com \
  cloudresourcemanager.googleapis.com --project "$PROJECT"

"$(dirname "$0")/setup-backend-accounts.sh" "$PROJECT"
NUMBER="$(gcloud projects describe "$PROJECT" --format 'value(projectNumber)')"
REGION="$(sed -n 's/^MEADOW_REGION=//p' "$(dirname "$0")/../../backend/functions/.env" | tr -d '[:space:]')"

if ! gcloud iam service-accounts describe "$ACCOUNT" --project "$PROJECT" > /dev/null 2>&1; then
  echo "Creating the $ACCOUNT_NAME service account"
  gcloud iam service-accounts create "$ACCOUNT_NAME" --project "$PROJECT" \
    --display-name "GitHub Actions deploys"
fi

# custom_role <id> <title> <permission>... creates the project's role, or sets its permissions.
custom_role() {
  local id="$1" title="$2"
  shift 2
  local permissions
  permissions="$(IFS=,; echo "$*")"
  if gcloud iam roles describe "$id" --project "$PROJECT" > /dev/null 2>&1; then
    gcloud iam roles update "$id" --project "$PROJECT" --permissions "$permissions" --quiet > /dev/null
  else
    gcloud iam roles create "$id" --project "$PROJECT" --title "$title" --stage GA \
      --permissions "$permissions" > /dev/null
  fi
}
custom_role "$DEPLOY_ROLE" "Meadow deploy" "${DEPLOY_PERMISSIONS[@]}"
custom_role "$LIFECYCLE_ROLE" "Meadow bucket lifecycle" storage.buckets.get storage.buckets.update

echo "Granting it the roles a deploy needs"
for role in "${ROLES[@]}"; do
  gcloud projects add-iam-policy-binding "$PROJECT" --member "serviceAccount:$ACCOUNT" \
    --role "$role" --condition None --quiet > /dev/null
done
for role in "${OLD_ROLES[@]}" roles/iam.serviceAccountUser; do
  gcloud projects remove-iam-policy-binding "$PROJECT" --member "serviceAccount:$ACCOUNT" \
    --role "$role" --condition None --quiet > /dev/null 2>&1 || true
done

# The functions run as meadow-functions and are built as the Compute Engine default account. The
# deploy may use those two accounts only, not every service account in the project.
for sa in "meadow-functions@$PROJECT.iam.gserviceaccount.com" "$NUMBER-compute@developer.gserviceaccount.com"; do
  echo "Letting it deploy functions that use $sa"
  gcloud iam service-accounts add-iam-policy-binding "$sa" --project "$PROJECT" \
    --member "serviceAccount:$ACCOUNT" --role roles/iam.serviceAccountUser > /dev/null
done

# The deploy deletes old images of the functions and keeps the repository's cleanup policy. The
# first deploy creates the repository: run this again after it.
if gcloud artifacts repositories describe gcf-artifacts --location "$REGION" --project "$PROJECT" > /dev/null 2>&1; then
  gcloud artifacts repositories add-iam-policy-binding gcf-artifacts --location "$REGION" --project "$PROJECT" \
    --member "serviceAccount:$ACCOUNT" --role roles/artifactregistry.repoAdmin > /dev/null
else
  echo "No function images yet: after the first deploy, run this again." >&2
fi

# The deploy sets the default bucket's lifecycle rule (backend/apply-lifecycle.cjs): on that bucket
# only, and without access to the files in it.
for name in "$PROJECT.firebasestorage.app" "$PROJECT.appspot.com"; do
  if gcloud storage buckets describe "gs://$name" --project "$PROJECT" > /dev/null 2>&1; then
    echo "Letting it set the lifecycle rule on gs://$name"
    gcloud storage buckets add-iam-policy-binding "gs://$name" --member "serviceAccount:$ACCOUNT" \
      --role "projects/$PROJECT/roles/$LIFECYCLE_ROLE" > /dev/null
    gcloud storage buckets remove-iam-policy-binding "gs://$name" --member "serviceAccount:$ACCOUNT" \
      --role roles/storage.admin > /dev/null 2>&1 || true
    break
  fi
done

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
