"""Pubblicazione su Google Play via Android Publisher API v3.

I comandi, usati dai workflow in .github/workflows/:

  upload   --aab <file> --track internal [--notes-dir docs/store/whatsnew]
      carica l'AAB e lo rende disponibile sul track (android.yml, a ogni
      push su main).
  promote  --from internal --to alpha [--rollout 0.2] [--notes-dir docs/store/whatsnew]
      prende la release attiva di un track e la mette su un altro, con le
      note di --notes-dir se date, altrimenti le stesse (play-promote.yml,
      a mano). Su GitHub Actions scrive version_code e name in GITHUB_OUTPUT.
  screenshots  --dir docs/store/screenshots
      sostituisce gli screenshot del telefono della scheda dello store con
      quelli di <dir>/<lingua>/*.png, in ordine di nome (play-listing.yml,
      a mano). Solo le lingue che la scheda ha.
  listing  --file docs/store/listing.md
      aggiorna descrizione breve e completa di it-IT ed en-US con i testi
      di listing.md (play-listing.yml, a mano). Titolo e resto invariati.
  universal-apk  --version-code 151 --out calm-otter.apk
      scarica l'APK universale che Play genera dall'AAB, firmato con la
      chiave di Google (github-release.yml). Non uno firmato da noi: quello
      non si installerebbe sopra la versione di Play, né viceversa, e
      cambiare fonte vorrebbe dire disinstallare e perdere la password.

Credenziali: il JSON di un service account in PLAY_SERVICE_ACCOUNT_JSON,
invitato in Play Console con il permesso di rilasciare sui track di test
(e in produzione, quando ci si arriva). Vedi docs/store/README.md.

Sui track che passano dalla revisione (closed testing, produzione) il
commit dell'edit invia la release in revisione da solo, come il pulsante
"Send for review" della Console.
"""

import argparse
import json
import os
import pathlib
import re
import sys

from google.oauth2 import service_account
from google.auth.transport.requests import AuthorizedSession

PACKAGE = "com.calmotter.app"
API = f"https://androidpublisher.googleapis.com/androidpublisher/v3/applications/{PACKAGE}"
UPLOAD_API = f"https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications/{PACKAGE}"


def session() -> AuthorizedSession:
    info = json.loads(os.environ["PLAY_SERVICE_ACCOUNT_JSON"])
    creds = service_account.Credentials.from_service_account_info(
        info, scopes=["https://www.googleapis.com/auth/androidpublisher"]
    )
    return AuthorizedSession(creds)


def call(s: AuthorizedSession, method: str, url: str, **kwargs) -> dict:
    r = s.request(method, url, **kwargs)
    if not r.ok:
        sys.exit(f"{method} {url} -> {r.status_code}\n{r.text}")
    return r.json() if r.content else {}


def listing_languages(s: AuthorizedSession, edit: str) -> set[str]:
    # Play rifiuta note in lingue che la scheda dello store non ha: si
    # tengono solo quelle presenti (oggi en-US; it-IT quando arriva la
    # traduzione della scheda).
    listings = call(s, "GET", f"{API}/edits/{edit}/listings").get("listings", [])
    return {l["language"] for l in listings}


def read_notes(notes_dir: pathlib.Path, languages: set[str]) -> list[dict]:
    notes = []
    for f in sorted(notes_dir.glob("*.txt")):
        if f.stem in languages:
            notes.append({"language": f.stem, "text": f.read_text().strip()[:500]})
    return notes


def upload(args) -> None:
    s = session()
    edit = call(s, "POST", f"{API}/edits")["id"]
    with open(args.aab, "rb") as f:
        bundle = call(
            s, "POST", f"{UPLOAD_API}/edits/{edit}/bundles",
            params={"uploadType": "media"},
            headers={"Content-Type": "application/octet-stream"},
            data=f, timeout=600,
        )
    code = str(bundle["versionCode"])
    release = {"name": args.name or code, "versionCodes": [code], "status": "completed"}
    if args.notes_dir:
        notes = read_notes(pathlib.Path(args.notes_dir), listing_languages(s, edit))
        if notes:
            release["releaseNotes"] = notes
    call(s, "PUT", f"{API}/edits/{edit}/tracks/{args.track}",
         json={"track": args.track, "releases": [release]})
    call(s, "POST", f"{API}/edits/{edit}:commit")
    print(f"versionCode {code} -> {args.track}")


def promote(args) -> None:
    s = session()
    edit = call(s, "POST", f"{API}/edits")["id"]
    source = call(s, "GET", f"{API}/edits/{edit}/tracks/{args.source}")
    active = [r for r in source.get("releases", []) if r.get("status") == "completed"]
    if not active:
        sys.exit(f"Nessuna release attiva su {args.source}")
    src = active[0]
    release = {
        "name": src.get("name", src["versionCodes"][0]),
        "versionCodes": src["versionCodes"],
        "releaseNotes": src.get("releaseNotes", []),
    }
    # Le note dal repo, non quelle del track di partenza: il 2026-10-16 la
    # 0.3.0 era salita sull'interno con le note della 0.2.0 rimaste in
    # whatsnew/, e promuoverla le avrebbe copiate tali e quali.
    if args.notes_dir:
        notes = read_notes(pathlib.Path(args.notes_dir), listing_languages(s, edit))
        if notes:
            release["releaseNotes"] = notes
    if args.rollout < 1:
        release["status"] = "inProgress"
        release["userFraction"] = args.rollout
    else:
        release["status"] = "completed"
    call(s, "PUT", f"{API}/edits/{edit}/tracks/{args.target}",
         json={"track": args.target, "releases": [release]})
    call(s, "POST", f"{API}/edits/{edit}:commit")
    print(f"versionCode {', '.join(src['versionCodes'])}: {args.source} -> {args.target}")
    if "GITHUB_OUTPUT" in os.environ:
        with open(os.environ["GITHUB_OUTPUT"], "a") as out:
            out.write(f"version_code={src['versionCodes'][-1]}\n")
            out.write(f"name={release['name']}\n")


