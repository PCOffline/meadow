import os
import re
import shlex
import subprocess
import tempfile
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
PROJECT = "meadow-test"
DEPLOY = f"serviceAccount:github-deploy@{PROJECT}.iam.gserviceaccount.com"
RUNTIME = f"serviceAccount:meadow-functions@{PROJECT}.iam.gserviceaccount.com"
BUILDER = "serviceAccount:123-compute@developer.gserviceaccount.com"

# A stand-in gcloud: logs each call; nothing exists yet except the project, its bucket and the
# functions' image repository. $RUN_AS is the account the deployed functions run as, and
# $SCHEDULED the schedule jobs that still call as the build account.
FAKE_GCLOUD = r"""#!/usr/bin/env bash
{ printf '%q ' "$@"; echo; } >> "$LOG"
case "$*" in
  "projects describe"*) echo 123 ;;
  "storage buckets describe gs://meadow-test.firebasestorage.app"*) ;;
  "storage buckets describe"*) exit 1 ;;
  "artifacts repositories describe"*) ;;
  "run services list"*) [ -n "$RUN_AS" ] && echo "$RUN_AS" ;;
  "scheduler jobs list"*) [ -n "$SCHEDULED" ] && echo "$SCHEDULED" ;;
  "iam service-accounts describe"* | "iam roles describe"*) exit 1 ;;
  "iam workload-identity-pools"*"describe"*"--format"*) echo projects/123/locations/global/workloadIdentityPools/github ;;
  "iam workload-identity-pools"*"describe"*) exit 1 ;;
esac
exit 0
"""


