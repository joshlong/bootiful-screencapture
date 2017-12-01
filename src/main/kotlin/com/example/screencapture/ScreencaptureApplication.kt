package com.example.screencapture

import org.apache.commons.logging.LogFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import java.awt.Rectangle
import java.awt.Robot
import java.awt.Toolkit
import java.io.File
import java.io.FileFilter
import java.time.Duration
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import javax.imageio.ImageIO
import javax.imageio.stream.FileImageOutputStream


@SpringBootApplication
class ScreencaptureApplication {

    @Bean
    fun run(@Value("\${HOME}") home: File) = CommandLineRunner {

        System.setProperty("java.awt.headless", "false")

        val log = LogFactory.getLog(javaClass)

        fun transcode(files: Array<File>, timeBetweenFramesMS: Long, targetGif: File) {
            FileImageOutputStream(targetGif).use { output ->
                GifSequenceWriter(output, ImageIO.read(files[0]).type, timeBetweenFramesMS, false).use { writer ->
                    files.forEach {
                        writer.addToSequence(ImageIO.read(it))
                    }
                }
            }
        }

        fun capture(out: File): Boolean {
            val robot = Robot()
            val screenRect = Rectangle(Toolkit.getDefaultToolkit().screenSize)
            val screenFullImage = robot.createScreenCapture(screenRect)
            return ImageIO.write(screenFullImage, "png", out)
        }

        fun captureUntil(fps: Int, imgDirectory: File, finish: Instant): Long {
            val executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
            val intervalInMs: Long = ((1.0 / (fps * 1.0)) * 1000.0).toLong()
            val semaphore = Semaphore(0)
            var permits = 0
            while (Instant.now().isBefore(finish)) {
                executor.submit({
                    val file = File(imgDirectory, "${permits}.png")
                    capture(file)
                    semaphore.release()
                })
                permits += 1
                Thread.sleep(intervalInMs)
            }
            semaphore.acquire(permits)
            return intervalInMs
        }

        val imgs: File = File(home, "/Desktop/captured/").apply {
            mkdirs()
        }
        val out = File(home, "out.gif")
        val intervalInMs = captureUntil(15, imgs, Instant.now().plus(Duration.ofSeconds(2)))
        val files = imgs.listFiles(FileFilter {
            it.extension == "png"
        })
        transcode(files, intervalInMs, out)
    }
}

fun main(args: Array<String>) {
    runApplication<ScreencaptureApplication>(*args)
}