package ucu.distributedalgorithms.raft


import akka.actor.typed.scaladsl.{ActorContext, Behaviors, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior, PostStop}
import ucu.distributedalgorithms.raft.AppendEntries.AppendEntriesSuccess
import ucu.distributedalgorithms.raft.Raft._
import ucu.distributedalgorithms.util.{appendEntries, calculateMajority, isLogOkOnAppendEntry}
import ucu.distributedalgorithms.{LogEntry, Node}

import scala.concurrent.duration.DurationInt
import scala.util.Random

object Leader {
  final case object LeaderTimeoutKey

  def apply(cluster: List[Node], state: RaftState): Behavior[RaftCommand] = Behaviors.withTimers { timers =>
    Behaviors.setup { context =>
      context.log.info("Init Leader with term {}", state.currentTerm)

      val leaderState = VolatileLeaderState(
        cluster.map(_ => state.log.length),
        cluster.map(_ => 0),
        state.log.length,
        0
      )

      new Leader(cluster, context, timers).leader(state, leaderState)
    }
  }
}

class Leader private(cluster: List[Node], context: ActorContext[RaftCommand], timers: TimerScheduler[RaftCommand]) {

  val appendEntriesResponseAdapter: ActorRef[AppendEntriesSuccess] = context.messageAdapter[AppendEntriesSuccess](r =>
    RaftAppendEntriesResponse(r.response.term, r.response.success, r.followerIndexInCluster)
  )

  private def leader(
                      state: RaftState,
                      leaderState: VolatileLeaderState,
                      appendEntriesManager: Option[ActorRef[Nothing]] = None,
                      replyToOnAppendEntries: Option[ActorRef[ServerResponse]] = None,
                    ): Behavior[RaftCommand] = {

    val appendEntriesManagerActor: ActorRef[Nothing] = appendEntriesManager match {
      case Some(actor) => actor
      case None =>
        val duration = Random.between(2500, 3000).milliseconds

        timers.startSingleTimer(Leader.LeaderTimeoutKey, LeaderTimeout, duration)

        context.spawnAnonymous[Nothing](AppendEntriesManager(cluster, state, leaderState, appendEntriesResponseAdapter)
        )
    }

    Behaviors.receiveMessage[RaftCommand] {
      case GetLog(replyTo) =>
        replyTo ! OK(Some(state.log))

        Behaviors.same

      case AppendEntry(message, replyTo) =>
        context.log.info("Leader received append entry")

        context.stop(appendEntriesManagerActor)

        replyTo ! OK(None)

        val newLog = LogEntry(state.currentTerm, message) :: state.log

        leader(
          state.copy(
            log = newLog
          ),
          leaderState.copy(
            leaderNextIndex = newLog.length
          ),
          None,
          Some(replyTo),
        )

      case request@RaftAppendEntriesRequest(term, leaderId, prevLogIndex, prevLogTerm, entries, leaderCommit, replyTo) =>
        var newState = state.copy()

        if (term > newState.currentTerm) {
          newState = newState.copy(
            currentTerm = term,
            leaderId = leaderId,
            votedFor = 0,
          )
        }

        val logOk = isLogOkOnAppendEntry(newState, request)

        if (term == newState.currentTerm && logOk) {

          replyTo ! RaftAppendEntriesResponse(newState.currentTerm, success = true, 0)

          Follower(cluster, appendEntries(newState, entries, prevLogIndex, leaderCommit))
        } else {
          replyTo ! RaftAppendEntriesResponse(newState.currentTerm, success = false, 0)

          Follower(cluster, newState)
        }

      case request@RaftRequestVoteRequest(term, candidateId, _, _, replyTo) =>
        context.log.info("Leader received Request Vote")

        request match {
          case request if term > state.currentTerm =>
            replyTo ! RaftAppendEntriesResponse(term, success = true, 0)

            Follower(
              cluster,
              state.copy(
                currentTerm = term,
                votedFor = candidateId,
              )
            )
        }

      case RaftAppendEntriesResponse(term, success, followerIndexInCluster) =>
        context.log.info("Received append entries response")

        var newState = state.copy()
        var newLeaderState = leaderState.copy()

        if (term == newState.currentTerm) {
          if (success) {
            val newLogLength = newState.log.length

            newLeaderState = newLeaderState.copy(
              nextIndex = newLeaderState.nextIndex.patch(followerIndexInCluster, Seq(newLogLength), 1),
              matchIndex = newLeaderState.matchIndex.patch(followerIndexInCluster, Seq(newLogLength), 1)
            )

            newState = commitLogEntries(newState)
          } else if (newLeaderState.nextIndex.lift(followerIndexInCluster).getOrElse(0) > 0) {
            val nextIndex = newLeaderState.nextIndex.lift(followerIndexInCluster).getOrElse(0)

            newLeaderState = newLeaderState.copy(
              nextIndex = newLeaderState.nextIndex.patch(followerIndexInCluster, Seq(nextIndex - 1), 1)
            )
          }

          leader(state, leaderState, appendEntriesManager)
        } else if (term > newState.currentTerm) {
          Follower(
            cluster,
            newState.copy(
              currentTerm = term,
              votedFor = 0,
            )
          )
        } else {
          Behaviors.same
        }

      case LeaderTimeout =>
        context.stop(appendEntriesManagerActor)

        leader(state, leaderState)
    }.receiveSignal {
      case (context, postStop: PostStop) =>
        timers.cancel(LeaderTimeout)

        context.log.info("Leader behaviour terminated on post stop")

        Behaviors.same
    }
  }

  private def acks(state: RaftState, length: Int): Unit = {
    // todo
  }

  private def commitLogEntries(state: RaftState): RaftState = {
    var majority = calculateMajority(state)

    // todo
    state
  }
}
