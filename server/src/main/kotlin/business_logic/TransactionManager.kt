package business_logic
import com.google.protobuf.Empty
import com.google.protobuf.util.JsonFormat
import cs236351.controller.*
import io.grpc.ManagedChannelBuilder
import java.io.Serializable
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.util.*
import java.util.stream.Collectors
import zookeeper.Zookeeper

class UTxOItem(utxo_item: UTxO) : Serializable {
    val utxo: UTxO = utxo_item
    var Locked: LocalDateTime? = null
}

class TransactionManager(conn: Zookeeper, myShard: String, IP: String, number_of_retries: Int, lock_timeout: Int) {
    private var committedTransactionsPerUser: HashMap<String, MutableList<Tx>> = HashMap()
    private var usersUtxos: HashMap<String, HashMap<String, UTxOItem>> = HashMap()
    private var conn: Zookeeper? = conn
    private var myShard: String? = myShard
    private var IP: String? = IP
    private var lockTimeout: Int? = lock_timeout
    private var NumberOfRetries: Int? = number_of_retries

    init {
        if (myShard == getShardNameFromAddress("0x0000000000000000")) {
            val utxo = getGenesisUTxO()
            usersUtxos["0x0000000000000000"] = HashMap()
            usersUtxos["0x0000000000000000"]?.put(utxo.txId, UTxOItem(utxo))
        }
    }

    private fun getGenesisUTxO(): UTxO {
        val builder = UTxO.newBuilder()
        builder.address = "0x0000000000000000"
        builder.txId = "0x0000000000000000"
        builder.coins = 0xffffffffffffff
        return builder.build()
    }


    // ##################################################################################################################################
    // ##################################################################################################################################
    // ######################################################### Local actions ##########################################################
    private fun getShardNameFromAddress(address: String): String {
        if (address.hashCode() > 0) {
            return "shard1"
        } else {
            return "shard2"
        }
    }

    fun Start() {
        try {
            conn!!.create("/$myShard", null)
        } catch (ignored: Exception) {
        }
        conn!!.createSequentialEphemeral("/$myShard/", IP!!.toByteArray(StandardCharsets.UTF_8))
    }

    fun getTxList(address: Address): TxList? {
        if (!committedTransactionsPerUser.containsKey(address.address)) {
            println("No such user")
            return null
        }
        val tx_list_builder = TxList.newBuilder()
        for (tx in committedTransactionsPerUser[address.address]!!) {
            tx_list_builder.addTxList(tx)
        }
        return tx_list_builder.build()
    }

    fun getAllTxList(): TxList? {
        val tx_list_builder = TxList.newBuilder()
        for (user in committedTransactionsPerUser.keys) {
            for (tx in committedTransactionsPerUser[user]!!) {
                tx_list_builder.addTxList(tx)
            }
        }
        return tx_list_builder.build()
    }

    private fun getShardLeader(address: String): String {
        val children = conn!!.getChildren("/$address")
        val leaderNode = children.stream().sorted().collect(Collectors.toList())[0]
        val data = conn!!.getZNodeData("/$address/$leaderNode", false)
        return String(data, StandardCharsets.UTF_8)
    }

    private fun getTimeStampLeader(): String {
        val children = conn!!.getChildren("/timestamp")
        val leaderNode = children.stream().sorted().collect(Collectors.toList())[0]
        val data = conn!!.getZNodeData("/timestamp/$leaderNode", false)
        return String(data, StandardCharsets.UTF_8)
    }

    fun getUnspent(address: Address?): UTxOList? {
        if (!usersUtxos.containsKey(address?.address)) {
            println("No such user")
            return null
        }
        val utxo_list_builder = UTxOList.newBuilder()
        for (utxo in usersUtxos[address?.address]!!) {
            utxo_list_builder.addUtxoList(utxo.value.utxo)
        }
        return utxo_list_builder.build()
    }

