#!/usr/bin/env bash
# Kiểm thử các chức năng Remote của PBL4.
# Cần: curl, jq   (sudo apt install curl jq)
#
# Dùng:  ./remote-test.sh <lệnh> [tham số]
# Xem danh sách lệnh:  ./remote-test.sh help

BASE="${BASE:-http://localhost:8080}"
ADMIN_USER="${ADMIN_USER:-admin}"
ADMIN_PASS="${ADMIN_PASS:-Admin@123}"
TOKEN_FILE="${TOKEN_FILE:-/tmp/pbl4_token}"
CLIENT_FILE="${CLIENT_FILE:-/tmp/pbl4_client}"

command -v jq >/dev/null || { echo "Thiếu jq: sudo apt install jq"; exit 1; }

# ---------------------------------------------------------------
# Hàm dùng chung
# ---------------------------------------------------------------
token()  { [[ -s $TOKEN_FILE ]] && cat "$TOKEN_FILE" || { echo "Chưa đăng nhập: chạy './remote-test.sh login'" >&2; return 1; }; }
client() { [[ -n ${CLIENT_ID:-} ]] && echo "$CLIENT_ID" || { [[ -s $CLIENT_FILE ]] && cat "$CLIENT_FILE" || { echo "Chưa chọn Client: './remote-test.sh use <clientId>'" >&2; return 1; }; }; }

# api METHOD PATH [JSON]  -> in "HTTP <mã>" và nội dung; lưu LAST_CODE, LAST_BODY
api() {
  local method=$1 path=$2 body=${3:-} tok
  tok=$(token) || return 1
  local args=(-s -w '\n%{http_code}' -X "$method" "$BASE$path" -H "Authorization: Bearer $tok")
  [[ -n $body ]] && args+=(-H 'Content-Type: application/json' -d "$body")
  local out; out=$(curl "${args[@]}")
  LAST_CODE=${out##*$'\n'}
  LAST_BODY=${out%$'\n'*}
  echo "HTTP $LAST_CODE"
  echo "$LAST_BODY" | jq . 2>/dev/null || echo "$LAST_BODY"
}

# Gửi lệnh Remote, lưu LAST_CMD_ID
send_command() {   # send_command <TYPE> [parameters-json]
  local cid; cid=$(client) || return 1
  local body
  if [[ -n ${2:-} ]]; then
    body=$(jq -nc --arg c "$cid" --arg t "$1" --argjson p "$2" '{clientId:$c,commandType:$t,parameters:$p}')
  else
    body=$(jq -nc --arg c "$cid" --arg t "$1" '{clientId:$c,commandType:$t}')
  fi
  api POST /api/remote/commands "$body"
  LAST_CMD_ID=$(echo "$LAST_BODY" | jq -r '.commandId // empty')
}

# Chờ lệnh kết thúc (tối đa 60 giây)
wait_command() {   # wait_command <commandId>
  local id=$1 st
  echo "Đang chờ lệnh $id (Agent hỏi lệnh mỗi vài giây)..."
  for _ in $(seq 1 30); do
    st=$(curl -s "$BASE/api/remote/commands/$id" -H "Authorization: Bearer $(token)" | jq -r '.status')
    case $st in
      COMPLETED|FAILED|TIMEOUT) echo "Trạng thái cuối: $st"
        curl -s "$BASE/api/remote/commands/$id" -H "Authorization: Bearer $(token)" | jq '{status,result,errorMessage}'
        [[ $st == COMPLETED ]]; return ;;
    esac
    sleep 2
  done
  echo "Hết thời gian chờ: lệnh vẫn ở trạng thái '$st'. Agent có đang chạy không?"; return 1
}

# ---------------------------------------------------------------
# Chuẩn bị
# ---------------------------------------------------------------
cmd_login() {
  local t
  t=$(curl -s -X POST "$BASE/api/auth/login" -H 'Content-Type: application/json' \
      -d "$(jq -nc --arg u "$ADMIN_USER" --arg p "$ADMIN_PASS" '{username:$u,password:$p}')" | jq -r '.token // empty')
  [[ -n $t ]] || { echo "Đăng nhập thất bại."; return 1; }
  echo -n "$t" > "$TOKEN_FILE"; chmod 600 "$TOKEN_FILE"
  echo "Đã đăng nhập, lưu token vào $TOKEN_FILE"
}
cmd_clients() { api GET /api/clients; }
cmd_agents()  { api GET /api/client-agents; }
cmd_use()     { [[ -n ${1:-} ]] || { echo "Dùng: use <clientId>"; return 1; }; echo -n "$1" > "$CLIENT_FILE"; echo "Client đang dùng: $1"; }

