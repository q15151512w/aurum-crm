#!/bin/bash
# ============================================================================
#  AurumCRM 后端服务管理脚本
#
#  用法：
#     ./service.sh start      启动（启动前自动备份当前 jar）
#     ./service.sh stop       停止（优雅停机，等不及才强杀）
#     ./service.sh restart    重启
#     ./service.sh rollback   回滚到上一版 jar 并重启
#     ./service.sh status     查看状态
#
#  放到服务器上的位置（和 jar 同级）：
#     /root/CRM/aurum-crm/
#       ├── service.sh          ← 本文件
#       ├── crm-server.jar
#       ├── env                 ← 密钥文件，chmod 600，【不进 git】
#       ├── backup/             ← 历次 jar 备份，保留最近 BACKUP_KEEP 份
#       └── logs/
#           ├── console.out     ← 控制台输出（启动横幅、JVM 级消息）
#           ├── crm-server.log  ← 应用日志（由 Spring Boot 按 100MB 滚动，上限 3GB）
#           └── app.pid
#
#  ⚠️ 本脚本会从【自身所在目录】推导所有路径，所以整个目录搬到别处也能直接用，
#     不需要改脚本、不需要重新打包。
#
#  ⚠️ 密钥不写在脚本里，放在同目录的 env 文件（模板见 env.example）。
# ============================================================================

set -Eeuo pipefail
# ⚠️ 日志里有客户姓名/电话/金额，不能让同机其他用户读到
umask 027

# ============================================================================
#  路径推导 —— 全部基于脚本自身位置，不依赖调用者的当前目录
# ============================================================================
WORK_DIR=$(cd "$(dirname "$0")" && pwd)
APP_NAME=$(basename "$WORK_DIR")
JAR_FILE="$WORK_DIR/crm-server.jar"
ENV_FILE="$WORK_DIR/env"
PID_FILE="$WORK_DIR/app.pid"
LOCK_FILE="$WORK_DIR/.service.lock"
LOG_DIR="$WORK_DIR/logs"
CONSOLE_LOG="$LOG_DIR/console.out"
# jar 备份目录。由 backup_current_jar 维护，rollback 子命令从这里取上一版。
# ⚠️ 每个备份 95M，靠 BACKUP_KEEP 限制个数，别让它无限长
BACKUP_DIR="$WORK_DIR/backup"
# ⚠️ export 出去给 application-prod.yml 里的 ${LOG_DIR:...} 用，
#    这样日志路径和脚本推导出的目录永远一致，不会两边各写一份然后漂移
export LOG_DIR

# ============================================================================
#  可调参数 —— 都可以在 env 文件里覆盖
# ============================================================================
# 优雅停机的等待上限（秒）。
# 120 是算出来的不是拍的：30s（HTTP 在途请求排空）
#                 + 60s（等待 @Scheduled / @Async 线程池收尾）
#                 + 30s（Druid 连接池与 Redisson 的 Netty 关闭余量）
STOP_TIMEOUT="${STOP_TIMEOUT:-120}"
# 健康检查重试次数，每次间隔 2 秒。90 × 2 = 180 秒上限（正常启动约 15~40 秒）
HEALTH_RETRIES="${HEALTH_RETRIES:-90}"
# ⚠️ 探测地址必须是 127.0.0.1 而不是 localhost：
#    localhost 可能先解析到 ::1，会对着一个明明活着的服务报连接失败
HEALTH_URL="${HEALTH_URL:-http://127.0.0.1:8080/actuator/health}"
# 保留几份历史 jar 备份。每个 95M —— 3 份约 285M，按磁盘余量调
BACKUP_KEEP="${BACKUP_KEEP:-3}"

# ============================================================================
#  输出辅助
# ============================================================================
info()  { echo "[$(date '+%F %T')] $*"; }
ok()    { echo "✅ $*"; }
warn()  { echo "⚠️  $*" >&2; }
err()   { echo "❌ $*" >&2; }

