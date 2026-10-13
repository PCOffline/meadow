import os
import subprocess
import tempfile
import unittest

import plan_version
from plan_version import plan


class PlanVersionTest(unittest.TestCase):
    """Each test runs in a fresh git repository, committing and tagging as Deliver would."""

    def setUp(self):
        self.old = os.getcwd()
        self.dir = tempfile.TemporaryDirectory()
        os.chdir(self.dir.name)
        self.git("init", "-q", "-b", "main")
        self.git("config", "user.email", "test@example.com")
        self.git("config", "user.name", "Test")

    def tearDown(self):
        os.chdir(self.old)
        self.dir.cleanup()

    def git(self, *args):
        return subprocess.run(["git", *args], check=True, capture_output=True, text=True).stdout.strip()

    def commit(self, path, message="Change"):
        os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
        with open(path, "a") as f:
            f.write("x\n")
        self.git("add", "-A")
        self.git("commit", "-q", "-m", message)
        return self.git("rev-parse", "HEAD")

    def auto(self, name, sha):
        return plan(name, "workflow_run", "", sha, "")

    def test_both_start_at_0_1_0(self):
        sha = self.commit("app/a.kt")
        self.commit("backend/f.ts")
        sha = self.git("rev-parse", "HEAD")
        self.assertEqual({"ship": "true", "version": "0.1.0", "tag": "v0.1.0", "reason": "first version"}, self.auto("app", sha))
        self.assertEqual("backend-v0.1.0", self.auto("backend", sha)["tag"])

    def test_each_only_ships_when_its_own_files_change(self):
        self.commit("app/a.kt")
        self.commit("backend/f.ts")
        self.git("tag", "v0.1.0")
        self.git("tag", "backend-v0.1.0")
        sha = self.commit("backend/f.ts", "Fix a rule")
        self.assertEqual("false", self.auto("app", sha)["ship"])
        self.assertEqual("backend-v0.1.1", self.auto("backend", sha)["tag"])
        sha = self.commit("docs/x.md")
        self.git("tag", "backend-v0.1.1", sha)
        self.assertEqual("false", self.auto("backend", self.commit("README.md"))["ship"])

    def test_the_legal_documents_belong_to_both(self):
        self.commit("app/a.kt")
        self.git("tag", "v0.1.0")
        self.git("tag", "backend-v0.1.0")
        sha = self.commit("legal/privacy_notice.md")
        self.assertEqual("v0.1.1", self.auto("app", sha)["tag"])
        self.assertEqual("backend-v0.1.1", self.auto("backend", sha)["tag"])

    def test_commit_messages_choose_the_part(self):
        self.commit("app/a.kt")
        self.git("tag", "v0.3.4")
        self.commit("app/a.kt", "Add schedules [minor]")
        self.assertEqual("0.4.0", self.auto("app", self.commit("app/a.kt", "Fix a typo"))["version"])

    def test_only_commits_touching_the_component_count(self):
        self.commit("app/a.kt")
        self.commit("backend/f.ts")
        self.git("tag", "v0.3.4")
        self.git("tag", "backend-v0.2.0")
        self.commit("backend/f.ts", "New callable [minor]")
        sha = self.commit("app/a.kt", "Fix a crash")
        self.assertEqual("0.3.5", self.auto("app", sha)["version"])
        self.assertEqual("0.3.0", self.auto("backend", sha)["version"])

    def test_stays_below_1_0_0(self):
        self.commit("app/a.kt")
        self.git("tag", "v0.9.3")
        planned = self.auto("app", self.commit("app/a.kt", "Drop the old sync [breaking]"))
        self.assertEqual("0.10.0", planned["version"])
        self.assertIn("while below 1.0.0", planned["reason"])
        self.assertEqual("0.10.0", plan("app", "workflow_dispatch", "", self.git("rev-parse", "HEAD"), "major")["version"])
        with self.assertRaises(SystemExit):
            plan("app", "push", "v1.0.0", self.git("rev-parse", "HEAD"), "")

    def test_versions_compare_as_numbers(self):
        self.commit("app/a.kt")
        self.git("tag", "v0.9.0")
        self.git("tag", "v0.10.0", self.commit("app/a.kt"))
        self.assertEqual("0.10.1", self.auto("app", self.commit("app/a.kt"))["version"])

    def test_skip_markers_and_already_shipped(self):
        self.commit("app/a.kt")
        sha = self.commit("backend/f.ts", "Tidy [skip deploy]")
        self.assertEqual("false", self.auto("backend", sha)["ship"])
        self.assertEqual("true", self.auto("app", sha)["ship"])
        self.git("tag", "v0.1.0", sha)
        self.assertEqual("false", self.auto("app", sha)["ship"])
        self.assertEqual("false", plan("app", "workflow_dispatch", "", sha, "patch")["ship"])

    def test_a_backend_deploy_by_hand_redeploys_the_same_version(self):
        sha = self.commit("backend/f.ts")
        self.git("tag", "backend-v0.2.1")
        self.assertEqual(
            {"ship": "true", "version": "0.2.1", "tag": "", "reason": "redeploying backend-v0.2.1, unchanged since"},
            plan("backend", "workflow_dispatch", "", sha, ""),
        )

    def test_a_pushed_tag_releases_that_app_version(self):
        sha = self.commit("app/a.kt")
        self.assertEqual("0.5.0", plan("app", "push", "v0.5.0", sha, "")["version"])
        with self.assertRaises(SystemExit):
            plan("app", "push", "v0.5", sha, "")

    def test_the_ceiling_is_one_constant(self):
        self.commit("app/a.kt")
        self.git("tag", "v0.9.3")
        sha = self.commit("app/a.kt", "[major]")
        original = plan_version.MAX_MAJOR
        try:
            plan_version.MAX_MAJOR = 1
            self.assertEqual("1.0.0", self.auto("app", sha)["version"])
        finally:
            plan_version.MAX_MAJOR = original


if __name__ == "__main__":
    unittest.main()
