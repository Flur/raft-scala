package ucu.distributedalgorithms

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import ucu.distributedalgorithms.raft.Follower
import ucu.distributedalgorithms.raft.Raft.{RaftAppendEntriesRequest, RaftAppendEntriesResponse, RaftCommand, RaftRequestVoteRequest, RaftRequestVoteResponse, RaftState}

import scala.util.Try

case class Node(id: Int, host: String, port: Int)

package object util {
  // todo default id
  val DEFAULT_ID = 0
  val DEFAULT_PORT = 8080
  val DEFAULT_HOST = "127.0.0.1"

  // todo
  def getListOfNodes(nodesSysEnvName: String): List[Node] = {
    val list: Option[List[String]] = readSysEnv(nodesSysEnvName).map(stringToList())
    val listOfNodes: List[Node] = list.map(_.map(stringToNode())).getOrElse(List())

    listOfNodes
  }

  def stringToNode(regex: String = ":")(string: String): Node = {
    val array: Array[String] = string.split(regex)

    val host: String = array.lift(0).getOrElse(DEFAULT_HOST)
    val port: Int = array.lift(1).flatMap(stringToInt).getOrElse(DEFAULT_PORT)
    val id: Int = array.lift(2).flatMap(stringToInt).getOrElse(DEFAULT_PORT)

    Node(id, host, port)
  }

  def readPortAndHost(portSysEnvName: String, hostSysEnvName: String): (String, Int) = {
    val port: Option[Int] = readSysEnv(portSysEnvName)
      .flatMap(stringToInt)
    val host = readSysEnv(hostSysEnvName)

    (host, port) match {
      case (Some(host), Some(port)) => (host, port)
      case (Some(host), None) => (host, DEFAULT_PORT)
      case (None, Some(port)) => (DEFAULT_HOST, port)
      case _ => (DEFAULT_HOST, DEFAULT_PORT)
    }
  }

  def readSysEnv(sysEnv: String): Option[String] = {
    sys.env.get(sysEnv)
  }

  def stringToInt(string: String): Option[Int] = {
    Try(string.toInt).toOption
  }

  def stringToList(regex: String = ",")(string: String): List[String] = {
    string.split(regex)
      .map(_.trim)
      .toList
  }

  def getLastLogIndex(state: RaftState): Int = {
    var lastLogIndex = 0

    if (state.log.nonEmpty) {
      lastLogIndex = state.log.length - 1
    }

    lastLogIndex
  }

  def getLastLogTerm(state: RaftState): Int = {
    Try(state.log.last).toOption match {
      case Some(logEntry) => logEntry.term
      case None => 0
    }
  }

  def calculateMajority(state: RaftState): Int = {
    // + 1 add current node
    ((state.log.length + 1) / 2.0).ceil.toInt
  }

  def leaderIDToLocation(leaderId: Int): String = {
    // todo move to env variables
    val leaderIdToLocation: Map[Int, String] = Map(
      (1 -> "node1:8081"),
      (2 -> "node2:8082"),
      (3 -> "node3:8083"),
      (4 -> "node4:8084"),
      (5 -> "node5:8085")
    )

    leaderIdToLocation.getOrElse(leaderId, "")
  }

  def isLogOkOnAppendEntry(state: RaftState, appendEntriesRequest: RaftAppendEntriesRequest): Boolean = {
    var currentPrevLogIndex = state.log.length - 1

    if (currentPrevLogIndex < 0) {
      currentPrevLogIndex = 0
    }

    val currentPrevLogTerm = state.log.lift(currentPrevLogIndex).getOrElse(0)

    val leaderLogIsEmpty = appendEntriesRequest.logLength == 0 && appendEntriesRequest.prevLogTerm == 0

    currentPrevLogIndex >= appendEntriesRequest.logLength && (leaderLogIsEmpty || currentPrevLogTerm == appendEntriesRequest.prevLogTerm)
  }

  //  ∧ &&
  //  ∨ ||
//  function AppendEntries(logLength, leaderCommit, entries)
//    if entries.length > 0 ∧ log.length > logLength then
//      if log[logLength].term 6= entries[0].term then
//        log := hlog[0], log[1], . . . , log[logLength − 1]i
//      end if
//    end if

//    if logLength + entries.length > log.length then
//      for i := log.length − logLength to entries.length − 1 do
//        append entries[i] to log
//      end for
//    end if

//    if leaderCommit > commitLength then
//      for i := commitLength to leaderCommit − 1 do
//       deliver log[i].msg to the application
//      end for
//    commitLength := leaderCommit
//    end if

//    end function
  def appendEntries(state: RaftState, logLength: Int, leaderCommit: Int, entries: Seq[LogEntry]): RaftState = {
    var newState = state.copy()

    // if log of this node is longer that leader log, cut tail of the log
    if (entries.nonEmpty && newState.log.length > logLength) {
      if (newState.log(logLength).term != entries.head.term) {
        newState = newState.copy(
          log = newState.log.slice(0, logLength - 1)
        )
      }
    }

    // if leader log is longer that local, append entries
    if ((logLength + entries.length) > newState.log.length) {
      newState = newState.copy(
        log = newState.log.concat(entries)
      )
    }

    if (leaderCommit > state.commitIndex) {
      newState = newState.copy(
        commitIndex = leaderCommit
      )
    }

    newState
  }

  //  ∧ &&
  //  ∨ ||

  //  on receiving (LogRequest, leaderId, term, logLength, logTerm,
  //    leaderCommit, entries) at node nodeId do
  //    if term > currentTerm then
  //      currentTerm := term; votedFor := null
  //      currentRole := follower; currentLeader := leaderId
  //    end if
  //    if term = currentTerm ∧ currentRole = candidate then
  //      currentRole := follower; currentLeader := leaderId
  //    end if
  //    logOk := (log.length ≥ logLength) ∧
  //      (logLength = 0 ∨ logTerm = log[logLength − 1].term)
  //  if term = currentTerm ∧ logOk then
  //    AppendEntries(logLength, leaderCommit, entries)
  //  ack := logLength + entries.length
  //  send (LogResponse, nodeId, currentTerm, ack,true) to leaderId
  //  else
  //  send (LogResponse, nodeId, currentTerm, 0, false) to leaderId
  //  end if
  //    end on
  def onAppendEntry(
                     state: RaftState,
                     request: RaftAppendEntriesRequest,
                     cluster: List[Node],
                     isCandidateRole: Boolean,
                     getSameBehaviour: RaftState => Behavior[RaftCommand],
                     onChangeCandidateState: () => Unit
                   ): Behavior[RaftCommand] = {
    val RaftAppendEntriesRequest(term, leaderId, logLength, prevLogTerm, entries, leaderCommit, replyTo) = request
    var newState = state.copy()
    var isFollower = false;

    if (term > newState.currentTerm) {
      newState = newState.copy(
        currentTerm = term,
        votedFor = 0,
        leaderId = leaderId
      )
      isFollower = true
    }

    if (term == newState.currentTerm && isCandidateRole) {
      newState = newState.copy(
        leaderId = leaderId,
      )
      isFollower = true
    }

    val logOk = (newState.log.length >= logLength) && (
      logLength == 0 || prevLogTerm == newState.log(logLength - 1).term
      )

    if (term == newState.currentTerm && logOk) {
      newState = appendEntries(newState, logLength, leaderCommit, entries)

      replyTo ! RaftAppendEntriesResponse(newState.currentTerm, success = true, None)
    } else {
      replyTo ! RaftAppendEntriesResponse(newState.currentTerm, success = false, None)
    }

    if (isFollower) {
      onChangeCandidateState()

      Follower(cluster, newState)
    } else {
      getSameBehaviour(newState)
    }
  }

  //  ∧ &&
  //  ∨ ||
  //on receiving (VoteRequest, cId, cTerm, cLogLength, cLogTerm)
  //  at node nodeId do
  //    myLogTerm := log[log.length − 1].term
  //  logOk := (cLogTerm > myLogTerm) ∨
  //    (cLogTerm = myLogTerm ∧ cLogLength ≥ log.length)
  //  termOk := (cTerm > currentTerm) ∨
  //    (cTerm = currentTerm ∧ votedFor ∈ {cId, null})
  //  if logOk ∧ termOk then
  //    currentTerm := cTerm
  //    currentRole := follower
  //    votedFor := cId
  //    send (VoteResponse, nodeId, currentTerm,true) to node cId
  //  else
  //    send (VoteResponse, nodeId, currentTerm, false) to node cId
  //   end if
  //  end on
  def onRequestVote(
                     state: RaftState,
                     request: RaftRequestVoteRequest,
                     cluster: List[Node],
                     getSameBehaviour: RaftState => Behavior[RaftCommand],
                     onChangeCandidateState: () => Unit
                   ): Behavior[RaftCommand] = {
    val RaftRequestVoteRequest(term, candidateId, lastLogIndex, lastLogTerm, replyTo) = request
    var newState = state.copy()

    var myLogTerm = 0

    if (newState.log.nonEmpty) {
      myLogTerm = newState.log.last.term
    }

    val logOk = (lastLogTerm > myLogTerm) || (lastLogTerm == myLogTerm && lastLogIndex >= (newState.log.length - 1))
    val termOk = (term > newState.currentTerm) || (term == newState.currentTerm && (newState.votedFor == candidateId || newState.votedFor == 0))

    if (logOk && termOk) {
      newState = newState.copy(
        currentTerm = term,
        votedFor = candidateId,
        leaderId = candidateId
      )

      replyTo ! RaftRequestVoteResponse(newState.currentTerm, voteGranted = true)

      onChangeCandidateState()

      Follower(cluster, newState)
    } else {
      replyTo ! RaftRequestVoteResponse(newState.currentTerm, voteGranted = false)

      getSameBehaviour(newState)
    }
  }
}
