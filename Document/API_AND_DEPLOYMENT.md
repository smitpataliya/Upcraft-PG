# Shree Krishna PG — API Contract & Deployment Guide

## Overview

Multi-tenant PG management backend (`stay/`) + frontend (`shree-krishna-spaces/`).  
One generic backend serves multiple PG frontends (each with its own domain/build).

---

## Architecture

```
┌─────────────────────────────────┐
│   Public Website (no auth)      │  ← Anyone can browse rooms & menu
├─────────────────────────────────┤
│   Member Portal (/member)       │  ← PG_MEMBER role via Google SSO
├─────────────────────────────────┤
│   Owner Console (/owner)        │  ← PG_OWNER role via email/password
├─────────────────────────────────┤
│   Super Admin (/admin)          │  ← SUPER_ADMIN role — manages owners
└─────────────────────────────────┘
          ↕  REST API (JWT)
┌─────────────────────────────────┐
│  Spring Boot Backend (stay/)    │
│  PostgreSQL / MySQL             │
└─────────────────────────────────┘
```

---

## Roles

| Role          | Access                                                     |
|---------------|------------------------------------------------------------|
| `SUPER_ADMIN` | Manages PG owners, system overview via `/admin`            |
| `PG_OWNER`    | Full access to their PG: members, rooms, food, complaints  |
| `PG_MEMBER`   | View dashboard, food, announcements, complaints, queries   |
| Public        | Rooms list, today's menu (no login required)               |

---

## Auth Endpoints

| Method | Path               | Auth          | Description                     |
|--------|--------------------|---------------|---------------------------------|
| POST   | `/api/auth/login`  | None          | Email + password → JWT          |
| POST   | `/api/auth/google` | None          | Google ID token → JWT           |
| GET    | `/api/auth/me`     | Bearer JWT    | Get current user from token     |
| POST   | `/api/auth/logout` | Bearer JWT    | Invalidate session (stateless)  |

### Login Request
```json
{ "email": "owner@pg.com", "password": "secret123" }
```

### Login / Google Response
```json
{
  "token": "eyJhbGci...",
  "id": "uuid",
  "name": "Ramesh Shah",
  "email": "owner@pg.com",
  "role": "PG_OWNER",
  "phone": "+91 9000000000",
  "avatarUrl": "https://..."
}
```

### Google Auth Request
```json
{ "idToken": "<Google credential from GSI>" }
```

---

## Public Endpoints (No Auth)

| Method | Path                    | Description               |
|--------|-------------------------|---------------------------|
| GET    | `/api/public/rooms`     | All available rooms       |
| GET    | `/api/public/menu/today`| Today's meals (flat list) |

---

## Menu Endpoints

| Method | Path              | Auth       | Description             |
|--------|-------------------|------------|-------------------------|
| GET    | `/api/menu/today` | Any auth   | Today's meals           |
| GET    | `/api/menu/weekly`| Any auth   | Next 7 days meals       |
| POST   | `/api/menu`       | PG_OWNER   | Add a meal              |
| DELETE | `/api/menu/{id}`  | PG_OWNER   | Delete a meal           |

### Create Meal Request
```json
{
  "name": "Aloo Paratha",
  "description": "Served with curd",
  "type": "BREAKFAST",
  "servingTime": "08:00:00",
  "veg": true,
  "dateScheduled": "2026-07-18"
}
```

### MealDto Response
```json
{
  "id": "uuid",
  "name": "Aloo Paratha",
  "description": "Served with curd",
  "type": "BREAKFAST",
  "servingTime": "08:00:00",
  "veg": true,
  "available": true,
  "dateScheduled": "2026-07-18"
}
```

> **Frontend note:** The frontend groups the flat meal list by `type` into `DailyMenu.meals[BREAKFAST|LUNCH|SNACKS|DINNER][]`.

---

## Room Endpoints

| Method | Path           | Auth     | Description        |
|--------|----------------|----------|--------------------|
| GET    | `/api/rooms`   | Any auth | All rooms          |
| GET    | `/api/rooms/mine`| Member | Member's assigned room |
| POST   | `/api/rooms`   | Owner    | Add room           |
| PUT    | `/api/rooms/{id}` | Owner | Edit room         |
| DELETE | `/api/rooms/{id}` | Owner | Delete room       |