# ============================================================================
#  CRLF 换行符检查
#
#  ⚠️⚠️ 这个检查看着小题大做，但它挡掉的是部署里【最难排查】的一类故障：
#
#    ① env 文件带 CRLF → 每个变量值尾部多一个看不见的 \r，
#       于是 DB_PASSWORD 实际是 "xxxx\r"：
#       数据库报 Access denied，而你在编辑器里怎么看那个密码都是对的，
#       排查方向会完全跑偏到"密码是不是被改了"上去。
#
#    ② service.sh 自己带 CRLF → 报 bad interpreter: /bin/bash^M。
#       这个还算好查，因为报错里能看见那个 ^M。
#
#  开发机是 Windows，脚本在那边编辑过就会带上 CRLF，所以每次启动都查一遍。
# ============================================================================
check_crlf() {
    local f="$1"
    [ -f "$f" ] || return 0
    if LC_ALL=C grep -q $'\r' "$f" 2>/dev/null; then
        err "$f 含 CRLF 换行符（Windows 换行），必须转成 LF"
        echo "    转换命令：dos2unix $f    （没装就先 yum install -y dos2unix）"
        echo "    或者：    sed -i 's/\r$//' $f"
        return 1
    fi
    return 0
}

# ============================================================================
#  java 可执行文件定位
#
#  ⚠️ 用绝对路径，不依赖 PATH —— 服务器上可能同时装着别的 JDK，
#     或者 /etc/profile 没生效（非登录 shell 不会读它）。
# ============================================================================
resolve_java() {
    if [ -n "${JAVA_BIN:-}" ] && [ -x "$JAVA_BIN" ]; then
        return 0
    fi
    local c
    # 优先找 /usr/local 下的 JDK 17（这是部署文档里的安装位置）
    for c in /usr/local/jdk-17*/bin/java /usr/local/jdk*/bin/java; do
        if [ -x "$c" ]; then JAVA_BIN="$c"; return 0; fi
    done
    c=$(command -v java 2>/dev/null || true)
    if [ -n "$c" ]; then JAVA_BIN="$c"; return 0; fi
    err "找不到 java，请在 env 文件里显式指定 JAVA_BIN=/usr/local/jdk-17.0.13/bin/java"
    return 1
}

# ============================================================================
#  加载环境变量文件
# ============================================================================
load_env() {
    if [ ! -f "$ENV_FILE" ]; then
        err "找不到环境变量文件：$ENV_FILE"
        echo "    首次部署：cp $WORK_DIR/env.example $ENV_FILE  然后填入真实值"
        return 78
    fi

    local perms
    perms=$(stat -c '%a' "$ENV_FILE" 2>/dev/null || echo "unknown")
    if [ "$perms" != "600" ]; then
        warn "$ENV_FILE 权限是 $perms，建议收紧：chmod 600 $ENV_FILE"
    fi

    # ⚠️ 这个文件是被 source 执行的，不是被解析的 ——
    #    所以每个值都必须用单引号包起来（见 env.example 里的说明）
    set -a
    # shellcheck disable=SC1090
    if ! . "$ENV_FILE"; then
        err "$ENV_FILE 加载失败（语法错误？）"
        return 78
    fi
    set +a
    return 0
}

# ============================================================================
#  必填环境变量校验
#
#  ⚠️ 这一步是整个脚本相对裸 nohup 最大的价值所在。
#     没有它，漏配一个变量的表现是：
#       "Could not resolve placeholder 'OSS_ACCESS_KEY_ID'" + 40 行 Java 堆栈，
#     然后你要在一堆框架日志里找那一行。
#     有了它，就是一句"少了这 3 个变量"。
#
#  ⚠️ 这里拦【两种】漏配，它们的症状完全不同：
#     · 变量为空        → 启动时 Could not resolve placeholder
#     · 值还是 <占位符> → 启动【能过】，然后连库报 Access denied，
#                          错误指向"密码错"，而真正原因是密码没填。
#     第二种更坑人，因为报错把你引向了完全错误的方向。两者都拦。
# ============================================================================
REQUIRED_VARS=(
    SPRING_PROFILES_ACTIVE
    DB_USERNAME
    DB_PASSWORD
    REDIS_PASSWORD
    MAIL_USERNAME
    MAIL_PASSWORD
    OSS_ENDPOINT
    OSS_ACCESS_KEY_ID
    OSS_ACCESS_KEY_SECRET
    OSS_BUCKET_NAME
    AI_MODEL
)

