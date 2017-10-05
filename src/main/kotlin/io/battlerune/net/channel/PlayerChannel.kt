package io.battlerune.net.channel

import io.battlerune.net.packet.GamePacket
import io.battlerune.net.packet.PacketRepository
import io.battlerune.game.world.actor.Player
import io.battlerune.net.NetworkConstants
import io.battlerune.net.codec.login.LoginRequest
import io.battlerune.net.packet.PacketWriter
import io.netty.channel.Channel
import io.netty.channel.socket.SocketChannel
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

class PlayerChannel(val channel: Channel) {

    val incomingPackets: Queue<GamePacket> = ConcurrentLinkedQueue()
    val prioritizedPackets: Queue<GamePacket> = ConcurrentLinkedQueue()

    val player = Player(this)
    val hostAddress: String = (channel as SocketChannel).remoteAddress().address.hostAddress

    fun validLogin(loginRequest: LoginRequest) : Boolean {
        val username = loginRequest.username
        val password = loginRequest.password

        if (!username.matches("^[a-z0-9_ ]{1,12}$".toRegex()) || password.isEmpty() || password.length > 20) {
            return false
        }

        player.username = username
        player.password = password
        return true
    }

    fun handlePrioritizedPackets() {
        while(true) {
            val packet = prioritizedPackets.poll() ?: break

            val reader = PacketRepository.readers[packet.opcode] ?: continue

            reader.readPacket(player, packet)
        }
    }

    fun handleQueuedPackets() {
        handlePrioritizedPackets()

        while(true) {
            val packet = incomingPackets.poll() ?: break

            val handler = PacketRepository.readers[packet.opcode] ?: continue

            handler.readPacket(player, packet)
        }

    }

    fun handleIncomingPacket(packet: GamePacket) {
        if (incomingPackets.size > NetworkConstants.PACKET_LIMIT) {
            return
        }

        if (packet.isPriotity()) {
            prioritizedPackets.add(packet)
        } else {
            incomingPackets.add(packet)
        }
    }

    fun writeAndFlush(writer: PacketWriter) {
        val packet = writer.writePacket(player)

        packet.ifPresent { channel.writeAndFlush(it) }
    }

}