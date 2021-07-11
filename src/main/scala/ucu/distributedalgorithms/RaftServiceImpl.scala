package ucu.distributedalgorithms

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.util.Timeout

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.DurationInt

class RaftServiceImpl(raft: ActorRef[Raft.Command])(implicit system: ActorSystem[_]) extends RaftCommunicationService {

  implicit val e: ExecutionContext = system.executionContext

  import akka.actor.typed.scaladsl.AskPattern.{Askable, schedulerFromActorSystem}

  // asking someone requires a timeout and a scheduler, if the timeout hits without response
  // the ask is failed with a TimeoutException
  implicit val timeout: Timeout = 3.seconds

  override def requestVote(in: RequestVoteRequest): Future[RequestVoteResponse] = {
    val operationPerformed: Future[Raft.ServerResponse] =
      raft.ask(Raft.RequestVote(in.term, in.candidateId, in.lastLogIndex, in.lastLogTerm, _))

    operationPerformed.collect {
      case Raft.OK => RequestVoteResponse(1, true)
      case Raft.KO(reason) => RequestVoteResponse(0, false)
    }
  }

  override def appendEntries(in: AppendEntriesRequest): Future[AppendEntriesResponse] = {
    val operationPerformed: Future[Raft.ServerResponse] =
      raft.ask(Raft.AppendEntries(in.term, in.leaderId, in.prevLogIndex, in.prevLogTerm, in.entries, in.leaderCommit, _))

    operationPerformed.collect {
      case Raft.OK => AppendEntriesResponse(1, true)
      case Raft.KO(reason) => AppendEntriesResponse(0, false)
    }
  }
}
