package com.andrerinas.wirelesshelper.strategy

import android.content.Context
import android.util.Log
import com.andrerinas.wirelesshelper.net.WifiNetworkBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val FALLBACK_GATEWAY = "192.168.43.1"
private const val GATEWAY_WAIT_MS = 15_000L
private const val POLL_MS = 150L

class StrategyHotspotTabletGateway(context: Context, scope: CoroutineScope) : BaseStrategy(context, scope) {

    override fun start() {
        Log.i(TAG, "Strategy: Tablet Hotspot Gateway (auto gateway)")
        getStrategyScope().launch(Dispatchers.IO) {
            val deadline = System.currentTimeMillis() + GATEWAY_WAIT_MS
            var gateway: String? = null
            while (System.currentTimeMillis() < deadline) {
                if (WifiNetworkBinding.currentNetwork != null) {
                    gateway = WifiNetworkBinding.ipv4GatewayOrNull()
                    if (gateway != null) break
                }
                delay(POLL_MS)
            }
            val host = gateway ?: FALLBACK_GATEWAY
            if (gateway == null) {
                Log.w(TAG, "Gateway not resolved in time, using fallback $FALLBACK_GATEWAY")
            } else {
                Log.i(TAG, "Using resolved gateway $gateway")
            }
            launchAndroidAuto(host, forceFakeNetwork = false)
        }
    }
}
