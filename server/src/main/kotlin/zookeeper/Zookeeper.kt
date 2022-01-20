package zookeeper

import org.apache.zookeeper.*

class Zookeeper(host: String) : ZookeeperManager {
    init {
        initialize(host)
    }

    private fun initialize(host: String) {
        zookeeperConnection = ZookeeperConnection()
        println("trying to connect to: " + host)
        zkeeper = zookeeperConnection!!.connect(host)
    }

    override fun create(path: String, data: ByteArray?) {
        zkeeper!!.create(
            path,
            data,
            ZooDefs.Ids.OPEN_ACL_UNSAFE,
            CreateMode.PERSISTENT
        )
    }

    fun createSequentialEphemeral(path: String?, data: ByteArray?) {
        zkeeper!!.create(
            path,
            data,
            ZooDefs.Ids.OPEN_ACL_UNSAFE,
            CreateMode.EPHEMERAL_SEQUENTIAL
        )
    }

    fun getChildren(path: String?): List<String> {
        return zkeeper!!.getChildren(path, false)
    }

    override fun getZNodeData(path: String, watchFlag: Boolean): ByteArray {
        val b: ByteArray? = null
        return zkeeper!!.getData(path, false, null)
    }

    companion object {
        var zkeeper: ZooKeeper? = null
        var zookeeperConnection: ZookeeperConnection? = null
    }
}