    fun sendAmount(tr: Tr): Tx? {
        val new_tx_builder = Tx.newBuilder()
        new_tx_builder.txId = UUID.randomUUID().toString()
        val UTxOList = getAddressUTxOList(tr.sender, tr.coins) ?: return null
        for (utxo in UTxOList) {
            new_tx_builder.addUtxoList(utxo)
        }
        for (tr_val in getTrList(tr, UTxOList)) {
            new_tx_builder.addTrList(tr_val)
        }
        new_tx_builder.sender = tr.sender
        removeUTxOFromSender(tr.sender, UTxOList)

        new_tx_builder.timestamp = remoteGetTimeStamp()!!
        return new_tx_builder.build()
    }

    fun updateNewTx(transactionManager:TransactionManager, transaction: Tx) {
        for (tr in transaction.trListList) {
            val leader: String = transactionManager.getShardLeader(getShardNameFromAddress(tr.address))
            if (leader == IP) {
                transactionManager.addNewTr(tr)
                transactionManager.addTrTransaction(tr, transaction)
            } else {
                val leader: String = transactionManager.getShardLeader(getShardNameFromAddress(tr.address))
                val channel = ManagedChannelBuilder.forTarget("$leader:8980").usePlaintext().build()
                val blockingStub = TransactionManagerServiceGrpc.newBlockingStub(channel)
                blockingStub.grpcAddNewTr(tr)
                val trtx = TrTx.newBuilder()
                trtx.tr = tr
                trtx.tx = transaction
                blockingStub.grpcAddTransaction(trtx.build())
                channel.shutdown()
            }
        }
    }

    fun followerRemoveUTxOFromSender(removeUTxO: RemoveUTxO) {
        if (!usersUtxos.containsKey(removeUTxO.sender)) {
            return
        }
        for (utxo in removeUTxO.uTxOListList) {
            if(usersUtxos[utxo.address]!!.containsKey(utxo.txId)) {
                usersUtxos[utxo.address]?.remove(utxo.txId)
                // Gossip
                for (server in getShardServers()!!) {
                    if (server == IP) continue
                    val channel = ManagedChannelBuilder.forTarget("$server:8980").usePlaintext().build()
                    val blockingStub = TransactionManagerServiceGrpc.newBlockingStub(channel)
                    blockingStub.grpcFollowerRemoveUTxOFromSender(removeUTxO)
                    channel.shutdown()
                }
            }
        }
    }

    fun removeUTxOFromSender(sender: String, UTxOList: MutableList<UTxO>) {
        val removeUTxO = RemoveUTxO.newBuilder()
        removeUTxO.sender = sender
        for (utxo in UTxOList) {
            removeUTxO.addUTxOList(utxo)
        }
        followerRemoveUTxOFromSender(removeUTxO.build())
        for (server in getShardServers()!!) {
            if (server == IP) continue
            val channel = ManagedChannelBuilder.forTarget("$server:8980").usePlaintext().build()
            val blockingStub = TransactionManagerServiceGrpc.newBlockingStub(channel)
            blockingStub.grpcFollowerRemoveUTxOFromSender(removeUTxO.build())
            channel.shutdown()
        }
    }

    fun followerAddNewTr(utxo: UTxO) {
        if (!usersUtxos.containsKey(utxo.address)) {
            usersUtxos[utxo.address] = HashMap()
        }
        if(!usersUtxos[utxo.address]!!.containsKey(utxo.txId)) {
            usersUtxos[utxo.address]?.put(utxo.txId, UTxOItem(utxo))
            // Gossip
            for (server in getShardServers()!!) {
                if (server == IP) continue
                val channel = ManagedChannelBuilder.forTarget("$server:8980").usePlaintext().build()
                val blockingStub = TransactionManagerServiceGrpc.newBlockingStub(channel)
                blockingStub.grpcFollowerAddNewTr(utxo)
                channel.shutdown()
            }
        }
    }

    fun addNewTr(tr: Tr) {
        val utxo = getNewUTxO(tr)
        followerAddNewTr(utxo)
        for (server in getShardServers()!!) {
            if (server == IP) continue
            val channel = ManagedChannelBuilder.forTarget("$server:8980").usePlaintext().build()
            val blockingStub = TransactionManagerServiceGrpc.newBlockingStub(channel)
            blockingStub.grpcFollowerAddNewTr(utxo)
            channel.shutdown()
        }
    }

