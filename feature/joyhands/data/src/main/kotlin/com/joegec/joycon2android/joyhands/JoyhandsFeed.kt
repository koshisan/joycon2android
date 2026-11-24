package com.joegec.joycon2android.joyhands

import com.joegec.joycon2android.model.ConnectedJoycon
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/** Sends each new report to joyhands' sender, which listens on this device: docs/joyhands.md */
class JoyhandsFeed(private val port: Int = JoyhandsPacket.PORT) : JoyhandsRepository {

    // Not getLoopbackAddress(), which is ::1 on Android; the sender listens on 127.0.0.1.
    private val address = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))
    private val buffer = ByteArray(JoyhandsPacket.SIZE)
    private val lastPacketIds = HashMap<String, Int>()
    private var socket: DatagramSocket? = null

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
}