check_required_env() {
    local missing=()
    local placeholder=()
    local v val
    for v in "${REQUIRED_VARS[@]}"; do
        val="${!v:-}"
        if [ -z "$val" ]; then
            missing+=("$v")
            continue
        fi
        # env.example 里的占位符形如 <改成真实密码>。它们【非空】，
        # 所以上面那个 -z 判不出来 —— 放过去的表现是：
        #   校验全绿 → 服务开始启动 → 连 MySQL 报 Access denied for user 'root'
        #   → 错误指向"密码错"，于是你去核对密码，
        #   而真正的原因是：密码那一行还是模板里的占位符，压根没填。
        # 这里提前拦掉，把「一行 Java 堆栈 + 一场误诊」换成一句可行动的话。
        case "$val" in
            '<'*'>') placeholder+=("$v") ;;
        esac
    done

    if [ ${#missing[@]} -gt 0 ]; then
        err "$ENV_FILE 里这些必填变量是空的："
        printf '      %s\n' "${missing[@]}"
        echo "    对照模板补全：$WORK_DIR/env.example"
        return 78
    fi

    if [ ${#placeholder[@]} -gt 0 ]; then
        err "$ENV_FILE 里这些必填变量还是 env.example 的【占位符】，没换成真实值："
        printf '      %s\n' "${placeholder[@]}"
        echo
        echo "    env.example 是模板，不能 cp 过来就直接用。"
        echo "    部署第一台：把上面这几项逐个换成真实凭证。"
        echo "    加第二台及以后：别用模板，去拷第一台填好的那份 ——"
        echo "      scp root@<第一台的地址>:$ENV_FILE $ENV_FILE.new"
        echo "      再只改 DB_HOST / REDIS_HOST 两行。详见 docs/12-集群部署清单.md"
        return 78
    fi

    return 0
}

# ============================================================================
#  PID 解析
#
#  ⚠️⚠️ 绝不只信 PID 文件。PID 会被系统回收，一次异常关机后遗留的脏 PID 文件，
#      可能指向一个完全无关的进程（MySQL 的线程、运维正在编辑的 vim）。
#      真按它 kill，就是杀错进程。
#
#  规则：一个 PID 只有在 /proc/<pid>/cmdline 里确实能看到我们的 jar 路径时，
#        才算数。校验不过就退回去扫 /proc。
#
#  返回：0 = 找到（结果打到 stdout）
#        1 = 确实没在运行
#        2 = 找到多个候选（结果打到 stdout），此时应当【拒绝操作】而不是猜
# ============================================================================
is_our_app() {
    local p="$1"
    [ -r "/proc/$p/cmdline" ] || return 1
    tr '\0' ' ' < "/proc/$p/cmdline" 2>/dev/null | grep -qF "$JAR_FILE"
}

# 列出所有命令行里含本 jar 的进程。
# 优先用 pgrep（procps-ng，CentOS 自带）；万一没有就退回 ps + grep。
# ⚠️ ps 那条里的 grep -v grep 不能省，否则会匹配到自己这条命令。
find_app_pids() {
    if command -v pgrep >/dev/null 2>&1; then
        pgrep -f "$JAR_FILE" 2>/dev/null || true
    else
        ps -ef 2>/dev/null | grep -F "$JAR_FILE" | grep -v grep | awk '{print $2}' || true
    fi
}

resolve_pid() {
    local p matches n

    # 第一优先：PID 文件，但必须通过 /proc 校验
    if [ -s "$PID_FILE" ]; then
        p=$(cat "$PID_FILE" 2>/dev/null || true)
        if [ -n "$p" ] && kill -0 "$p" 2>/dev/null && is_our_app "$p"; then
            echo "$p"
            return 0
        fi
    fi

    # 兜底：扫进程表
    matches=$(find_app_pids)
    n=$(printf '%s\n' "$matches" | grep -c . || true)
    case "$n" in
        1)
            # 只有一个候选，认领它并回写 PID 文件（自愈：PID 文件丢了也能停掉服务）
            printf '%s\n' "$matches" > "$PID_FILE"
            echo "$matches"
            return 0
            ;;
        0)
            return 1
            ;;
        *)
            printf '%s\n' "$matches"
            return 2
            ;;
    esac
}

