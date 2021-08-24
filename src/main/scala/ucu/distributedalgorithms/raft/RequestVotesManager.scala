package ucu.distributedalgorithms.raft

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, PostStop}
import ucu.distributedalgorithms.raft.Raft.RaftState
import ucu.distributedalgorithms.{Node, RequestVoteResponse}

object RequestVotesManager {
  def apply(cluster: List[Node], state: RaftState, candidate: ActorRef[RequestVoteResponse]): Behavior[Nothing] =
    Behaviors.setup[Nothing] { context =>
      new RequestVotesManager(cluster, state, candidate, context).requestVoteManager()
    }
}

class RequestVotesManager private(
                                   cluster: List[Node],
                                   state: RaftState,
                                   candidate: ActorRef[RequestVoteResponse],
                                   context: ActorContext[Nothing]
                                 ) {

  initVotesPerNode()

  private def requestVoteManager(): Behavior[Nothing] = {
    Behaviors.receiveSignal[Nothing] {
      case (context, postStop: PostStop) =>
        context.log.info("Stop Request Vote Manager per term {}", state.currentTerm)

        Behaviors.stopped
    }
  }

  private def initVotesPerNode(): Unit = {
    cluster.foreach { node =>
      context.spawnAnonymous(RequestVote(node, candidate, state))
    }
  }
}

