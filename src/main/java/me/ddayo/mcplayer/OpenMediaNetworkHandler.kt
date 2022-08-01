package me.ddayo.mcplayer

import me.ddayo.mcplayer.client.VideoGui
import net.minecraft.client.Minecraft
import net.minecraft.network.PacketBuffer
import net.minecraftforge.fml.network.NetworkEvent
import org.apache.logging.log4j.LogManager
import java.util.function.Supplier

class OpenMediaNetworkHandler() {
    var media = ""

    constructor(media: String): this() {
        this.media = media
    }

    companion object {
        private val logger = LogManager.getLogger()

        fun onMessageReceived(message: OpenMediaNetworkHandler, ctxSuf: Supplier<NetworkEvent.Context>) = message.run {
            val ctx = ctxSuf.get()
            ctx.packetHandled = true
            ctx.enqueueWork {
                logger.info("Display video: ${message.media}")
                Minecraft.getInstance().displayGuiScreen(VideoGui(message.media))
            }
        }


        @JvmStatic
        fun decode(buf: PacketBuffer) = OpenMediaNetworkHandler(buf.readString())
    }

    fun encode(buf: PacketBuffer)
            = buf.writeString(media)
}