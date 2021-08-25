package ucu.distributedalgorithms.raft


import akka.actor.typed.{Behavior, PostStop}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, TimerScheduler}
import ucu.distributedalgorithms.{LogEntry, Node}
import ucu.distributedalgorithms.raft.Raft._
import ucu.distributedalgorithms.util.{appendEntries, isLogOkOnAppendEntry, leaderIDToLocation}

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
    val duration = Random.between(4000, 8000).milliseconds

    timers.startSingleTimer(Follower.FollowerTimerKey, FollowerTimeout, duration)

    Behaviors.receiveMessage[RaftCommand] {
      case GetLog(replyTo) =>
        replyTo ! OK(Some(state.log))

        Behaviors.same

      case AppendEntry(message, replyTo) =>
        if (state.leaderId != 0) {
          val leader = leaderIDToLocation(state.leaderId)

          replyTo ! KO(s"Node is not leader, leader is $leader")
        } else {
          replyTo ! KO(s"Node is not leader, no leader is elected")
        }

        Behaviors.same

      case request@RaftAppendEntriesRequest(term, leaderId, prevLogIndex, prevLogTerm, entries, leaderCommit, replyTo) =>
        var newState = state.copy()

        if (term > state.currentTerm) {
          newState = newState.copy(
            currentTerm = term,
            votedFor = 0,
            leaderId = leaderId
          )
        }

        val logOk = isLogOkOnAppendEntry(newState, request)

        if (term == newState.currentTerm && logOk) {
          replyTo ! RaftAppendEntriesResponse(newState.currentTerm, success = true, 0)

          follower(appendEntries(newState, entries, prevLogIndex, leaderCommit))
        } else {
          replyTo ! RaftAppendEntriesResponse(newState.currentTerm, success = false, 0)

          follower(newState)
        }

      case request@RaftRequestVoteRequest(term, candidateId, _, _, replyTo) =>
        context.log.info("Follower received Request Vote")

        request match {
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
    }.receiveSignal {
      case (context, postStop: PostStop) =>
        context.log.info("Follower behaviour terminated on post stop")

        Behaviors.same
    }
  }
}
