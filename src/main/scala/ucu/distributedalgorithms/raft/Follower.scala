package ucu.distributedalgorithms.raft


import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, TimerScheduler}
import ucu.distributedalgorithms.Node
import ucu.distributedalgorithms.raft.Raft._

import scala.concurrent.duration.DurationInt
import scala.util.Random

object Follower {
  final case object FollowerTimerKey

  def apply(cluster: List[Node], state: RaftState): Behavior[RaftCommand] = Behaviors.withTimers { timers =>
    Behaviors.setup { context =>
      context.log.info("Init Follower with term {}", state.currentTerm)

      new Follower(context, timers, cluster).follower(state)
    }
  }
}

class Follower private(
                        context: ActorContext[RaftCommand],
                        timers: TimerScheduler[RaftCommand],
                        cluster: List[Node],
                      ) {
  private def follower(state: RaftState): Behavior[RaftCommand] = {
    // todo timeout 100 - 500 m
    val duration = Random.between(1000, 5000).milliseconds

    timers.startSingleTimer(Follower.FollowerTimerKey, FollowerTimeout, duration)

    Behaviors.receiveMessage {
      case request@RaftAppendEntriesRequest(term, leaderId, _, _, _, _, replyTo) => request match {
        case request if term > state.currentTerm =>
          val newState = state.copy(currentTerm = term, votedFor = 0, leaderId = leaderId)

          replyTo ! RaftAppendEntriesResponse(newState.currentTerm, success = false)

          follower(newState)

        case _ =>
          replyTo ! RaftAppendEntriesResponse(state.currentTerm, success = false)

          follower(state)
      }

      case request@RaftRequestVoteRequest(term, candidateId, _, _, replyTo) => request match {
        case request if term < state.currentTerm =>
          replyTo ! RaftRequestVoteResponse(state.currentTerm, voteGranted = false)

          follower(state)

        case request if term > state.currentTerm ||
          (term == state.currentTerm && (state.votedFor == 0 || state.votedFor == candidateId)) =>
          replyTo ! RaftRequestVoteResponse(term, voteGranted = true)

          follower(state.copy(votedFor = candidateId, currentTerm = term))

        case _ =>
          replyTo ! RaftRequestVoteResponse(state.currentTerm, voteGranted = false)

          follower(state)
      }

      case FollowerTimeout =>
        timers.cancel(FollowerTimeout)

        context.log.info("Election timeout, switch to candidate")

        Candidate(cluster, state)
    }
  }
}
