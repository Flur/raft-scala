package ucu.distributedalgorithms.raft

import akka.actor.typed.scaladsl.{ActorContext, Behaviors, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior, PostStop}
import ucu.distributedalgorithms.raft.Raft._
import ucu.distributedalgorithms.util.{appendEntries, calculateMajority, isLogOkOnAppendEntry, leaderIDToLocation, onAppendEntry, onRequestVote}
import ucu.distributedalgorithms.{Node, RequestVoteResponse}

import scala.concurrent.duration.DurationInt
import scala.util.Random

object Candidate {
  final case object CandidateTimerKey

  def apply(cluster: List[Node], state: RaftState): Behavior[RaftCommand] = Behaviors.withTimers { timers =>
    Behaviors.setup { context =>
      val newState = state.copy(currentTerm = state.currentTerm + 1, votedFor = state.id, leaderId = 0)

      context.log.info("Init Candidate with term {}", newState.currentTerm)

      new Candidate(context, cluster, timers).candidate(1, newState, None)
    }
  }
}

class Candidate private(
                         context: ActorContext[RaftCommand],
                         cluster: List[Node],
                         timers: TimerScheduler[RaftCommand],
                       ) {

  startSingleTimer()

  private def candidate(votes: Int, state: RaftState, requestVotes: Option[ActorRef[Nothing]]): Behavior[RaftCommand] = {
    val requestVotesActor: ActorRef[Nothing] = requestVotes match {
      case Some(actor) => actor
      case None =>
        val requestVoteResponseAdapter =
          context.messageAdapter[RequestVoteResponse](rsp => RaftRequestVoteResponse(rsp.term, rsp.voteGranted))

        context.spawnAnonymous[Nothing](
          RequestVotesManager(cluster, state, requestVoteResponseAdapter)
        )
    }

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
        context.log.info("Candidate received Append Entries")

        onAppendEntry(
          state,
          request,
          cluster,
          isCandidateRole = true,
          newState => candidate(votes, newState, Some(requestVotesActor)),
          () =>
            candidateCleanup(requestVotesActor),
          context
        )

      case request: RaftRequestVoteRequest =>
        context.log.info("Candidate received Request Vote with term {}")

        onRequestVote(
          state,
          request,
          cluster,
          newState => candidate(votes, newState, Some(requestVotesActor)),
          () =>
            candidateCleanup(requestVotesActor)
        )

      case response@RaftRequestVoteResponse(term, voteGranted) =>
        context.log.info("Candidate received RaftRequestVoteResponse term {} voteGranted {}", term, voteGranted)

        response match {
          case response if state.currentTerm == term && voteGranted =>
            val newVotes = votes + 1

            if (newVotes >= calculateMajority(cluster)) {
              candidateCleanup(requestVotesActor)

              Leader(cluster, state.copy(leaderId = state.id))
            } else {
              candidateCleanup(requestVotesActor)

              candidate(newVotes, state, Some(requestVotesActor))
            }

          case response if term > state.currentTerm =>
            candidateCleanup(requestVotesActor)

            Follower(cluster, state.copy(currentTerm = term, votedFor = 0))

          case _ =>
            Behaviors.same
        }

      case CandidateTimeout =>
        candidateCleanup(requestVotesActor)

        val newState = state.copy(currentTerm = state.currentTerm + 1)

        context.log.info("Candidate timeout, new candidate with term {}", newState.currentTerm)

        startSingleTimer()
        candidate(1, newState, None)

      case _ => Behaviors.same

    }.receiveSignal {
      case (context, postStop: PostStop) =>
        context.log.info("Candidate behaviour terminated on post stop")

        Behaviors.same
    }
  }

  private def startSingleTimer(): Unit = {
    // todo 150-300 ms
    val duration = Random.between(3000, 6000).milliseconds

    timers.startSingleTimer(Candidate.CandidateTimerKey, CandidateTimeout, duration)
  }

  private def candidateCleanup(requestVotesManager: ActorRef[Nothing]): Unit = {
    context.stop(requestVotesManager)
    timers.cancel(Candidate.CandidateTimerKey)
  }
}
