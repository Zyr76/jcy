/*
 * 囧次元 1.0.11 取证脚本（Frida）
 *
 * 目标：把原版 App 里【解密后的】请求体/响应体/设备指纹原文打印出来。
 * 用法：
 *   frida -U -f com.dskiow.cjsq.p1 -l jcy-hook.js --no-pause
 * 输出：
 *   终端 console + /sdcard/jcy-hook.log（官方 App 有 MANAGE_EXTERNAL_STORAGE 权限）
 *
 * 抓取动作：打开 App → 进任意剧集 → 点播放 → 等出画面 → 退出
 */

var TAG = "[JCY]";
var LOG_PATH = "/sdcard/jcy-hook.log";

// ---------- 工具 ----------
function toStr(jbytes) {
    try {
        var arr = Java.array('byte', jbytes);
        var s = "";
        for (var i = 0; i < arr.length; i++) s += String.fromCharCode(arr[i] & 0xff);
        try { return decodeURIComponent(escape(s)); } catch (e) { return s; }
    } catch (e) { return "<decode-fail " + e + ">"; }
}

var fileStream = null;
function initLogFile() {
    try {
        var FOS = Java.use("java.io.FileOutputStream");
        fileStream = FOS.$new(LOG_PATH, true); // append
    } catch (e) {
        console.log(TAG + " 无法写入 " + LOG_PATH + "（仅终端输出）: " + e);
    }
}
function log(s) {
    console.log(s);
    if (fileStream !== null) {
        try {
            var b = Java.use("java.lang.String").$new(s + "\n").getBytes("UTF-8");
            fileStream.write(b);
            fileStream.flush();
        } catch (e) { }
    }
}

Java.perform(function () {
    initLogFile();
    log("================ JCY HOOK START ================");

    // ---------- 1. 禁用 zqguard 反调试/阻断 ----------
    try {
        var SG = Java.use("com.android.support.runtime.SupportGuard");
        SG.runAll.overloads.forEach(function (m) {
            m.implementation = function () { return 0; };
        });
        log(TAG + " [ok] SupportGuard.runAll -> 0");
    } catch (e) { log(TAG + " [!] SupportGuard: " + e); }

    try {
        var SL = Java.use("com.android.support.runtime.SupportLoader");
        SL.onThreatDetected.implementation = function (code, src) {
            log(TAG + " [zq] threat ignored code=" + code + " src=" + src);
        };
        log(TAG + " [ok] SupportLoader.onThreatDetected muted");
    } catch (e) { log(TAG + " [!] SupportLoader: " + e); }

    // 阻止 zqguard 在 Application 早期加载 native 库做校验
    try {
        var SIP = Java.use("com.android.support.runtime.SupportInitProvider");
        SIP.onCreate.implementation = function () {
            log(TAG + " [ok] SupportInitProvider.onCreate -> true (skip native init)");
            return true;
        };
    } catch (e) { log(TAG + " [!] SupportInitProvider: " + e); }

    // ---------- 2. 请求/响应明文 ----------
    try {
        var N = Java.use("com.yy.myuko.feature.encryptionsdk.bridge.NativeSdk_androidKt");

        N.nativeSdkEncryptV3.overload('[B', '[B', 'J', 'I').implementation = function (secret, plain, ts, nodes) {
            var r = this.nativeSdkEncryptV3(secret, plain, ts, nodes);
            log(TAG + " REQ  > " + toStr(plain));
            return r;
        };

        try {
            N.nativeSdkEncryptV3WithCode.overload('[B', '[B', '[B', 'J').implementation = function (secret, plain, code, ts) {
                var r = this.nativeSdkEncryptV3WithCode(secret, plain, code, ts);
                log(TAG + " REQc > " + toStr(plain));
                return r;
            };
        } catch (e) { }

        N.nativeSdkDecryptV3.overload('[B', '[B').implementation = function (secret, cipher) {
            var r = this.nativeSdkDecryptV3(secret, cipher);
            log(TAG + " RESP < " + toStr(r));
            return r;
        };

        log(TAG + " [ok] NativeSdk_androidKt 明文钩子已挂");
    } catch (e) { log(TAG + " [!] NativeSdk_androidKt: " + e); }

    // ---------- 3. 设备指纹原文（最关键） ----------
    try {
        var S = Java.use("com.yy.myuko.feature.devicefingerprint.DeviceFingerprintSigner");
        S.sign.overloads.forEach(function (m) {
            m.implementation = function () {
                var args = Array.prototype.slice.call(arguments);
                var r = m.call.apply(m, [this].concat(args));
                try {
                    if (args.length > 0 && args[0] !== null) {
                        log(TAG + " DEVCTX.obj = " + args[0].toString());
                    }
                } catch (e) { }
                try {
                    log(TAG + " FPHDR = " + r.getHeaderValue());
                    log(TAG + " FPSIGN = " + r.getSignature());
                } catch (e) { }
                return r;
            };
        });
        log(TAG + " [ok] DeviceFingerprintSigner.sign x" + S.sign.overloads.length);
        log(TAG + " [ok] DeviceFingerprintSigner.sign 已挂");
    } catch (e) { log(TAG + " [!] DeviceFingerprintSigner: " + e); }

    // 备选：直接 hook DeviceContext 构造，确保能拿到字段值
    try {
        var DCB = Java.use("com.yy.myuko.feature.devicefingerprint.DeviceContextBuilder");
        var mBuild = DCB["build$device_fingerprint_release"];
        mBuild.overload('long', 'java.lang.String').implementation = function (ts, devId) {
            var r = mBuild.call(this, ts, devId);
            log(TAG + " BUILD ts=" + ts + " devId=" + devId + " => " + r.toString());
            return r;
        };
        log(TAG + " [ok] DeviceContextBuilder 已挂");
    } catch (e) { log(TAG + " [!] DeviceContextBuilder: " + e); }

    // ---------- 4. 请求签名（校验用，可选） ----------
    try {
        var AS = Java.use("com.yy.myuko.api.secure.ApiSignatureSigner");
        AS.sign.overload('long', 'java.lang.String', 'java.lang.String', 'java.lang.String').implementation = function (ts, nonce, appId, secret) {
            var r = this.sign(ts, nonce, appId, secret);
            log(TAG + " SIGN ts=" + ts + " nonce=" + nonce + " appId=" + appId + " => " + r);
            return r;
        };
        log(TAG + " [ok] ApiSignatureSigner.sign 已挂");
    } catch (e) { log(TAG + " [!] ApiSignatureSigner: " + e); }

    // ---------- 5. 抓 x-form（上游请求头） ----------
    // 官方用 Ktor，上游请求会带 x-form；这里 hook OkHttp/系统层不易，
    // 改为在 sign 响应里看（RESP 里就有 headers.x-form）
    try {
        var OkHttpClient = Java.use("okhttp3.OkHttpClient");
        log(TAG + " [ok] okhttp 类存在（可选）");
    } catch (e) { }

    log("================ JCY HOOK READY ================");
});
