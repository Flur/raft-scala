package ucu.distributedalgorithms.raft

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, PostStop}
import ucu.distributedalgorithms.raft.AppendEntries.AppendEntriesSuccess
import ucu.distributedalgorithms.raft.Raft.{RaftState, VolatileLeaderState}
import ucu.distributedalgorithms.{AppendEntriesResponse, Node}

object AppendEntriesManager {
  def apply(cluster: List[Node], state: RaftState, leaderState: VolatileLeaderState, candidate: ActorRef[AppendEntriesSuccess]): Behavior[Nothing] =
    Behaviors.setup[Nothing] { context =>
      new AppendEntriesManager(cluster, state, leaderState, candidate, context).appendEntriesManager()
    }
}

class AppendEntriesManager private(
                                    cluster: List[Node],
                                    state: RaftState,
                                    leaderState: VolatileLeaderState,
                                    candidate: ActorRef[AppendEntriesSuccess],
                                    context: ActorContext[Nothing]
                                  ) {

  appendEntriesPerNode()

  private def appendEntriesManager(): Behavior[Nothing] = {
    Behaviors.receiveSignal[Nothing] {
      case (context, postStop: PostStop) =>
        context.log.info("Stop Append Entries Manager")

        Behaviors.stopped
    }
  }

  private def appendEntriesPerNode(): Unit = {
    val tupleOfLeaderState = leaderState.nextIndex
      .zip(leaderState.matchIndex)
      .zip(cluster)
      .map(data => (data._1._1, data._1._2, data._2))
      .zipWithIndex


    tupleOfLeaderState.foreach { data =>
      val ((nextIndex, matchIndex, node), index) = data

      context.spawnAnonymous(AppendEntries(node, candidate, state, nextIndex, index))
    }
  }
}

