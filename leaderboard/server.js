import express from "express";
import cors from "cors";
import helmet from "helmet";
import rateLimit from "express-rate-limit";
import bcrypt from "bcryptjs";
import jwt from "jsonwebtoken";
import pg from "pg";

const app=express();
const PORT=process.env.PORT||10000;
const JWT_SECRET=process.env.JWT_SECRET;
const pool=process.env.DATABASE_URL?new pg.Pool({
 connectionString:process.env.DATABASE_URL,
 ssl:process.env.DATABASE_URL.includes("render.com")?{rejectUnauthorized:false}:undefined
}):null;

app.use(helmet());
app.use(cors({origin:true,methods:["GET","POST"],allowedHeaders:["Content-Type","Authorization"]}));
app.use(express.json({limit:"20kb"}));
app.use("/auth",rateLimit({windowMs:15*60*1000,limit:30,standardHeaders:true,legacyHeaders:false}));
app.use("/scores",rateLimit({windowMs:60*1000,limit:20,standardHeaders:true,legacyHeaders:false}));

async function ready(){
 if(!pool||!JWT_SECRET) throw new Error("Service configuration incomplete");
 await pool.query(`
 CREATE TABLE IF NOT EXISTS players(
  id BIGSERIAL PRIMARY KEY,
  username VARCHAR(20) UNIQUE NOT NULL,
  password_hash TEXT NOT NULL,
  best_score INTEGER NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
 );
 CREATE INDEX IF NOT EXISTS players_best_score_idx ON players(best_score DESC,created_at ASC);
 `);
}
function auth(req,res,next){
 try{
  const token=(req.headers.authorization||"").replace(/^Bearer\s+/i,"");
  const data=jwt.verify(token,JWT_SECRET);
  req.playerId=data.sub;
  next();
 }catch{res.status(401).json({error:"ابتدا وارد حساب کاربری شوید."});}
}
app.get("/health",async(req,res)=>{
 try{await ready();res.json({ok:true,service:"padafand-leaderboard"});}
 catch{res.status(503).json({ok:false,configured:false});}
});
app.post("/auth/register",async(req,res)=>{
 try{
  await ready();
  const username=String(req.body.username||"").trim();
  const password=String(req.body.password||"");
  if(!/^[\p{L}\p{N}_]{3,20}$/u.test(username)) return res.status(400).json({error:"نام کاربری باید ۳ تا ۲۰ حرف، عدد یا زیرخط باشد."});
  if(password.length<8||password.length>72) return res.status(400).json({error:"رمز عبور باید حداقل ۸ و حداکثر ۷۲ کاراکتر باشد."});
  const hash=await bcrypt.hash(password,12);
  const result=await pool.query("INSERT INTO players(username,password_hash) VALUES($1,$2) RETURNING id,username,best_score",[username,hash]);
  const p=result.rows[0],token=jwt.sign({sub:String(p.id)},JWT_SECRET,{expiresIn:"30d"});
  res.status(201).json({token,player:{id:p.id,username:p.username,bestScore:p.best_score}});
 }catch(e){
  if(e.code==="23505") return res.status(409).json({error:"این نام کاربری قبلاً ثبت شده است."});
  console.error(e);res.status(503).json({error:"سرویس موقتاً در دسترس نیست."});
 }
});
app.post("/auth/login",async(req,res)=>{
 try{
  await ready();
  const username=String(req.body.username||"").trim();
  const password=String(req.body.password||"");
  const r=await pool.query("SELECT id,username,password_hash,best_score FROM players WHERE username=$1",[username]);
  if(!r.rowCount||!await bcrypt.compare(password,r.rows[0].password_hash)) return res.status(401).json({error:"نام کاربری یا رمز عبور اشتباه است."});
  const p=r.rows[0],token=jwt.sign({sub:String(p.id)},JWT_SECRET,{expiresIn:"30d"});
  res.json({token,player:{id:p.id,username:p.username,bestScore:p.best_score}});
 }catch(e){console.error(e);res.status(503).json({error:"سرویس موقتاً در دسترس نیست."});}
});
app.get("/auth/me",auth,async(req,res)=>{
 try{await ready();const r=await pool.query("SELECT id,username,best_score FROM players WHERE id=$1",[req.playerId]);if(!r.rowCount)return res.status(404).json({error:"حساب پیدا نشد."});const p=r.rows[0];res.json({player:{id:p.id,username:p.username,bestScore:p.best_score}});}
 catch(e){res.status(503).json({error:"سرویس موقتاً در دسترس نیست."});}
});
app.post("/scores",auth,async(req,res)=>{
 try{
  await ready();
  const score=Number(req.body.score);
  if(!Number.isSafeInteger(score)||score<0||score>100000000) return res.status(400).json({error:"امتیاز نامعتبر است."});
  const r=await pool.query("UPDATE players SET best_score=GREATEST(best_score,$1) WHERE id=$2 RETURNING best_score",[score,req.playerId]);
  if(!r.rowCount)return res.status(404).json({error:"حساب پیدا نشد."});
  res.json({bestScore:r.rows[0].best_score});
 }catch(e){res.status(503).json({error:"سرویس موقتاً در دسترس نیست."});}
});
app.get("/leaderboard",async(req,res)=>{
 try{
  await ready();
  const limit=Math.min(100,Math.max(1,Number(req.query.limit)||100));
  const r=await pool.query("SELECT username,best_score AS score FROM players ORDER BY best_score DESC,created_at ASC LIMIT $1",[limit]);
  res.json({players:r.rows});
 }catch(e){res.status(503).json({error:"جدول رده‌بندی هنوز آماده نیست."});}
});
app.get("/rank",auth,async(req,res)=>{
 try{
  await ready();
  const r=await pool.query(`SELECT p.username,p.best_score AS score,
   (SELECT COUNT(*)+1 FROM players q WHERE q.best_score>p.best_score) AS rank
   FROM players p WHERE p.id=$1`,[req.playerId]);
  if(!r.rowCount)return res.status(404).json({error:"حساب پیدا نشد."});
  res.json({player:r.rows[0]});
 }catch(e){res.status(503).json({error:"رتبه فعلاً در دسترس نیست."});}
});
app.listen(PORT,()=>console.log("Leaderboard API listening on",PORT));
