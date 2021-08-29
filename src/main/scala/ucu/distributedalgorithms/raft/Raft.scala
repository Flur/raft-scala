package ucu.distributedalgorithms.raft

import akka.actor.typed.{ActorRef, Behavior}
import ucu.distributedalgorithms.raft.Raft.RaftState
import ucu.distributedalgorithms.{LogEntry, Node}

object Raft {

  sealed trait ServerResponse

  final case class OK(data: Option[List[LogEntry]]) extends ServerResponse

  final case class KO(reason: String) extends ServerResponse

  final case class AppendEntry(message: String, replyTo: ActorRef[ServerResponse]) extends RaftCommand

  final case class GetLog(replyTo: ActorRef[ServerResponse]) extends RaftCommand

  final case class RaftState(
                              id: Int = 0,
                              leaderId: Int,
                              // persistent state
                              currentTerm: Int = 1,
                              votedFor: Int = 0,
                              log: List[LogEntry] = List.empty,
                              // volatile state
                              commitIndex: Int = 0,
                              lastApplied: Int = 0
                            )

  final case class LeaderState(
                                nextIndex: Map[Int, Int],
                                matchIndex: Map[Int, Int],
                              )

  sealed trait RaftCommand

  final case class RaftAppendEntriesRequest(
                                             term: Int,
                                             leaderId: Int,
                                             logLength: Int,
                                             prevLogTerm: Int,
                                             entries: Seq[LogEntry],
                                             leaderCommit: Int,
                                             replyTo: ActorRef[RaftCommand]
                                           ) extends RaftCommand

  final case class RaftAppendEntriesResponse(term: Int, success: Boolean, nodeId: Option[Int]) extends RaftCommand

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

  def apply(cluster: List[Node], id: Int): Behavior[RaftCommand] =
    new Raft(
      cluster,
      RaftState(id = id, leaderId = 0),
    ).raft()
}

class Raft private(
                    cluster: List[Node],
                    state: RaftState,
                  ) {

  import Raft._

  private def raft(): Behavior[RaftCommand] = {
    Follower(cluster, state)
  }
}