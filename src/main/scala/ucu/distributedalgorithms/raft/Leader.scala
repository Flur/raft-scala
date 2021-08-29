package ucu.distributedalgorithms.raft


import akka.actor.typed.scaladsl.{ActorContext, Behaviors, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior, PostStop}
import ucu.distributedalgorithms.raft.AppendEntries.AppendEntriesSuccess
import ucu.distributedalgorithms.raft.Raft._
import ucu.distributedalgorithms.util.{calculateMajority, onAppendEntry, onRequestVote}
import ucu.distributedalgorithms.{LogEntry, Node}

import scala.concurrent.duration.DurationInt
import scala.util.Random

object Leader {
  final case object LeaderTimeoutKey

  def apply(cluster: List[Node], state: RaftState): Behavior[RaftCommand] = Behaviors.withTimers { timers =>
    Behaviors.setup { context =>
      context.log.info("Init Leader with term {}", state.currentTerm)

      val leaderState = LeaderState(
        nextIndex = cluster.map(n => n.id -> state.log.length).toMap + (state.id -> state.log.length),
        matchIndex = cluster.map(n => n.id -> 0).toMap + (state.id -> 0),
      )

      new Leader(cluster, context, timers).leader(state, leaderState)
    }
  }
}

class Leader private(cluster: List[Node], context: ActorContext[RaftCommand], timers: TimerScheduler[RaftCommand]) {

  val appendEntriesResponseAdapter: ActorRef[AppendEntriesSuccess] = context.messageAdapter[AppendEntriesSuccess](r =>
    RaftAppendEntriesResponse(r.response.term, r.response.success, Some(r.nodeId))
  )

  private def leader(
                      state: RaftState,
                      leaderState: LeaderState,
                      appendEntriesManager: Option[ActorRef[Nothing]] = None,
                      replyToOnAppendEntry: Option[(ActorRef[ServerResponse], Int)] = None,
                    ): Behavior[RaftCommand] = {

    val appendEntriesManagerActor: ActorRef[Nothing] = appendEntriesManager match {
      case Some(actor) => actor
      case None =>
        val duration = Random.between(2500, 3000).milliseconds

        timers.startSingleTimer(Leader.LeaderTimeoutKey, LeaderTimeout, duration)

        context.spawnAnonymous[Nothing](AppendEntriesManager(cluster, state, leaderState, appendEntriesResponseAdapter)
        )
    }

    replyToOnAppendEntry match {
      case Some(replyTo) if replyTo._2 >= state.commitIndex  =>
        replyTo._1 ! OK(None)
    }

    Behaviors.receiveMessage[RaftCommand] {
      case GetLog(replyTo) =>
        replyTo ! OK(Some(state.log))

        Behaviors.same

      case AppendEntry(message, replyTo) =>
        context.log.info("Leader received append entry")

        timers.cancel(LeaderTimeout)
        context.stop(appendEntriesManagerActor)

        val newState = state.copy(
          log = LogEntry(state.currentTerm, message) :: state.log
        )
        val newLeaderState = leaderState.copy(
          matchIndex = leaderState.matchIndex + (state.id -> newState.log.length)
        )

        leader(
          newState,
          newLeaderState,
          None,
          Some((replyTo, newState.log.length))
        )

      case request: RaftAppendEntriesRequest =>
        context.log.info("Leader received Append Entries")

        onAppendEntry(
          state,
          request,
          cluster,
          isCandidateRole = false,
          newState => leader(newState, leaderState, appendEntriesManager),
          () => {
            context.stop(appendEntriesManagerActor)
            timers.cancel(LeaderTimeout)
          }
        )

      case request: RaftRequestVoteRequest =>
        context.log.info("Leader received Request Vote")

        context.stop(appendEntriesManagerActor)
        timers.cancel(LeaderTimeout)

        onRequestVote(
          state,
          request,
          cluster,
          s => leader(s, leaderState, appendEntriesManager),
          () => {
            context.stop(appendEntriesManagerActor)
            timers.cancel(LeaderTimeout)
          }
        )

      case RaftAppendEntriesResponse(term, success, nodeIdOpt) =>
        context.log.info("Received append entries response")

        val nodeId: Int = nodeIdOpt.getOrElse(0)
        var newState = state.copy()
        var newLeaderState = leaderState.copy()

        if (term == newState.currentTerm) {
          if (success) {
            newLeaderState = newLeaderState.copy(
              nextIndex = newLeaderState.nextIndex + (nodeId -> newState.log.length),
              matchIndex = newLeaderState.matchIndex + (nodeId -> newState.log.length)
            )

            newState = commitLogEntries(newState, leaderState, cluster)
          } else if (newLeaderState.nextIndex.getOrElse(nodeId, 0) > 0) {
            val nextIndex = newLeaderState.nextIndex.getOrElse(nodeId, 0)

            newLeaderState = newLeaderState.copy(
              nextIndex = newLeaderState.nextIndex + (nodeId -> (nextIndex - 1))
            )
          }

          timers.cancel(LeaderTimeout)
          context.stop(appendEntriesManagerActor)

          leader(state, newLeaderState)
        } else if (term > newState.currentTerm) {
          context.stop(appendEntriesManagerActor)
          timers.cancel(LeaderTimeout)

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

  private def acks(leaderState: LeaderState)(cluster: List[Node])(length: Int): Int = {
    cluster
      .map(n => leaderState.matchIndex.getOrElse(n.id, 0))
      .count(_ >= length)
  }

  private def commitLogEntries(state: RaftState, leaderState: LeaderState, cluster: List[Node]): RaftState = {
    val majority = calculateMajority(state)
    val a: Int => Int  = acks(leaderState)(cluster)
    var newState = state.copy()

    val ready = List.range(1, state.log.length)
      .map(len => a(len))
      .filter(a => a >= majority)

    if (ready.nonEmpty && ready.max > newState.commitIndex && newState.log(ready.max - 1).term == newState.currentTerm) {
      newState = newState.copy(
        commitIndex = ready.max
      )
    }

    newState
  }
}
