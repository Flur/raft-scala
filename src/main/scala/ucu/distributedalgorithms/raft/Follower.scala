package ucu.distributedalgorithms.raft


import akka.actor.typed.{Behavior, PostStop}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, TimerScheduler}
import ucu.distributedalgorithms.{LogEntry, Node}
import ucu.distributedalgorithms.raft.Raft._
import ucu.distributedalgorithms.util.{appendEntries, isLogOkOnAppendEntry, leaderIDToLocation, onAppendEntry, onRequestVote}

import scala.concurrent.duration.DurationInt
import scala.util.Random

object Follower {
  final case object FollowerTimerKey

  def apply(cluster: List[Node], state: RaftState): Behavior[RaftCommand] = Behaviors.withTimers { timers =>
    Behaviors.setup { context =>
      context.log.info("Init Follower with term {}", state.currentTerm)
      context.log.info("Init Follower with term {}", state.leaderId)

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

      case request: RaftAppendEntriesRequest =>
        context.log.info("Follower received Append Entries currentTerm {} logLength {} ",
          state.currentTerm, state.log.length
        )

        onAppendEntry(
          state,
          request,
          cluster,
          isCandidateRole = false,
          follower,
          () =>
            timers.cancel(FollowerTimeout)
        )

      case request: RaftRequestVoteRequest =>
        context.log.info("Follower received Request Vote")

        onRequestVote(
          state,
          request,
          cluster,
          follower,
          () =>
            timers.cancel(FollowerTimeout)
        )

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
