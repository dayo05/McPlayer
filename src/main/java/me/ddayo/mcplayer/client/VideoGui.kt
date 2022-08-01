package me.ddayo.mcplayer.client

import com.mojang.blaze3d.matrix.MatrixStack
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screen.Screen
import net.minecraft.util.text.StringTextComponent
import org.lwjgl.opengl.GL21
import java.io.File

class VideoGui(video: String): Screen(StringTextComponent.EMPTY) {
    private val sp = SimplePlayer(video, false)
    private var tex = -1

    override fun render(matrixStack: MatrixStack, mouseX: Int, mouseY: Int, partialTicks: Float) {
        if(!sp.isPlaying())
            sp.play()
        if(sp.isLoading()) return

        if(tex == -1) {
            tex = GL21.glGenTextures()
            GL21.glBindTexture(GL21.GL_TEXTURE_2D, tex)
            GL21.glTexParameteri(GL21.GL_TEXTURE_2D, GL21.GL_TEXTURE_WRAP_S, GL21.GL_CLAMP_TO_EDGE)
            GL21.glTexParameteri(GL21.GL_TEXTURE_2D, GL21.GL_TEXTURE_WRAP_T, GL21.GL_CLAMP_TO_EDGE)
            GL21.glTexParameteri(GL21.GL_TEXTURE_2D, GL21.GL_TEXTURE_MIN_FILTER, GL21.GL_LINEAR)
            GL21.glTexParameteri(GL21.GL_TEXTURE_2D, GL21.GL_TEXTURE_MAG_FILTER, GL21.GL_LINEAR)
            GL21.glBindTexture(GL21.GL_TEXTURE_2D, 0)
        }

        GL21.glBindTexture(GL21.GL_TEXTURE_2D, tex)
        GL21.glTexImage2D(
            GL21.GL_TEXTURE_2D,
            0,
            GL21.GL_RGBA,
            sp.width,
            sp.height,
            0,
            GL21.GL_RGBA,
            GL21.GL_UNSIGNED_BYTE,
            sp.getBuffer()
        )

        GL21.glBegin(GL21.GL_QUADS)
        GL21.glTexCoord2d(1.0, 0.0)
        GL21.glVertex2i(width, 0)
        GL21.glTexCoord2d(0.0, 0.0)
        GL21.glVertex2i(0, 0)
        GL21.glTexCoord2d(0.0, 1.0)
        GL21.glVertex2i(0, height)
        GL21.glTexCoord2d(1.0, 1.0)
        GL21.glVertex2i(width, height)
        GL21.glEnd()
        GL21.glBindTexture(GL21.GL_TEXTURE_2D, 0)
        super.render(matrixStack, mouseX, mouseY, partialTicks)
    }

    override fun onClose() {
        if(sp.isPlaying())
            sp.stop()
        super.onClose()
    }
}