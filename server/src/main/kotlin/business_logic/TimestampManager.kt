package business_logic
import cs236351.controller.*
import io.grpc.ManagedChannelBuilder
import java.nio.charset.StandardCharsets
import java.util.stream.Collectors
import zookeeper.Zookeeper



class TimestampManager(zookeeper: Zookeeper, myShard: String, IP: String) {
    private var zookeeper: Zookeeper? = zookeeper
    private var myShard: String? = myShard
    private var IP: String? = IP
    public var timestamp : Long = 0

    fun Start() {
        try {
            zookeeper!!.create("/$myShard", null)
        } catch (ignored: Exception) {
        }
        zookeeper!!.createSequentialEphemeral("/$myShard/", IP!!.toByteArray(StandardCharsets.UTF_8))
    }

    fun getAndIncrementTimeStamp() : Long? {
        val result = timestamp
        timestamp += 1
        for (server in getTimeStampServers()!!) {
            if (server == IP) continue
            val channel = ManagedChannelBuilder.forTarget("$server:8980").usePlaintext().build()
            val blockingStub = TimeStampManagerServiceGrpc.newBlockingStub(channel)
            val builder = TimeStamp.newBuilder()
            builder.timestamp = timestamp
            blockingStub.grpcUpdateTimeStamp(builder.build())
            channel.shutdown()
        }
        return result
    }

    fun getTimeStampServers(): List<String?>? {
        val children = zookeeper!!.getChildren("/$myShard")
        return children.stream().map { path ->
            try {
                return@map String(zookeeper!!.getZNodeData("/$myShard/$path", false), StandardCharsets.UTF_8)
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            }
            null
        }.sorted().collect(Collectors.toList())
    }
}


