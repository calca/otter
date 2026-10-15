"""Pubblicazione su Google Play via Android Publisher API v3.

Due comandi, usati dai workflow in .github/workflows/:

  upload   --aab <file> --track internal [--notes-dir docs/store/whatsnew]
      carica l'AAB e lo rende disponibile sul track (android.yml, a ogni
      push su main).
  promote  --from internal --to alpha [--rollout 0.2]
      prende la release attiva di un track e la mette su un altro, con le
      stesse note (play-promote.yml, a mano).

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
    if args.rollout < 1:
        release["status"] = "inProgress"
        release["userFraction"] = args.rollout
    else:
        release["status"] = "completed"
    call(s, "PUT", f"{API}/edits/{edit}/tracks/{args.target}",
         json={"track": args.target, "releases": [release]})
    call(s, "POST", f"{API}/edits/{edit}:commit")
    print(f"versionCode {', '.join(src['versionCodes'])}: {args.source} -> {args.target}")


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
    pr.set_defaults(func=promote)

    args = p.parse_args()
    args.func(args)


if __name__ == "__main__":
    main()
