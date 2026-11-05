#!/bin/bash
# ============================================================================
#  部署环境体检（只读，不改任何东西）
#
#  用法：
#      chmod +x check-env.sh
#      ./check-env.sh
#
#  第一次部署前跑一遍，它会告诉你还差什么。
#  部署出问题时也先跑它，能把"环境没配好"和"程序有问题"分开。
#
#  ⚠️ 本脚本【只读】：不改配置、不建目录、不装软件、不碰数据库数据。
#     所有需要动手的地方它只打印命令，由你自己决定要不要执行。
#
#  ⚠️ 这里故意【不用 set -e】——它的职责是"报告现状"而不是"出错就停"，
#     检查项缺失（JDK 没装、端口被占）正是它要报告的内容，不是它的故障。
# ============================================================================

umask 027

WORK_DIR=$(cd "$(dirname "$0")" && pwd)
ENV_FILE="$WORK_DIR/env"
# 本应用的 jar 名。第 4 项用它来分辨"端口占用者是不是我们自己"
APP_JAR_NAME="crm-server.jar"
PASS=0
FAIL=0

# 要检查的端口。
# ⚠️ 这里用 grep 从 env 里【读】SERVER_PORT，而不是 source 整个文件 ——
#    体检脚本不该执行 env 里的任意内容，何况它自己还要检查 env 的换行符问题。
CHK_PORT="8080"
if [ -f "$ENV_FILE" ]; then
    _p=$(grep -E "^SERVER_PORT=" "$ENV_FILE" 2>/dev/null | tail -1 | cut -d= -f2 | tr -d "'\" \r" || true)
    [ -n "${_p:-}" ] && CHK_PORT="$_p"
fi

ok()   { echo "  ✅ $*"; PASS=$((PASS + 1)); }
bad()  { echo "  ❌ $*"; FAIL=$((FAIL + 1)); }
warn() { echo "  ⚠️  $*"; }
head_() { echo ""; echo "── $* ─────────────────────────────────"; }

# 从 ss / netstat 的输出里抠出占用端口那些进程的 PID。
# 两种工具的输出格式不同，所以两种模式都试一遍：
#   ss      : users:(("java",pid=1748,fd=128))   → pid=NNN
#   netstat : ... LISTEN      1748/java          → 行内 NNN/程序名
_occupied_pids() {
    { printf '%s\n' "$1" | sed -n 's/.*pid=\([0-9][0-9]*\).*/\1/p'
      printf '%s\n' "$1" | sed -n 's/.*[[:space:]]\([0-9][0-9]*\)\/[^ ]*.*/\1/p'
    } | sort -u
}

echo "════════════════════════════════════════════════════"
echo "  AurumCRM 部署环境体检"
echo "  目录：$WORK_DIR"
echo "  时间：$(date '+%F %T %Z')"
echo "════════════════════════════════════════════════════"

# ============================================================================
head_ "1. 操作系统"
# ============================================================================
if [ -f /etc/os-release ]; then
    . /etc/os-release
    echo "  $PRETTY_NAME"
else
    echo "  $(uname -a)"
fi
echo "  内核：$(uname -r)"

# ============================================================================
head_ "2. JDK 17"
#
# ⚠️ 只认 17。项目是 Spring Boot 3.3.5，编译目标是 17，
#    用 8/11 启动会直接报 UnsupportedClassVersionError。
# ============================================================================
JAVA_BIN=""
for c in /usr/local/jdk-17*/bin/java /usr/local/jdk*/bin/java; do
    [ -x "$c" ] && { JAVA_BIN="$c"; break; }
done
[ -n "$JAVA_BIN" ] || JAVA_BIN=$(command -v java 2>/dev/null || true)

if [ -z "$JAVA_BIN" ]; then
    bad "找不到 java"
    echo "     装 JDK 17："
    echo "       tar -zxvf /root/jdk-17.0.13_linux-x64_bin.tar.gz -C /usr/local"
    echo "       # 然后 /etc/profile 里加 JAVA_HOME 和 PATH"