# ---------------------------------------------------------------
# KILL_PROCESS
# ---------------------------------------------------------------
cmd_kill() {        # kill <pid>
  [[ ${1:-} =~ ^[0-9]+$ ]] || { echo "Dùng: kill <pid>"; return 1; }
  send_command KILL_PROCESS "{\"pid\":$1}" && [[ -n $LAST_CMD_ID ]] && wait_command "$LAST_CMD_ID"
}

# Tạo một tiến trình thật, bảo Agent dừng nó, rồi xác nhận nó đã chết.
# Chỉ đúng khi Agent chạy trên CÙNG máy với script này.
cmd_kill_demo() {
  # bash -c + & + echo: tiến trình mồ côi, được hệ thống dọn khi chết (không thành zombie)
  local pid; pid=$(bash -c 'sleep 600 >/dev/null 2>&1 & echo $!')
  echo "Đã tạo tiến trình thử nghiệm: PID $pid"
  kill -0 "$pid" 2>/dev/null && echo "  -> đang chạy (đúng)" || { echo "Không tạo được tiến trình."; return 1; }
  cmd_kill "$pid"
  sleep 1
  if kill -0 "$pid" 2>/dev/null; then echo "KẾT QUẢ: LỖI - tiến trình $pid vẫn còn."; return 1
  else echo "KẾT QUẢ: ĐẠT - tiến trình $pid đã bị dừng."; fi
}

# ---------------------------------------------------------------
# RESTART / SHUTDOWN  (NGUY HIỂM: tắt máy thật)
# ---------------------------------------------------------------
confirm_power() {
  echo "CẢNH BÁO: lệnh $1 sẽ thực sự $2 máy Client (nếu Agent có quyền sudo)."
  read -r -p "Gõ YES để tiếp tục: " a; [[ $a == YES ]]
}
cmd_restart()  { confirm_power RESTART  "khởi động lại" && send_command RESTART  && wait_command "$LAST_CMD_ID"; }
cmd_shutdown() { confirm_power SHUTDOWN "tắt"           && send_command SHUTDOWN && wait_command "$LAST_CMD_ID"; }

# ---------------------------------------------------------------
# Lịch sử lệnh
# ---------------------------------------------------------------
cmd_history() { api GET "/api/remote/commands/client/$(client)"; }
cmd_get()     { api GET "/api/remote/commands/${1:?Dùng: get <commandId>}"; }

# ---------------------------------------------------------------
# Chia sẻ màn hình
# ---------------------------------------------------------------
cmd_share_start()   { api POST /api/screen-sharing/sessions "$(jq -nc --arg c "$(client)" '{clientId:$c}')"
                      echo "$LAST_BODY" | jq -r '.sessionCode // empty' | { read -r c; [[ -n $c ]] && echo "$c" > /tmp/pbl4_session && echo "-> sessionCode đã lưu: $c"; }; }
cmd_share_current() { api GET /api/screen-sharing/sessions/current; }
cmd_share_stop()    { local c=${1:-$(cat /tmp/pbl4_session 2>/dev/null)}; [[ -n $c ]] || { echo "Dùng: share-stop <sessionCode>"; return 1; }
                      api POST "/api/screen-sharing/sessions/$c/stop"; }

