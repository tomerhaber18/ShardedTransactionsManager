package zookeeper

interface ZookeeperManager {
    fun create(path: String, data: ByteArray?)
    fun getZNodeData(path: String, watchFlag: Boolean): Any
}