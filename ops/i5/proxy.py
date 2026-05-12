"""MamY proxy — translates Ollama-format requests to Groq Cloud (with local Ollama fallback).

The Android app keeps using OllamaProvider unchanged. This proxy listens on
127.0.0.1:11435 and presents the Ollama HTTP shape (/api/generate, /api/tags)
while routing actual inference to Groq's chat completions endpoint. If Groq
returns 5xx or rate-limits, we fall back to a local Ollama instance running
on 127.0.0.1:11434 — slow on this CPU but always available.
"""
import logging
import os
import sys
import time
from pathlib import Path
from typing import Optional

import httpx
from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import FileResponse, HTMLResponse
from pydantic import BaseModel

DIST_DIR = Path(os.environ.get("MAMY_DIST_DIR", "/opt/mamy-dist"))

GROQ_API_KEY = os.environ.get("GROQ_API_KEY")
if not GROQ_API_KEY:
    print("Missing GROQ_API_KEY env var", file=sys.stderr)
    sys.exit(1)

GROQ_URL = "https://api.groq.com/openai/v1/chat/completions"
DEFAULT_MODEL = os.environ.get("GROQ_MODEL", "llama-3.1-8b-instant")
OLLAMA_FALLBACK_URL = os.environ.get("OLLAMA_FALLBACK_URL", "http://127.0.0.1:11434")
OLLAMA_FALLBACK_MODEL = os.environ.get("OLLAMA_FALLBACK_MODEL", "llama3.2:3b-instruct-q4_K_M")

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
log = logging.getLogger("mamy-proxy")

app = FastAPI()


class GenerateRequest(BaseModel):
    model: Optional[str] = None
    prompt: str
    system: Optional[str] = None
    format: Optional[str] = None
    stream: bool = False
    options: Optional[dict] = None
    keep_alive: Optional[str] = None


_INSTALL_PAGE = """<!doctype html>
<html lang="fr"><meta charset="utf-8">
<title>MamY — alpha install</title>
<meta name="viewport" content="width=device-width,initial-scale=1">
<style>
  :root{color-scheme:light dark}
  body{font-family:system-ui,-apple-system,sans-serif;max-width:520px;margin:2.5rem auto;padding:0 1.25rem;line-height:1.55}
  h1{margin:0 0 .25rem;font-size:1.6rem}
  .badge{display:inline-block;background:#e0e7ff;color:#3730a3;padding:.15rem .5rem;border-radius:.25rem;font-size:.75rem;letter-spacing:.04em}
  a.btn{display:block;background:#4f46e5;color:#fff;text-decoration:none;text-align:center;padding:.85rem 1rem;border-radius:.5rem;font-weight:600;margin:1.25rem 0}
  a.btn:hover{background:#4338ca}
  ol{padding-left:1.25rem}
  code{background:rgba(127,127,127,.15);padding:.1rem .35rem;border-radius:.25rem;font-size:.92em}
  small{color:#666}
  @media (prefers-color-scheme:dark){.badge{background:#312e81;color:#c7d2fe}}
</style>
<h1>MamY — alpha</h1>
<p><span class="badge">v0.4.5 · voice FAB Notes + Actions · Toast Record feedback</span></p>
<p>Secrétaire vocale Android. 🎤 dicte tes notes/actions, 🔊 Mamy te les relit, agenda téléphone synchronisé, notifications 24h+1h avant chaque réunion.</p>

<a class="btn" href="/dl/MamY-v0.4.5-alpha.apk">Télécharger l'APK</a>

<h3>Installation (3 minutes)</h3>
<ol>
  <li>Ouvre l'APK téléchargé sur ton tel Android.</li>
  <li>Si demandé, autorise les <em>« sources inconnues »</em> pour le navigateur.</li>
  <li>Lance MamY → autorise micro + notifications → tap <strong>« Use built-in keyword »</strong> à l'étape 2.</li>
  <li>Skip Calendar + SMS si tu veux. Test « Jarvis test 1 2 3 » à l'étape WakeWord.</li>
</ol>

<h3>Tester</h3>
<p>Dis <strong>« Jarvis »</strong> → attends le bip → décris ta réunion / ton 1:1. Le rapport apparaît sous l'onglet Reports en moins de 2 sec.</p>

<p><small>Backend hébergé sur server perso → Groq Cloud (Llama 3.1 8B). Aucune clé API à entrer. Build signé v3, Android 9+.</small></p>
"""


