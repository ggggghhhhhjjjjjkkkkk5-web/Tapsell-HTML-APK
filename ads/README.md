# Padafand — Ad integration scaffold

This directory is the configuration and adapter boundary for adding Tapsell placements without changing the game engine.

## Current state

- The game loads `ads/ad-config.js` and `ads/ad-manager.js`.
- Ads are **disabled by default**. Empty IDs do not trigger requests or show a blank banner.
- The banner container is present but stays hidden until configuration and an adapter are available.
- Rewarded-ad results are considered eligible only when the adapter returns both `completed: true` and `verified: true`. The game must not grant coins/ammunition before that condition is met.
- No SDK, App ID, Zone ID, API secret, or user credential has been fabricated or committed.

## Required adapter contract

The Android WebView wrapper should expose `window.PadafandTapsellBridge`; a web integration should expose `window.PadafandTapsellWebAdapter` only if Tapsell provides a supported web SDK for the selected product. Each adapter may implement:

- `showBanner({ appId, zoneId, containerId }) -> Promise<boolean | object>`
- `showInterstitial({ appId, zoneId, context }) -> Promise<boolean | object>`
- `showRewarded({ appId, zoneId, rewardKind }) -> Promise<{ completed: boolean, verified: boolean }>`

For rewarded ads, `verified` must be set only after the SDK's genuine reward-completion callback (preferably backed by server-side verification where supported). A click, impression, close event, or arbitrary JavaScript message is not sufficient.

## Configuration steps after receiving panel details

1. Confirm whether the release target is HTML/web, Android APK, or both.
2. Create the app in the Tapsell panel and provide its App ID.
3. Create the needed banner, interstitial, and rewarded placements and provide their Zone IDs.
4. Confirm the desired reward amounts and interstitial frequency.
5. Implement the matching SDK adapter in the web host or Android wrapper. Android SDK code does not run directly inside a plain HTML file; a WebView bridge/native wrapper is required.
6. Test on a real device with test placements first, then production placements.

## Configuration file

Edit `ad-config.js` only with non-secret app/placement identifiers and approved reward/frequency values. Never commit private API keys or signing secrets to this repository.
