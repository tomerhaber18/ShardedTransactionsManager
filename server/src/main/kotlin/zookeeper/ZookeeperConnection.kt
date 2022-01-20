package zookeeper

import org.apache.zookeeper.WatchedEvent
import org.apache.zookeeper.Watcher.Event.KeeperState
import org.apache.zookeeper.ZooKeeper
import java.util.concurrent.CountDownLatch


class ZookeeperConnection {
    private var zoo: ZooKeeper? = null
    private val connSignal = CountDownLatch(0)

    fun connect(host: String?): ZooKeeper? {
        this.zoo = ZooKeeper(host, 3000) { event: WatchedEvent ->
            if (event.state == KeeperState.SyncConnected) {
                connSignal.countDown()
            }
        }
        connSignal.await()
        return this.zoo
    }

    fun close() {
        zoo!!.close()
    }
}