/*
 * Tapsell configuration for the Padafand game.
 * Fill these values only after creating the matching placements in the Tapsell panel.
 * Do not put private API secrets in this browser-side file.
 */
window.PadafandAdsConfig = Object.freeze({
  provider: "tapsell",
  platform: "auto", // "web" or "android" after the delivery target is confirmed
  enabled: false,
  appId: "",
  zones: {
    banner: "",
    interstitial: "",
    rewarded: "",
    rewardedExtra: ""
  },
  rewards: {
    coins: 0,
    homingAmmo: 0
  },
  frequency: {
    interstitialCooldownSeconds: 90
  }
});