@app.get("/", response_class=HTMLResponse)
async def install_page():
    return HTMLResponse(_INSTALL_PAGE)


@app.get("/dl/{filename}")
async def download_apk(filename: str):
    if "/" in filename or ".." in filename or "\\" in filename:
        raise HTTPException(status_code=400, detail="invalid filename")
    target = DIST_DIR / filename
    if not target.is_file():
        raise HTTPException(status_code=404, detail="file not found")
    return FileResponse(
        path=target,
        media_type="application/vnd.android.package-archive",
        filename=filename,
    )


@app.get("/health")
async def health():
    return {"status": "ok", "backend": "groq", "model": DEFAULT_MODEL}


CRASH_LOG = Path(os.environ.get("MAMY_CRASH_LOG", "/var/log/mamy-crashes.log"))


@app.post("/api/crash")
async def crash_report(request: Request):
    """Persist crash reports POSTed by the app's CrashReporter."""
    try:
        body = (await request.body()).decode("utf-8", errors="replace")
    except Exception as e:
        body = f"<failed to decode body: {e}>"
    ts = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
    client = request.client.host if request.client else "?"
    sep = "=" * 60
    entry = f"\n{sep}\n# {ts}  client={client}\n{sep}\n{body}\n"
    try:
        CRASH_LOG.parent.mkdir(parents=True, exist_ok=True)
        with CRASH_LOG.open("a", encoding="utf-8") as f:
            f.write(entry)
        log.warning("crash report received from %s, %d bytes", client, len(body))
    except Exception as e:
        log.error("could not persist crash report: %s", e)
    return {"received": True}


@app.get("/api/tags")
async def tags():
    return {
        "models": [
            {
                "name": DEFAULT_MODEL,
                "model": DEFAULT_MODEL,
                "size": 0,
                "modified_at": "2026-05-06T00:00:00Z",
                "details": {
                    "family": "llama",
                    "parameter_size": "8B",
                    "format": "groq-proxy",
                },
            }
        ]
    }


@app.post("/api/generate")
async def generate(req: GenerateRequest):
    messages = []
    if req.system:
        messages.append({"role": "system", "content": req.system})
    messages.append({"role": "user", "content": req.prompt})

    body = {
        "model": DEFAULT_MODEL,
        "messages": messages,
        "stream": False,
        "temperature": 0.3,
    }
    if req.format == "json":
        body["response_format"] = {"type": "json_object"}
    if req.options and isinstance(req.options.get("num_predict"), int):
        body["max_tokens"] = req.options["num_predict"]

    try:
        async with httpx.AsyncClient(timeout=60) as client:
            r = await client.post(
                GROQ_URL,
                headers={
                    "Authorization": f"Bearer {GROQ_API_KEY}",
                    "Content-Type": "application/json",
                },
                json=body,
            )
        if r.status_code != 200:
            log.warning("groq HTTP %s: %s", r.status_code, r.text[:200])
            return await fallback_ollama(req)
        data = r.json()
    except Exception as e:
        log.exception("groq call failed: %s", e)
        return await fallback_ollama(req)

    text = data["choices"][0]["message"]["content"]
    usage = data.get("usage", {})
    return {
        "model": data.get("model", DEFAULT_MODEL),
        "created_at": time.strftime(
            "%Y-%m-%dT%H:%M:%SZ", time.gmtime(data.get("created", time.time()))
        ),
        "response": text,
        "done": True,
        "done_reason": "stop",
        "prompt_eval_count": usage.get("prompt_tokens", 0),
        "eval_count": usage.get("completion_tokens", 0),
        "backend": "groq",
    }


async def fallback_ollama(req: GenerateRequest):
    log.info("falling back to local Ollama %s", OLLAMA_FALLBACK_MODEL)
    body = {
        "model": OLLAMA_FALLBACK_MODEL,
        "prompt": req.prompt,
        "stream": False,
    }
    if req.system:
        body["system"] = req.system
    if req.format:
        body["format"] = req.format
    if req.keep_alive:
        body["keep_alive"] = req.keep_alive
    try:
        async with httpx.AsyncClient(timeout=300) as client:
            r = await client.post(f"{OLLAMA_FALLBACK_URL}/api/generate", json=body)
        if r.status_code == 200:
            data = r.json()
            data["backend"] = "ollama-fallback"
            return data
    except Exception as e:
        log.exception("fallback failed: %s", e)
    raise HTTPException(status_code=502, detail="both groq and ollama fallback failed")


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="127.0.0.1", port=11435, log_level="info")
