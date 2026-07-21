#!/usr/bin/env bash
# Công cụ vận hành DLT của SwiftPay — xem / replay / xoá / inject lỗi.
#
# Đường xử lý lỗi (DLQ) là thứ không chạy trong lúc bình thường, nên chỉ chủ động inject lỗi mới biết
# nó có hoạt động đúng hay không (vd: consumer có bị chặn partition, hay chỉ message rơi vào DLT bình
# thường). Việc kiểm chứng đó cần lặp lại được, không phải làm tay một lần rồi thôi.
#
# Dùng:
#   ./scripts/kafka-dlt.sh list                      # các topic .DLT và số message đang giữ
#   ./scripts/kafka-dlt.sh peek  <topic.DLT> [n]     # xem n message (mặc định 5) + lý do chết
#   ./scripts/kafka-dlt.sh replay <topic.DLT>        # đẩy lại toàn bộ về topic gốc
#   ./scripts/kafka-dlt.sh purge  <topic.DLT>        # xoá sạch (dùng cho message rác của bài test)
#   ./scripts/kafka-dlt.sh inject [topic]            # bơm 1 message hỏng để kiểm chứng đường DLQ
#   ./scripts/kafka-dlt.sh verify                    # inject + chờ + khẳng định DLT tăng và consumer còn sống
set -euo pipefail

KAFKA_CONTAINER="${KAFKA_CONTAINER:-kafka}"
BOOTSTRAP="${BOOTSTRAP:-localhost:9092}"
KBIN=/opt/kafka/bin

k() { docker exec "$KAFKA_CONTAINER" bash -c "$*"; }

die() { echo "LỖI: $*" >&2; exit 1; }

dlt_topics() {
  k "$KBIN/kafka-topics.sh --bootstrap-server $BOOTSTRAP --list" | tr -d '\r' | grep '\.DLT$' || true
}

# Số message đang giữ = tổng (offset cuối - offset đầu) trên mọi partition.
count_messages() {
  local topic="$1"
  local ends starts
  ends=$(k "$KBIN/kafka-get-offsets.sh --bootstrap-server $BOOTSTRAP --topic $topic --time -1" | tr -d '\r')
  starts=$(k "$KBIN/kafka-get-offsets.sh --bootstrap-server $BOOTSTRAP --topic $topic --time -2" | tr -d '\r')
  awk -F: -v s="$starts" '
    BEGIN { split(s, sl, "\n"); for (i in sl) { split(sl[i], p, ":"); start[p[1] ":" p[2]] = p[3] } }
    { key = $1 ":" $2; total += $3 - (start[key] + 0) }
    END { print total + 0 }' <<<"$ends"
}

cmd_list() {
  local topics; topics=$(dlt_topics)
  [ -z "$topics" ] && { echo "Không có topic .DLT nào."; return; }
  printf '%-40s %s\n' "TOPIC" "SỐ MESSAGE"
  while read -r t; do
    [ -z "$t" ] && continue
    printf '%-40s %s\n' "$t" "$(count_messages "$t")"
  done <<<"$topics"
  echo
  echo "Khác 0 KHÔNG BAO GIỜ là bình thường: đã có event bị bỏ. Xem bằng 'peek', xử lý xong thì 'replay'."
}

cmd_peek() {
  local topic="${1:?thiếu tên topic}" n="${2:-5}"
  # Chỉ in phần đáng đọc: topic gốc, consumer group, và thông điệp lỗi — không in cả stacktrace.
  k "$KBIN/kafka-console-consumer.sh --bootstrap-server $BOOTSTRAP --topic $topic \
      --from-beginning --max-messages $n --timeout-ms 15000 --property print.headers=true" 2>/dev/null \
    | tr ',' '\n' \
    | grep -a -E "kafka_dlt-(original-topic|original-consumer-group|exception-message|exception-fqcn)" || true
    # -a bắt buộc: header DLT chứa byte nhị phân (offset/timestamp dạng thô) nên grep coi cả luồng là binary.
}

cmd_replay() {
  local dlt="${1:?thiếu tên topic .DLT}"
  local origin="${dlt%.DLT}"
  local n; n=$(count_messages "$dlt")
  [ "$n" -eq 0 ] && { echo "$dlt rỗng, không có gì để replay."; return; }

  echo "Replay $n message: $dlt → $origin"
  echo "LƯU Ý: nếu nguyên nhân gốc CHƯA sửa, message sẽ chết lại và quay về DLT ngay."
  read -r -p "Tiếp tục? [y/N] " ok
  [ "$ok" = "y" ] || { echo "Huỷ."; return; }

  # Đọc value (bỏ header) rồi bơm lại vào topic gốc. Key không được giữ — chấp nhận được vì các event
  # của hệ thống này không phụ thuộc thứ tự theo key; nếu sau này có, phải chuyển sang replay giữ key.
  k "$KBIN/kafka-console-consumer.sh --bootstrap-server $BOOTSTRAP --topic $dlt \
      --from-beginning --max-messages $n --timeout-ms 20000 2>/dev/null \
     | $KBIN/kafka-console-producer.sh --bootstrap-server $BOOTSTRAP --topic $origin"
  echo "Đã đẩy lại $n message vào $origin."
  echo "DLT chưa tự rỗng (Kafka không xoá khi đọc). Xác nhận đã xử lý xong thì chạy: $0 purge $dlt"
}

