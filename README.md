# Instagram Downloader Website

A Persian, mobile-friendly frontend for downloading publicly accessible Instagram media through a self-hosted Cobalt API.

## Files
- `index.html`: standalone static website.
- `Dockerfile`: existing Cobalt API container definition in this repository.

## Run the website
Open `index.html` locally or publish the repository root with GitHub Pages. In the website, open **تنظیمات اتصال** and enter the HTTPS base URL of your deployed Cobalt API.

## API deployment
The repository Dockerfile runs the Cobalt API. Deploy it as a Docker web service (for example, on Render). Configure the service's required environment variables according to the current Cobalt documentation. Set the website's API URL to the deployed service URL, including the trailing slash.

The frontend sends a POST request to the API root with:
```json
{"url":"https://www.instagram.com/reel/…/","videoQuality":"max","filenameStyle":"basic"}
```

## Important
- A working API deployment is required; GitHub Pages only hosts the static website.
- Instagram may change access rules, rate-limit requests, or block some posts. Private, age-restricted, login-gated, or otherwise inaccessible content may fail.
- The API must permit browser CORS requests from the website origin.
- Do not ask users for Instagram passwords or cookies. Respect copyright, privacy, and platform terms.
- This project does not guarantee availability or support for every Instagram URL.
