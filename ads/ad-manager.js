/*
 * Padafand ad adapter contract.
 * The native Android WebView wrapper should expose window.PadafandTapsellBridge.
 * A web integration may expose window.PadafandTapsellWebAdapter only when an
 * official, supported Tapsell web SDK is available for the chosen product.
 * Both adapters implement Promise-returning showBanner/showInterstitial/showRewarded.
 */
(function (window, document) {
  "use strict";

  const config = window.PadafandAdsConfig || {};
  const bannerSlotId = "padafand-banner-slot";
  let lastInterstitialAt = 0;

  function zoneId(name) {
    return String((config.zones || {})[name] || "").trim();
  }

  function adapter() {
    return window.PadafandTapsellBridge || window.PadafandTapsellWebAdapter || null;
  }

  function isReady(name) {
    return config.enabled === true &&
      Boolean(String(config.appId || "").trim()) &&
      Boolean(zoneId(name)) &&
      Boolean(adapter());
  }

  async function call(method, zoneName, extra) {
    if (!isReady(zoneName)) {
      return { ok: false, reason: "ad_not_configured" };
    }
    const api = adapter();
    if (typeof api[method] !== "function") {
      return { ok: false, reason: "adapter_method_missing" };
    }
    try {
      const result = await api[method]({
        appId: String(config.appId).trim(),
        zoneId: zoneId(zoneName),
        ...((extra && typeof extra === "object") ? extra : {})
      });
      return { ok: true, result: result || null };
    } catch (error) {
      console.warn("Padafand ad request failed:", error);
      return { ok: false, reason: "ad_request_failed" };
    }
  }

  async function init() {
    const slot = document.getElementById(bannerSlotId);
    if (!slot || !isReady("banner")) {
      if (slot) slot.classList.add("hidden");
      return { ok: false, reason: "banner_not_configured" };
    }
    const result = await call("showBanner", "banner", { containerId: bannerSlotId });
    if (result.ok) {
      slot.classList.remove("hidden");
      slot.setAttribute("aria-hidden", "false");
    }
    return result;
  }

  async function showInterstitial(context) {
    const cooldown = Math.max(0, Number((config.frequency || {}).interstitialCooldownSeconds) || 0) * 1000;
    const now = Date.now();
    if (lastInterstitialAt && now - lastInterstitialAt < cooldown) {
      return { ok: false, reason: "cooldown" };
    }
    const result = await call("showInterstitial", "interstitial", { context: String(context || "") });
    if (result.ok) lastInterstitialAt = now;
    return result;
  }

  async function showRewarded(kind) {
    const zoneName = kind === "extra" ? "rewardedExtra" : "rewarded";
    const response = await call("showRewarded", zoneName, { rewardKind: String(kind || "default") });
    if (!response.ok) return { ok: false, completed: false, verified: false, reason: response.reason };
    const result = response.result || {};
    // Never grant in-game value unless the adapter confirms a completed, verified reward.
    return {
      ok: true,
      completed: result.completed === true,
      verified: result.verified === true,
      reason: result.reason || null
    };
  }

  window.PadafandAds = Object.freeze({
    init,
    showInterstitial,
    showRewarded,
    isReady,
    getConfig: () => ({
      provider: config.provider || "unset",
      platform: config.platform || "auto",
      enabled: config.enabled === true,
      placements: {
        banner: Boolean(zoneId("banner")),
        interstitial: Boolean(zoneId("interstitial")),
        rewarded: Boolean(zoneId("rewarded")),
        rewardedExtra: Boolean(zoneId("rewardedExtra"))
      },
      adapterAvailable: Boolean(adapter())
    })
  });
})(window, document);
