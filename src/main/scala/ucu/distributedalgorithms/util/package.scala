package ucu.distributedalgorithms.util

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

  def readPortAndInterface(portSysEnvName: String, interfaceSysEnvName: String): Node = {
    val port: Option[Int] = readSysEnv(portSysEnvName)
      .flatMap(stringToInt)
    val host = readSysEnv(interfaceSysEnvName)

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
}