---

## Member Endpoints

| Method | Path                              | Auth  | Description              |
|--------|-----------------------------------|-------|--------------------------|
| GET    | `/api/members`                    | Owner | All members              |
| POST   | `/api/members`                    | Owner | Add member               |
| DELETE | `/api/members/{id}`               | Owner | Remove member            |
| POST   | `/api/members/{id}/assign-room`   | Owner | Assign room + bed        |

---

## Admin Endpoints (SUPER_ADMIN only)

| Method | Path                      | Auth        | Description          |
|--------|---------------------------|-------------|----------------------|
| GET    | `/api/admin/owners`       | SUPER_ADMIN | List all PG owners   |
| POST   | `/api/admin/owners`       | SUPER_ADMIN | Create a PG owner    |
| DELETE | `/api/admin/owners/{id}`  | SUPER_ADMIN | Deactivate owner     |

### Create Owner Request
```json
{
  "name": "Smit Patel",
  "email": "smit@example.com",
  "phone": "+91 9999999999",
  "password": "initialpassword"
}
```

---

## Dashboard Stats (PG_OWNER)

```
GET /api/dashboard/stats
```

Response:
```json
{
  "totalMembers": 24,
  "totalRooms": 12,
  "occupiedRooms": 10,
  "vacantRooms": 2,
  "pendingComplaints": 3,
  "openQueries": 5,
  "occupancySeries": [{ "month": "Jan", "occupied": 8, "vacant": 4 }],
  "complaintsSeries": [{ "day": "Mon", "count": 2 }],
  "growthSeries": [{ "month": "Jan", "members": 18 }]
}
```

---

## Deployment Guide (Hostinger VPS)

### Backend

1. **Build:** `./mvnw clean package -DskipTests`
2. **Upload:** `stay/target/stay-0.0.1-SNAPSHOT.jar` to VPS
3. **Set environment variables on VPS:**

```bash
export DB_URL=jdbc:mysql://localhost:3306/skpg
export DB_USERNAME=skpg_user
export DB_PASSWORD=yourpassword
export JWT_SECRET=your-super-secret-jwt-key-min-32-chars
export GOOGLE_CLIENT_ID=your-google-client-id.apps.googleusercontent.com
export ALLOWED_ORIGINS=https://yourdomain.com,https://www.yourdomain.com
```

4. **Run:**
```bash
java -jar stay-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

5. **Nginx reverse proxy:**
```nginx
server {
    listen 80;
    server_name api.yourdomain.com;
    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

### Frontend

1. **Edit `.env.production`** with your real `VITE_API_BASE_URL` and `VITE_GOOGLE_CLIENT_ID`
2. **Build:** `npm run build`
3. **Upload** the `.output/public/` directory to your web server / Nginx static root
4. **Nginx static site config:**

```nginx
server {
    listen 80;
    server_name yourdomain.com;
    root /var/www/shree-krishna-spaces;
    index index.html;
    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

---

## Google SSO Setup

1. Go to [Google Cloud Console](https://console.cloud.google.com)
2. Create / select a project
3. Enable **Google Identity** API
4. Create an **OAuth 2.0 Client ID** (Web application type)
5. Add authorised origins: `https://yourdomain.com`
6. Copy the Client ID and set it in:
   - Backend: `GOOGLE_CLIENT_ID` env var
   - Frontend: `VITE_GOOGLE_CLIENT_ID` in `.env.production`

---

## Frontend Environment Variables

| Variable                | Purpose                                  | Default                    |
|-------------------------|------------------------------------------|----------------------------|
| `VITE_API_BASE_URL`     | Backend API base URL                     | `http://localhost:8080`    |
| `VITE_USE_MOCK`         | Use mock data (no backend needed)        | `true`                     |
| `VITE_GOOGLE_CLIENT_ID` | Google OAuth Client ID for SSO           | —                          |
