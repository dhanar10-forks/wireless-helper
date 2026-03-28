package com.andrerinas.wirelesshelper.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log
import kotlin.jvm.Synchronized
import java.net.Inet4Address

object WifiNetworkBinding {

    private const val TAG = "HUREV_WIFI_BIND"

    @Volatile
    var currentNetwork: Network? = null
        private set

    @Volatile
    private var cachedIpv4Gateway: String? = null

    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    fun ipv4GatewayOrNull(): String? {
        val net = currentNetwork ?: return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            connectivityManager?.getLinkProperties(net)?.let { props ->
                parseDefaultIpv4Gateway(props)?.let { return it }
            }
        }
        return cachedIpv4Gateway
    }

    @Synchronized
    fun start(context: Context) {
        if (networkCallback != null) return

        val app = context.applicationContext
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivityManager = cm

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.i(TAG, "Wi-Fi (no internet) network available: $network")
                currentNetwork = network
                bindProcessToNetwork(cm, network)
            }

            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                if (network == currentNetwork) {
                    val gw = parseDefaultIpv4Gateway(linkProperties)
                    if (gw != null) {
                        cachedIpv4Gateway = gw
                        Log.i(TAG, "IPv4 gateway from link properties: $gw")
                    }
                }
            }

            override fun onLost(network: Network) {
                if (currentNetwork == network) {
                    Log.i(TAG, "Wi-Fi binding lost for $network")
                    currentNetwork = null
                    cachedIpv4Gateway = null
                    bindProcessToNetwork(cm, null)
                }
            }
        }
        networkCallback = callback
        try {
            cm.requestNetwork(request, callback)
        } catch (e: SecurityException) {
            Log.e(TAG, "requestNetwork failed", e)
            networkCallback = null
            connectivityManager = null
        }
    }

    @Synchronized
    fun stop(context: Context) {
        val cm = connectivityManager
            ?: context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val cb = networkCallback
        if (cb != null && cm != null) {
            try {
                cm.unregisterNetworkCallback(cb)
            } catch (e: Exception) {
                Log.w(TAG, "unregisterNetworkCallback", e)
            }
        }
        networkCallback = null
        if (cm != null) {
            bindProcessToNetwork(cm, null)
        }
        currentNetwork = null
        cachedIpv4Gateway = null
        connectivityManager = null
    }

    private fun parseDefaultIpv4Gateway(linkProperties: LinkProperties): String? {
        for (route in linkProperties.routes) {
            if (route.isDefaultRoute && route.hasGateway()) {
                val gw = route.gateway
                if (gw is Inet4Address) {
                    return gw.hostAddress
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            linkProperties.dhcpServerAddress?.hostAddress?.let { return it }
        }
        return null
    }

    @Suppress("DEPRECATION")
    private fun bindProcessToNetwork(cm: ConnectivityManager, network: Network?) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                cm.bindProcessToNetwork(network)
            } else {
                ConnectivityManager.setProcessDefaultNetwork(network)
            }
        } catch (e: Exception) {
            Log.w(TAG, "bindProcessToNetwork", e)
        }
    }
}