else
    JVER=$("$JAVA_BIN" -version 2>&1 | head -1 | sed -n 's/.*version "\([0-9]*\).*/\1/p')
    if [ "$JVER" = "17" ]; then
        ok "java 17（$JAVA_BIN）"
    else
        bad "java 版本是 $JVER，需要 17（$JAVA_BIN）"
    fi
fi

# ============================================================================
head_ "3. 系统时区"
#
# ⚠️ 必须是 Asia/Shanghai。理由是这一项错了【不会报错】，
#    只会让数据悄悄错 8 小时 —— 最难查的那类问题：
#      · 定时任务 cron 是 0 5 3 * * ?（凌晨 3:05），跑在 UTC 会变成北京时间 11:05
#      · Jackson 固定 GMT+8 序列化，落库的 LocalDateTime 会对不上
# ============================================================================
TZ_NOW=$(date +%Z)
OFFSET=$(date +%z)
if [ "$OFFSET" = "+0800" ]; then
    ok "当前是北京时间（$TZ_NOW $OFFSET）"
else
    bad "当前是 $TZ_NOW $OFFSET，应为 +0800"
    echo "     修复：timedatectl set-timezone Asia/Shanghai"
fi

# ============================================================================
head_ "4. 端口 ${CHK_PORT}"
#
# ⚠️ 这台机器上已经有别的东西在跑，被占了应用起不来，报 Address already in use。
#    这台机器已知占用：22(ssh) 80(nginx) 1234(云监控) 3306(MySQL) 6379(Redis)
# ============================================================================
if command -v ss >/dev/null 2>&1; then
    OCCUPIED=$(ss -ltnp 2>/dev/null | grep -F ":${CHK_PORT}" || true)
elif command -v netstat >/dev/null 2>&1; then
    OCCUPIED=$(netstat -ltnp 2>/dev/null | grep -F ":${CHK_PORT}" || true)
else
    OCCUPIED=""
    warn "没有 ss 也没有 netstat，跳过端口检查"
fi

if [ -z "$OCCUPIED" ]; then
    ok "${CHK_PORT} 空闲"
else
    # ⚠️ 看到"被占用"就报 ❌ 是不够的 —— 必须先分清是【谁】占的。
    #    服务已经跑起来之后再跑本脚本，占用者就是 CRM 自己，那是最正常的状态；
    #    报成 ❌ 会把人引去停服务或改端口，属于自己给自己制造故障。
    #    （和 service.sh 里 flock 的误报同一类毛病：分不清"被别人占了"和"被自己占了"）
    self_pids=""
    other_pids=""
    for _pid in $(_occupied_pids "$OCCUPIED"); do
        if [ -r "/proc/$_pid/cmdline" ] &&
           tr '\0' ' ' < "/proc/$_pid/cmdline" 2>/dev/null | grep -qF "$APP_JAR_NAME"; then
            self_pids="$self_pids $_pid"
        else
            other_pids="$other_pids $_pid"
        fi
    done

    if [ -n "$other_pids" ]; then
        bad "${CHK_PORT} 被【其他进程】占用："
        printf '      %s\n' "$OCCUPIED"
        echo "     要么停掉占用方，要么在 env 文件里改 SERVER_PORT（记得同步改 HEALTH_URL）"
    elif [ -n "$self_pids" ]; then
        ok "${CHK_PORT} 被本应用占用（PID${self_pids}）—— 服务正在运行，属正常"
        echo "     要确认端口本身是否空闲，先 ./service.sh stop 再跑本脚本"
    else
        # 认不出占用者（例如没权限读 /proc/<pid>）时保守提示，但不判死
        warn "${CHK_PORT} 已被占用，但无法确认占用者是否为本应用："
        printf '      %s\n' "$OCCUPIED"
    fi
fi

