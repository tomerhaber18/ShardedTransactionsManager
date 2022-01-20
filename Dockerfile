FROM zookeeper
WORKDIR /home
ADD server/build/libs/server.jar /home/transaction_manager.jar
ADD configuration /home/configuration
CMD ["java", "--add-opens" , "java.base/jdk.internal.misc=ALL-UNNAMED", "-Dio.netty.tryReflectionSetAccessible=true", "-Dserver.port=8080", "-jar", "transaction_manager.jar"]