# 多个候选时打印详情，让人来判断该杀哪个
dump_candidates() {
    local p
    echo "    候选进程（PID / 启动时间 / 命令）：" >&2
    for p in $1; do
        local et
        et=$(ps -o etime= -p "$p" 2>/dev/null | tr -d ' ' || echo "?")
        echo "      $p  已运行 $et" >&2
    done
    echo "    请先用 'ss -ltnp | grep 8080' 确认哪个占着端口，再手动处理。" >&2
    echo "    ⚠️ 脚本在这里【故意不猜】—— 猜错就是杀错进程。" >&2
}

# ============================================================================
#  启动失败时打印现场
# ============================================================================
show_failure() {
    echo ""
    err "启动失败，最后 40 行控制台输出："
    echo "──────────────────────────────────────────────"
    if [ -f "$CONSOLE_LOG" ]; then
        tail -n 40 "$CONSOLE_LOG"
    else
        echo "(没有 $CONSOLE_LOG)"
    fi
    echo "──────────────────────────────────────────────"

    # 把常见失败原因挑出来单独提示，省得在一堆框架日志里翻
    if [ -f "$CONSOLE_LOG" ]; then
        if grep -q "Could not resolve placeholder" "$CONSOLE_LOG" 2>/dev/null; then
            err "→ 有环境变量没解析出来，检查 $ENV_FILE"
        fi
        if grep -q "Access denied for user" "$CONSOLE_LOG" 2>/dev/null; then
            err "→ 数据库账号或密码不对（也检查一下 env 文件是否带 CRLF）"
        fi
        if grep -q "Unable to connect to Redis\|RedisConnectionException" "$CONSOLE_LOG" 2>/dev/null; then
            err "→ 连不上 Redis，检查 Redis 是否启动、密码是否正确"
        fi
        if grep -q "缺少 OSS 配置项" "$CONSOLE_LOG" 2>/dev/null; then
            err "→ OSS 四项配置有缺失，这四项是必填，缺一个就起不来"
        fi
    fi
    echo ""
    echo "完整日志：tail -n 200 $CONSOLE_LOG"
}

# ============================================================================
#  start
# ============================================================================
# ============================================================================
#  jar 备份与回滚
#
#  ⚠️⚠️ 为什么备份发生在 start 里，而不是 stop 里：
#     发版动作是「先 scp 覆盖 jar，再 ./service.sh restart」——
#     等 stop 跑起来的时候，磁盘上那个 jar 已经是【新版】了，旧版早被覆盖没了。
#     真正还能抢救的，是【上一次 start 那一刻】留下的那份副本。
#     所以每次 start 都留一份，按内容去重，回滚时挑「与当前内容不同的最新一份」。
# ============================================================================
backup_current_jar() {
    [ -f "$JAR_FILE" ] || return 0
    if ! command -v md5sum >/dev/null 2>&1; then
        warn "系统没有 md5sum，跳过 jar 备份（rollback 将不可用）"
        return 0
    fi

    mkdir -p "$BACKUP_DIR"

    local sum
    sum=$(md5sum "$JAR_FILE" | awk '{print $1}')

    # 内容和上一次备份相同就跳过 —— 否则每次 restart 都白堆一个 95M 的副本
    if [ -f "$BACKUP_DIR/.last.md5" ] &&
       [ "$(cat "$BACKUP_DIR/.last.md5" 2>/dev/null || true)" = "$sum" ]; then
        return 0
    fi

    local dest="$BACKUP_DIR/crm-server.jar.$(date +%F_%H%M%S)"
    # ⚠️ 同一秒内连续备份会撞名，撞上就加序号。
    #    不处理的话 cp 会【静默覆盖】掉刚存的那一份 —— 回滚时少一个可选项，
    #    而且没有任何提示。
    local i=1
    while [ -e "$dest" ]; do
        dest="$BACKUP_DIR/crm-server.jar.$(date +%F_%H%M%S).$i"
        i=$((i + 1))
    done
    # ⚠️ 用 cp 而不是 mv：旧进程可能还占着这个路径，搬走它会把在跑的实例搞坏
    cp -a "$JAR_FILE" "$dest"
    echo "$sum" > "$BACKUP_DIR/.last.md5"
    info "已备份当前 jar → $(basename "$dest")"
    prune_backups
}

