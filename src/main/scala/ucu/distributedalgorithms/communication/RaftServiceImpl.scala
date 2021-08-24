package ucu.distributedalgorithms.communication

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.util.Timeout
import ucu.distributedalgorithms._
import ucu.distributedalgorithms.raft.Raft._

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}

class RaftServiceImpl(raft: ActorRef[RaftCommand])(implicit system: ActorSystem[_]) extends RaftCommunicationService {

  implicit val e: ExecutionContext = system.executionContext

  import akka.actor.typed.scaladsl.AskPattern.{Askable, schedulerFromActorSystem}

  // asking someone requires a timeout and a scheduler, if the timeout hits without response
  // the ask is failed with a TimeoutException
  implicit val timeout: Timeout = 3.seconds

  override def requestVote(in: RequestVoteRequest): Future[RequestVoteResponse] = {
    system.log.info("Raft gRPC Service Received Request vote term:{} candidateId:{} lastLogIndex:{} lastLogTerm:{}",
      in.term, in.candidateId, in.lastLogIndex, in.lastLogTerm
    )

    val operationPerformed: Future[RaftCommand] =
      raft.ask(
        RaftRequestVoteRequest(in.term, in.candidateId, in.lastLogIndex, in.lastLogTerm, _)
      )

    operationPerformed.collect {
      case RaftRequestVoteResponse(t, v) =>
        system.log.info("Raft gRPC Service Responded with RequestVoteResponse term:{} voteGranted:{}", t, v)

        RequestVoteResponse(t, v)
    }
  }

  override def appendEntries(in: AppendEntriesRequest): Future[AppendEntriesResponse] = {
    system.log.info("Raft gRPC Service Received AppendEntries term:{} leaderId:{} prevLogIndex:{} prevLogTerm:{} entries:{} leaderCommit:{}",
      in.term, in.leaderId, in.prevLogIndex, in.prevLogTerm, in.entries, in.leaderCommit
    )

    val operationPerformed: Future[RaftCommand] =
      raft.ask(RaftAppendEntriesRequest(in.term, in.leaderId, in.prevLogIndex, in.prevLogTerm, in.entries, in.leaderCommit, _))

    operationPerformed.collect {
      case RaftAppendEntriesResponse(t, s) =>
        system.log.info("Raft gRPC Service Responded with AppendEntriesResponse term:{} success:{}", t, s)

        AppendEntriesResponse(t, s)
    }
  }
}