    fun followerAddSenderTransaction(new_transaction: Tx) {
        if (!committedTransactionsPerUser.containsKey(new_transaction.sender)) {
            committedTransactionsPerUser[new_transaction.sender] = mutableListOf<Tx>()
        }
        if(!committedTransactionsPerUser[new_transaction.sender]!!.contains(new_transaction)) {
            committedTransactionsPerUser[new_transaction.sender]?.add(new_transaction)
            // Gossip
            for (server in getShardServers()!!) {
                if (server == IP) continue
                val channel = ManagedChannelBuilder.forTarget("$server:8980").usePlaintext().build()
                val blockingStub = TransactionManagerServiceGrpc.newBlockingStub(channel)
                blockingStub.grpcFollowerAddSenderTransaction(new_transaction)
                channel.shutdown()
            }
        }
    }

    fun add_sender_transaction(new_transaction: Tx) {
        followerAddSenderTransaction(new_transaction)
        for (server in getShardServers()!!) {
            if (server == IP) continue
            val channel = ManagedChannelBuilder.forTarget("$server:8980").usePlaintext().build()
            val blockingStub = TransactionManagerServiceGrpc.newBlockingStub(channel)
            blockingStub.grpcFollowerAddSenderTransaction(new_transaction)
            channel.shutdown()
        }
    }

    fun followerAddTrTransaction(trTx: TrTx) {
        if (!committedTransactionsPerUser.containsKey(trTx.tr.address)) {
            committedTransactionsPerUser[trTx.tr.address] = mutableListOf()
        }
        if(!committedTransactionsPerUser[trTx.tr.address]!!.contains(trTx.tx)) {
            committedTransactionsPerUser[trTx.tr.address]?.add(trTx.tx)
            // Gossip
            for (server in getShardServers()!!) {
                if (server == IP) continue
                val channel = ManagedChannelBuilder.forTarget("$server:8980").usePlaintext().build()
                val blockingStub = TransactionManagerServiceGrpc.newBlockingStub(channel)
                blockingStub.grpcFollowerAddTrTransaction(trTx)
                channel.shutdown()
            }
        }
    }

    fun addTrTransaction(tr: Tr, new_transaction: Tx) {
        val trtx = TrTx.newBuilder()
        trtx.tr = tr
        trtx.tx = new_transaction
        followerAddTrTransaction(trtx.build())
        for (server in getShardServers()!!) {
            if (server == IP) continue
            val channel = ManagedChannelBuilder.forTarget("$server:8980").usePlaintext().build()
            val blockingStub = TransactionManagerServiceGrpc.newBlockingStub(channel)
            blockingStub.grpcFollowerAddTrTransaction(trtx.build())
            channel.shutdown()
        }
    }

    private fun getNewUTxO(tr: Tr): UTxO {
        val builder = UTxO.newBuilder()
        builder.txId = UUID.randomUUID().toString()
        builder.address = tr.address
        builder.coins = tr.coins
        return builder.build()
    }

    private fun getTrList(tr: Tr, utxo_list: List<UTxO>): List<Tr> {
        val trList = mutableListOf(tr)
        val builder = Tr.newBuilder()
        var sum: Long = 0
        for (utxo in utxo_list) {
            sum += utxo.coins
        }
        if (sum > tr.coins) {
            builder.address = tr.sender
            builder.coins = sum - tr.coins
            trList.add(builder.build())
        }
        return trList
    }

    private fun getAddressUTxOList(sender: String, coins: Long): MutableList<UTxO>? {
        if (!usersUtxos.containsKey(sender)) {
            println("No such user")
            return null
        }
        val AddressUTxOList = mutableListOf<UTxO>()
        var sum: Long = 0
        for (utxo in usersUtxos[sender].orEmpty()) {
            sum += utxo.value.utxo.coins
            AddressUTxOList.add(utxo.value.utxo)
            if (sum >= coins)
                break
        }
        if (sum < coins) {
            println("User doesn't have enough money")
            return null
        }
        return AddressUTxOList
    }

