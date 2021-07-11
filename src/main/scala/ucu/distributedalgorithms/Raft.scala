package ucu.distributedalgorithms

import akka.actor.typed.{ActorRef, Behavior, PostStop, Signal}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}

object Raft {
  sealed trait ServerResponse

  final case object OK extends ServerResponse
  final case class KO(reason: String) extends ServerResponse


  sealed trait Command

  final case class AppendEntry(message: String, replyTo: ActorRef[ServerResponse]) extends Command

  // todo use other case class instead LogEntry here? (entries: Array[LogEntry])
  final case class AppendEntries(term: Int, leaderId: Int, prevLogIndex: Int, prevLogTerm: Int,
                                 entries: Seq[LogEntry], leaderCommit: Int,
                                 replyTo: ActorRef[ServerResponse]) extends Command
  final case class RequestVote(term: Int, candidateId: Int, lastLogIndex: Int, lastLogTerm: Int,
                               replyTo: ActorRef[ServerResponse]) extends Command
  final case class RequestVoteResult(term: Int, voteGranted: Boolean) extends Command

  def apply(): Behavior[Command] = Behaviors.setup(context => new Raft(context))
}

class Raft(context: ActorContext[Raft.Command]) extends AbstractBehavior[Raft.Command](context) {
  import Raft._


  override def onMessage(msg: Raft.Command): Behavior[Raft.Command] = msg match {
    case RequestVote(term, candidateId, lastLogIndex, lastLogTerm, replyTo) =>
      context.log.info(
        "RequestVote: term {}, candidateId {}, lastLogIndex {}, lastLogTerm {}",
        term, candidateId, lastLogIndex, lastLogTerm
      )
      replyTo ! OK
      this
    case AppendEntries(term, leaderId, prevLogIndex, prevLogTerm, entries, leaderCommit, replyTo) =>
      println(entries.mkString(","))
      context.log.info(
        "Append entries: term {}, leaderId {}, prevLogIndex {}, prevLogTerm {},  entries {}, leaderCommit {}",
        term, leaderId, prevLogIndex, prevLogTerm, entries.mkString(","), leaderCommit
      )
      replyTo ! OK
      this
    case AppendEntry(message, replyTo) =>
      context.log.info("Append log message: {}", message)
      replyTo ! OK
      this

  }

  override def onSignal: PartialFunction[Signal, Behavior[Raft.Command]] = {
    case PostStop =>
      context.log.info("Raft Application stopped")
      this
  }
}
