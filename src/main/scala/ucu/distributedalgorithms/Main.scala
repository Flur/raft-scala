package ucu.distributedalgorithms

import akka.actor.typed.{ActorRef, ActorSystem}

object Main {
  def main(args: Array[String]): Unit = {
    val system: ActorSystem[RaftServer.Message] = ActorSystem(RaftServer("localhost", 8080), "raft-server")

//    val raftServer: ActorRef[RaftServer.Message] = system
//    raftServer ! RaftServer.Stop
  }
}
