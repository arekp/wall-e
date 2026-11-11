package pl.walle.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier

/**
 * Łączy telefon z siecią „WALLE” i kieruje cały ruch aplikacji do niej
 * (bindProcessToNetwork). Dzięki temu Android nie przełączy się na dane komórkowe,
 * choć sieć robota nie ma internetu.
 */
class WifiConnector(context: Context) {
    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private var callback: ConnectivityManager.NetworkCallback? = null

    fun connect(
        ssid: String,
        password: String,
        onResult: (connected: Boolean) -> Unit,
    ) {
        disconnect()
        val specifier = WifiNetworkSpecifier.Builder()
            .setSsid(ssid)
            .apply { if (password.isNotEmpty()) setWpa2Passphrase(password) }
            .build()
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(specifier)
            .build()
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                cm.bindProcessToNetwork(network)
                onResult(true)
            }

            override fun onUnavailable() = onResult(false)

            override fun onLost(network: Network) {
                cm.bindProcessToNetwork(null)
                onResult(false)
            }
        }
        callback = cb
        cm.requestNetwork(request, cb)
    }

    fun disconnect() {
        callback?.let { runCatching { cm.unregisterNetworkCallback(it) } }
        callback = null
        cm.bindProcessToNetwork(null)
    }
}