    fun newTransaction(transaction: TxRequest): Tx? {
        val newTransaction = Tx.newBuilder()
        val UTxOList = getUtxos(transaction) ?: return null
        if (!isSumValid(transaction, UTxOList)) return null

        newTransaction.txId = UUID.randomUUID().toString()
        newTransaction.sender = transaction.sender
        for (utxo in UTxOList) {
            newTransaction.addUtxoList(utxo)
        }
        val TrList = mutableListOf<Tr>()
        transaction.coinsList.zip(transaction.trListList) { a, b ->
            val newTr = Tr.newBuilder()
            newTr.address = b
            newTr.coins = a
            newTr.sender = transaction.sender
            TrList.add(newTr.build())
        }
        for (tr_val in TrList) {
            newTransaction.addTrList(tr_val)
        }
        newTransaction.timestamp = remoteGetTimeStamp()!!

        return newTransaction.build()
    }

    fun getUtxos(new_transaction: TxRequest): MutableList<UTxO>? {
        if (!usersUtxos.containsKey(new_transaction.sender)) {
            println("No such user")
            return null
        }
        val utxosList = mutableListOf<UTxO>()
        for (utxo in new_transaction.utxoIdList) {
            if (utxo in usersUtxos[new_transaction.sender]!!) {
                if (isLocked(usersUtxos[new_transaction.sender]?.get(utxo)!!)) {
                    println("UTxO is already locked")
                    return null
                }
                utxosList.add(usersUtxos[new_transaction.sender]?.get(utxo)?.utxo!!)
            } else {
                println("UTxO is not valid")
                return null
            }
        }
        return utxosList
    }

    private fun isSumValid(new_transaction: TxRequest, utxoList: MutableList<UTxO>): Boolean {
        if (!usersUtxos.containsKey(new_transaction.sender)) {
            println("No such user")
            return false
        }
        if (new_transaction.coinsList.size != new_transaction.trListList.size) {
            println("Coins should match tr")
            println(new_transaction.coinsList)
            println(new_transaction.trListList)
            return false
        }

        var utxoSum: Long = 0
        for (utxo in utxoList) {
            utxoSum += utxo.coins
        }
        var trSum: Long = 0
        for (coins in new_transaction.coinsList) {
            trSum += coins
        }
        if (trSum != utxoSum) {
            println("Sum of coins doesn't match to sum of UTxO")
            return false
        }
        return true
    }

    fun isLocked(utxo_item: UTxOItem): Boolean {
        val lock = utxo_item.Locked
        return lock != null && lock.isAfter(LocalDateTime.now().minusSeconds(lockTimeout!!.toLong()))
    }

    fun lockAndGetUtxos(new_transaction: TxRequest): MutableList<UTxO>? {
        if (!usersUtxos.containsKey(new_transaction.sender)) {
            println("No such user")
            return null
        }
        val utxosList = mutableListOf<UTxO>()
        for (utxo in new_transaction.utxoIdList) {
            if (utxo in usersUtxos[new_transaction.sender]!!) {
                if (isLocked(usersUtxos[new_transaction.sender]?.get(utxo)!!)) {
                    println("UTxO is already locked")
                    return null
                }
                usersUtxos[new_transaction.sender]?.get(utxo)?.Locked = LocalDateTime.now()
                utxosList.add(usersUtxos[new_transaction.sender]?.get(utxo)?.utxo!!)
            } else {
                println("UTxO is not valid")
                return null
            }
        }
        return utxosList
    }

