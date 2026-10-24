import os
import subprocess
import tempfile
import unittest

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

    def test_commits_alone_never_leave_0_x(self):
        self.commit("app/a.kt")
        self.git("tag", "v0.9.3")
        planned = self.auto("app", self.commit("app/a.kt", "Drop the old sync [breaking]"))
        self.assertEqual("0.10.0", planned["version"])
        self.assertIn("kept below 1.0.0", planned["reason"])

    def test_major_chosen_by_hand_leaves_0_x_and_later_versions_follow_the_tag(self):
        self.commit("app/a.kt")
        self.git("tag", "v0.9.3")
        sha = self.commit("app/a.kt")
        self.assertEqual("1.0.0", plan("app", "workflow_dispatch", "", sha, "major")["version"])
        self.git("tag", "v1.0.0")
        self.assertEqual("1.0.1", self.auto("app", self.commit("app/a.kt", "Fix"))["version"])
        self.git("tag", "v1.0.1")
        self.assertEqual("2.0.0", self.auto("app", self.commit("app/a.kt", "New sync [breaking]"))["version"])

    def test_every_part_can_be_chosen_by_hand_for_either(self):
        self.commit("app/a.kt")
        self.commit("backend/f.ts")
        self.git("tag", "v0.3.4")
        self.git("tag", "backend-v0.2.7")
        sha = self.git("rev-parse", "HEAD")
        for part, app, backend in (("patch", "0.3.5", "0.2.8"), ("minor", "0.4.0", "0.3.0"), ("major", "1.0.0", "1.0.0")):
            # Even with nothing changed: choosing a part is asking for a new version.
            self.assertEqual(app, plan("app", "workflow_dispatch", "", sha, part)["version"])
            self.assertEqual(backend, plan("backend", "workflow_dispatch", "", sha, part)["version"])
        self.assertEqual("backend-v1.0.0", plan("backend", "workflow_dispatch", "", sha, "major")["tag"])

    def test_a_first_version_chosen_as_major_is_1_0_0(self):
        sha = self.commit("app/a.kt")
        self.assertEqual("1.0.0", plan("app", "workflow_dispatch", "", sha, "major")["version"])
        self.assertEqual("0.1.0", plan("app", "workflow_dispatch", "", sha, "minor")["version"])

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
        self.assertEqual("false", plan("app", "workflow_dispatch", "", sha, "")["ship"])
        # By hand with auto, an app that didn't change isn't released again either.
        self.assertEqual("false", plan("app", "workflow_dispatch", "", self.commit("backend/f.ts"), "")["ship"])

    def test_a_backend_deploy_by_hand_redeploys_the_same_version(self):
        sha = self.commit("backend/f.ts")
        self.git("tag", "backend-v0.2.1")
        self.assertEqual(
            {"ship": "true", "version": "0.2.1", "tag": "", "reason": "redeploying backend-v0.2.1, unchanged since"},
            plan("backend", "workflow_dispatch", "", sha, ""),
        )
        self.assertEqual(
            "0.2.2",
            plan("backend", "workflow_dispatch", "", sha, "patch")["version"],
        )

    def test_a_pushed_tag_releases_that_app_version_if_it_is_above_the_latest(self):
        # As in Deliver, the checkout already has the tag that was pushed.
        sha = self.commit("app/a.kt")
        self.git("tag", "v0.5.0")
        self.assertEqual("0.5.0", plan("app", "push", "v0.5.0", sha, "")["version"])
        self.git("tag", "v1.2.0")
        self.assertEqual("1.2.0", plan("app", "push", "v1.2.0", sha, "")["version"])
        self.git("tag", "v0.4.9")
        for bad in ("v0.5", "v0.4.9", "backend-v0.6.0"):
            with self.assertRaises(SystemExit, msg=bad):
                plan("app", "push", bad, sha, "")


if __name__ == "__main__":
    unittest.main()
