# Bale membership gate

Separate Render Web Service. Set **Root Directory** to `bale-bot`, runtime to Docker, and add:

- `BALE_BOT_TOKEN`: token from Bale BotFather (Render secret; never commit it)
- `BALE_CHANNEL_ID`: `@downloader_instagram` (adjust if the channel's actual username differs)
- `BALE_CHANNEL_URL`: `https://ble.ir/downloader_instagram`
- `BALE_BOT_USERNAME`: `downloader_instagram_bot`
- `PUBLIC_BASE_URL`: the public HTTPS URL Render assigns to this service
- `WEBHOOK_PATH_SECRET`: a long random path secret

The service configures its Bale webhook on startup. The bot must be an administrator of the channel so `getChatMember` can verify membership. It does not persist membership as a permanent grant: every download attempt gets a new nonce and a fresh membership check.

The app opens `https://ble.ir/downloader_instagram_bot?start=<nonce>`. After a successful check, the bot returns a one-time nonce link using `mediafetch://authorized?nonce=<nonce>`; the Android app accepts it only when it matches the currently pending request.

Health check: `/health`.
