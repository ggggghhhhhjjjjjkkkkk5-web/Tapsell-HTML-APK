import http from "node:http";
import crypto from "node:crypto";

const TOKEN = process.env.BALE_BOT_TOKEN || "";
const CHANNEL = process.env.BALE_CHANNEL_ID || "@downloader_instagram";
const CHANNEL_URL = process.env.BALE_CHANNEL_URL || "https://ble.ir/downloader_instagram";
const BOT_USERNAME = process.env.BALE_BOT_USERNAME || "downloader_instagram_bot";
const BASE_URL = (process.env.PUBLIC_BASE_URL || "").replace(/\/$/, "");
const WEBHOOK_SECRET = process.env.WEBHOOK_PATH_SECRET || "";

if (!TOKEN || !BASE_URL || !WEBHOOK_SECRET) {
  console.error("Required env: BALE_BOT_TOKEN, PUBLIC_BASE_URL, WEBHOOK_PATH_SECRET");
  process.exit(1);
}
const API = `https://tapi.bale.ai/bot${TOKEN}/`;

async function bale(method, payload = {}) {
  const response = await fetch(API + method, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(payload),
    signal: AbortSignal.timeout(15000)
  });
  const data = await response.json();
  if (!response.ok || !data.ok) throw new Error(data.description || `Bale API ${method} failed`);
  return data.result;
}
async function isMember(userId) {
  const member = await bale("getChatMember", { chat_id: CHANNEL, user_id: userId });
  return ["creator", "administrator", "member"].includes(member?.status) ||
    (member?.status === "restricted" && member?.is_member === true);
}
function keyboard(nonce, joined = false) {
  if (joined) return { inline_keyboard: [[
    { text: "بازگشت به برنامه و ادامه دانلود", url: `mediafetch://authorized?nonce=${encodeURIComponent(nonce)}` }
  ]] };
  return { inline_keyboard: [
    [{ text: "عضویت در کانال", url: CHANNEL_URL }],
    [{ text: "بررسی عضویت و ادامه", callback_data: `check:${nonce}`, style: "success" }]
  ] };
}
async function sendGate(chatId, nonce, userId) {
  let joined = false;
  try { joined = await isMember(userId); }
  catch (error) { console.error("Membership check failed:", error.message); }
  if (joined) {
    await bale("sendMessage", {
      chat_id: chatId,
      text: "عضویت شما تأیید شد. برای بازگشت به برنامه و ادامه دانلود، دکمه زیر را بزنید.",
      reply_markup: keyboard(nonce, true)
    });
  } else {
    await bale("sendMessage", {
      chat_id: chatId,
      text: "برای استفاده از دانلودر باید عضو کانال باشید. پس از عضویت، دکمه «بررسی عضویت و ادامه» را بزنید. عضویت پیش از هر دانلود دوباره بررسی می‌شود.",
      reply_markup: keyboard(nonce, false)
    });
  }
}
async function handleUpdate(update) {
  const msg = update.message;
  if (msg?.chat?.type === "private" && msg.from?.id && typeof msg.text === "string") {
    const match = msg.text.match(/^\/start(?:\s+([A-Za-z0-9_-]{8,64}))?/);
    if (match) {
      const nonce = match[1];
      if (!nonce) {
        await bale("sendMessage", {
          chat_id: msg.chat.id,
          text: "این ربات برای تأیید عضویت دانلودر است. برای شروع دانلود، از داخل برنامه روی دکمه دانلود بزنید."
        });
      } else {
        await sendGate(msg.chat.id, nonce, msg.from.id);
      }
    }
    return;
  }
  const cb = update.callback_query;
  if (cb?.id && cb.from?.id && typeof cb.data === "string") {
    await bale("answerCallbackQuery", { callback_query_id: cb.id, text: "در حال بررسی عضویت…" });
    const match = cb.data.match(/^check:([A-Za-z0-9_-]{8,64})$/);
    if (match && cb.message?.chat?.id) await sendGate(cb.message.chat.id, match[1], cb.from.id);
  }
}
const server = http.createServer(async (req, res) => {
  if (req.method === "GET" && req.url === "/health") {
    res.writeHead(200, { "content-type": "application/json" });
    return res.end(JSON.stringify({ ok: true, service: "bale-membership-gate" }));
  }
  if (req.method === "POST" && req.url === `/webhook/${WEBHOOK_SECRET}`) {
    let body = "";
    req.setEncoding("utf8");
    for await (const chunk of req) {
      body += chunk;
      if (body.length > 1024 * 1024) { res.writeHead(413); return res.end(); }
    }
    res.writeHead(200, { "content-type": "application/json" });
    res.end('{"ok":true}');
    try { await handleUpdate(JSON.parse(body)); }
    catch (error) { console.error("Update handling failed:", error); }
    return;
  }
  res.writeHead(404); res.end("Not found");
});
const port = Number(process.env.PORT || 10000);
server.listen(port, async () => {
  console.log(`Bale membership bot listening on ${port}`);
  try {
    const hook = await bale("setWebhook", { url: `${BASE_URL}/webhook/${WEBHOOK_SECRET}` });
    console.log("Webhook configured:", hook);
  } catch (error) {
    console.error("Webhook setup failed:", error.message);
  }
});
