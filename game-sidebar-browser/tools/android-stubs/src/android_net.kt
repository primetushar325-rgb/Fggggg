@file:Suppress("PackageDirectoryMismatch", "unused", "UNUSED_PARAMETER", "ClassName")

package android.net

class Uri {
    override fun toString(): String = ""

    companion object {
        @JvmStatic
        fun parse(uriString: String): Uri = Uri()
    }
}

class Network

class NetworkCapabilities {
    fun hasCapability(capability: Int): Boolean = false

    companion object {
        const val NET_CAPABILITY_INTERNET = 12
    }
}

class ConnectivityManager {
    val activeNetwork: Network? = null
    fun getNetworkCapabilities(network: Network): NetworkCapabilities? = null
    fun registerDefaultNetworkCallback(callback: NetworkCallback) {}
    fun unregisterNetworkCallback(callback: NetworkCallback) {}

    abstract class NetworkCallback {
        open fun onAvailable(network: Network) {}
        open fun onLost(network: Network) {}
        open fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {}
    }
}