# 只保留最近 BACKUP_KEEP 份。不限个数的话，95M × N 迟早把磁盘吃满
prune_backups() {
    local keep="${BACKUP_KEEP:-3}" n
    n=$(ls -1 "$BACKUP_DIR"/crm-server.jar.* 2>/dev/null | wc -l || true)
    if [ "${n:-0}" -le "$keep" ]; then
        return 0
    fi
    # -t = 按修改时间倒序，最新的在前；tail 跳过前 keep 个，剩下的删掉
    ls -1t "$BACKUP_DIR"/crm-server.jar.* 2>/dev/null \
        | tail -n +$((keep + 1)) \
        | while IFS= read -r f; do rm -f "$f"; done
    info "备份已超过 $keep 份，清理了较旧的"
}

do_start() {
    local rc=0 pid
    pid=$(resolve_pid) || rc=$?

    if [ "$rc" = 0 ]; then
        ok "$APP_NAME 已在运行（PID $pid），不重复启动"
        return 0
    fi
    if [ "$rc" = 2 ]; then
        err "$APP_NAME 发现多个候选进程，拒绝启动："
        dump_candidates "$pid"
        return 1
    fi

    # --- 前置检查 ---
    check_crlf "$0" || return 78
    check_crlf "$ENV_FILE" || return 78

    if [ ! -f "$JAR_FILE" ]; then
        err "找不到 jar：$JAR_FILE"
        echo "    打包：mvn -DskipTests clean package"
        echo "    上传：把 crm-server/target/crm-server.jar 放到 $WORK_DIR/"
        return 78
    fi

    load_env || return 78
    check_required_env || return 78
    resolve_java || return 78

    # 备份当前 jar（内容没变会跳过）。必须在 JVM 拉起来【之前】，
    # 因为这一步存的就是"即将被启动的这一版"，回滚时拿它当锚点
    backup_current_jar

    # JAVA_OPTS 默认值：堆大小按机器内存调整，务必给同机的 MySQL/Redis 留余地
    # ⚠️ 不设 -Xmx 时 JVM 默认吃物理内存的 1/4，内存紧张时内核 OOM killer
    #    可能挑 MySQL 下手，一次部署就变成一次数据库宕机。
    : "${JAVA_OPTS:--Xms512m -Xmx512m -XX:MaxMetaspaceSize=256m -Duser.timezone=Asia/Shanghai -Dfile.encoding=UTF-8 -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=$LOG_DIR/}"

    mkdir -p "$LOG_DIR"

    # 保留上一轮的控制台输出（只留一代），用来抓 Logback 初始化之前的输出：
    # 启动横幅、profile 行、JVM 级消息（OOM、Killed）
    if [ -f "$CONSOLE_LOG" ]; then
        mv -f "$CONSOLE_LOG" "$CONSOLE_LOG.1"
    fi

    info "启动 $APP_NAME"
    info "  java     : $JAVA_BIN"
    info "  jar      : $JAR_FILE"
    info "  profile  : ${SPRING_PROFILES_ACTIVE:-}"
    info "  日志     : $LOG_DIR"

    # ⚠️ 先 cd 到工作目录：脚本可能被从任意目录调用，
    #    相对路径的 jar 或日志会当场崩掉
    cd "$WORK_DIR"

    # ⚠️ $JAVA_OPTS 故意不加引号，靠词分割拆成多个参数；
    #    $! 在这一句里就是 JVM 的 PID —— 前提是中间不套 sh -c / setsid 包装，
    #    套了 $! 就变成子 shell 的 PID，后面全错。
    # ⚠️⚠️ 末尾的 9>&- 不是可有可无的，删掉会锁死整个脚本：
    #    并发锁挂在 fd 9 上，而子进程【默认继承】父进程已打开的 fd。
    #    flock 的锁是绑定在"打开文件描述"上的，被继承后由父子共享 ——
    #    于是应用只要活着，锁就一直被攥着，之后每一次 stop/restart/status
    #    都会报"另一个操作正在进行中"，而且因为 stop 也走这条路径，
    #    你将【再也无法用脚本停掉服务】。9>&- 让 java 这个子进程不继承它。
    #
    # shellcheck disable=SC2086
    nohup "$JAVA_BIN" $JAVA_OPTS -jar "$JAR_FILE" >>"$CONSOLE_LOG" 2>&1 9>&- &
    local newpid=$!
    echo "$newpid" > "$PID_FILE"

    info "进程已拉起（PID $newpid），等待健康检查..."

    # --- 健康检查：进程活着 → 端口能连 → /actuator/health 返回 200 ---
    local i code
    for i in $(seq 1 "$HEALTH_RETRIES"); do
        # 先确认进程还活着：JVM 起崩了 2 秒内就能发现，
        # 不用干等满 180 秒才报错
        if ! kill -0 "$newpid" 2>/dev/null; then
            rm -f "$PID_FILE"
            err "进程在启动过程中退出了（第 ${i} 次探测时发现）"
            show_failure
            return 1
        fi

        code=$(curl -fsS -m 3 -o /dev/null -w '%{http_code}' "$HEALTH_URL" 2>/dev/null || true)
        if [ "$code" = "200" ]; then
            ok "$APP_NAME 启动成功（约 $((i * 2)) 秒）"
            echo "    健康检查：$HEALTH_URL → 200"
            echo "    查看日志：tail -f $LOG_DIR/crm-server.log"
            return 0
        fi

        sleep 2
    done

    rm -f "$PID_FILE"
    err "健康检查 $((HEALTH_RETRIES * 2)) 秒内始终没通过，判定启动失败"
    # ⚠️ 失败不自动重试、不自动回滚：重启循环会把第 1 个错误淹没在第 5 个里，
    #    反复起停的进程也让人没法看日志。响亮地失败，让人来看现场。
    kill -TERM "$newpid" 2>/dev/null || true
    show_failure
    return 1
}

