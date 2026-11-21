import express from "express";
import crypto from "node:crypto";
import { Readable } from "node:stream";

const app = express();
const PORT = process.env.PORT || 3000;
const SPACE = (process.env.HF_SPACE_URL || "https://lightricks-ltx-video-distilled.hf.space").replace(/\/$/, "");
const PASSWORD = process.env.APP_PASSWORD || "";
const jobs = new Map();

app.use((req, res, next) => {
  if (req.path === "/api/health") return next();
  if (!PASSWORD) return res.status(503).send("APP_PASSWORD is not configured.");
  const header = req.headers.authorization || "";
  const supplied = header.startsWith("Basic ")
    ? Buffer.from(header.slice(6), "base64").toString().split(":").slice(1).join(":")
    : "";
  const a = Buffer.from(supplied);
  const b = Buffer.from(PASSWORD);
  if (a.length !== b.length || !crypto.timingSafeEqual(a, b)) {
    res.setHeader("WWW-Authenticate", 'Basic realm="Private Video AI"');
    return res.status(401).send("Password required");
  }
  next();
});

app.use(express.json({ limit: "2mb" }));
app.use(express.static("public"));

const clamp = (n, min, max) => Math.min(max, Math.max(min, n));

function extractVideoUrl(value) {
  if (!value) return null;
  if (typeof value === "string" && /^https?:\/\//i.test(value)) return value;
  if (typeof value !== "object") return null;
  if (typeof value.url === "string" && /^https?:\/\//i.test(value.url)) return value.url;
  if (value.video) return extractVideoUrl(value.video);
  if (value.path && typeof value.path === "string" && /^https?:\/\//i.test(value.path)) return value.path;
  return null;
}

function parseResult(raw) {
  const blocks = raw.split(/\r?\n\r?\n/);
  for (const block of blocks.reverse()) {
    const event = block.match(/^event:\s*(\S+)/m)?.[1];
    const data = block.match(/^data:\s*(.+)$/m)?.[1];
    if (!event || !data) continue;
    if (event === "error") { console.error("Hugging Face returned error event:", JSON.stringify({ data, block: block.slice(0, 2000) })); throw new Error(data && data !== "null" ? "خطای موتور: " + data.slice(0, 500) : "موتور ویدئوساز خطای بدون جزئیات برگرداند؛ احتمالاً پردازش GPU یا صف سرویس متوقف شده است. مدت ۳ ثانیه و تلاش مجدد را امتحان کن."); }
    if (event === "complete") {
      const payload = JSON.parse(data);
      const videoUrl = extractVideoUrl(payload?.[0]);
      if (!videoUrl) throw new Error("موتور پاسخ داده، اما لینک فایل ویدئو در پاسخ پیدا نشد.");
      return { videoUrl, seed: payload?.[1] };
    }
  }
  console.error("Unexpected Gradio response:", raw.slice(-3000)); throw new Error("پاسخ موتور ناقص یا نامعتبر بود؛ جزئیات پاسخ در گزارش سرور ثبت شد.");
}

async function generate(job, input) {
  try {
    job.status = "processing";
    job.message = "در حال اتصال به موتور و ورود به صف GPU...";
    const ratio = input.ratio || "9:16";
    const [height, width] = ratio === "16:9" ? [512, 704] : ratio === "1:1" ? [512, 512] : [704, 512];
    const data = [
      input.prompt.trim(),
      (input.negativePrompt || "worst quality, inconsistent motion, blurry, jittery, distorted").trim(),
      null,
      null,
      height,
      width,
      "text-to-video",
      clamp(Number(input.duration) || 5, 0.3, 8.5),
      9,
      clamp(Number(input.seed) || 42, 0, 4294967295),
      true,
      1.0,
      false
    ];

    const headers = { "Content-Type": "application/json" };
    if (process.env.HF_TOKEN) headers.Authorization = "Bearer " + process.env.HF_TOKEN;

    job.message = "در حال ارسال درخواست به موتور ویدئوساز...";
    const startResponse = await fetch(SPACE + "/gradio_api/call/text_to_video", {
      method: "POST",
      headers,
      body: JSON.stringify({ data }),
      signal: AbortSignal.timeout(90000)
    });
    if (!startResponse.ok) {
      const detail = (await startResponse.text()).slice(0, 400);
      throw new Error("ارسال به موتور ناموفق بود (HTTP " + startResponse.status + "): " + detail);
    }
    const startData = await startResponse.json();
    if (!startData.event_id) throw new Error("موتور شناسه پیگیری درخواست را برنگرداند.");

    job.message = "درخواست ثبت شد؛ منتظر نوبت و پردازش GPU هستیم...";
    const resultResponse = await fetch(SPACE + "/gradio_api/call/text_to_video/" + encodeURIComponent(startData.event_id), {
      headers: process.env.HF_TOKEN ? { Authorization: "Bearer " + process.env.HF_TOKEN } : {},
      signal: AbortSignal.timeout(900000)
    });
    if (!resultResponse.ok) {
      const detail = (await resultResponse.text()).slice(0, 400);
      throw new Error("گرفتن نتیجه از موتور ناموفق بود (HTTP " + resultResponse.status + "): " + detail);
    }

    const result = parseResult(await resultResponse.text());
    job.remote = result.videoUrl;
    job.seed = result.seed;
    job.status = "completed";
    job.message = "ویدئو با موفقیت آماده شد.";
  } catch (error) {
    job.status = "failed";
    job.message = error.name === "TimeoutError"
      ? "زمان انتظار موتور تمام شد. صف GPU ممکن است شلوغ باشد؛ دوباره تلاش کن."
      : (error.message || "تولید ویدئو ناموفق بود");
    console.error("Video generation failed:", error);
  }
}

app.get("/api/health", async (req, res) => {
  const result = { app: "ok", engine: "unknown", engineUrl: SPACE };
  try {
    const response = await fetch(SPACE + "/", { signal: AbortSignal.timeout(10000) });
    result.engine = response.ok ? "reachable" : "responding_with_error";
    result.engineHttpStatus = response.status;
  } catch (error) {
    result.engine = "unreachable";
    result.engineError = error.message;
  }
  res.status(200).json(result);
});

app.post("/api/generate", (req, res) => {
  const prompt = req.body?.prompt;
  if (typeof prompt !== "string" || !prompt.trim()) return res.status(400).json({ error: "پرامپت را وارد کن." });
  if (prompt.length > 5000) return res.status(400).json({ error: "حداکثر طول پرامپت ۵۰۰۰ نویسه است." });
  if ([...jobs.values()].filter(j => ["queued", "processing"].includes(j.status)).length >= 2) {
    return res.status(429).json({ error: "دو درخواست هم‌زمان در حال اجراست؛ کمی صبر کن." });
  }
  const id = crypto.randomUUID();
  const job = { id, status: "queued", message: "درخواست در صف قرار گرفت.", created: Date.now() };
  jobs.set(id, job);
  generate(job, req.body);
  res.status(202).json({ id });
});

app.get("/api/jobs/:id", (req, res) => {
  const job = jobs.get(req.params.id);
  if (!job) return res.status(404).json({ error: "درخواست پیدا نشد." });
  res.json({
    id: job.id,
    status: job.status,
    message: job.message,
    videoUrl: job.status === "completed" ? "/api/download/" + job.id : null,
    seed: job.seed
  });
});

app.get("/api/download/:id", async (req, res) => {
  const job = jobs.get(req.params.id);
  if (!job?.remote || job.status !== "completed") return res.sendStatus(404);
  try {
    const response = await fetch(job.remote, { signal: AbortSignal.timeout(120000) });
    if (!response.ok || !response.body) return res.status(502).send("دریافت فایل ویدئو از موتور ناموفق بود.");
    res.setHeader("Content-Type", response.headers.get("content-type") || "video/mp4");
    res.setHeader("Content-Disposition", 'attachment; filename="video-ai.mp4"');
    Readable.fromWeb(response.body).pipe(res);
  } catch (error) {
    res.status(502).send("خطا در دریافت فایل: " + error.message);
  }
});

app.get("*", (req, res) => res.sendFile(process.cwd() + "/public/index.html"));

setInterval(() => {
  for (const [id, job] of jobs) if (Date.now() - job.created > 21600000) jobs.delete(id);
}, 1800000).unref();

app.listen(PORT, () => console.log("Private Video AI listening on port " + PORT));