# ---------------------------------------------------------------
# Kiểm thử trường hợp lỗi / bảo mật (kết quả mong đợi ghi bên cạnh)
# ---------------------------------------------------------------
expect() {   # expect <mô tả> <mã mong đợi> <mã thực tế>
  [[ $2 == "$3" ]] && echo "  ĐẠT   $1 (HTTP $3)" || echo "  LỖI   $1: mong đợi $2, nhận $3"
}
cmd_negative() {
  local cid; cid=$(client) || return 1
  local tok; tok=$(token) || return 1
  local c

  echo "== Bảo mật =="
  c=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/api/remote/commands" -H 'Content-Type: application/json' \
      -d "{\"clientId\":\"$cid\",\"commandType\":\"KILL_PROCESS\",\"parameters\":{\"pid\":1}}")
  [[ $c == 401 || $c == 403 ]] && echo "  ĐẠT   Không có token bị chặn (HTTP $c)" || echo "  LỖI   Không có token mà nhận HTTP $c"
  c=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/api/remote/commands" -H 'Authorization: Bearer abc.def.ghi' \
      -H 'Content-Type: application/json' -d "{\"clientId\":\"$cid\",\"commandType\":\"RESTART\"}")
  [[ $c == 401 || $c == 403 ]] && echo "  ĐẠT   Token giả bị chặn (HTTP $c)" || echo "  LỖI   Token giả mà nhận HTTP $c"

  echo "== Dữ liệu không hợp lệ (Server phải từ chối, không tạo lệnh) =="
  api POST /api/remote/commands '{"clientId":"","commandType":"KILL_PROCESS"}' >/dev/null;           expect "clientId rỗng" 400 "$LAST_CODE"
  api POST /api/remote/commands "{\"clientId\":\"$cid\"}" >/dev/null;                                expect "thiếu commandType" 400 "$LAST_CODE"
  api POST /api/remote/commands "{\"clientId\":\"$cid\",\"commandType\":\"ABC\"}" >/dev/null;        expect "commandType không tồn tại" 400 "$LAST_CODE"
  api POST /api/remote/commands '{"clientId":"KHONG-CO","commandType":"RESTART"}' >/dev/null;        expect "Client không tồn tại" 400 "$LAST_CODE"
  api POST /api/remote/commands "{\"clientId\":\"$cid\",\"commandType\":\"KILL_PROCESS\",\"parameters\":[1]}" >/dev/null
                                                                                                     expect "parameters không phải object" 400 "$LAST_CODE"
  api GET /api/remote/commands/999999999 >/dev/null;                                                 expect "lệnh không tồn tại" 400 "$LAST_CODE"
  echo "(Mã 404 thay cho 400 ở hai dòng cuối cũng có thể chấp nhận được, tùy cách xử lý lỗi.)"
}

# Các lệnh mà Server nhận nhưng Agent phải từ chối (kết quả mong đợi: FAILED)
cmd_negative_agent() {
  echo "== Agent phải từ chối (mong đợi trạng thái FAILED) =="
  echo "-- PID không tồn tại"; send_command KILL_PROCESS '{"pid":99999999}' >/dev/null; wait_command "$LAST_CMD_ID"
  echo "-- PID = 0";            send_command KILL_PROCESS '{"pid":0}' >/dev/null;        wait_command "$LAST_CMD_ID"
  echo "-- Thiếu pid";          send_command KILL_PROCESS '{}' >/dev/null;               wait_command "$LAST_CMD_ID"
  echo "-- pid không phải số";  send_command KILL_PROCESS '{"pid":"abc"}' >/dev/null;    wait_command "$LAST_CMD_ID"
  echo "-- START_SCREEN_SHARING không có sessionCode"
  send_command START_SCREEN_SHARING >/dev/null; wait_command "$LAST_CMD_ID"
}

cmd_help() {
cat <<'TXT'
Chuẩn bị
  login                   đăng nhập Admin, lưu token
  clients | agents        liệt kê Client / Agent
  use <clientId>          chọn Client để thử

Chức năng
  kill <pid>              gửi KILL_PROCESS
  kill-demo               tạo tiến trình giả, kill từ xa, kiểm tra đã chết
  restart | shutdown      RESTART / SHUTDOWN (hỏi xác nhận, NGUY HIỂM)
  history                 các lệnh đã gửi cho Client
  get <commandId>         chi tiết một lệnh
  share-start             tạo phiên chia sẻ màn hình
  share-current           xem phiên hiện tại
  share-stop [code]       dừng phiên

Kiểm thử lỗi
  negative                bảo mật + dữ liệu sai (chỉ cần Server)
  negative-agent          lệnh sai mà Agent phải từ chối (cần Agent chạy)

Biến: BASE, ADMIN_USER, ADMIN_PASS, CLIENT_ID
TXT
}

c=${1:-help}; shift || true
"cmd_${c//-/_}" "$@" 2>/dev/null || { type "cmd_${c//-/_}" >/dev/null 2>&1 && "cmd_${c//-/_}" "$@" || { echo "Lệnh không hợp lệ: $c"; cmd_help; }; }