# ============================================================================
#  stop
#
#  ⚠️⚠️ 为什么必须优雅停机、不能用 kill -9 一把梭：
#
#    SIGKILL 是无法被 JVM 处理的 —— 它没有机会做任何收尾。对本项目具体意味着：
#
#    ① @Async 正在写的【操作日志】被截断，留下写了一半的脏数据；
#    ② @Scheduled 的【提醒任务】（每天 03:05）被杀在扫描中途，
#       可能一半通知发了、一半没发，而且没有任何回滚；
#    ③ Redisson 的【分布式锁】来不及释放。客户领取用的
#       crm:customer:claim:* 锁会一直挂到 TTL 过期，这期间该功能是锁死的；
#    ④ 正在上传的附件直接中断。
#
#    所以这里是 SIGTERM → 每秒轮询 → 超时后才 SIGKILL。
#    配合 application-prod.yml 里的 server.shutdown=graceful
#    和 spring.task.*.shutdown.await-termination 才完整生效。
#
#    ⚠️ 顺带一条纪律：不要在 03:00~03:15 之间发版（提醒任务的执行窗口）。
# ============================================================================
do_stop() {
    local rc=0 pid
    pid=$(resolve_pid) || rc=$?

    case "$rc" in
        1)
            info "$APP_NAME 未在运行"
            rm -f "$PID_FILE"
            return 0
            ;;
        2)
            err "$APP_NAME 发现多个候选进程，拒绝操作："
            dump_candidates "$pid"
            return 1
            ;;
    esac

    info "正在停止 $APP_NAME（PID $pid），最多等 ${STOP_TIMEOUT}s..."
    kill -TERM "$pid" 2>/dev/null || true

    local i
    for i in $(seq 1 "$STOP_TIMEOUT"); do
        if ! kill -0 "$pid" 2>/dev/null; then
            rm -f "$PID_FILE"
            ok "$APP_NAME 已优雅停止（用时 ${i}s）"
            return 0
        fi
        sleep 1
    done

    # 走到这里说明卡住了。可能是某个请求或定时任务不放手。
    warn "${STOP_TIMEOUT}s 内没有退出，强制 SIGKILL"
    kill -KILL "$pid" 2>/dev/null || true
    sleep 2

    if kill -0 "$pid" 2>/dev/null; then
        err "无法终止进程 $pid，请手动处理"
        return 1
    fi
    rm -f "$PID_FILE"
    warn "$APP_NAME 已被强制终止（可能留下了未完成的异步任务）"
    return 0
}

