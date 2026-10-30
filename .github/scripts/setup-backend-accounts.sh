#!/usr/bin/env bash
# Sets up the accounts that Meadow's backend runs as, each with only what it uses.
#
#   .github/scripts/setup-backend-accounts.sh <firebase-project-id>
#
# - "meadow-functions" runs every Cloud Function (backend/functions/src/index.ts names it).
# - Cloud Storage's own account may read Firestore, which storage.rules does.
# - The Compute Engine default account only builds the functions. Its old roles for running them
#   are removed once no function runs as it any more: run this again after the next deploy.
#
# Run it before the first deploy. Safe to run again. Needs gcloud with its beta component
# (`gcloud components install beta`), signed in as a project owner.
set -euo pipefail

if [ $# -ne 1 ]; then
  echo "Usage: $0 <firebase-project-id>" >&2
  exit 1
fi
PROJECT="$1"
RUNTIME_NAME="meadow-functions"
RUNTIME="$RUNTIME_NAME@$PROJECT.iam.gserviceaccount.com"
NUMBER="$(gcloud projects describe "$PROJECT" --format 'value(projectNumber)')"
BUILDER="$NUMBER-compute@developer.gserviceaccount.com"
REGION="$(sed -n 's/^MEADOW_REGION=//p' "$(dirname "$0")/../../backend/functions/.env" | tr -d '[:space:]')"
if [ -z "$REGION" ]; then
  echo "Set MEADOW_REGION in backend/functions/.env first." >&2
  exit 1
fi

# What the functions use. Storage is limited to the default bucket, below.
RUNTIME_ROLES=(
  roles/datastore.user               # Firestore
  roles/firebaseauth.admin           # deleting an account
  roles/firebasecloudmessaging.admin # notifications
  roles/eventarc.eventReceiver       # Firestore triggers
  roles/run.invoker                  # triggers and schedules call the functions as this account
)
# What the Compute Engine default account held when the functions ran as it.
OLD_RUNTIME_ROLES=(
  "${RUNTIME_ROLES[@]}"
  roles/storage.objectAdmin
  roles/artifactregistry.admin
  roles/artifactregistry.repoAdmin
)

bind() { # bind <member> <role>
  gcloud projects add-iam-policy-binding "$PROJECT" --member "$1" --role "$2" --condition None --quiet > /dev/null
}
unbind() { # unbind <member> <role>, if it has it
  gcloud projects remove-iam-policy-binding "$PROJECT" --member "$1" --role "$2" --condition None --quiet \
    > /dev/null 2>&1 || true
}

BUCKET=""
for name in "$PROJECT.firebasestorage.app" "$PROJECT.appspot.com"; do
  if gcloud storage buckets describe "gs://$name" --project "$PROJECT" > /dev/null 2>&1; then
    BUCKET="$name"
    break
  fi
done
if [ -z "$BUCKET" ]; then
  echo "No default Storage bucket yet: create it (docs/self-hosting.md, 2.5), then run this again." >&2
  exit 1
fi

if ! gcloud iam service-accounts describe "$RUNTIME" --project "$PROJECT" > /dev/null 2>&1; then
  echo "Creating the $RUNTIME_NAME service account"
  gcloud iam service-accounts create "$RUNTIME_NAME" --project "$PROJECT" \
    --display-name "Meadow's Cloud Functions"
fi
echo "Granting $RUNTIME_NAME what the functions use"
for role in "${RUNTIME_ROLES[@]}"; do
  bind "serviceAccount:$RUNTIME" "$role"
done
gcloud storage buckets add-iam-policy-binding "gs://$BUCKET" --member "serviceAccount:$RUNTIME" \
  --role roles/storage.objectAdmin > /dev/null

# storage.rules reads Firestore, which Storage may do only with this role. `firebase deploy` offers
# it only when a person runs it, and CI can't grant roles.
echo "Letting Storage rules read Firestore"
gcloud beta services identity create --service firebasestorage.googleapis.com --project "$PROJECT" > /dev/null
bind "serviceAccount:service-$NUMBER@gcp-sa-firebasestorage.iam.gserviceaccount.com" \
  roles/firebaserules.firestoreServiceAgent

# The builds write the functions' images to this repository, which the first deploy creates.
if gcloud artifacts repositories describe gcf-artifacts --location "$REGION" --project "$PROJECT" > /dev/null 2>&1; then
  gcloud artifacts repositories add-iam-policy-binding gcf-artifacts --location "$REGION" --project "$PROJECT" \
    --member "serviceAccount:$BUILDER" --role roles/artifactregistry.writer > /dev/null
fi

# A function still running as the Compute Engine default account needs its roles until the next
# deploy moves it.
if gcloud run services list --project "$PROJECT" --format 'value(spec.template.spec.serviceAccountName)' \
  | grep -qx "$BUILDER"; then
  echo "Some functions still run as $BUILDER. Deploy, then run this again to remove its old roles."
else
  echo "Removing the roles that $BUILDER held to run the functions"
  for role in "${OLD_RUNTIME_ROLES[@]}"; do
    unbind "serviceAccount:$BUILDER" "$role"
  done
fi
