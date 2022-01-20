package com.example.api

import business_logic.TimestampManager
import business_logic.TransactionManager
import com.google.protobuf.util.JsonFormat
import cs236351.controller.Configuration
import java.net.InetAddress
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import rpc.RpcServer
import rpc.TimeStampRpcServer
import zookeeper.Zookeeper


@SpringBootApplication
class SpringBootBoilerplateApplication1 {
	object a {
		var TransactionManager: TransactionManager? = null
	}
	init {
		val text = Files.readString(Path.of(System.getenv("CONF_FILE")))
		val conf = Configuration.newBuilder()

		JsonFormat.parser().ignoringUnknownFields().merge(text, conf)
		val host = InetAddress.getByName("zoo1.zk.local")
		val conn = Zookeeper(host.hostAddress + ":2181") // zk default
		TimeUnit.SECONDS.sleep(10)

		val addr = InetAddress.getLocalHost().hostAddress
		if (conf.shard == "timestamp") {
			val timestampManager = TimestampManager(conn, conf.shard, addr)
			val server = TimeStampRpcServer( timestampManager, 8980)
			server.start()
			timestampManager.Start()
		} else {
			a.TransactionManager = TransactionManager(conn, conf.shard, addr, 3, 10)
			val server = RpcServer( a.TransactionManager!!, 8980)
			server.start()
			a.TransactionManager?.Start()
		}
	}
}

fun main(args: Array<String>) {
	runApplication<SpringBootBoilerplateApplication1>(*args)
}