# ============================================================================
#  restart
# ============================================================================
do_restart() {
    do_stop || return 1
    # 明确等端口释放。TIME_WAIT 状态下立刻重启会报 Address already in use
    sleep 2
    do_start
}

# ============================================================================
#  rollback —— 回滚到上一版 jar
#
#  选取规则：备份目录里【内容与当前 jar 不同】且【最新】的那一份。
#  因为 start 时会先备份当前 jar，所以跑过一次 start 之后，
#  最新那份备份就是"现在这一版"，它的上一份才是回滚目标。
#
#  ⚠️ 回滚是双向的：备份本身不动（只是复制回 jar 位），
#     所以再执行一次 rollback 可以回到原来那版（相当于"前进"）。
# ============================================================================
do_rollback() {
    if [ ! -d "$BACKUP_DIR" ]; then
        err "没有备份目录：$BACKUP_DIR"
        echo "    第一次 ./service.sh start 之后才会产生备份"
        return 1
    fi

    local cur_sum="" f target="" n=0
    if [ -f "$JAR_FILE" ]; then
        cur_sum=$(md5sum "$JAR_FILE" 2>/dev/null | awk '{print $1}' || true)
    fi

    while IFS= read -r f; do
        [ -n "$f" ] || continue
        n=$((n + 1))
        # 跳过和当前 jar 内容一致的那份 —— 那就是现在正在跑的这一版
        if [ -n "$cur_sum" ] &&
           [ "$(md5sum "$f" 2>/dev/null | awk '{print $1}' || true)" = "$cur_sum" ]; then
            continue
        fi
        target="$f"
        break
    done < <(ls -1t "$BACKUP_DIR"/crm-server.jar.* 2>/dev/null || true)

    if [ -z "$target" ]; then
        err "没有可回滚的备份"
        if [ "$n" -eq 0 ]; then
            echo "    备份目录是空的：$BACKUP_DIR"
        else
            echo "    备份目录里只有当前这一版，没有更早的版本"
        fi
        echo "    查看现状：ls -lt $BACKUP_DIR"
        return 1
    fi

    info "回滚：crm-server.jar ← $(basename "$target")"
    info "  当前 md5: ${cur_sum:-（jar 不存在）}"
    info "  目标 md5: $(md5sum "$target" | awk '{print $1}')"

    cp -a "$target" "$JAR_FILE"
    # 同步去重标记，免得下一次 start 把刚回滚的版本又存成一份重复备份
    md5sum "$JAR_FILE" | awk '{print $1}' > "$BACKUP_DIR/.last.md5"

    do_restart
}

