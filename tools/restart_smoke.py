"""Exercise real disk persistence using this checkout's isolated local server."""
import os
import pathlib
import subprocess

root = pathlib.Path(__file__).resolve().parents[1]
directory = root / "run" / "server"
directory.mkdir(parents=True, exist_ok=True)
(directory / "eula.txt").write_text("eula=true\n", encoding="utf-8")
(directory / "server.properties").write_text(
    "server-ip=127.0.0.1\nserver-port=25578\nonline-mode=false\nlevel-name=world\n"
    "level-seed=12345\nview-distance=4\nsimulation-distance=3\nspawn-protection=0\n"
    "max-players=2\nmotd=Structure Control isolated test\n",
    encoding="utf-8",
)
wrapper = str(root / ("gradlew.bat" if os.name == "nt" else "gradlew"))
(root / "build").mkdir(exist_ok=True)
for stage in ("write", "verify"):
    log = root / "build" / ("restart-" + stage + ".log")
    arguments = [wrapper, "-PgameTests", "-PrestartSmoke=" + stage, "runServer", "--console=plain"]
    options = {"creationflags": subprocess.CREATE_NO_WINDOW} if os.name == "nt" else {"start_new_session": True}
    with log.open("w", encoding="utf-8") as stream:
        process = subprocess.Popen(arguments, cwd=root, stdout=stream, stderr=subprocess.STDOUT, **options)
        try:
            code = process.wait(timeout=300)
        except subprocess.TimeoutExpired:
            if os.name == "nt":
                subprocess.run(["taskkill", "/PID", str(process.pid), "/T", "/F"], capture_output=True)
            else:
                import signal
                os.killpg(process.pid, signal.SIGTERM)
            raise
    text = log.read_text(encoding="utf-8", errors="replace")
    marker = "STRUCTURE_CONTROL_RESTART_" + stage.upper() + "_OK"
    if code or marker not in text or "STRUCTURE_CONTROL_RESTART_FAILED" in text:
        raise SystemExit("Restart test failed; inspect " + str(log))
    print(marker, flush=True)
