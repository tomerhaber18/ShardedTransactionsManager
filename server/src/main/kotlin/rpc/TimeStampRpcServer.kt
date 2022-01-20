package rpc

import business_logic.TimestampManager
import business_logic.TransactionManager
import io.grpc.Server
import io.grpc.ServerBuilder
import java.util.concurrent.TimeUnit


class TimeStampRpcServer(manager: TimestampManager, private val port: Int) {
    val server: Server = ServerBuilder
        .forPort(port)
        .addService(TimeStampManagersService(manager))
        .build()

    fun start() {
        server.start()
        println("Server started, listening on $port")
        Runtime.getRuntime().addShutdownHook(object : Thread() {
            override fun run() {
                // Use stderr here since the logger may have been reset by its JVM shutdown hook.
                System.err.println("*** shutting down gRPC server since JVM is shutting down")
                try {
                    this@TimeStampRpcServer.stop()
                } catch (e: InterruptedException) {
                    e.printStackTrace(System.err)
                }
                System.err.println("*** server shut down")
            }
        })
    }

    /**
     * Stop serving requests and shutdown resources.
     */
    fun stop() {
        server?.shutdown()?.awaitTermination(30, TimeUnit.SECONDS)
    }

    /**
     * Await termination on the java.host.main thread since the grpc library uses daemon threads.
     */
    fun blockUntilShutdown() {
        server?.awaitTermination()
    }
}