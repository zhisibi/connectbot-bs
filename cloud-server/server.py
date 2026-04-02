"""
SbSSH Cloud Sync Server
Python FastAPI + SQLite + JWT
"""
import os
import json
import sqlite3
import time
import hashlib
import secrets
from datetime import datetime, timedelta
from typing import Optional

import uvicorn
from fastapi import FastAPI, HTTPException, Depends, Header
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
import jwt

# --- Config ---
SECRET_KEY = os.environ.get("SBSH_CLOUD_SECRET", secrets.token_hex(32))
DB_PATH = os.environ.get("SBSH_CLOUD_DB", "sbssh_cloud.db")
PORT = int(os.environ.get("SBSH_CLOUD_PORT", "9800"))

app = FastAPI(title="SbSSH Cloud Sync", version="1.0.0")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# --- Models ---
class RegisterRequest(BaseModel):
    username: str
    password: str
    encryptedSalt: str  # base64-encoded encrypted salt

class LoginRequest(BaseModel):
    username: str
    password: str

class SyncUploadRequest(BaseModel):
    encryptedData: str  # base64-encoded encrypted backup data
    deviceId: Optional[str] = None

class TokenResponse(BaseModel):
    token: str
    userId: int
    username: str

class SyncResponse(BaseModel):
    encryptedData: Optional[str] = None
    updatedAt: Optional[str] = None

# --- Database ---
def get_db():
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn

def init_db():
    conn = get_db()
    conn.executescript("""
        CREATE TABLE IF NOT EXISTS users (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            username TEXT UNIQUE NOT NULL,
            password_hash TEXT NOT NULL,
            encrypted_salt TEXT NOT NULL,
            created_at TEXT DEFAULT (datetime('now'))
        );
        CREATE TABLE IF NOT EXISTS sync_data (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id INTEGER NOT NULL,
            encrypted_data TEXT NOT NULL,
            device_id TEXT,
            updated_at TEXT DEFAULT (datetime('now')),
            FOREIGN KEY (user_id) REFERENCES users(id)
        );
        CREATE INDEX IF NOT EXISTS idx_sync_user ON sync_data(user_id);
    """)
    conn.commit()
    conn.close()

# --- Auth helpers ---
def hash_password(password: str, salt: str = None) -> str:
    if salt is None:
        salt = secrets.token_hex(16)
    h = hashlib.pbkdf2_hmac("sha256", password.encode(), salt.encode(), 100000)
    return f"{salt}${h.hex()}"

def verify_password(password: str, stored: str) -> bool:
    salt, expected = stored.split("$", 1)
    h = hashlib.pbkdf2_hmac("sha256", password.encode(), salt.encode(), 100000)
    return h.hex() == expected

def create_token(user_id: int, username: str) -> str:
    payload = {
        "sub": str(user_id),
        "username": username,
        "exp": datetime.utcnow() + timedelta(days=30),
        "iat": datetime.utcnow(),
    }
    return jwt.encode(payload, SECRET_KEY, algorithm="HS256")

def verify_token(authorization: str = Header(None)) -> dict:
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="Missing or invalid Authorization header")
    token = authorization.split(" ", 1)[1]
    try:
        payload = jwt.decode(token, SECRET_KEY, algorithms=["HS256"])
        return payload
    except jwt.ExpiredSignatureError:
        raise HTTPException(status_code=401, detail="Token expired")
    except jwt.InvalidTokenError:
        raise HTTPException(status_code=401, detail="Invalid token")

# --- Routes ---
@app.on_event("startup")
def startup():
    init_db()
    print(f"SbSSH Cloud Server started on port {PORT}")
    print(f"Database: {DB_PATH}")

@app.get("/health")
def health():
    return {"status": "ok", "time": datetime.utcnow().isoformat()}

@app.post("/api/v1/register", response_model=TokenResponse)
def register(req: RegisterRequest):
    if len(req.username) < 3:
        raise HTTPException(400, "Username too short (min 3 chars)")
    if len(req.password) < 6:
        raise HTTPException(400, "Password too short (min 6 chars)")

    db = get_db()
    try:
        existing = db.execute("SELECT id FROM users WHERE username = ?", (req.username,)).fetchone()
        if existing:
            raise HTTPException(409, "Username already exists")

        pw_hash = hash_password(req.password)
        cur = db.execute(
            "INSERT INTO users (username, password_hash, encrypted_salt) VALUES (?, ?, ?)",
            (req.username, pw_hash, req.encryptedSalt)
        )
        db.commit()
        user_id = cur.lastrowid
        token = create_token(user_id, req.username)
        return TokenResponse(token=token, userId=user_id, username=req.username)
    finally:
        db.close()

@app.post("/api/v1/login", response_model=TokenResponse)
def login(req: LoginRequest):
    db = get_db()
    try:
        user = db.execute("SELECT * FROM users WHERE username = ?", (req.username,)).fetchone()
        if not user or not verify_password(req.password, user["password_hash"]):
            raise HTTPException(401, "Invalid username or password")
        token = create_token(user["id"], user["username"])
        return TokenResponse(token=token, userId=user["id"], username=user["username"])
    finally:
        db.close()

@app.get("/api/v1/sync/salt")
def get_salt(username: str, token: dict = Depends(verify_token)):
    db = get_db()
    try:
        user = db.execute("SELECT encrypted_salt FROM users WHERE username = ?", (username,)).fetchone()
        if not user:
            raise HTTPException(404, "User not found")
        return {"encryptedSalt": user["encrypted_salt"]}
    finally:
        db.close()

@app.post("/api/v1/sync/upload")
def upload(req: SyncUploadRequest, token: dict = Depends(verify_token)):
    user_id = int(token["sub"])
    db = get_db()
    try:
        existing = db.execute("SELECT id FROM sync_data WHERE user_id = ?", (user_id,)).fetchone()
        if existing:
            db.execute(
                "UPDATE sync_data SET encrypted_data = ?, device_id = ?, updated_at = datetime('now') WHERE user_id = ?",
                (req.encryptedData, req.deviceId, user_id)
            )
        else:
            db.execute(
                "INSERT INTO sync_data (user_id, encrypted_data, device_id) VALUES (?, ?, ?)",
                (user_id, req.encryptedData, req.deviceId)
            )
        db.commit()
        return {"status": "ok", "message": "Data uploaded successfully"}
    finally:
        db.close()

@app.get("/api/v1/sync/download", response_model=SyncResponse)
def download(token: dict = Depends(verify_token)):
    user_id = int(token["sub"])
    db = get_db()
    try:
        row = db.execute(
            "SELECT encrypted_data, updated_at FROM sync_data WHERE user_id = ?",
            (user_id,)
        ).fetchone()
        if not row:
            return SyncResponse(encryptedData=None, updatedAt=None)
        return SyncResponse(encryptedData=row["encrypted_data"], updatedAt=row["updated_at"])
    finally:
        db.close()

@app.get("/api/v1/user/info")
def user_info(token: dict = Depends(verify_token)):
    user_id = int(token["sub"])
    db = get_db()
    try:
        user = db.execute("SELECT id, username, created_at FROM users WHERE id = ?", (user_id,)).fetchone()
        sync = db.execute("SELECT updated_at FROM sync_data WHERE user_id = ?", (user_id,)).fetchone()
        return {
            "userId": user["id"],
            "username": user["username"],
            "createdAt": user["created_at"],
            "lastSync": sync["updated_at"] if sync else None,
        }
    finally:
        db.close()

if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=PORT)
