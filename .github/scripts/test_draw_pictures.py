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
    def run_script(self, pull: str) -> tuple[subprocess.CompletedProcess, Path]:
        tmp = Path(tempfile.mkdtemp())
        bin_dir, checkout, out = tmp / "bin", tmp / "checkout", tmp / "out"
        bin_dir.mkdir()
        checkout.mkdir()
        # A stand-in emulator: [pull] says what "adb pull" does.
        executable(bin_dir / "adb", f'#!/bin/sh\nif [ "$1" = pull ]; then {pull}; fi\nexit 0\n')
        executable(checkout / "gradlew", "#!/bin/sh\nexit 0\n")
        env = {**os.environ, "PATH": f"{bin_dir}:{os.environ['PATH']}"}
        result = subprocess.run(["bash", str(SCRIPT), str(checkout), str(out)], env=env, capture_output=True, text=True, check=False)
        return result, out

    def test_fails_when_the_emulator_stopped_and_nothing_was_copied(self):
        result, _ = self.run_script("echo \"adb: error: device 'emulator-5554' not found\" >&2; exit 1")
        self.assertEqual(1, result.returncode)
        self.assertIn("No pictures were copied", result.stdout)

    def test_passes_when_the_pictures_were_copied(self):
        result, out = self.run_script('mkdir -p "$3/gallery" && touch "$3/gallery/a.png"')
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn("Drew 1 pictures", result.stdout)
        self.assertTrue((out / "gallery" / "a.png").exists())


if __name__ == "__main__":
    unittest.main()