    fun lockAndValidateUtxos(transaction: TxRequest): Tx? {
        val newTransaction = Tx.newBuilder()
        val UTxOList = lockAndGetUtxos(transaction) ?: return null
        for (server in getShardServers()!!) {
            if (server == IP) continue
            val channel = ManagedChannelBuilder.forTarget("$server:8980").usePlaintext().build()
            val blockingStub = TransactionManagerServiceGrpc.newBlockingStub(channel)
            blockingStub.grpcFollowerLockUtxo(transaction)
            channel.shutdown()
        }
        if (!isSumValid(transaction, UTxOList)) return null

        newTransaction.txId = UUID.randomUUID().toString()
        newTransaction.sender = transaction.sender
        for (utxo in UTxOList) {
            newTransaction.addUtxoList(utxo)
        }
        val TrList = mutableListOf<Tr>()
        transaction.coinsList.zip(transaction.trListList) { a, b ->
            val newTr = Tr.newBuilder()
            newTr.address = b
            newTr.coins = a
            newTr.sender = transaction.sender
            TrList.add(newTr.build())
        }
        for (tr_val in TrList) {
            newTransaction.addTrList(tr_val)
        }
        newTransaction.timestamp = remoteGetTimeStamp()!!
        return newTransaction.build()
    }

    fun commitTx(tx: Tx) {
        val UTxOList = tx.utxoListList
        removeUTxOFromSender(tx.sender, UTxOList)
        this.add_sender_transaction(tx)
        this.updateNewTx(this, tx)
    }

    fun getShardServers(): List<String?>? {
        val children = conn!!.getChildren("/$myShard")
        return children.stream().map { path ->
            try {
                return@map String(conn!!.getZNodeData("/$myShard/$path", false), StandardCharsets.UTF_8)
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            }
            null
        }.sorted().collect(Collectors.toList())
    }

    fun getShards(): List<String?>? {
        return conn!!.getChildren("/")
    }

    // ######################################################### Local actions ##########################################################
    // ##################################################################################################################################
    // ##################################################################################################################################




    // ##################################################################################################################################
    // ##################################################################################################################################
    // ######################################################### Remote actions #########################################################
    private fun getRemoteUnspent(address: Address): List<UTxO>? {
        for (i in 1 until NumberOfRetries!!) {
            try {
                val leader: String = getShardLeader(getShardNameFromAddress(address.address))
                val channel = ManagedChannelBuilder.forTarget("$leader:8980").usePlaintext().build()
                val blockingStub = TransactionManagerServiceGrpc.newBlockingStub(channel)
                val ret = blockingStub.grpcGetRemoteUnspent(address).utxoListList
                channel.shutdown()
                return ret
            } catch (e: Exception) {
                if (i == NumberOfRetries!!) throw e
            }
        }
        return null // should not get here
    }

    private fun getRemoteTxList(address: Address): List<Tx>? {
        for (i in 1 until NumberOfRetries!!) {
            try {
                val leader: String = getShardLeader(getShardNameFromAddress(address.address))
                val channel = ManagedChannelBuilder.forTarget("$leader:8980").usePlaintext().build()
                val blockingStub = TransactionManagerServiceGrpc.newBlockingStub(channel)
                val ret = blockingStub.grpcGetRemoteTxList(address).txListList
                channel.shutdown()
                return ret
            } catch (e: Exception) {
                if (i == NumberOfRetries!!) throw e
            }
        }
        return null // should not get here
    }

    private fun getRemoteAllTxList(shard: String): List<Tx>? {
        for (i in 1 until NumberOfRetries!!) {
            try {
                val leader: String = getShardLeader(getShardNameFromAddress(shard))
                val channel = ManagedChannelBuilder.forTarget("$leader:8980").usePlaintext().build()
                val blockingStub = TransactionManagerServiceGrpc.newBlockingStub(channel)
                val ret = blockingStub.grpcGetRemoteAllTxList(Empty.newBuilder().build()).txListList
                channel.shutdown()
                return ret
            } catch (e: Exception) {
                if (i == NumberOfRetries!!) throw e
            }
        }
        return null // should not get here
    }


    private fun newRemoteTransaction(transaction: TxRequest): String? {
        for (i in 1 until NumberOfRetries!!) {
            try {
                val leader: String = getShardLeader(getShardNameFromAddress(transaction.sender))
                val channel = ManagedChannelBuilder.forTarget("$leader:8980").usePlaintext().build()
                val blockingStub = TransactionManagerServiceGrpc.newBlockingStub(channel)
                blockingStub.grpcNewRemoteTransaction(transaction)
                channel.shutdown()
                return "success"
            } catch (e: Exception) {
                if (i == NumberOfRetries!!) throw e
            }
        }
        return null // should not get here
    }


