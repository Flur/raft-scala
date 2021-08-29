package ucu.distributedalgorithms

import akka.actor.typed.{ActorRef, ActorSystem}
import ucu.distributedalgorithms.communication.RaftServer
import ucu.distributedalgorithms.util.{getListOfNodes, readPortAndHost, readSysEnv, stringToInt}

object Main {
  def main(args: Array[String]): Unit = {
    val currentNode = readPortAndHost("PORT", "HOST")
    val clusterNodes = getListOfNodes("CLUSTER_LIST")
    val id = readSysEnv("ID").flatMap(stringToInt).getOrElse(0)

    println(s"List of nodes is $clusterNodes")
    println(s"Node is is $id")

    val system: ActorSystem[RaftServer.Message] = ActorSystem(RaftServer(currentNode._1, currentNode._2, clusterNodes, id), "raft-server")
  }
}
