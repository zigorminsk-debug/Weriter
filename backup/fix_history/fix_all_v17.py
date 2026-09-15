import subprocess, pathlib
for script in ["fix_v17.py", "fix_grammar_v17.py", "fix_opds_v16.py", "fix_main_v16.py", "fix_layout_v16.py"]:
    p = pathlib.Path(f"/home/user/{script}")
    if p.exists():
        print(f"Running {script}")
        subprocess.run(["python3", str(p)], check=False)
# bump version
gradle = pathlib.Path("/home/user/zi-writer/app/build.gradle")
txt = gradle.read_text(encoding='utf-8')
txt = txt.replace('versionCode 16', 'versionCode 17').replace('versionCode 15', 'versionCode 17')
if '17.0 LIVING NO-PRONOUN' not in txt:
    txt = txt.replace('16.0 LIVING EDITOR v2', '17.0 LIVING NO-PRONOUN').replace('15.0 LIVING EDITOR', '17.0 LIVING NO-PRONOUN')
gradle.write_text(txt, encoding='utf-8')
print("All v17 fixes applied")
