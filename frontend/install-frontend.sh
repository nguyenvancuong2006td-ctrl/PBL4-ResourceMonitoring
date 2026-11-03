#!/usr/bin/env bash

# ============================================================
# PBL4 - RESOURCE MONITORING SYSTEM
# FILE: install-frontend.sh
#
# Muc dich:
#   Cai dat cac cong nghe va thu vien cho frontend React.
#
# Cach su dung:
#   1. Dat file nay trong thu muc frontend/
#   2. Mo Terminal tai thu muc frontend
#   3. Chay:
#        chmod +x install-frontend.sh
#        ./install-frontend.sh
#
# Yeu cau:
#   - Node.js va npm da duoc cai dat
#   - Nen su dung Node.js 20.19+ hoac 22.12+ (Vite 8)
#
# Luu y:
#   - Script nay cai dat dependencies, khong tao lai project.
#   - Khong chay lai shadcn init de tranh ghi de components.json
#     va cau hinh hien tai cua du an.
# ============================================================

set -e

# Di chuyen den thu muc chua file script (thu muc frontend)
cd "$(dirname "$0")"

echo ""
echo "============================================================"
echo " PBL4 - CAI DAT CONG NGHE FRONTEND"
echo "============================================================"

# ------------------------------------------------------------
# 1. KIEM TRA MOI TRUONG NODE.JS VA NPM
# ------------------------------------------------------------
echo ""
echo "[1/7] Kiem tra Node.js va npm..."

if ! command -v node >/dev/null 2>&1; then
    echo "LOI: Chua tim thay Node.js. Hay cai Node.js truoc."
    exit 1
fi

if ! command -v npm >/dev/null 2>&1; then
    echo "LOI: Chua tim thay npm. Hay cai Node.js/npm truoc."
    exit 1
fi

node --version
npm --version

# ------------------------------------------------------------
# 2. REACT + TYPESCRIPT + VITE
# ------------------------------------------------------------
echo ""
echo "[2/7] Cai dat React, TypeScript va Vite..."

# React: thu vien xay dung giao dien.
# React DOM: hien thi React tren trinh duyet.
npm install react react-dom

# Vite: cong cu khoi tao, chay server phat trien va build frontend.
# TypeScript: bo sung kieu du lieu cho JavaScript.
# Plugin React: ho tro React trong Vite.
# Cac goi @types: khai bao kieu du lieu cho TypeScript.
npm install -D vite typescript @vitejs/plugin-react \
    @types/react @types/react-dom

# ------------------------------------------------------------
# 3. TAILWIND CSS V4
# ------------------------------------------------------------
echo ""
echo "[3/7] Cai dat Tailwind CSS v4..."

# Tailwind CSS: tao giao dien bang cac utility class.
# @tailwindcss/vite: tich hop Tailwind CSS v4 voi Vite.
npm install -D tailwindcss @tailwindcss/vite

# ------------------------------------------------------------
# 4. SHADCN/UI VA CAC GOI HO TRO GIAO DIEN
# ------------------------------------------------------------
echo ""
echo "[4/7] Cai dat shadcn/ui va cac goi giao dien..."

# shadcn: CLI de them cac component giao dien vao ma nguon.
# tw-animate-css: cac hieu ung chuyen dong dung trong giao dien.
# Geist: font chu cho giao dien.
# lucide-react: bo icon cho sidebar, dashboard, nut chuc nang...
npm install -D shadcn
npm install tw-animate-css @fontsource-variable/geist lucide-react

# LUU Y VE SHADCN/UI:
# Du an hien tai da co components.json va da khoi tao shadcn/ui.
# Khong chay lai "npx shadcn init" trong script nay.
# Khi can them component, dung lenh vi du:
#   npx shadcn@latest add button
#   npx shadcn@latest add card
#   npx shadcn@latest add input
# Hay kiem tra component co phu hop preset Base Nova hien tai khong.

# ------------------------------------------------------------
# 5. DIEU HUONG VA LAM VIEC VOI REST API
# ------------------------------------------------------------
echo ""
echo "[5/7] Cai dat React Router, Axios va TanStack Query..."

# React Router DOM: dieu huong giua Login, Dashboard va cac trang.
npm install react-router-dom

# Axios: gui HTTP request den REST API cua Spring Boot.
npm install axios

# TanStack Query: quan ly du lieu API, cache va tu dong tai lai.
npm install @tanstack/react-query

# ------------------------------------------------------------
# 6. BIEU DO, FORM, VALIDATION, THONG BAO VA NGAY GIO
# ------------------------------------------------------------
echo ""
echo "[6/7] Cai dat cac thu vien phuc vu chuc nang frontend..."

# Recharts: ve bieu do CPU, RAM, Disk, Network va lich su.
npm install recharts

# React Hook Form: quan ly form dang nhap, cau hinh va cac form khac.
npm install react-hook-form

# Zod: kiem tra va rang buoc du lieu dau vao.
npm install zod

# Resolver: ket noi Zod voi React Hook Form.
npm install @hookform/resolvers

# Sonner: hien thi thong bao thanh cong, that bai, canh bao.
npm install sonner

# date-fns: xu ly ngay gio, khoang thoi gian va lich su giam sat.
npm install date-fns

# ------------------------------------------------------------
# 7. KIEM TRA BUILD
# ------------------------------------------------------------
echo ""
echo "[7/7] Kiem tra build frontend..."

if [ -f "package.json" ] && npm run | grep -q "build"; then
    npm run build
else
    echo "Khong tim thay script build trong package.json."
    echo "Hay kiem tra lai cau hinh project."
fi

echo ""
echo "============================================================"
echo " CAI DAT HOAN TAT!"
echo "============================================================"
echo "Chay frontend bang lenh:"
echo "  npm run dev"
echo ""
echo "Mo dia chi Vite hien thi trong Terminal, thuong la:"
echo "  http://localhost:5173"
echo ""
echo "Luu y:"
echo "- WebSocket: trinh duyet da co WebSocket API."
echo "- WebRTC: trinh duyet da co WebRTC API."
echo "- Neu Backend dung giao thuc STOMP tren WebSocket, co the"
echo "  cai them bang lenh: npm install @stomp/stompjs"
echo "- WebRTC chi can thu vien ngoai neu kien truc/backend cua"
echo "  du an yeu cau mot SDK cu the; chua nen cai them tuy tien."
echo "============================================================"