# ============================================================================
head_ "5. 防火墙（本机）"
#
#  ⚠️ 这里必须分清三种情况，它们的【处理方式完全不同】：
#      ① firewalld 在跑，但没放行端口 → 要加规则
#      ② firewalld 装了但没跑         → 【什么都不用做】，本机没东西在拦
#      ③ 压根没装 firewalld           → 同上
#     早先的版本把 ②③ 也报成"没放行"，会让人跑去执行 firewall-cmd，
#     而那条命令在服务没运行时是无效的 —— 白折腾，还以为自己配好了。
# ============================================================================
MACHINE_FW=""

if command -v firewall-cmd >/dev/null 2>&1; then
    if systemctl is-active --quiet firewalld 2>/dev/null; then
        MACHINE_FW="firewalld"
        if firewall-cmd --list-ports 2>/dev/null | grep -q "${CHK_PORT}/tcp"; then
            ok "firewalld 运行中，已放行 ${CHK_PORT}/tcp"
        else
            bad "firewalld 在运行，但没放行 ${CHK_PORT}"
            echo "     firewall-cmd --permanent --add-port=${CHK_PORT}/tcp && firewall-cmd --reload"
        fi
    else
        warn "firewalld 已安装但【没有运行】—— 本机没有防火墙在拦，无需处理"
        echo "     （确认一下是不是有意关的：systemctl status firewalld）"
    fi
elif command -v ufw >/dev/null 2>&1; then
    if ufw status 2>/dev/null | grep -q "^Status: active"; then
        MACHINE_FW="ufw"
        ufw status 2>/dev/null | grep -q "${CHK_PORT}" \
            && ok "ufw 运行中，已放行 ${CHK_PORT}" \
            || bad "ufw 在运行，但没放行 ${CHK_PORT}"
    else
        warn "ufw 已安装但没有运行 —— 本机没有防火墙在拦，无需处理"
    fi
else
    warn "没装 firewalld / ufw"
fi

# iptables 兜底：有些机器不用 firewalld，直接写 iptables 规则
if command -v iptables >/dev/null 2>&1; then
    DROPS=$(iptables -L INPUT -n 2>/dev/null | grep -cE "DROP|REJECT" || true)
    if [ "${DROPS:-0}" -gt 0 ]; then
        warn "iptables INPUT 链里有 $DROPS 条 DROP/REJECT 规则，确认没拦 ${CHK_PORT}："
        echo "     iptables -L INPUT -n --line-numbers"
    fi
fi

if [ -z "$MACHINE_FW" ]; then
    echo "  ℹ️  本机没有防火墙在运行 —— ${CHK_PORT} 能否被外网访问，"
    echo "     【完全由云控制台的安全组决定】。"
fi
echo "  ⚠️ 云控制台的安全组是【完全独立的另一套规则】，无论本机有没有防火墙都要检查。"
echo "     只配一层就会出现「端口开着但连不上，且哪儿都不报错」"

# ============================================================================
head_ "6. 部署目录与文件"
# ============================================================================
[ -f "$WORK_DIR/service.sh" ] && ok "service.sh 存在" || bad "service.sh 不存在"
[ -f "$WORK_DIR/crm-server.jar" ] && ok "crm-server.jar 存在（$(du -h "$WORK_DIR/crm-server.jar" | cut -f1)）" \
                                  || bad "crm-server.jar 不存在，还没上传"

if [ -f "$WORK_DIR/service.sh" ] && [ ! -x "$WORK_DIR/service.sh" ]; then
    bad "service.sh 没有可执行权限"
    echo "     chmod +x $WORK_DIR/service.sh"
fi

if [ -f "$ENV_FILE" ]; then
    PERMS=$(stat -c '%a' "$ENV_FILE" 2>/dev/null || echo "?")
    if [ "$PERMS" = "600" ]; then
        ok "env 存在，权限 600"
    else
        bad "env 权限是 $PERMS，应改为 600"
        echo "     chmod 600 $ENV_FILE"
    fi
    # CRLF 检查：密码尾部多个 \r 会报 Access denied，且屏幕上完全看不出来
    if LC_ALL=C grep -q $'\r' "$ENV_FILE" 2>/dev/null; then
        bad "env 含 CRLF 换行，密码尾部会多出不可见的 \\r"
        echo "     dos2unix $ENV_FILE"
    else
        ok "env 换行符正常（LF）"
    fi
