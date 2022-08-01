package me.ddayo.mcplayer

import com.mojang.authlib.GameProfile
import com.mojang.brigadier.arguments.StringArgumentType
import net.minecraft.command.Commands
import net.minecraft.command.arguments.GameProfileArgument
import net.minecraft.util.ResourceLocation
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.event.RegisterCommandsEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext
import net.minecraftforge.fml.network.NetworkDirection
import net.minecraftforge.fml.network.NetworkRegistry
import net.minecraftforge.fml.network.simple.SimpleChannel
import net.minecraftforge.fml.server.ServerLifecycleHooks
import org.apache.logging.log4j.LogManager
import java.util.*

@Mod("mcplayer")
class McPlayer {
    init {
        FMLJavaModLoadingContext.get().modEventBus.addListener { event: FMLCommonSetupEvent -> setup(event) }
        MinecraftForge.EVENT_BUS.register(this)
    }

    companion object {
        lateinit var network: SimpleChannel
    }

    @Suppress("INACCESSIBLE_TYPE")
    private fun setup(event: FMLCommonSetupEvent) {
        network = NetworkRegistry.newSimpleChannel(
            ResourceLocation("mcplayer", "networkchannel"),
            { "1.0" },
            "1.0"::equals,
            "1.0"::equals
        )

        network.registerMessage(
            10, OpenMediaNetworkHandler::class.java,
            OpenMediaNetworkHandler::encode, OpenMediaNetworkHandler.Companion::decode,
            OpenMediaNetworkHandler.Companion::onMessageReceived, Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        )
    }

    @SubscribeEvent
    fun onCommandRegister(event: RegisterCommandsEvent) {
        event.dispatcher.register(Commands.literal("mcpl")
            .then(Commands.argument("player", GameProfileArgument.gameProfile())
                .then(Commands.argument("media", StringArgumentType.string())
                    .executes {
                        GameProfileArgument.getGameProfiles(it, "player").map { profile -> ServerLifecycleHooks.getCurrentServer().playerList.getPlayerByUUID(profile.id) }.forEach { p ->
                            LogManager.getLogger().info(p!!.name)
                            network.sendTo(
                                OpenMediaNetworkHandler(it.getArgument("media", String::class.java)),
                                p!!.connection.netManager,
                                NetworkDirection.PLAY_TO_CLIENT
                            )
                        }
                        1
                    })))
    }
}