    private fun remoteSendAmount(tr: Tr): String? {
        for (i in 1 until NumberOfRetries!!) {
            try {
                val leader: String = getShardLeader(getShardNameFromAddress(tr.sender))
                val channel = ManagedChannelBuilder.forTarget("$leader:8980").usePlaintext().build()
                val blockingStub = TransactionManagerServiceGrpc.newBlockingStub(channel)
                blockingStub.grpcRemoteSendAmount(tr)
                channel.shutdown()
                return "success"
            } catch (e: Exception) {
                if (i == NumberOfRetries!!) throw e
            }
        }
        return null // should not get here
    }
    private fun remoteLockAndValidateUtxos(transaction: TxRequest): Tx? {
        for (i in 1 until NumberOfRetries!!) {
            try {
                val leader: String = getShardLeader(getShardNameFromAddress(transaction.sender))
                val channel = ManagedChannelBuilder.forTarget("$leader:8980").usePlaintext().build()
                val blockingStub = TransactionManagerServiceGrpc.newBlockingStub(channel)
                val ret = blockingStub.grpcRemoteLockAndValidateUtxos(transaction)
                channel.shutdown()
                return ret
            } catch (e: Exception) {
                if (i == NumberOfRetries!!) throw e
            }
        }
        return null // should not get here
    }

    private fun remoteCommitTx(tx: Tx) {
        for (i in 1 until NumberOfRetries!!) {
            try {
                val leader: String = getShardLeader(getShardNameFromAddress(tx.sender))
                val channel = ManagedChannelBuilder.forTarget("$leader:8980").usePlaintext().build()
                val blockingStub = TransactionManagerServiceGrpc.newBlockingStub(channel)
                blockingStub.grpcRemoteCommitTx(tx)
                channel.shutdown()
                return
            } catch (e: Exception) {
                if (i == NumberOfRetries!!) throw e
            }
        }
    }

    private fun remoteGetTimeStamp(): Long? {
        for (i in 1 until NumberOfRetries!!) {
            try {
                val leader: String = getTimeStampLeader()
                val channel = ManagedChannelBuilder.forTarget("$leader:8980").usePlaintext().build()
                val blockingStub = TimeStampManagerServiceGrpc.newBlockingStub(channel)
                val res = blockingStub.grpcGetTimeStamp(Empty.newBuilder().build())
                channel.shutdown()
                return res.timestamp
            } catch (e: Exception) {
                if (i == NumberOfRetries!!) throw e
            }
        }
        return null
    }
    // ######################################################### Remote actions #########################################################
    // ##################################################################################################################################
    // ##################################################################################################################################




    // ##################################################################################################################################
    // ##################################################################################################################################
    // ####################################################### Controller actions #######################################################
    fun controllerGetUnspentTransactions(address: String): String {
        println(address)
        val builder = Address.newBuilder().setAddress(address)
        var returnValue: String
        val unspent: List<UTxO>?
        if (getShardNameFromAddress(address) == myShard) {
            returnValue = "from server $IP\n"
            unspent = this.getUnspent(builder.build())?.utxoListList
        } else {
            returnValue = "from other server\n"
            unspent = this.getRemoteUnspent(builder.build())
        }

        for (utxo in unspent.orEmpty()) {
            returnValue += utxo.txId
            returnValue += "  amount: " + utxo.coins
            returnValue += "\n"
        }
        return returnValue
    }

    fun controllerGetAddressHistoryTransactions(address: String, number_of_records: Int?): String {
        println(address)
        val builder = Address.newBuilder().setAddress(address)
        val returnValue: String
        val txList: List<Tx>
        if (getShardNameFromAddress(address) == myShard) {
            returnValue = "from server $IP\n"
            txList = this.getTxList(builder.build())!!.txListList
        } else {
            returnValue = "from other server\n"
            txList = this.getRemoteTxList(builder.build())!!
        }

        return returnValue + getTxListString(txList, number_of_records)
    }

