package ucu.distributedalgorithms.raft

import akka.actor.typed.scaladsl.{Behaviors, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior, PostStop}
import ucu.distributedalgorithms.raft.Raft.{RaftCommand, RaftState}
import ucu.distributedalgorithms.{LogEntry, Node}

import scala.concurrent.duration.DurationInt

object Raft {

  sealed trait ServerResponse

  final case object OK extends ServerResponse

  final case class KO(reason: String) extends ServerResponse

  final case class AppendEntry(text: String, replyTo: ActorRef[ServerResponse]) extends RaftCommand

  final case class RaftState(
                              id: Int = 0,
                              leaderId: Int = 0,
                              // persistent state
                              currentTerm: Int = 1,
                              votedFor: Int = 0,
                              log: List[LogEntry] = List.empty,
                              // volatile state
                              commitIndex: Int = 0,
                              lastApplied: Int = 0
                            )

  sealed trait RaftCommand


  final case class RaftAppendEntriesRequest(
                                             term: Int,
                                             leaderId: Int,
                                             prevLogIndex: Int,
                                             prevLogTerm: Int,
                                             entries: Seq[LogEntry],
                                             leaderCommit: Int,
                                             replyTo: ActorRef[RaftCommand]
                                           ) extends RaftCommand

  final case class RaftAppendEntriesResponse(term: Int, success: Boolean) extends RaftCommand

  final case class RaftRequestVoteRequest(
                                           term: Int,
                                           candidateId: Int,
                                           lastLogIndex: Int,
                                           lastLogTerm: Int,
                                           replyTo: ActorRef[RaftCommand]
                                         ) extends RaftCommand

  final case class RaftRequestVoteResponse(term: Int, voteGranted: Boolean) extends RaftCommand

  final case object CandidateTimeout extends RaftCommand

  final case object FollowerTimeout extends RaftCommand

  final case object LeaderTimeout extends RaftCommand

  final case object InitTimeout extends RaftCommand

  final case object RaftTimeoutKey

  def apply(cluster: List[Node], id: Int): Behavior[RaftCommand] = Behaviors.withTimers { timers =>
    new Raft(
      cluster,
      RaftState(id = id),
      timers
    ).raft()

  }
}

class Raft private(
                    cluster: List[Node],
                    state: RaftState,
                    timers: TimerScheduler[RaftCommand],
                  ) {

  import Raft._

//  timers.startSingleTimer(RaftTimeoutKey, InitTimeout, 5.seconds)

  private def raft(): Behavior[RaftCommand] = {
    Follower(cluster, state)
  }
//    Behaviors.receiveMessage[RaftCommand] {
//    case InitTimeout =>
//      timers.cancel(RaftTimeoutKey)
//
//      Follower(cluster, state)
//    case _ =>
//      Behaviors.same
//  }.receiveSignal {
//    case (context, postStop: PostStop) =>
//      context.log.info("Raft behaviour terminated on post stop")
//
//      Behaviors.same
//  }
}