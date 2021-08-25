package ucu.distributedalgorithms

import ucu.distributedalgorithms.raft.Raft.{RaftAppendEntriesRequest, RaftState}

import scala.util.Try

case class Node(host: String, port: Int)

package object util {
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

    Node(host, port)
  }

  def readPortAndHost(portSysEnvName: String, hostSysEnvName: String): Node = {
    val port: Option[Int] = readSysEnv(portSysEnvName)
      .flatMap(stringToInt)
    val host = readSysEnv(hostSysEnvName)

    (host, port) match {
      case (Some(host), Some(port)) => Node(host, port)
      case (Some(host), None) => Node(host, DEFAULT_PORT)
      case (None, Some(port)) => Node(DEFAULT_HOST, port)
      case _ => Node(DEFAULT_HOST, DEFAULT_PORT)
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
    state.log.length - 1
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

    val leaderLogIsEmpty = appendEntriesRequest.prevLogIndex == 0 && appendEntriesRequest.prevLogTerm == 0

    currentPrevLogIndex >= appendEntriesRequest.prevLogIndex && (leaderLogIsEmpty || currentPrevLogTerm == appendEntriesRequest.prevLogTerm)
  }

  def appendEntries(state: RaftState, entries: Seq[LogEntry], prevLogIndex: Int, leaderCommit: Int): RaftState = {
    var newState = state.copy()

    if (entries.nonEmpty && (newState.log.length > (prevLogIndex + 1))) {
      // could be check for same term , newState.log.lift(prevLogIndex + 1) != entries.lift(0)
      var log = newState.log.toList

      newState = newState.copy(
        log = log.slice(0, prevLogIndex)
      )
    }

    if ((prevLogIndex + 1 + entries.length) > newState.log.length) {
      newState = newState.copy(
        log = newState.log.concat(entries)
      )
    }

    if (leaderCommit > newState.commitIndex) {
      newState = newState.copy(
        commitIndex = leaderCommit
      )
    }

    newState
  }
}
