package me.ddayo.mcplayer.client

import org.apache.logging.log4j.LogManager
import org.bytedeco.ffmpeg.global.avutil
import org.bytedeco.javacv.FFmpegFrameFilter
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Java2DFrameConverter
import java.awt.image.DataBufferByte
import java.io.File
import java.nio.ByteBuffer
import java.nio.ShortBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine
import kotlin.math.roundToInt


class SimplePlayer(video: String, private val chromakey: Boolean) {
    private class PlaybackTimer {
        private var startTime = -1L
        private val soundLine: DataLine?

        constructor(soundLine: DataLine?) {
            this.soundLine = soundLine
        }

        constructor() {
            soundLine = null
        }

        fun start() {
            if (soundLine == null) {
                startTime = System.nanoTime()
            }
        }

        fun elapsedMicros(): Long {
            return if (soundLine == null) {
                check(startTime >= 0) { "PlaybackTimer not initialized." }
                (System.nanoTime() - startTime) / 1000
            } else {
                soundLine.microsecondPosition
            }
        }
    }

    @Volatile
    private lateinit var vbuf: ByteBuffer
    @Volatile
    public var width = -1
        private set
    @Volatile
    public var height = -1
        private set

    private val playThread = Thread {
        try {
            val videoFilename = File("assets/videos/$video").canonicalPath
            val grabber = FFmpegFrameGrabber(videoFilename)
            grabber.pixelFormat = avutil.AV_PIX_FMT_RGBA

            grabber.start()
            width = grabber.imageWidth
            height = grabber.imageHeight

            val chromakeyFilter = FFmpegFrameFilter(
                "chromakey=0x12da11:0.2:0.2",
                width, height
            )
            chromakeyFilter.start()

            val playbackTimer: PlaybackTimer
            val soundLine: SourceDataLine?
            if (grabber.audioChannels > 0) {
                val audioFormat = AudioFormat(grabber.sampleRate.toFloat(), 16, grabber.audioChannels, true, true)
                val info = DataLine.Info(SourceDataLine::class.java, audioFormat)
                soundLine = AudioSystem.getLine(info) as SourceDataLine
                soundLine.open(audioFormat)
                soundLine.start()
                playbackTimer = PlaybackTimer(soundLine)
            } else {
                soundLine = null
                playbackTimer = PlaybackTimer()
            }
            val converter = Java2DFrameConverter()
            val audioExecutor: ExecutorService = Executors.newSingleThreadExecutor()
            val imageExecutor: ExecutorService = Executors.newSingleThreadExecutor()
            val maxReadAheadBufferMicros = 1000 * 1000L
            var lastTimeStamp = -1L
            while (!Thread.interrupted()) {
                val frame = grabber.grab() ?: break
                if (lastTimeStamp < 0) {
                    playbackTimer.start()
                }
                lastTimeStamp = frame.timestamp
                if (frame.image != null) {
                    val imageFrame = if(chromakey) {
                        chromakeyFilter.push(frame.clone())
                        chromakeyFilter.pullImage()
                    }
                    else frame.clone()

                    imageExecutor.submit {
                        val image = converter.convert(imageFrame)
                        val timeStampDeltaMicros: Long = imageFrame.timestamp - playbackTimer.elapsedMicros()
                        imageFrame.close()
                        if (timeStampDeltaMicros > 0) {
                            val delayMillis = timeStampDeltaMicros / 1000L
                            try {
                                Thread.sleep(delayMillis)
                            } catch (e: InterruptedException) {
                                Thread.currentThread().interrupt()
                            }
                        }

                        val bytes = (image.raster.dataBuffer as DataBufferByte).data
                        val buf = ByteBuffer.allocateDirect(bytes.size)
                        buf.put(bytes)
                        buf.flip()
                        vbuf = buf
                    }
                } else if (frame.samples != null) {
                    checkNotNull(soundLine) { "Internal error: sound playback not initialized" }
                    val channelSamplesShortBuffer = frame.samples[0] as ShortBuffer
                    channelSamplesShortBuffer.rewind()
                    val outBuffer = ByteBuffer.allocate(channelSamplesShortBuffer.capacity() * 2)
                    for (i in 0 until channelSamplesShortBuffer.capacity()) {
                        val `val` = channelSamplesShortBuffer[i]
                        outBuffer.putShort(`val`)
                    }
                    audioExecutor.submit {
                        soundLine.write(outBuffer.array(), 0, outBuffer.capacity())
                        outBuffer.clear()
                    }
                }
                val timeStampDeltaMicros: Long = frame.timestamp - playbackTimer.elapsedMicros()
                if (timeStampDeltaMicros > maxReadAheadBufferMicros) {
                    Thread.sleep((timeStampDeltaMicros - maxReadAheadBufferMicros) / 1000)
                }
            }
            if (!Thread.interrupted()) {
                val delay: Long = (lastTimeStamp - playbackTimer.elapsedMicros()) / 1000 +
                        (1 / grabber.frameRate * 1000).roundToInt()
                Thread.sleep(0.coerceAtLeast(delay.toInt()).toLong())
            }
            chromakeyFilter.stop()
            chromakeyFilter.release()
            grabber.stop()
            grabber.release()
            soundLine?.stop()
            audioExecutor.shutdownNow()
            audioExecutor.awaitTermination(10, TimeUnit.SECONDS)
            imageExecutor.shutdownNow()
            imageExecutor.awaitTermination(10, TimeUnit.SECONDS)
        } catch (exception: Exception) {
            LogManager.getLogger().info(exception)
        }
    }

    private var isPlaying = false

    fun play() {
        playThread.start()
        isPlaying = true
    }

    fun stop() {
        playThread.interrupt()
        isPlaying = false
    }

    fun isPlaying() = isPlaying

    fun getBuffer() = vbuf

    fun isLoading() = isPlaying && !this::vbuf.isInitialized
}