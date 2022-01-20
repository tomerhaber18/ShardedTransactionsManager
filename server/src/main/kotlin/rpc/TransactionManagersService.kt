package rpc

import business_logic.TransactionManager
import com.google.protobuf.Empty
import cs236351.controller.*
import io.grpc.stub.StreamObserver


class TransactionManagersService(manager: TransactionManager) : TransactionManagerServiceGrpc.TransactionManagerServiceImplBase() {
    private var manager: TransactionManager? = null

    init {
        this.manager = manager
    }
    private fun getManager(): TransactionManager? {
        return manager
    }

    override fun grpcGetRemoteUnspent(address: Address?, responseObserver: StreamObserver<UTxOList>?) {
        val transactionManager = getManager()
        try {
            val res = transactionManager?.getUnspent(address)
            responseObserver?.onNext(res)
        } catch (ignored: Exception) {
        }
        responseObserver?.onCompleted()
    }

    override fun grpcGetRemoteTxList(address: Address?, responseObserver: StreamObserver<TxList>?) {
        val transactionManager = getManager()
        try {
            val res = transactionManager?.getTxList(address!!)
            responseObserver?.onNext(res)
        } catch (ignored: Exception) {
        }
        responseObserver?.onCompleted()
    }

    override fun grpcGetRemoteAllTxList(empty: Empty?, responseObserver: StreamObserver<TxList>?) {
        val transactionManager = getManager()
        try {
            val res = transactionManager?.getAllTxList()
            responseObserver?.onNext(res)
        } catch (ignored: Exception) {
        }
        responseObserver?.onCompleted()
    }


    override fun grpcAddNewTr(tr: Tr?, responseObserver: StreamObserver<Empty>?) {
        val transactionManager = getManager()
        try {
            transactionManager?.addNewTr(tr!!)
        } catch (ignored: Exception) {
        }
        responseObserver?.onNext(Empty.newBuilder().build())
        responseObserver?.onCompleted()
    }

    override fun grpcAddTransaction(trtx: TrTx?, responseObserver: StreamObserver<Empty>?) {
        val transactionManager = getManager()
        try {
            transactionManager?.addTrTransaction(trtx?.tr!!, trtx.tx!!)
        } catch (ignored: Exception) {
        }
        responseObserver?.onNext(Empty.newBuilder().build())
        responseObserver?.onCompleted()
    }

    override fun grpcNewRemoteTransaction(transaction: TxRequest?, responseObserver: StreamObserver<Empty>?) {
        val transactionManager = getManager()
        try {
            val tx_res = transactionManager?.newTransaction(transaction!!)
            val UTxOList = transactionManager?.getUtxos(transaction!!)
            transactionManager?.removeUTxOFromSender(transaction?.sender!!, UTxOList!!)
            transactionManager?.add_sender_transaction(tx_res!!)
            transactionManager?.updateNewTx(transactionManager, tx_res!!)
        } catch (ignored: Exception) {
        }
        responseObserver?.onNext(Empty.newBuilder().build())
        responseObserver?.onCompleted()
    }

    override fun grpcRemoteSendAmount(tr: Tr?, responseObserver: StreamObserver<Empty>?) {
        val transactionManager = getManager()
        try {
            val tx_res = transactionManager?.sendAmount(tr!!)
            transactionManager?.add_sender_transaction(tx_res!!)
            transactionManager?.updateNewTx(transactionManager, tx_res!!)
        } catch (ignored: Exception) {
        }
        responseObserver?.onNext(Empty.newBuilder().build())
        responseObserver?.onCompleted()
    }

    override fun grpcRemoteLockAndValidateUtxos(transaction: TxRequest?, responseObserver: StreamObserver<Tx>?) {
        val transactionManager = getManager()
        try {
            val tx_res = transactionManager?.lockAndValidateUtxos(transaction!!)
            responseObserver?.onNext(tx_res)
        } catch (ignored: Exception) {
        }
        responseObserver?.onCompleted()
    }

    override fun grpcRemoteCommitTx(tx: Tx?, responseObserver: StreamObserver<Empty>?) {
        val transactionManager = getManager()
        try {
            transactionManager?.commitTx(tx!!)
        } catch (ignored: Exception) {
        }
        responseObserver?.onNext(Empty.newBuilder().build())
        responseObserver?.onCompleted()
    }

    override fun grpcFollowerRemoveUTxOFromSender(removeUTxO: RemoveUTxO, responseObserver: StreamObserver<Empty>?) {
        // gossip added
        val transactionManager = getManager()
        try {
            transactionManager?.followerRemoveUTxOFromSender(removeUTxO)
        } catch (ignored: Exception) {
        }
        responseObserver?.onNext(Empty.newBuilder().build())
        responseObserver?.onCompleted()
    }

    override fun grpcFollowerAddNewTr(utxo: UTxO, responseObserver: StreamObserver<Empty>?) {
        // gossip added
        val transactionManager = getManager()
        try {
            transactionManager?.followerAddNewTr(utxo)
        } catch (ignored: Exception) {
        }
        responseObserver?.onNext(Empty.newBuilder().build())
        responseObserver?.onCompleted()
    }

    override fun grpcFollowerAddSenderTransaction(new_transaction: Tx, responseObserver: StreamObserver<Empty>?) {
        // gossip added
        val transactionManager = getManager()
        try {
            transactionManager?.followerAddSenderTransaction(new_transaction)
        } catch (ignored: Exception) {
        }
        responseObserver?.onNext(Empty.newBuilder().build())
        responseObserver?.onCompleted()
    }

    override fun grpcFollowerAddTrTransaction(trtx: TrTx, responseObserver: StreamObserver<Empty>?) {
        // gossip added
        val transactionManager = getManager()
        try {
            transactionManager?.followerAddTrTransaction(trtx)
        } catch (ignored: Exception) {
        }
        responseObserver?.onNext(Empty.newBuilder().build())
        responseObserver?.onCompleted()
    }

    override fun grpcFollowerLockUtxo(transaction: TxRequest, responseObserver: StreamObserver<Empty>?) {
        val transactionManager = getManager()
        try {
            transactionManager?.lockAndGetUtxos(transaction)
        } catch (ignored: Exception) {
        }
        responseObserver?.onNext(Empty.newBuilder().build())
        responseObserver?.onCompleted()
    }
}