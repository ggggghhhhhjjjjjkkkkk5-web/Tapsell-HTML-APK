# Bale membership gate

Create a separate Render Web Service from this repository. Set **Root Directory** to `bale-bot`, runtime to Docker, and deploy the service.

Required environment variable:
- `BALE_BOT_TOKEN`: token from Bale BotFather (Render secret; never commit it)

Optional environment variables:
- `BALE_CHANNEL_ID`: defaults to `@downloader_instagram` (adjust if the channel's actual username differs)
- `BALE_CHANNEL_URL`: defaults to `https://ble.ir/downloader_instagram`
- `BALE_BOT_USERNAME`: defaults to `downloader_instagram_bot`
- `PUBLIC_BASE_URL`: normally unnecessary; Render's `RENDER_EXTERNAL_URL` is detected automatically
- `WEBHOOK_PATH_SECRET`: optional; a random secret is generated on startup if omitted

The service configures its Bale webhook on startup. The bot must be an administrator of the channel so `getChatMember` can verify membership. It does not persist membership as a permanent grant: every download attempt gets a new nonce and a fresh membership check.

The app opens `https://ble.ir/downloader_instagram_bot?start=<nonce>`. After a successful check, the bot returns a nonce-bound link using `mediafetch://authorized?nonce=<nonce>`; the Android app accepts it only when it matches the currently pending request.

Health check: `/health`.
