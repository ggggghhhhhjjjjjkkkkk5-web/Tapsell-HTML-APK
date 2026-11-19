# Instagram Downloader — Render API

This repository Android app calls a Cobalt-compatible API. The Render service uses the official upstream Cobalt container.

## Render setup

1. Create a **Web Service** from this repository, branch `main`, runtime **Docker**.
2. Leave **Root Directory** empty (repository root), and set **Dockerfile Path** to `Dockerfile`.
3. Choose Free only for initial testing. Render free services can sleep after inactivity and are not dependable always-on production hosting.
4. Create the service. After Render assigns its public URL, open **Environment** and add:
   - Key: `API_URL`
   - Value: the exact service URL ending in a slash, e.g. `https://YOUR-SERVICE.onrender.com/`
5. Save and redeploy. The container maps Render `PORT` to Cobalt `API_PORT`.

## App configuration

In the Android app, open **تنظیم سرویس پردازش لینک** and enter the same base URL without an extra path, e.g. `https://YOUR-SERVICE.onrender.com`. Leave auth blank only while testing an instance configured without authentication.

## Security and licensing

- Do not distribute an unrestricted public API as a commercial service. Protect a public instance against abuse before production. Cobalt supports API-key and Turnstile authentication; configure one before production and keep secrets in Render environment variables/secret files, never in Git.
- The upstream Cobalt API is licensed under AGPL-3.0. If you modify or distribute it, comply with that license and provide corresponding source and notices.
- Only download public content you are authorized to save. This does not bypass private-account access.
- Render free services may sleep and restart; processing may be slow or fail under resource limits. Verify deployment and test a public post before calling the APK production-ready.

Upstream docs: https://github.com/imputnet/cobalt/blob/main/docs/run-an-instance.md and https://github.com/imputnet/cobalt/blob/main/docs/api.md