    fun getTxListString(txList : List<Tx>, number_of_records: Int?): String {
        val limit = number_of_records ?: txList.count()
        txList.sortedWith(compareBy { it.timestamp })
        var returnValue = ""
        for (tx in txList.distinct().take(limit)) {
            returnValue += ("transaction: " + tx.txId + " from " + tx.sender + " at " + tx.timestamp)
            for(tr in tx.trListList) {
                returnValue += "\n"
                returnValue += ("     to " + tr.address + " of " + tr.coins + " coins")
            }
            returnValue += "\n"
        }
        returnValue += "\n"
        return returnValue
    }

    fun controllerGetHistoryTransactions(number_of_records: Int?): String {
        val txList = mutableListOf<Tx>()
        for (shard in getShards()!!) {
            val leader: String = getShardLeader(shard!!)
            if (leader == IP) {
                txList += this.getAllTxList()!!.txListList
            } else {
                txList += this.getRemoteAllTxList(shard)!!
            }
        }
        return getTxListString(txList, number_of_records)

    }

    fun controllerNewTransaction(new_transaction: String): String? {
        println(new_transaction)
        val new_tx = TxRequest.newBuilder()
        JsonFormat.parser().ignoringUnknownFields().merge(new_transaction, new_tx)
        val returnValue: String
        val leader: String = getShardLeader(getShardNameFromAddress(new_tx.build().sender))
        if (leader == this.IP) {
            returnValue = "from server $IP\n"
            println(new_tx.build())
            val tx_res = this.newTransaction(new_tx.build())
            val UTxOList = getUtxos(new_tx.build()) ?: return null
            removeUTxOFromSender(new_tx.build().sender, UTxOList)
            this.add_sender_transaction(tx_res!!)
            this.updateNewTx(this, tx_res)
        } else {
            returnValue = "from other server\n"
            this.newRemoteTransaction(new_tx.build())
        }

        return returnValue
    }

    fun controllerSendAmount(tr: String): String {
        println(tr)
        val new_tr = Tr.newBuilder()
        JsonFormat.parser().ignoringUnknownFields().merge(tr, new_tr)
        var returnValue: String
        val leader: String = getShardLeader(getShardNameFromAddress(new_tr.build().sender))
        if (leader == this.IP) {
            returnValue = "from server $IP\n"
            val tx_res = this.sendAmount(new_tr.build())
            this.add_sender_transaction(tx_res!!)
            this.updateNewTx(this, tx_res)
        } else {
            returnValue = "from other server\n"
            this.remoteSendAmount(new_tr.build())
        }
        val newTr = new_tr.build()
        returnValue += "got new tr to " + newTr.address.toString() + " of " + newTr.coins.toString() + " coins"
        return returnValue
    }

    fun controllerNewTransactions(new_transactions: String): String {
        println(new_transactions)
        val new_txs = TxRequests.newBuilder()
        JsonFormat.parser().ignoringUnknownFields().merge(new_transactions, new_txs)
        val txs = mutableListOf<Tx>()
        for (tx in new_txs.build().txList) {
            val leader: String = getShardLeader(getShardNameFromAddress(tx.sender))
            if (leader == IP) {
                println(tx)
                val tx_res = this.lockAndValidateUtxos(tx)
                if (tx_res == null) {
                    return "transactions failed"
                } else {
                    txs.add(tx_res)
                }
            } else {
                val tx_res = this.remoteLockAndValidateUtxos(tx)
                if (tx_res == null) {
                    return "transactions failed"
                } else {
                    txs.add(tx_res)
                }
            }
        }

        for (tx in txs) {
            val leader: String = getShardLeader(getShardNameFromAddress(tx.sender))
            if (leader == IP) {
                commitTx(tx)
            } else {
                remoteCommitTx(tx)
            }
        }
        return "success"
    }
    // ####################################################### Controller actions #######################################################
    // ##################################################################################################################################
    // ##################################################################################################################################
}