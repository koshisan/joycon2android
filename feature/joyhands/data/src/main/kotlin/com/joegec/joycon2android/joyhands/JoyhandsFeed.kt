package com.joegec.joycon2android.joyhands

import com.joegec.joycon2android.model.ConnectedJoycon
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/** Reports go to joyhands' sender on this device, vibration comes back from it: docs/joyhands.md */
class JoyhandsFeed(
    private val port: Int = JoyhandsPacket.PORT,
    private val hapticPort: Int = JoyhandsHapticPacket.PORT,
) : JoyhandsRepository {

    // Not getLoopbackAddress(), which is ::1 on Android; the sender uses 127.0.0.1.
    private val address = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))
    private val buffer = ByteArray(JoyhandsPacket.SIZE)
    private val lastPacketIds = HashMap<String, Int>()
    private var socket: DatagramSocket? = null

    override val haptics: Flow<JoyhandsHaptic> = flow {
        val incoming = DatagramSocket(null)
        try {
            incoming.reuseAddress = true
            incoming.bind(InetSocketAddress(address, hapticPort))
            // So that a cancelled collector lets go of the port.
            incoming.soTimeout = RECEIVE_TIMEOUT_MS
            val datagram = DatagramPacket(ByteArray(RECEIVE_BUFFER_SIZE), RECEIVE_BUFFER_SIZE)
            while (true) {
                currentCoroutineContext().ensureActive()
                datagram.length = RECEIVE_BUFFER_SIZE
                try {
                    incoming.receive(datagram)
                } catch (e: SocketTimeoutException) {
                    continue
                }
                JoyhandsHapticPacket.parse(datagram.data, datagram.length)?.let { emit(it) }
            }
        } finally {
            incoming.close()
        }
    }.flowOn(Dispatchers.IO)

    /** Called from one coroutine at a time. The controller list re-emits on any change, so only new reports go out. */
    override fun push(controllers: List<ConnectedJoycon>) {
        lastPacketIds.keys.retainAll(controllers.mapTo(HashSet()) { it.address })
        for (controller in controllers) {
            if (!controller.ready) continue
            if (lastPacketIds.put(controller.address, controller.input.packetId) == controller.input.packetId) continue
            if (!JoyhandsPacket.encode(controller, buffer)) continue
            try {
                val open = socket ?: DatagramSocket().also { socket = it }
                open.send(DatagramPacket(buffer, buffer.size, address, port))
            } catch (e: IOException) {
                // No sender running: nothing listens, and the next report tries again.
                socket?.close()
                socket = null
            }
        }
    }

    private companion object {
        const val RECEIVE_BUFFER_SIZE = 64
        const val RECEIVE_TIMEOUT_MS = 500
    }
}
