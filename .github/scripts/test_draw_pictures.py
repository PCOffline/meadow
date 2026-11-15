import os
import stat
import subprocess
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).with_name("draw-pictures.sh")


def executable(path: Path, text: str) -> None:
    path.write_text(text)
    path.chmod(path.stat().st_mode | stat.S_IEXEC)


class DrawPicturesTest(unittest.TestCase):
    def run_script(self, pull: str, listing: str = "", gradle: str = "exit 0", out_files=()) -> tuple[subprocess.CompletedProcess, Path, Path]:
        tmp = Path(tempfile.mkdtemp())
        bin_dir, checkout, out = tmp / "bin", tmp / "checkout", tmp / "out"
        bin_dir.mkdir()
        checkout.mkdir()
        for name in out_files:
            (out / name).parent.mkdir(parents=True, exist_ok=True)
            (out / name).write_text("drawn before")
        # A stand-in emulator. "adb pull" of the whole folder does [pull]; of one file, makes it.
        # "adb shell cd … find" lists [listing]; "adb push" keeps what was pushed in pushed.txt.
        executable(bin_dir / "adb", f"""#!/bin/sh
case "$1 $2" in
  "pull "*/.) {pull} ;;
  "pull "*) mkdir -p "$(dirname "$3")" && echo picture > "$3" ;;
  "push "*) cp "$2" "{tmp}/pushed.txt" ;;
  "shell cd"*) printf '{listing}' ;;
esac
exit 0
""")
        executable(checkout / "gradlew", f"#!/bin/sh\n{gradle}\n")
        env = {**os.environ, "PATH": f"{bin_dir}:{os.environ['PATH']}", "COPY_EVERY": "0.2"}
        result = subprocess.run(["bash", str(SCRIPT), str(checkout), str(out)], env=env, capture_output=True, text=True, check=False)
        return result, out, tmp

    def test_fails_when_the_emulator_stopped_and_nothing_was_copied(self):
        result, out, _ = self.run_script("echo \"adb: error: device 'emulator-5554' not found\" >&2; exit 1")
        self.assertEqual(1, result.returncode)
        self.assertIn("No pictures were copied", result.stdout)
        self.assertFalse((out / ".complete").exists())

    def test_passes_and_marks_the_folder_complete_when_the_pictures_were_copied(self):
        result, out, _ = self.run_script('mkdir -p "$3/gallery" && touch "$3/gallery/a.png"')
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn("Has 1 pictures", result.stdout)
        self.assertTrue((out / "gallery" / "a.png").exists())
        self.assertTrue((out / ".complete").exists())

    def test_keeps_what_it_copied_while_drawing_when_the_emulator_stops(self):
        # The picture is listed twice at the same size while the tests run, then the emulator goes.
        result, out, _ = self.run_script("exit 1", listing="120 ./gallery/a.png\\n", gradle="sleep 1; exit 1")
        self.assertEqual(1, result.returncode)
        self.assertTrue((out / "gallery" / "a.png").exists(), result.stdout + result.stderr)
        self.assertIn("Has 1 pictures", result.stdout)
        self.assertFalse((out / ".complete").exists())

    def test_isnt_complete_when_the_emulator_stopped_though_the_tests_passed(self):
        # Gradle passed, but the emulator crashed partway, so the last copy fails.
        result, out, _ = self.run_script("exit 1", listing="120 ./gallery/a.png\\n", gradle="sleep 1; exit 0")
        self.assertEqual(1, result.returncode)
        self.assertTrue((out / "gallery" / "a.png").exists(), result.stdout + result.stderr)
        self.assertFalse((out / ".complete").exists())

    def test_tells_a_fresh_emulator_what_is_already_drawn(self):
        _, _, tmp = self.run_script('true', out_files=["gallery/meadow-day/a.png", "stories/HoursStory/story.json"])
        pushed = (tmp / "pushed.txt").read_text().split()
        self.assertEqual(sorted(["gallery/meadow-day/a.png", "stories/HoursStory/story.json"]), sorted(pushed))


if __name__ == "__main__":
    unittest.main()