class SetupScriptsTest(unittest.TestCase):
    """Runs the setup scripts against a stand-in gcloud and checks who gets which role."""

    def run_script(self, *args, run_as="", scheduled=""):
        with tempfile.TemporaryDirectory() as bin_dir:
            for name, body in (("gcloud", FAKE_GCLOUD), ("curl", "#!/bin/sh\nexit 1\n")):
                path = os.path.join(bin_dir, name)
                with open(path, "w") as f:
                    f.write(body)
                os.chmod(path, 0o755)
            log = os.path.join(bin_dir, "log")
            env = {**os.environ, "PATH": f"{bin_dir}:{os.environ['PATH']}", "LOG": log, "RUN_AS": run_as,
                   "SCHEDULED": scheduled}
            subprocess.run([os.path.join(HERE, args[0]), *args[1:]], check=True, env=env, capture_output=True)
            with open(log) as f:
                return [shlex.split(line) for line in f]

    @staticmethod
    def bindings(calls, verb, member):
        """The roles that [calls] [verb] (add/remove) for [member], on the project or on any resource."""
        roles = set()
        for call in calls:
            if f"{verb}-iam-policy-binding" in call and member in call:
                roles.add((call[0], call[call.index("--role") + 1]))
        return roles

    def deploy_access(self, run_as=""):
        return self.run_script("setup-deploy-access.sh", PROJECT, "PCOffline/meadow", run_as=run_as)

    def test_the_deploy_account_gets_no_admin_role_over_data(self):
        added = {role for _, role in self.bindings(self.deploy_access(), "add", DEPLOY)}
        for broad in ("roles/owner", "roles/editor", "roles/firebase.admin", "roles/storage.admin",
                      "roles/storage.objectAdmin", "roles/datastore.user", "roles/firebaseauth.admin",
                      "roles/run.admin", "roles/cloudfunctions.admin", "roles/pubsub.admin", "roles/browser"):
            self.assertNotIn(broad, added)

    def test_the_deploy_account_loses_the_roles_it_used_to_have(self):
        removed = {role for _, role in self.bindings(self.deploy_access(), "remove", DEPLOY)}
        for old in ("roles/firebase.admin", "roles/run.admin", "roles/cloudfunctions.admin", "roles/pubsub.admin",
                    "roles/artifactregistry.admin", "roles/browser", "roles/storage.admin",
                    "roles/iam.serviceAccountUser"):
            self.assertIn(old, removed)

    def test_the_deploy_account_acts_only_as_the_two_function_accounts(self):
        calls = self.deploy_access()
        acting = [c for c in calls if c[:2] == ["iam", "service-accounts"] and "roles/iam.serviceAccountUser" in c]
        self.assertEqual(sorted(c[3] for c in acting),
                         ["123-compute@developer.gserviceaccount.com", f"meadow-functions@{PROJECT}.iam.gserviceaccount.com"])
        self.assertNotIn(("projects", "roles/iam.serviceAccountUser"), self.bindings(calls, "add", DEPLOY))

    def test_the_deploy_account_may_change_the_bucket_but_not_its_files(self):
        calls = self.deploy_access()
        on_bucket = {role for scope, role in self.bindings(calls, "add", DEPLOY) if scope == "storage"}
        self.assertEqual(on_bucket, {f"projects/{PROJECT}/roles/meadowBucketLifecycle"})
        lifecycle = next(c for c in calls if c[:3] == ["iam", "roles", "create"] and "meadowBucketLifecycle" in c)
        self.assertEqual(lifecycle[lifecycle.index("--permissions") + 1], "storage.buckets.get,storage.buckets.update")

    def test_the_functions_run_with_only_what_they_use(self):
        calls = self.run_script("setup-backend-accounts.sh", PROJECT)
        self.assertEqual(self.bindings(calls, "add", RUNTIME), {
            ("projects", "roles/datastore.user"),
            ("projects", "roles/firebaseauth.admin"),
            ("projects", "roles/firebasecloudmessaging.admin"),
            ("projects", "roles/eventarc.eventReceiver"),
            ("projects", "roles/run.invoker"),
            ("storage", "roles/storage.objectAdmin"),
        })

    def test_the_build_account_keeps_its_old_roles_while_a_function_runs_as_it(self):
        calls = self.run_script("setup-backend-accounts.sh", PROJECT, run_as=BUILDER.split(":")[1])
        self.assertEqual(self.bindings(calls, "remove", BUILDER), set())

    def test_the_build_account_loses_its_old_roles_once_no_function_runs_as_it(self):
        calls = self.run_script("setup-backend-accounts.sh", PROJECT, run_as=RUNTIME.split(":")[1])
        removed = {role for _, role in self.bindings(calls, "remove", BUILDER)}
        for old in ("roles/datastore.user", "roles/storage.objectAdmin", "roles/firebaseauth.admin",
                    "roles/artifactregistry.admin", "roles/artifactregistry.repoAdmin"):
            self.assertIn(old, removed)
        self.assertNotIn("roles/cloudbuild.builds.builder", removed)
        self.assertIn(("artifacts", "roles/artifactregistry.writer"), self.bindings(calls, "add", BUILDER))

    def test_schedules_made_before_the_move_call_as_the_functions_account(self):
        calls = self.run_script("setup-backend-accounts.sh", PROJECT, scheduled="firebase-schedule-hourlyCare-europe-west1")
        listed = next(c for c in calls if c[:3] == ["scheduler", "jobs", "list"])
        self.assertIn(f"httpTarget.oidcToken.serviceAccountEmail={BUILDER.split(':')[1]}", listed)
        moved = [c for c in calls if c[:4] == ["scheduler", "jobs", "update", "http"]]
        self.assertEqual([c[4] for c in moved], ["firebase-schedule-hourlyCare-europe-west1"])
        self.assertEqual(moved[0][moved[0].index("--oidc-service-account-email") + 1], RUNTIME.split(":")[1])

    def test_no_schedule_is_changed_when_none_calls_as_the_build_account(self):
        calls = self.run_script("setup-backend-accounts.sh", PROJECT)
        self.assertFalse([c for c in calls if c[:3] == ["scheduler", "jobs", "update"]])

    def test_storage_rules_may_read_firestore(self):
        calls = self.run_script("setup-backend-accounts.sh", PROJECT)
        agent = "serviceAccount:service-123@gcp-sa-firebasestorage.iam.gserviceaccount.com"
        self.assertIn(("projects", "roles/firebaserules.firestoreServiceAgent"), self.bindings(calls, "add", agent))

    def test_the_functions_name_the_account_the_script_creates(self):
        with open(os.path.join(ROOT, "backend/functions/src/index.ts")) as f:
            named = re.search(r"`([a-z-]+)@\$\{projectId\(\)\}\.iam\.gserviceaccount\.com`", f.read()).group(1)
        with open(os.path.join(HERE, "setup-backend-accounts.sh")) as f:
            created = re.search(r'^RUNTIME_NAME="([a-z-]+)"', f.read(), re.MULTILINE).group(1)
        self.assertEqual(named, created)


if __name__ == "__main__":
    unittest.main()
