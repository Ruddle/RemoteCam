package com.samsung.android.scan3d.http

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.response.respondOutputStream
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import java.io.OutputStream

class HttpService {

    lateinit var engine: NettyApplicationEngine
    var channel = Channel<ByteArray>(2)
    fun producer(): suspend OutputStream.() -> Unit = {
        val o = this
        channel = Channel()
        channel.consumeEach {
            o.write("--FRAME\r\nContent-Type: image/jpeg\r\n\r\n".toByteArray())
            o.write(it)
            o.flush()
        }
    }

    fun main() {
        engine = embeddedServer(Netty, port = 8080) {
            routing {
                get("/cam") {
                    call.respondText("Ok")
                }
                get("/cam.mjpeg") {
                    call.respondOutputStream(
                        ContentType.parse("multipart/x-mixed-replace;boundary=FRAME"),
                        HttpStatusCode.OK,
                        producer()
                    )
                }
            }
        }
        engine.start(wait = false)
    }
}