package com.reeman.agv.utils

import kotlinx.coroutines.*
import timber.log.Timber
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.MulticastSocket

class MulticastClient {

    private val host: String = "239.0.0.1"
    private val port: Int = 7979
    private val mInetAddress: InetAddress = InetAddress.getByName(host)
    private val multicastSocket: MulticastSocket = MulticastSocket(port).apply {
        joinGroup(mInetAddress)
    }
    private var sendJob: Job? = null
    private var receiveJob: Job? = null

    fun startSendingMulticast(msg: String) {
        sendJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                sendMulticastMessage(msg)
                delay(2000)
            }
        }
    }

    fun sendMulticastMessage(msg: String, onResult: ((Boolean) -> Unit)? = null) {
        val sendAction: () -> Boolean = {
            try {
                val data = msg.toByteArray()
                val datagramPacket = DatagramPacket(data, data.size, mInetAddress, port)
                multicastSocket.send(datagramPacket)
                Timber.d("组播: $msg")
                true
            } catch (e: Exception) {
                Timber.w(e, "组播失败")
                false
            }
        }

        if (onResult == null) {
            sendAction.invoke()
        } else {
            CoroutineScope(Dispatchers.IO).launch {
                val result = sendAction()
                withContext(Dispatchers.Main) {
                    onResult(result)
                }
            }
        }
    }


    fun startReceivingMulticast(onReceived: (String) -> Unit) {
        receiveJob = CoroutineScope(Dispatchers.IO).launch {
            val buffer = ByteArray(1024)
            while (isActive) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    multicastSocket.receive(packet)
                    val receivedMessage = String(packet.data, 0, packet.length)
                    Timber.d("收到组播: $receivedMessage")
                    withContext(Dispatchers.Main) {
                        onReceived(receivedMessage)
                    }
                } catch (e: Exception) {
                    Timber.w(e, "组播接收失败")
                    e.printStackTrace()
                }
            }
        }
    }

    fun closeMulticast() {
        sendJob?.cancel()
        receiveJob?.cancel()
        try {
            multicastSocket.leaveGroup(mInetAddress)
            multicastSocket.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
