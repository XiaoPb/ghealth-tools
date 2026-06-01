#!/bin/bash
#
# pull_debug_data.sh — 从 Android 设备拉取 GHealthTools 的 LOG 和 CSV 数据到本地 debug 目录
#
# 用法:
#   ./scripts/pull_debug_data.sh              # 拉取当天数据
#   ./scripts/pull_debug_data.sh 2026-05-18   # 拉取指定日期
#   ./scripts/pull_debug_data.sh -a           # 拉取所有日期
#   ./scripts/pull_debug_data.sh -d emulator-5554  # 指定设备
#
# 前置条件:
#   - ANDROID_HOME 环境变量已设置
#   - 设备通过 USB 连接并已授权调试
#

set -euo pipefail

# ── 配置 ──────────────────────────────────────────────
ADB="${ANDROID_HOME}/platform-tools/adb"
REMOTE_BASE="/sdcard/Documents/GHealthTools"
PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
DEBUG_DIR="${PROJECT_DIR}/debug"
SELECTED_DEVICE=""

# ── 函数 ──────────────────────────────────────────────
show_devices() {
    echo "已连接的设备列表:"
    "$ADB" devices -l | grep -v "List of devices" | grep "device$" | sed 's/^/  /'
}

select_device() {
    local devices=()
    while IFS= read -r line; do
        [[ -z "$line" ]] && continue
        devices+=("$line")
    done < <("$ADB" devices 2>/dev/null | grep -v "List of devices" | grep "device$" | cut -f1)
    
    if [[ ${#devices[@]} -eq 0 ]]; then
        echo "错误: 未检测到已连接的设备"
        exit 1
    elif [[ ${#devices[@]} -eq 1 ]]; then
        SELECTED_DEVICE="${devices[0]}"
        echo "    使用设备: ${SELECTED_DEVICE}"
    else
        echo "    检测到 ${#devices[@]} 台设备:"
        for i in "${!devices[@]}"; do
            echo "      [$((i+1))] ${devices[$i]}"
        done
        read -p "    请选择设备编号 [1-${#devices[@]}]: " choice
        if [[ "$choice" =~ ^[0-9]+$ ]] && [ "$choice" -ge 1 ] && [ "$choice" -le ${#devices[@]} ]; then
            SELECTED_DEVICE="${devices[$((choice-1))]}"
        else
            echo "错误: 无效的选择，使用第一台设备"
            SELECTED_DEVICE="${devices[0]}"
        fi
        echo "    使用设备: ${SELECTED_DEVICE}"
    fi
}

# 在 Git Bash / Windows 环境下，adb pull 的远程路径会被错误解释为本地路径，
# 因此统一使用 "adb shell cat 远程文件 > 本地文件" 的方式拉取。
pull_file() {
    local remote="$1"
    local local_path="$2"
    "$ADB" $([ -n "$SELECTED_DEVICE" ] && echo "-s $SELECTED_DEVICE") shell "cat $remote" > "$local_path" 2>/dev/null
}

# ── 参数解析 ──────────────────────────────────────────
ALL_DATES=false
DATE_ARG=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        -a|--all) ALL_DATES=true; shift ;;
        -d|--device) SELECTED_DEVICE="$2"; shift 2 ;;
        -l|--list-devices) 
            "$ADB" devices -l
            exit 0
            ;;
        -h|--help)
            echo "用法: $0 [日期|选项]"
            echo ""
            echo "选项:"
            echo "  (无参数)          拉取今天的数据"
            echo "  2026-05-18        拉取指定日期的数据"
            echo "  -a, --all         拉取所有日期的数据"
            echo "  -d, --device DEV  指定设备序列号"
            echo "  -l, --list-devices 列出所有已连接设备"
            echo "  -h, --help        显示帮助"
            exit 0
            ;;
        *) DATE_ARG="$1"; shift ;;
    esac
done

if $ALL_DATES; then
    TARGET_DATE="all"
elif [[ -n "$DATE_ARG" ]]; then
    TARGET_DATE="$DATE_ARG"
else
    TARGET_DATE="$(date +%Y-%m-%d)"
fi

# ── 检查 adb ─────────────────────────────────────────
if [[ ! -f "$ADB" ]]; then
    echo "错误: 找不到 adb ($ADB)"
    echo "请设置 ANDROID_HOME 环境变量"
    exit 1
fi

echo ">>> 检查设备连接..."
if [[ -z "$SELECTED_DEVICE" ]]; then
    select_device
else
    # 验证指定的设备是否存在
    if ! "$ADB" devices | grep -q "^${SELECTED_DEVICE}[[:space:]]*device$"; then
        echo "错误: 设备 ${SELECTED_DEVICE} 未连接或未授权"
        show_devices
        exit 1
    fi
    echo "    使用指定设备: ${SELECTED_DEVICE}"
fi

# 构建带设备参数的 adb 命令
ADB_CMD="$ADB"
if [[ -n "$SELECTED_DEVICE" ]]; then
    ADB_CMD="$ADB -s $SELECTED_DEVICE"
fi

# ── 检查远程目录 ─────────────────────────────────────
echo ""
echo ">>> 检查远程数据目录..."
REMOTE_FILES=$($ADB_CMD shell "ls ${REMOTE_BASE}/ 2>/dev/null" | tr -d '\r' || true)
if [[ -z "$REMOTE_FILES" ]]; then
    echo "错误: 远程目录 ${REMOTE_BASE} 不存在或为空"
    exit 1
fi
echo "    远程目录内容:"
echo "$REMOTE_FILES" | sed 's/^/      /'

# ── 创建本地目录 ─────────────────────────────────────
if $ALL_DATES; then
    LOCAL_DIR="${DEBUG_DIR}/all"
else
    LOCAL_DIR="${DEBUG_DIR}/${TARGET_DATE}"
fi
mkdir -p "${LOCAL_DIR}/logs"
mkdir -p "${LOCAL_DIR}/server"
mkdir -p "${LOCAL_DIR}/records"
mkdir -p "${LOCAL_DIR}/crash"
echo ""
echo ">>> 本地输出目录: ${LOCAL_DIR}"

# ── 拉取 LOG 文件 ────────────────────────────────────
echo ""
echo ">>> 拉取 LOG 文件..."

pull_logs_for_date() {
    local date_dir="$1"
    local log_remote="${REMOTE_BASE}/logs/${date_dir}"
    local log_local="${DEBUG_DIR}/${date_dir}/logs"
    mkdir -p "$log_local"

    local log_list=$($ADB_CMD shell "ls ${log_remote}/ 2>/dev/null" | tr -d '\r' || true)
    if [[ -n "$log_list" ]]; then
        while IFS= read -r f; do
            [[ -z "$f" ]] && continue
            echo "    ${f}"
            pull_file "${log_remote}/${f}" "${log_local}/${f}"
            LOG_COUNT=$((LOG_COUNT + 1))
        done <<< "$log_list"
    fi
}

LOG_COUNT=0

if $ALL_DATES; then
    for date_dir in $($ADB_CMD shell "ls ${REMOTE_BASE}/logs/ 2>/dev/null" | tr -d '\r' || true); do
        [[ -z "$date_dir" ]] && continue
        pull_logs_for_date "$date_dir"
    done
else
    pull_logs_for_date "$TARGET_DATE"
fi
echo "    共拉取 ${LOG_COUNT} 个 LOG 文件"

# ── 拉取 CSV 文件 ────────────────────────────────────
pull_csv_dir() {
    local remote_sub="$1"   # "server" or "records"
    local local_sub="$2"

    for mode_dir in $($ADB_CMD shell "ls ${REMOTE_BASE}/${remote_sub}/ 2>/dev/null" | tr -d '\r' || true); do
        [[ -z "$mode_dir" ]] && continue
        local remote_dir="${REMOTE_BASE}/${remote_sub}/${mode_dir}"
        local local_dir="${LOCAL_DIR}/${local_sub}/${mode_dir}"
        mkdir -p "$local_dir"

        local csv_list=$($ADB_CMD shell "ls ${remote_dir}/ 2>/dev/null" | tr -d '\r' || true)
        if [[ -n "$csv_list" ]]; then
            while IFS= read -r f; do
                [[ -z "$f" ]] && continue
                # 如果指定了日期，只拉取匹配日期的 CSV
                if ! $ALL_DATES && [[ -n "$DATE_ARG" ]] && [[ ! "$f" =~ $DATE_ARG ]]; then
                    continue
                fi
                echo "    ${remote_sub}/${mode_dir}/${f}"
                pull_file "${remote_dir}/${f}" "${local_dir}/${f}"
                CSV_COUNT=$((CSV_COUNT + 1))
            done <<< "$csv_list"
        fi
    done
}

CSV_COUNT=0
echo ""
echo ">>> 拉取 CSV 文件 (server)..."
pull_csv_dir "server" "server"

echo ""
echo ">>> 拉取 CSV 文件 (records)..."
pull_csv_dir "records" "records"

echo "    共拉取 ${CSV_COUNT} 个 CSV 文件"

# ── 拉取崩溃日志 ─────────────────────────────────────
echo ""
echo ">>> 拉取崩溃日志..."
$ADB_CMD shell dumpsys dropbox --print | grep -A 50 "crash" > "${LOCAL_DIR}/crash/crash_log_$(date +%Y%m%d).log" 2>/dev/null || true
echo "    崩溃日志已保存"

# ── 文件统计 ─────────────────────────────────────────
echo ""
echo "============================================="
echo "  拉取完成"
echo "============================================="
echo "  设备:     ${SELECTED_DEVICE}"
echo "  本地路径: ${LOCAL_DIR}"
echo "  LOG 文件: ${LOG_COUNT} 个"
echo "  CSV 文件: ${CSV_COUNT} 个"
echo "  总大小:   $(du -sh "${LOCAL_DIR}" 2>/dev/null | cut -f1 || echo 'N/A')"
echo ""

# 列出文件结构
find "${LOCAL_DIR}" -type f | sed "s|${LOCAL_DIR}/|  |" | head -50

total_files=$(find "${LOCAL_DIR}" -type f | wc -l)
if [[ $total_files -gt 50 ]]; then
    echo "  ... 还有 $((total_files - 50)) 个文件"
fi