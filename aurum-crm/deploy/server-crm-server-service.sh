#!/bin/bash
#
# ============================================================================
# ⚠️ 2026-09-15 修订说明（改动见下面标 [FIX] 的两处）
#
# 这个脚本原本【读了 env 文件却从没生效】，根因是两个独立的 bug：
#
#   [FIX-1] 变量定义顺序反了
#             ENV_FILE="$WORK_DIR/env"       ← 此时 WORK_DIR 还是空串
#             WORK_DIR=$(cd $(dirname $0); pwd)   ← 下一行才定义
#           于是 ENV_FILE 实际是 "/env"，不是 "<部署目录>/env"。
#           症状：就算后面 source 了它，也是去读根目录的 /env（不存在）。
#
#   [FIX-2] ENV_FILE 定义了但【从未被 source】
#           它被算出来之后就再没人用过。nohup 启动时环境里什么都没有，
#           于是 application-prod.yml 里的 ${DB_USERNAME} 被当成【字面量】
#           原样发给 MySQL，报：
#               Access denied for user '${DB_USERNAME}'@'localhost'
#           ⚠️ 这句报错极具误导性 —— 看着像「密码错了」，
#              实际是「配置压根没读进去」，人会跑去核对密码白折腾一轮。
#
# 现在 start() 里加了 `set -a; . "$ENV_FILE"; set +a`：
#   set -a  = 之后定义的所有变量自动 export，子进程（java）才看得到
#
# ---------------------------------------------------------------------------
# ⚠️ 还有两处【没改】，需要你自己判断：
#
#   ① SPRING_PROFILE=-Dspring.profiles.active=dev
#      dev 不是 prod。dev 会开 debug 日志、开 Swagger(/doc.html)、
#      且数据库地址由 application-dev.yml 里的硬编码决定，**不看 env**。
#      也就是说：只要还用 dev，上面 [FIX-2] 修好的 env 依然不会生效。
#      生产环境应该是：SPRING_PROFILE=-Dspring.profiles.active=prod
#
#   ② stop() 用的是 `kill -9`
#      强杀不走 JVM 的关闭钩子，会：
#        · 截断 @Async 正在写的操作日志
#        · 把定时任务杀死在扫描中途（一半通知已发一半没发）
#        · 跳过 Redisson 锁释放，分布式锁一直挂到 TTL 过期
#      建议换成 kill -TERM + 轮询等待，超过 120 秒再 kill -9。
#
#   ③ 项目里已经有一套更完整的脚本：/root/CRM/aurum-crm/service.sh
#      （会读 env、校验必填变量、拦占位符、优雅停机、健康检查、回滚）
#      建议统一用它，别维护两份。
# ============================================================================

ADATE=`date +%Y%m%d%H%M%S`
WORK_DIR=$(cd $(dirname $0); pwd)     # [FIX-1] 提到前面：ENV_FILE 依赖它
ENV_FILE="$WORK_DIR/env"
APP_NAME=$(basename $WORK_DIR)
APP_PATH=${WORK_DIR#'/root/crm-server/'}
APP_LOG=$APP_PATH/logs
JAR_FILE=$WORK_DIR/crm-server.jar
#SPRING_PORT=--server.port=9004
SPRING_PROFILE=-Dspring.profiles.active=dev

if [[ ! -f $JAR_FILE ]]; then
  echo "$JAR_FILE not exists"
  exit 1
fi
if [[ ! -d "$APP_LOG" ]]; then
  mkdir -p $APP_LOG
fi
if [[ ! -d "$APP_LOG/gc-log" ]]; then
  mkdir -p $APP_LOG/gc-log
fi

GC_LOG_PATH=$APP_LOG/gc-log/GC-$ADATE.log

JVM_OPTS="-Dname=$APP_NAME -Xms256M -Xmx256M"

pid=0
function start()
{
  checkpid
  if [ ! -n "$pid" ]; then
    # [FIX-2] 加载环境变量。set -a 让后续赋值自动 export，java 子进程才看得到。
    if [ -f "$ENV_FILE" ]; then
      set -a
      . "$ENV_FILE"
      set +a
    else
      echo "⚠️  找不到 $ENV_FILE —— 配置不会生效，启动大概率会失败"
      echo "    （application-prod.yml 里的 \${DB_USERNAME} 会被当字面量发给 MySQL）"
    fi

    nohup  java -jar $JVM_OPTS $SPRING_PROFILE $JAR_FILE $SPRING_PORT > $APP_LOG/crm.log 2>&1 &
    echo "---------------------------------"
    echo "启动完成，按CTRL+C退出日志界面即可>>>>>"
    echo "---------------------------------"
    sleep 2s
  fi
  status
}

function status()
{
  checkpid
  if [ ! -n "$pid" ]; then
    echo "$APP_NAME not runing"
  else
    echo "$APP_NAME runing PID: $pid"
  fi
}

function checkpid()
{
  pid=`ps -ef |grep $JAR_FILE |grep -v grep |awk '{print $2}'`
}

function stop()
{
  checkpid
  if [ ! -n "$pid" ]; then
    echo "$APP_NAME not runing"
  else
    echo "$APP_NAME stop..."
    kill -9 $pid
  fi
}

function restart()
{
  stop
  sleep 1s
  start
}

case $1 in
  start)
    start
    ;;
  stop)
    stop
    ;;
  restart)
    restart
    ;;
  status)
    status
    ;;
  *)
    echo "Usage: $0 start|stop|restart|status"
    ;;
esac
