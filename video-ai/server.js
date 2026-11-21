import express from "express";
import crypto from "node:crypto";
const app=express(), PORT=process.env.PORT||3000;
const SPACE=process.env.HF_SPACE_URL||"https://lightricks-ltx-video-distilled.hf.space";
const PASSWORD=process.env.APP_PASSWORD||"";
const jobs=new Map();
app.use((req,res,next)=>{
 if(req.path==="/api/health") return next();
 if(!PASSWORD) return res.status(503).send("APP_PASSWORD is not configured.");
 const h=req.headers.authorization||"", supplied=h.startsWith("Basic ")?Buffer.from(h.slice(6),"base64").toString().split(":").slice(1).join(":"):"";
 const a=Buffer.from(supplied),b=Buffer.from(PASSWORD);
 if(a.length!==b.length||!crypto.timingSafeEqual(a,b)){res.setHeader("WWW-Authenticate",'Basic realm="Private Video AI"');return res.status(401).send("Password required");}
 next();
});
app.use(express.json({limit:"2mb"})); app.use(express.static("public"));
const clamp=(n,a,b)=>Math.min(b,Math.max(a,n));
function parseSSE(raw){for(const block of raw.split(/\r?\n\r?\n/).reverse()){const e=block.match(/^event:\s*(\S+)/m),d=block.match(/^data:\s*(.+)$/m);if(!e||!d)continue;if(e[1]==="error")throw Error("مدل خطا داد: "+d[1].slice(0,300));if(e[1]==="complete"){const p=JSON.parse(d[1]),v=p?.[0],url=v?.video?.url||v?.url||v?.video;if(typeof url!=="string")throw Error("آدرس ویدئو دریافت نشد");return {url,seed:p?.[1]};}}throw Error("پاسخ مدل کامل نشد؛ بعداً دوباره تلاش کن.");}
async function generate(job,input){try{
 job.status="processing";job.message="در صف موتور هوش مصنوعی...";
 const ratio=input.ratio||"9:16",dims=ratio==="16:9"?[512,896]:ratio==="1:1"?[704,704]:[896,512];
 const data=[input.prompt.trim(),(input.negativePrompt||"worst quality, blurry, distorted, watermark").trim(),"","",clamp(+input.height||dims[0],256,1280),clamp(+input.width||dims[1],256,1280),"text-to-video",clamp(+input.duration||5,0.3,8.5),9,clamp(+input.seed||42,0,4294967295),!!input.randomizeSeed,1,true];
 const headers={"Content-Type":"application/json"};if(process.env.HF_TOKEN)headers.Authorization="Bearer "+process.env.HF_TOKEN;
 const s=await fetch(SPACE+"/gradio_api/call/text_to_video",{method:"POST",headers,body:JSON.stringify({data}),signal:AbortSignal.timeout(60000)});
 if(!s.ok)throw Error("موتور فعلاً پاسخ نمی‌دهد (HTTP "+s.status+")");
 const {event_id}=await s.json();if(!event_id)throw Error("شناسه درخواست دریافت نشد");
 const r=await fetch(SPACE+"/gradio_api/call/text_to_video/"+event_id,{headers:process.env.HF_TOKEN?{Authorization:"Bearer "+process.env.HF_TOKEN}:{},signal:AbortSignal.timeout(300000)});
 if(!r.ok)throw Error("دریافت نتیجه ناموفق بود (HTTP "+r.status+")");
 const out=parseSSE(await r.text());job.remote=out.url;job.seed=out.seed;job.status="completed";job.message="ویدئو آماده است.";
 }catch(e){job.status="failed";job.message=e.message||"تولید ناموفق بود";}}
app.get("/api/health",(q,s)=>s.json({ok:true,engine:"LTX-Video"}));
app.post("/api/generate",(q,s)=>{const p=q.body?.prompt;if(typeof p!=="string"||!p.trim())return s.status(400).json({error:"پرامپت را وارد کن"});if(p.length>5000)return s.status(400).json({error:"حداکثر ۵۰۰۰ نویسه"});if([...jobs.values()].filter(j=>["queued","processing"].includes(j.status)).length>=2)return s.status(429).json({error:"دو درخواست هم‌زمان در حال اجراست"});const id=crypto.randomUUID(),job={id,status:"queued",message:"در حال آماده‌سازی",created:Date.now()};jobs.set(id,job);generate(job,q.body);s.status(202).json({id});});
app.get("/api/jobs/:id",(q,s)=>{const j=jobs.get(q.params.id);if(!j)return s.status(404).json({error:"درخواست پیدا نشد"});s.json({id:j.id,status:j.status,message:j.message,videoUrl:j.status==="completed"?"/api/download/"+j.id:null,seed:j.seed});});
app.get("/api/download/:id",async(q,s)=>{const j=jobs.get(q.params.id);if(!j?.remote||j.status!=="completed")return s.sendStatus(404);try{const r=await fetch(j.remote);if(!r.ok||!r.body)return s.sendStatus(502);s.setHeader("Content-Type",r.headers.get("content-type")||"video/mp4");s.setHeader("Content-Disposition",'attachment; filename="video-ai.mp4"');const {Readable}=await import("node:stream");Readable.fromWeb(r.body).pipe(s);}catch{s.sendStatus(502);}});
app.get("*",(q,s)=>s.sendFile(process.cwd()+"/public/index.html"));
setInterval(()=>{for(const [id,j] of jobs)if(Date.now()-j.created>21600000)jobs.delete(id)},1800000).unref();
app.listen(PORT,()=>console.log("Private Video AI listening on "+PORT));