# Le cartelle di docs/store/screenshots/ hanno la lingua corta, Play quella
# con la regione.
SCREENSHOT_LANGUAGES = {"it": "it-IT", "en": "en-US"}


def screenshots(args) -> None:
    s = session()
    edit = call(s, "POST", f"{API}/edits")["id"]
    languages = listing_languages(s, edit)
    done = []
    for short, language in SCREENSHOT_LANGUAGES.items():
        files = sorted((pathlib.Path(args.dir) / short).glob("*.png"))
        if language not in languages or not files:
            continue
        base = f"{API}/edits/{edit}/listings/{language}/phoneScreenshots"
        call(s, "DELETE", base)
        for f in files:
            with open(f, "rb") as data:
                call(s, "POST", f"{UPLOAD_API}/edits/{edit}/listings/{language}/phoneScreenshots",
                     params={"uploadType": "media"}, headers={"Content-Type": "image/png"},
                     data=data, timeout=120)
        done.append(f"{language}: {len(files)}")
    if not done:
        sys.exit("Nessuna lingua della scheda ha screenshot in " + args.dir)
    call(s, "POST", f"{API}/edits/{edit}:commit")
    print("screenshot caricati -> " + ", ".join(done))


def read_listing(path: pathlib.Path) -> dict[str, dict[str, str]]:
    """Le descrizioni di listing.md: la breve dai punti "**IT** (75): …",
    la completa dal blocco di codice sotto "## Full description — …"."""
    text = path.read_text()
    out = {}
    for short, language, title in (("IT", "it-IT", "Italiano"), ("EN", "en-US", "English")):
        brief = re.search(rf"^- \*\*{short}\*\* \(\d+\): (.+)$", text, re.M)
        full = re.search(rf"^## Full description — {title} \(\d+\)\n\n```\n(.*?)\n```", text, re.M | re.S)
        if not brief or not full:
            sys.exit(f"{path}: testi {title} non trovati")
        if len(brief.group(1)) > 80 or len(full.group(1)) > 4000:
            sys.exit(f"{path}: testi {title} oltre i limiti di Play (80 / 4000)")
        out[language] = {"shortDescription": brief.group(1).strip(), "fullDescription": full.group(1)}
    return out


def listing(args) -> None:
    s = session()
    edit = call(s, "POST", f"{API}/edits")["id"]
    languages = listing_languages(s, edit)
    done = []
    for language, texts in read_listing(pathlib.Path(args.file)).items():
        if language not in languages:
            continue
        # PATCH: cambia solo i campi passati, il titolo resta quello di Play.
        call(s, "PATCH", f"{API}/edits/{edit}/listings/{language}", json=texts)
        done.append(language)
    if not done:
        sys.exit("Nessuna lingua della scheda in " + args.file)
    call(s, "POST", f"{API}/edits/{edit}:commit")
    print("testi della scheda aggiornati -> " + ", ".join(done))


def universal_apk(args) -> None:
    s = session()
    generated = call(s, "GET", f"{API}/generatedApks/{args.version_code}")
    # Una voce per chiave di firma; con la firma delle app di Play è una sola.
    ids = [g["generatedUniversalApk"]["downloadId"]
           for g in generated.get("generatedApks", []) if "generatedUniversalApk" in g]
    if not ids:
        sys.exit(f"Nessun APK universale per versionCode {args.version_code}")
    url = f"{API}/generatedApks/{args.version_code}/downloads/{ids[0]}:download"
    r = s.get(url, params={"alt": "media"}, stream=True, timeout=600)
    if not r.ok:
        sys.exit(f"GET {url} -> {r.status_code}\n{r.text}")
    with open(args.out, "wb") as f:
        for chunk in r.iter_content(chunk_size=1 << 20):
            f.write(chunk)
    print(f"versionCode {args.version_code} -> {args.out}")


def main() -> None:
    p = argparse.ArgumentParser()
    sub = p.add_subparsers(required=True)

    u = sub.add_parser("upload")
    u.add_argument("--aab", required=True)
    u.add_argument("--track", default="internal")
    u.add_argument("--name")
    u.add_argument("--notes-dir")
    u.set_defaults(func=upload)

    pr = sub.add_parser("promote")
    pr.add_argument("--from", dest="source", required=True)
    pr.add_argument("--to", dest="target", required=True)
    pr.add_argument("--rollout", type=float, default=1.0)
    pr.add_argument("--notes-dir")
    pr.set_defaults(func=promote)

    sc = sub.add_parser("screenshots")
    sc.add_argument("--dir", default="docs/store/screenshots")
    sc.set_defaults(func=screenshots)

    li = sub.add_parser("listing")
    li.add_argument("--file", default="docs/store/listing.md")
    li.set_defaults(func=listing)

    ua = sub.add_parser("universal-apk")
    ua.add_argument("--version-code", required=True)
    ua.add_argument("--out", required=True)
    ua.set_defaults(func=universal_apk)

    args = p.parse_args()
    args.func(args)


if __name__ == "__main__":
    main()
