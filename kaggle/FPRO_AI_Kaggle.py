# FPRO AI — Kaggle tek hücre başlatıcı
# Kaggle Notebook'ta Accelerator = GPU seçin ve bu hücreyi çalıştırın.
import os, sys, subprocess, shutil, time
from pathlib import Path

ROOT=Path("/kaggle/working/A-V-DEO")
if ROOT.exists():
    subprocess.run(["git","-C",str(ROOT),"fetch","origin","main"],check=True)
    subprocess.run(["git","-C",str(ROOT),"reset","--hard","origin/main"],check=True)
else:
    subprocess.run(["git","clone","--depth","1","https://github.com/efecanca/A-V-DEO.git",str(ROOT)],check=True)

# Kaggle Secrets'ta NGROK_AUTHTOKEN varsa otomatik al.
try:
    from kaggle_secrets import UserSecretsClient
    token=UserSecretsClient().get_secret("NGROK_AUTHTOKEN")
    if token:
        os.environ["NGROK_AUTHTOKEN"]=token
except Exception:
    pass

os.environ["KEHRIBAR_REPO_ROOT"]=str(ROOT)
print("FPRO AI repo:", subprocess.check_output(["git","-C",str(ROOT),"rev-parse","--short","HEAD"],text=True).strip())
print("GPU:")
subprocess.run(["nvidia-smi","--query-gpu=name,memory.total","--format=csv,noheader"],check=False)

# Colab başlatıcısı GPU/FastAPI/CogVideoX/ngrok için platformdan bağımsızdır.
# Google'a özel secret okuması başarısız olursa env/Kaggle Secret kullanılır.
runpy_code="import runpy; runpy.run_path("+repr(str(ROOT/"colab"/"start_server.py"))+", run_name='__main__')"
exec(runpy_code)
