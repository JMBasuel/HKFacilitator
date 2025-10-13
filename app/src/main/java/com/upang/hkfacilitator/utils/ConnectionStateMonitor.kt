package com.upang.hkfacilitator.utils

import android.content.Context
import android.net.ConnectivityManager.NetworkCallback
import android.net.*
import android.os.*
import java.util.concurrent.Executor

class ConnectionStateMonitor(
    private val listener: ConnectionStateListener,
    private val executor: Executor
) : NetworkCallback() {

    private var isConnected: Boolean = false
    private val mainHandler = Handler(Looper.getMainLooper())

    private val networkRequest: NetworkRequest = NetworkRequest.Builder()
        .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        .build()

    fun enable(context: Context) {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivityManager.registerNetworkCallback(networkRequest, this, mainHandler)
    }

    fun disable(context: Context) {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivityManager.unregisterNetworkCallback(this)
    }

    override fun onAvailable(network: Network) {
        super.onAvailable(network)
        isConnected = true
        executor.execute {
            mainHandler.post {
                listener.onNetworkAvailable()
            }
        }
    }

    override fun onLost(network: Network) {
        super.onLost(network)
        isConnected = false
        executor.execute {
            mainHandler.post {
                listener.onNetworkLost()
            }
        }
    }
}