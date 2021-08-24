package ucu.distributedalgorithms.raft

import akka.actor.typed.scaladsl.{ActorContext, Behaviors, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior}
import ucu.distributedalgorithms.raft.Raft._
import ucu.distributedalgorithms.util.calculateMajority
import ucu.distributedalgorithms.{Node, RequestVoteResponse}

import scala.concurrent.duration.DurationInt
import scala.util.Random

object Candidate {
  final case object CandidateTimerKey

  def apply(cluster: List[Node], state: RaftState): Behavior[RaftCommand] = Behaviors.withTimers { timers =>
    Behaviors.setup { context =>
      val newState = state.copy(currentTerm = state.currentTerm + 1, votedFor = state.id)

      context.log.info("Init Candidate with term {}", newState.currentTerm)

      new Candidate(context, cluster, timers).candidate(1, newState)
    }
  }
}

class Candidate private(
                         context: ActorContext[RaftCommand],
                         cluster: List[Node],
                         timers: TimerScheduler[RaftCommand],
                       ) {

  startSingleTimer()

  private def candidate(votes: Int, state: RaftState): Behavior[RaftCommand] = {
    val requestVoteResponseAdapter =
      context.messageAdapter[RequestVoteResponse](rsp => RaftRequestVoteResponse(rsp.term, rsp.voteGranted))

    val requestVotesManager = context.spawnAnonymous[Nothing](
      RequestVotesManager(cluster, state, requestVoteResponseAdapter))

    Behaviors.receiveMessage {
      case request@RaftAppendEntriesRequest(term, leaderId, _, _, _, _, replyTo) => request match {
        case request if term > state.currentTerm =>
          candidateCleanup(requestVotesManager)

          replyTo ! RaftAppendEntriesResponse(term, success = false)

          Follower(
            cluster,
            state.copy(currentTerm = term, votedFor = 0, leaderId = leaderId)
          )

        case request if term == state.currentTerm =>
          candidateCleanup(requestVotesManager)

          replyTo ! RaftAppendEntriesResponse(state.currentTerm, success = false)

          Follower(
            cluster,
            state.copy(leaderId = leaderId)
          )
        case request if term < state.currentTerm =>
          replyTo ! RaftAppendEntriesResponse(state.currentTerm, success = false)

          Behaviors.same
      }

      case request@RaftRequestVoteRequest(term, candidateId, _, _, replyTo) => request match {
        case request if term > state.currentTerm =>
          candidateCleanup(requestVotesManager)

          replyTo ! RaftRequestVoteResponse(term, voteGranted = true)

          Follower(
            cluster,
            state.copy(currentTerm = term, votedFor = candidateId)
          )

        case _ =>
          replyTo ! RaftRequestVoteResponse(state.currentTerm, voteGranted = false)

          Behaviors.same
      }

      case response@RaftRequestVoteResponse(term, voteGranted) => response match {
        case response if state.currentTerm == term && voteGranted =>
          val newVotes = votes + 1

          if (newVotes >= calculateMajority(state)) {
            candidateCleanup(requestVotesManager)

             Leader(cluster, state.copy(leaderId = state.id))
          } else {
            candidateCleanup(requestVotesManager)

            candidate(newVotes, state)
          }

        case response if term > state.currentTerm =>
          candidateCleanup(requestVotesManager)

          Follower(cluster, state.copy(currentTerm = term, votedFor = 0))

        case _ =>
          Behaviors.same
      }

      case CandidateTimeout =>
        candidateCleanup(requestVotesManager)

        val newState = state.copy(currentTerm = state.currentTerm + 1)

        context.log.info("Candidate timeout, new candidate with term {}", newState.currentTerm)

        startSingleTimer()
        candidate(1, newState)
    }
  }

  private def startSingleTimer(): Unit = {
    // todo 150-300 ms
    val duration = Random.between(1500, 3000).milliseconds

    timers.startSingleTimer(Candidate.CandidateTimerKey, CandidateTimeout, duration)
  }

  private def candidateCleanup(requestVotesManager: ActorRef[Nothing]): Unit = {
    context.stop(requestVotesManager)
    timers.cancel(Candidate.CandidateTimerKey)
  }
}
