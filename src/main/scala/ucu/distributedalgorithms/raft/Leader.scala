package ucu.distributedalgorithms.raft


import akka.actor.typed.{Behavior, PostStop}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, TimerScheduler}
import ucu.distributedalgorithms.{AppendEntriesResponse, Node}
import ucu.distributedalgorithms.raft.Raft._

import scala.concurrent.duration.DurationInt
import scala.util.Random

object Leader {
  final case object LeaderTimeoutKey

  def apply(cluster: List[Node], state: RaftState): Behavior[RaftCommand] = Behaviors.withTimers { timers =>
    Behaviors.setup { context =>
      context.log.info("Init Leader with term {}", state.currentTerm)

      new Leader(cluster, context, timers).leader(state)
    }
  }
}

class Leader private(cluster: List[Node], context: ActorContext[RaftCommand], timers: TimerScheduler[RaftCommand]) {
  private def leader(state: RaftState): Behavior[RaftCommand] = {
    val appendEntriesResponseAdapter = context.messageAdapter[AppendEntriesResponse](r =>
      RaftAppendEntriesResponse(r.term, r.success)
    )

    val appendEntryManager = context.spawnAnonymous[Nothing](
      AppendEntriesManager(cluster, state, appendEntriesResponseAdapter)
    )

    // todo timeout ??
    val duration = Random.between(100, 900).milliseconds

    timers.startSingleTimer(Leader.LeaderTimeoutKey, LeaderTimeout, duration)

    Behaviors.receiveMessage[RaftCommand] {
      case request@RaftAppendEntriesRequest(term, leaderId, _, _, _, _, replyTo) => request match {
        case request if term > state.currentTerm =>
          replyTo ! RaftAppendEntriesResponse(term, success = false)

          Follower(
            cluster,
            state.copy(
              currentTerm = term,
              leaderId = leaderId,
              votedFor = 0,
            )
          )
        case _ =>
          replyTo ! RaftAppendEntriesResponse(state.currentTerm, success = false)

          Behaviors.same
      }

      case request@RaftRequestVoteRequest(term, candidateId, _, _, replyTo) =>
        context.log.info("Leader received Request Vote")

        request match {
          case request if term > state.currentTerm =>
            replyTo ! RaftAppendEntriesResponse(term, success = true)

            Follower(
              cluster,
              state.copy(
                currentTerm = term,
                votedFor = candidateId,
              )
            )
        }

      case RaftAppendEntriesResponse(term, success) =>
        context.log.info("Received append entries response")

        Behaviors.same

      case LeaderTimeout =>
        context.log.info("Leader timeout")

        context.stop(appendEntryManager)

        leader(state)
    }.receiveSignal {
      case (context, postStop: PostStop) =>
        context.log.info("Leader behaviour terminated on post stop")

        Behaviors.same
    }
  }
}
