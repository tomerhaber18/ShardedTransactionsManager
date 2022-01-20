package rpc

import business_logic.TimestampManager
import com.google.protobuf.Empty
import cs236351.controller.*
import io.grpc.stub.StreamObserver


class TimeStampManagersService(manager: TimestampManager) : TimeStampManagerServiceGrpc.TimeStampManagerServiceImplBase() {
    private var manager: TimestampManager? = null

    init {
        this.manager = manager
    }
    private fun getManager(): TimestampManager? {
        return manager
    }

    override fun grpcGetTimeStamp(empty: Empty?, responseObserver: StreamObserver<TimeStamp>?) {
        val timestampManager = getManager()
        try {
            val timestamp_builder = TimeStamp.newBuilder()
            timestamp_builder.timestamp = timestampManager?.getAndIncrementTimeStamp()!!
            responseObserver?.onNext(timestamp_builder.build())
        } catch (ignored: Exception) {
        }
        responseObserver?.onCompleted()
    }

    override fun grpcUpdateTimeStamp(timeStamp: TimeStamp?, responseObserver: StreamObserver<Empty>?) {
        val timestampManager = getManager()
        try {
            timestampManager!!.timestamp = timeStamp!!.timestamp
        } catch (ignored: Exception) {
        }
        responseObserver?.onNext(Empty.newBuilder().build())
        responseObserver?.onCompleted()
    }
}