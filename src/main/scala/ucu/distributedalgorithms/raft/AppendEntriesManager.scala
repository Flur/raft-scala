package ucu.distributedalgorithms.raft

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, PostStop}
import ucu.distributedalgorithms.raft.Raft.RaftState
import ucu.distributedalgorithms.{AppendEntriesResponse, Node}

object AppendEntriesManager {
  def apply(cluster: List[Node], state: RaftState, candidate: ActorRef[AppendEntriesResponse]): Behavior[Nothing] =
    Behaviors.setup[Nothing] { context =>
      new AppendEntriesManager(cluster, state, candidate, context).appendEntriesManager()
    }
}

class AppendEntriesManager private(
                                    cluster: List[Node],
                                    state: RaftState,
                                    candidate: ActorRef[AppendEntriesResponse],
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
    cluster.foreach { node =>
      context.spawnAnonymous(AppendEntries(node, candidate, state))
    }
  }
}

