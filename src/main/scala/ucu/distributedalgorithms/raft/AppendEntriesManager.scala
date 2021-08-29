package ucu.distributedalgorithms.raft

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, PostStop}
import ucu.distributedalgorithms.Node
import ucu.distributedalgorithms.raft.AppendEntries.AppendEntriesSuccess
import ucu.distributedalgorithms.raft.Raft.{RaftState, LeaderState}

object AppendEntriesManager {
  def apply(cluster: List[Node], state: RaftState, leaderState: LeaderState, candidate: ActorRef[AppendEntriesSuccess]): Behavior[Nothing] =
    Behaviors.setup[Nothing] { context =>
      new AppendEntriesManager(cluster, state, leaderState, candidate, context).appendEntriesManager()
    }
}

class AppendEntriesManager private(
                                    cluster: List[Node],
                                    state: RaftState,
                                    leaderState: LeaderState,
                                    candidate: ActorRef[AppendEntriesSuccess],
                                    context: ActorContext[Nothing]
                                  ) {

  //  appendEntriesPerNode()

  private def appendEntriesManager(): Behavior[Nothing] = {
    val appendEntries = cluster.map { node =>
      val nextIndex = leaderState.nextIndex.getOrElse(node.id, 0)

      context.spawnAnonymous(AppendEntries(node, candidate, state, nextIndex))
    }

    Behaviors.receiveSignal[Nothing] {
      case (context, postStop: PostStop) =>
        appendEntries.foreach(a => context.stop(a))

        context.log.info("Stop Append Entries Manager")

        Behaviors.stopped
    }
  }

  //  private def appendEntriesPerNode(): Unit = {
  //
  //  }
}