else
    bad "env 不存在"
    echo "     umask 077 && cp $WORK_DIR/env.example $ENV_FILE && vim $ENV_FILE"
fi

# ============================================================================
head_ "7. 磁盘"
# ============================================================================
AVAIL=$(df -h "$WORK_DIR" 2>/dev/null | awk 'NR==2 {print $4}')
AVAIL_M=$(df -m "$WORK_DIR" 2>/dev/null | awk 'NR==2 {print $4}')
if [ -n "${AVAIL_M:-}" ] && [ "$AVAIL_M" -lt 2048 ] 2>/dev/null; then
    bad "可用空间只有 $AVAIL，日志滚动上限是 3GB，建议清理"
else
    ok "可用空间 $AVAIL"
fi

# ============================================================================
head_ "8. Nginx（本次部署【不需要】，仅作了解）"
# ============================================================================
if command -v nginx >/dev/null 2>&1; then
    echo "  已安装：$(nginx -v 2>&1)"
    echo "  ⚠️ 后端走 8080，和 Nginx 的 80/443 互不干扰，本次不用改它。"
    echo "     将来部署前端时才需要加站点 + /api 反代到 127.0.0.1:8080。"
else
    echo "  未安装。本次不需要。"
fi

# ============================================================================
head_ "9. 数据库连通性（需要 env）"
#
# ⚠️ 这里只做【连通性和结构】检查，不打印任何密码、不修改任何数据。
# ============================================================================
if [ -f "$ENV_FILE" ] && command -v mysql >/dev/null 2>&1; then
    # shellcheck disable=SC1090
    set -a; . "$ENV_FILE" 2>/dev/null; set +a

    # 地址与端口跟随 env（2026-09-15 起可配），不填则回落到回环。
    # ⚠️ 必须与应用程序用【同一个】地址：否则体检报"可连"、而应用连的是另一个
    #    地址，就会出现"体检全绿但服务起不来"——最难查的那一类。
    DB_H="${DB_HOST:-127.0.0.1}";  DB_P="${DB_PORT:-3306}"
    RD_H="${REDIS_HOST:-127.0.0.1}"; RD_P="${REDIS_PORT:-6379}"
    echo "     连接目标：MySQL ${DB_H}:${DB_P} ／ Redis ${RD_H}:${RD_P}"

    # ---- 先确认 env 不是从 env.example 直接 cp 来的 ----
    #
    # env.example 里的占位符形如 <改成真实密码>。它们【非空】，
    # 所以下面那个 -n 判不出来，会一路走到连接测试、报「MySQL 连不上」——
    # 于是你去查数据库、查端口、查安全组，而真正的原因是这几行压根没填。
    # 这是本脚本最容易被误读的一处输出，所以在它之前先点破。
    _ph=()
    for _v in DB_USERNAME DB_PASSWORD REDIS_PASSWORD MAIL_USERNAME MAIL_PASSWORD \
              OSS_ACCESS_KEY_ID OSS_ACCESS_KEY_SECRET; do
        case "${!_v:-}" in
            '<'*'>') _ph+=("$_v") ;;
        esac
    done
    if [ ${#_ph[@]} -gt 0 ]; then
        bad "env 里这些变量还是 env.example 的【占位符】，没换成真值："
        printf '       %s\n' "${_ph[@]}"
        echo "     ⚠️ 下面的连接测试【必然失败】，而且报错会把你引向「密码错」——"
        echo "        那不是原因，原因是上面这几行没填。"
        echo "     env.example 是模板，不能 cp 过来就用；加机器时请去拷第一台填好的那份"
        echo "     （正确做法见 docs/12-集群部署清单.md 第三节）。"
        echo
    fi

    if [ -n "${DB_USERNAME:-}" ] && [ -n "${DB_PASSWORD:-}" ]; then
        # 用 MYSQL_PWD 传密码，避免出现在 ps 的命令行里被同机用户看到
        if MYSQL_PWD="$DB_PASSWORD" mysql -h "$DB_H" -P "$DB_P" -u "$DB_USERNAME" \
             -N -B -e "SELECT 1" crm >/dev/null 2>&1; then
            ok "MySQL 可连，crm 库存在"

            # ---- 增量脚本是否已执行（对应文档 7.1 的核对清单）----
            echo "     增量脚本执行情况："
            chk() {  # $1=描述  $2=SQL  $3=期望值
                local got
                got=$(MYSQL_PWD="$DB_PASSWORD" mysql -h "$DB_H" -P "$DB_P" -u "$DB_USERNAME" \
                       -N -B -e "$2" crm 2>/dev/null || echo "ERR")
                if [ "$got" = "$3" ]; then echo "       ✅ $1"
                else echo "       ❌ $1（实际：$got）"; fi
            }
            chk "发票多回款改造（payment_id）" \
                "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='crm' AND TABLE_NAME='fin_invoice' AND COLUMN_NAME='payment_id'" "1"
            chk "发票快照列（invoice_title）" \
                "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='crm' AND TABLE_NAME='fin_invoice' AND COLUMN_NAME='invoice_title'" "1"
            chk "公告表补列（sys_notice.update_by）" \
                "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='crm' AND TABLE_NAME='sys_notice' AND COLUMN_NAME='update_by'" "1"
            chk "编号表（sys_numbering_rule_item）" \
                "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='crm' AND TABLE_NAME='sys_numbering_rule_item'" "1"
            chk "详情页菜单两条（报价单/产品）" \
                "SELECT COUNT(*) FROM sys_menu WHERE path IN ('/crm/quotation/:id','/crm/product/:id')" "2"
            chk "发票详情菜单（menu_id 66）" \
                "SELECT COUNT(*) FROM sys_menu WHERE path = '/finance/invoice/:id'" "1"
        else
            bad "连不上 MySQL 的 crm 库（账号密码对？MySQL 在跑？）"
        fi
    else
        warn "env 里 DB_USERNAME / DB_PASSWORD 还空着，跳过"
    fi

    if [ -n "${REDIS_PASSWORD:-}" ] && command -v redis-cli >/dev/null 2>&1; then
        if REDISCLI_AUTH="$REDIS_PASSWORD" redis-cli -h "$RD_H" -p "$RD_P" ping 2>/dev/null | grep -q PONG; then
            NDBS=$(REDISCLI_AUTH="$REDIS_PASSWORD" redis-cli -h "$RD_H" -p "$RD_P" config get databases 2>/dev/null | tail -1)
            if [ -n "${NDBS:-}" ] && [ "$NDBS" -ge 3 ] 2>/dev/null; then
                ok "Redis 可连，有 $NDBS 个库（生产用 database 2，够用）"
            else
                bad "Redis 的 databases 是 ${NDBS:-未知}，生产要用的 database 2 不存在"
            fi
        else
            bad "连不上 Redis（密码对？服务在跑？）"
        fi
    else
        warn "env 里 REDIS_PASSWORD 空着、或没装 redis-cli，跳过 Redis 检查"
    fi
else
    warn "env 还没建好、或服务器上没装 mysql 客户端，跳过数据库检查"
fi

# ============================================================================
echo ""
echo "════════════════════════════════════════════════════"
if [ "$FAIL" -eq 0 ]; then
    echo "  ✅ 全部通过（$PASS 项）—— 可以执行 ./service.sh start"
else
    echo "  ❌ $FAIL 项待处理，$PASS 项通过"
    echo "     处理完再跑一次本脚本确认"
fi
echo "════════════════════════════════════════════════════"
exit 0
