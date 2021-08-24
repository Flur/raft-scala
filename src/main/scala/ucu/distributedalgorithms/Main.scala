package ucu.distributedalgorithms

import akka.actor.typed.{ActorRef, ActorSystem}
import ucu.distributedalgorithms.communication.RaftServer
import ucu.distributedalgorithms.util.{getListOfNodes, readPortAndHost, readSysEnv, stringToInt}

object Main {
  def main(args: Array[String]): Unit = {
    val currentNode = readPortAndHost("PORT", "HOST")
    val clusterNodes = getListOfNodes("CLUSTER_LIST")
    val id = readSysEnv("ID").flatMap(stringToInt).getOrElse(0)


//    val clusterNodes = List(Node("127.0.0.1", 8082), Node("127.0.0.1", 8083))
    println(clusterNodes)


    val system: ActorSystem[RaftServer.Message] = ActorSystem(RaftServer(currentNode.host, currentNode.port, clusterNodes, id), "raft-server")
  }
}
