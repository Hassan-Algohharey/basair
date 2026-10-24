"""Create a local Play upload key once. Secrets never go to stdout or Git."""
import os
import secrets
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
private = ROOT / '.secrets'
properties = ROOT / 'keystore.properties'
keystore = private / 'basair-upload.jks'
if keystore.exists() or properties.exists():
    assert keystore.is_file() and properties.is_file(), 'Partial signing setup; inspect locally before proceeding.'
    print('Existing local upload key retained.')
else:
    private.mkdir(exist_ok=True)
    password = secrets.token_urlsafe(36)
    env = {**os.environ, 'BASAIR_STORE_PASSWORD': password}
    keytool = Path(r'C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot\bin\keytool.exe')
    subprocess.run([str(keytool), '-genkeypair', '-keystore', str(keystore), '-storetype', 'JKS',
        '-storepass:env', 'BASAIR_STORE_PASSWORD', '-keypass:env', 'BASAIR_STORE_PASSWORD',
        '-alias', 'basair-upload', '-keyalg', 'RSA', '-keysize', '4096', '-validity', '10000',
        '-dname', 'CN=Basair Upload, OU=Android, O=Basair'], env=env, check=True, capture_output=True)
    properties.write_text('storeFile=.secrets/basair-upload.jks\nstorePassword=' + password +
                          '\nkeyAlias=basair-upload\nkeyPassword=' + password + '\n', encoding='ascii')
    print('Created local RSA-4096 upload key. Secret files are excluded from Git.')
