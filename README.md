# Persian Instagram Downloader

A public, mobile-first Persian frontend for downloading publicly accessible Instagram media through the Cobalt API.

## Deployment
- Static frontend: Render static site, publishing the repository root.
- API: Cobalt API Docker service at `https://tapsell-html-apk.onrender.com/`.
- The frontend API endpoint is fixed in `index.html`; visitors cannot edit connection settings.
- No user/admin dashboard or public settings page is included.

## Privacy and file handling
- The website does not create an online gallery or persist downloaded media.
- The frontend does not upload downloaded files to a storage bucket or database.
- Media is fetched from the Cobalt API/source and saved by the visitor's browser to the visitor's device.
- The API container has no Render persistent disk configured; any temporary processing is ephemeral and is not a user-accessible gallery.
- The site does not request Instagram passwords, cookies, or account credentials.
- Download history and media files are not stored by this frontend.

## Progress and saving
- The UI shows a real transfer percentage only when the browser can read the media response and its content length.
- If the media host blocks browser CORS or does not expose a content length, the site offers a direct download link and cannot show a truthful percentage for that transfer.
- On Android, the browser normally saves the file to Downloads. Whether it appears in the phone's Gallery app depends on the device and Gallery app; a website cannot silently force a save into a native gallery.

## Limitations
- Only public, accessible Instagram posts/reels are supported, subject to upstream Cobalt and Instagram availability.
- Private, age-restricted, login-gated, removed, or otherwise restricted content may fail.
- Use the tool only for content you are authorized to download and respect copyright and platform terms.
- This independent project is not affiliated with or endorsed by Instagram or Meta.

## Upstream
Cobalt API: https://github.com/imputnet/cobalt
