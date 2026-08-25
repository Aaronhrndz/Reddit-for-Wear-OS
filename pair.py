import subprocess

adb = r"C:\Users\aaron\tools\platform-tools\adb.exe"
cmd = [adb, "pair", "192.168.1.176:37093", "229342"]
p = subprocess.run(cmd, capture_output=True, text=True, timeout=8)
print("STDOUT:", p.stdout)
print("STDERR:", p.stderr)
print("EXIT:", p.returncode)
