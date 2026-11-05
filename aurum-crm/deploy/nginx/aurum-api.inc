# ============================================================================
# /api/ 反向代理片段
#
# 放置位置：/etc/nginx/snippets/aurum-api.inc
# 被 /etc/nginx/conf.d/aurum-crm.conf 里的两个 server 块 include。
#
# ⚠️ 为什么后缀是 .inc 而不是 .conf：
#    conf.d 下有 `include conf.d/*.conf`，.conf 结尾的文件会被当作
#    http 层级的内容解析，而 location 块只能出现在 server 里 —— 那样会
#    直接 [emerg] 起不来。放 snippets 目录 + .inc 后缀，双保险。
# ============================================================================

location /api/ {
    # 两台后端轮询。upstream 定义在 aurum-crm.conf 的最外层（server 之外）——
    # nginx 要求 upstream 必须在 http 层级，写在这里会 [emerg]。
    #
    # ⚠️ 末尾这个斜杠必须保留 —— 它负责把 /api 前缀剥掉：
    #    前端请求 /api/auth/login  →  后端收到 /auth/login
    #    少了斜杠的表现：全部 404，且后端日志里一条记录都没有。
    proxy_pass http://aurum-backend/;

    # upstream 里写了 keepalive 就必须清掉 Connection 头，
    # 否则 keepalive 不生效（每个请求照样新建连接）。
    proxy_http_version 1.1;
    proxy_set_header Connection "";

    # ⚠️ 单节点故障的表现：nginx 的 upstream 默认【没有主动健康检查】，
    #    只有「失败 1 次后摘掉 10 秒」（max_fails=1 fail_timeout=10s）。
    #    所以某台挂掉后会有少量请求先失败一次才被摘掉 —— 想让失败更平滑
    #    要配 proxy_next_upstream，那是另一件事。
    #
    #    另外：若第二台是「本机连接被拒」这种快失败，max_fails 的计数在
    #    每个 30 秒窗口里攒不满，死节点可能【摘不掉】，一直分流。
    #    加机器/停机器前后都要 `ss -ltnp | grep 8080` 确认对方真的活着。

    # 把「这次请求落到哪台后端」写进响应头。
    # 没有它，负载均衡是【看不出来的】—— 两台返回的 body 一模一样，
    # 光看 curl 的结果根本分不清是轮询了还是全打到同一台。
    #   单节点：YOUR_SERVER_IP_2:8080
    #   重试过：127.0.0.1:8080, YOUR_SERVER_IP_2:8080   ← 逗号分隔，说明前者失败过
    # 排查完可以删掉，生产上暴露内网地址略有风险（这里是公网 IP，无所谓）。
    add_header X-Upstream-Addr $upstream_addr always;

    # ⚠️ 【不能用 $host】—— 两台后端在不同的云上（148 京东云 / 121 阿里云），
    #    两家的备案核查都会拦「解析到自己机房但没在自己这儿备案」的域名。
    #    用 $host 会把公网域名原样转发给上游，落到对方那台就被拦（403）。
    #    症状：**这边的机器返回了那边云厂商的拦截页**，且因为轮询，
    #    表现为「一半请求 200、一半 403」。
    #
    #    本文件所在的第一台目前是用 IP 访问的（$host 就是 IP 字面量，
    #    不触发检查），所以还没暴露；但只要哪天用域名访问 148，立刻就会犯。
    #
    #    $proxy_host = proxy_pass 里写的那个名字（upstream 组名），不含点、
    #    不像域名，两家的检查都不触发。后端不依赖 Host 做路由。
    proxy_set_header Host              $proxy_host;

    # 真实 Host 挪到这里保留，应用要拼绝对 URL 时可以从它取。
    proxy_set_header X-Forwarded-Host  $host;
    proxy_set_header X-Real-IP         $remote_addr;
    proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;

    # nginx 默认 1M，而后端配的是 multipart 50MB / 100MB。
    # 不放开的话大文件上传被 nginx 挡在门外回 413，后端日志里什么都看不到。
    client_max_body_size 50m;

    # AI 小助手是 SSE 流式返回，缓冲会让它「憋着一句话都不出」。
    proxy_buffering    off;
    proxy_read_timeout 300s;
}
