import os
import shutil
import stat
import subprocess
import tempfile
import unittest
from pathlib import Path

HERE = Path(__file__).parent


def executable(path: Path, text: str) -> None:
    path.write_text(text)
    path.chmod(path.stat().st_mode | stat.S_IEXEC)


class DrawAllTest(unittest.TestCase):
    """draw-all.sh, with a stand-in draw-pictures.sh that logs each part it's asked to draw."""

    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp())
        scripts = self.tmp / "scripts"
        scripts.mkdir()
        shutil.copy(HERE / "draw-all.sh", scripts / "draw-all.sh")
        # The stand-in draws nothing and fails: the emulator crashed.
        executable(scripts / "draw-pictures.sh", f'#!/bin/sh\necho "$2" >> "{self.tmp}/drawn.log"\nexit 1\n')
        bin_dir = self.tmp / "bin"
        bin_dir.mkdir()
        executable(bin_dir / "adb", "#!/bin/sh\nexit 0\n")
        self.env = {**os.environ, "PATH": f"{bin_dir}:{os.environ['PATH']}", "SETTLE_SECONDS": "0",
                    "PICTURES": str(self.tmp / "pictures"), "DRAW_BASE": "true"}

    def emulator(self) -> list[str]:
        """Runs draw-all.sh once, as one emulator would, and returns the parts it drew."""
        log = self.tmp / "drawn.log"
        log.unlink(missing_ok=True)
        subprocess.run(["bash", str(self.tmp / "scripts" / "draw-all.sh")], env=self.env, cwd=self.tmp, check=False)
        return [Path(line).name for line in log.read_text().split()] if log.exists() else []

    def test_the_first_emulator_draws_the_base_then_the_pull_request(self):
        self.assertEqual(["before", "after"], self.emulator())

    def test_a_fresh_emulator_draws_only_the_pull_request(self):
        self.emulator()
        self.assertEqual(["after"], self.emulator())
        self.assertEqual(["after"], self.emulator())

    def test_a_complete_part_isnt_drawn_again(self):
        (self.tmp / "pictures" / "after").mkdir(parents=True)
        (self.tmp / "pictures" / "after" / ".complete").touch()
        self.assertEqual(["before"], self.emulator())


if __name__ == "__main__":
    unittest.main()