# ============================================================================
#  status
# ============================================================================
do_status() {
    local rc=0 pid
    pid=$(resolve_pid) || rc=$?

    echo "──────────────────────────────────────────────"
    echo "  应用目录 : $WORK_DIR"
    echo "  jar      : $JAR_FILE"

    case "$rc" in
        1)
            echo "  运行状态 : ❌ 未运行"
            echo "──────────────────────────────────────────────"
            return 1
            ;;
        2)
            echo "  运行状态 : ⚠️  多个候选进程"
            dump_candidates "$pid"
            echo "──────────────────────────────────────────────"
            return 1
            ;;
    esac

    local et
    et=$(ps -o etime= -p "$pid" 2>/dev/null | tr -d ' ' || echo "?")

    echo "  运行状态 : ✅ 运行中"
    echo "  PID      : $pid"
    echo "  已运行   : $et"

    # 端口监听情况
    local listen
    listen=$(ss -ltnp 2>/dev/null | grep -F ":8080" | head -3 || true)
    if [ -n "$listen" ]; then
        echo "  端口监听 : "
        printf '      %s\n' "$listen"
    else
        echo "  端口监听 : ⚠️  没看到 8080 在监听"
    fi

    # 健康检查
    local body
    body=$(curl -fsS -m 5 "$HEALTH_URL" 2>/dev/null || true)
    if [ -n "$body" ]; then
        echo "  健康检查 : $body"
    else
        echo "  健康检查 : ❌ $HEALTH_URL 无响应"
    fi

    # 磁盘余量：日志写满磁盘是这类部署最常见的"突然挂了"
    local avail
    avail=$(df -h "$LOG_DIR" 2>/dev/null | awk 'NR==2 {print $4}' || true)
    [ -n "$avail" ] && echo "  磁盘余量 : $avail"

    # jar 备份：回滚前先看这里，确认有可回的目标
    local bks
    bks=$(ls -1t "$BACKUP_DIR"/crm-server.jar.* 2>/dev/null | head -"${BACKUP_KEEP:-3}" || true)
    if [ -n "$bks" ]; then
        echo "  jar 备份 : "
        local f
        while IFS= read -r f; do
            printf '      %s  %s\n' "$(basename "$f")" "$(du -h "$f" 2>/dev/null | cut -f1)"
        done <<< "$bks"
    else
        echo "  jar 备份 : 无（执行过一次 start 后才会产生）"
    fi

    echo "──────────────────────────────────────────────"
    return 0
}

# ============================================================================
#  入口
# ============================================================================
usage() {
    cat <<EOF
用法：$0 {start|stop|restart|rollback|status}

  start     启动服务（启动前自动备份当前 jar），并等待健康检查通过
  stop      停止服务（先 SIGTERM 优雅停机，${STOP_TIMEOUT}s 后才强杀）
  restart   重启
  rollback  回滚到上一版 jar 并重启（备份保留最近 ${BACKUP_KEEP} 份）
  status    查看 PID / 运行时长 / 端口监听 / 健康检查 / 磁盘余量 / jar 备份

部署目录：$WORK_DIR
环境变量：$ENV_FILE
日志目录：$LOG_DIR
备份目录：$BACKUP_DIR
EOF
}

main() {
    local cmd="${1:-}"
    [ -n "$cmd" ] || { usage; exit 1; }

    # ⚠️ 并发锁：防止 restart 和 stop 交叠时，
    #    stop 把 start 刚拉起来的进程当成旧进程杀掉
    #
    # ⚠️ flock 必须单独判存在性。不判的话，在没装 flock 的系统上
    #    `if ! flock -n 9` 会因为 "command not found" 而走进分支，
    #    报出「另一个操作正在进行中」—— 把"工具没装"误报成"有并发冲突"，
    #    然后你会去反复重试、去查是不是有残留进程，方向完全跑偏。
    #    （CentOS 上 flock 属于 util-linux，默认自带，正常不会触发这个分支）
    if command -v flock >/dev/null 2>&1; then
        exec 9>"$LOCK_FILE"
        if ! flock -n 9; then
            err "另一个 $APP_NAME 操作正在进行中，请稍后重试"
            echo "    先确认是不是真的还有操作在跑：ps -ef | grep -F service.sh | grep -v grep"
            echo "    如果没有任何 service.sh 在跑，说明锁被【应用进程继承了】"
            echo "    （旧版脚本的 bug，已修复）。应急解锁："
            echo "        rm -f $LOCK_FILE   然后重试"
            exit 1
        fi
    else
        warn "系统没有 flock 命令，本次跳过并发保护（不影响功能）"
    fi

    case "$cmd" in
        start)   do_start ;;
        stop)    do_stop ;;
        restart)  do_restart ;;
        rollback) do_rollback ;;
        status)   do_status ;;
        -h|--help|help) usage ;;
        *)       usage; exit 1 ;;
    esac
    exit $?
}

main "$@"