# Cắt bỏ dữ liệu bằng delete-records, KHÔNG xoá topic. Xoá topic thì nó chỉ được tạo lại lúc app khởi
# động (topic khai báo trong KafkaTopicConfig, auto-create đang TẮT) — trong khoảng trống đó message
# chết sẽ không publish được vào đâu cả, tức là lại rơi vào đúng cái bẫy "DLT không tồn tại" cũ.
cmd_purge() {
  local topic="${1:?thiếu tên topic}"
  echo "XOÁ SẠCH message trong $topic — không lấy lại được."
  echo "Chỉ làm khi đã 'peek' và chắc chắn không còn gì cần replay."
  read -r -p "Gõ tên topic để xác nhận: " confirm
  [ "$confirm" = "$topic" ] || { echo "Không khớp, huỷ."; return; }

  # Cắt tới offset cuối của từng partition.
  local json partitions
  partitions=$(k "$KBIN/kafka-get-offsets.sh --bootstrap-server $BOOTSTRAP --topic $topic --time -1" | tr -d '\r')
  json=$(awk -F: 'BEGIN { printf "{\"partitions\":[" }
    { if (n++) printf ","; printf "{\"topic\":\"%s\",\"partition\":%s,\"offset\":%s}", $1, $2, $3 }
    END { print "],\"version\":1}" }' <<<"$partitions")

  k "echo '$json' > /tmp/purge.json && $KBIN/kafka-delete-records.sh --bootstrap-server $BOOTSTRAP --offset-json-file /tmp/purge.json"
  echo "Đã cắt sạch $topic (topic vẫn tồn tại — quan trọng, vì auto-create đang tắt)."
}

cmd_inject() {
  local topic="${1:-swiftpay.users}"
  echo "Bơm 1 message KHÔNG PHẢI JSON vào $topic — consumer phải đẩy nó sang ${topic}.DLT."
  k "echo '{{{ khong-phai-json-hop-le' | $KBIN/kafka-console-producer.sh --bootstrap-server $BOOTSTRAP --topic $topic"
  echo "Đã bơm."
}

# Kiểm chứng đầu-cuối: DLT phải TĂNG, và consumer phải VẪN CÒN SỐNG (đây mới là phần bug cũ làm hỏng).
cmd_verify() {
  local topic="${1:-swiftpay.users}" dlt
  dlt="${topic}.DLT"

  local before; before=$(count_messages "$dlt" 2>/dev/null || echo 0)
  echo "Trước khi inject: $dlt = $before message"

  cmd_inject "$topic"

  echo -n "Chờ message chết được đẩy sang DLT"
  local after=$before
  for _ in $(seq 1 30); do
    sleep 2; echo -n "."
    after=$(count_messages "$dlt" 2>/dev/null || echo 0)
    [ "$after" -gt "$before" ] && break
  done
  echo

  [ "$after" -gt "$before" ] || die "DLT KHÔNG tăng ($before → $after). Đường DLQ đang hỏng."
  echo "OK: $dlt $before → $after"

  # Phần quan trọng nhất: partition có bị chặn không. Nếu consumer chết, mọi event SAU đó cũng kẹt.
  echo "Kiểm tra consumer còn tiêu thụ được không (lag phải về 0):"
  sleep 5
  # Bỏ qua group 'console-consumer-*' do chính các lệnh peek/replay ở trên sinh ra — chúng là rác của
  # công cụ, không phải consumer của hệ thống, và làm nhiễu đúng chỗ cần đọc.
  k "$KBIN/kafka-consumer-groups.sh --bootstrap-server $BOOTSTRAP --describe --all-groups" \
    | tr -d '\r' | grep -v 'console-consumer-' \
    | awk 'NR==1 || $0 ~ /'"${topic//./\\.}"'/ {print}'
  echo
  echo "LAG phải bằng 0. Nếu LAG tăng dần thì partition đang bị CHẶN, không phải chỉ 'có message chết'."
}

case "${1:-}" in
  list)   cmd_list ;;
  peek)   shift; cmd_peek "$@" ;;
  replay) shift; cmd_replay "$@" ;;
  purge)  shift; cmd_purge "$@" ;;
  inject) shift; cmd_inject "$@" ;;
  verify) shift; cmd_verify "$@" ;;
  *) sed -n '2,18p' "$0"; exit 1 ;;
esac
