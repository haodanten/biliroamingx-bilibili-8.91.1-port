package app.revanced.patches.bilibili.misc.protobuf.fingerprints

import app.revanced.patcher.fingerprint.MethodFingerprint

/**
 * 定位 Moss gRPC 的 OkHttp 拦截器。
 *
 * 背景: 旧版把"请求侧篡改"(区域限制的客户端伪装 / authorization / 网络类型固定) 挂在
 * Cronet 的 `org.chromium.net.impl.BidirectionalStreamBuilderImpl` 上。
 * **B站 8.91.1 已彻底移除 Cronet**, 该 hook 点不存在。
 *
 * 新宿主里 Moss 的 gRPC 走 OkHttp, 统一由这个拦截器补齐 `x-bili-*-bin` 系列元数据头:
 *   `Lqw2/a;->intercept(Lokhttp3/Interceptor$Chain;)Lokhttp3/Response;`
 * 它依次写入 x-bili-metadata-bin / authorization / x-bili-device-bin / x-bili-network-bin /
 * x-bili-restriction-bin / x-bili-locale-bin / x-bili-exps-bin / buvid,
 * 最后 `Request.newBuilder().headers(...).build()` 交给 `Chain.proceed`。
 *
 * 因此这里按"同时含 metadata 与 network 两个头名"锚定, 精确命中该拦截器。
 */
object MossOkHttpInterceptorFingerprint : MethodFingerprint(
    returnType = "Lokhttp3/Response;",
    parameters = listOf("Lokhttp3/Interceptor\$Chain;"),
    strings = listOf("x-bili-metadata-bin", "x-bili-network-bin")
)
