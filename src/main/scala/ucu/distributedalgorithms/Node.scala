//package ucu.distributedalgorithms
//
//import akka.actor.typed.{ActorRef, Behavior}
//import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors, LoggerOps}
//
//object Node {
//  sealed trait Command
//
//  final case class Request(msg: String, replyTo: ActorRef[Response]) extends Command
//  final case class Response(msg: String) extends Command
//  final case class SimpleRequest(msg: String) extends Command
//  final case class AppendEntry(msg: String) extends Command
//
//  def apply(): Behavior[Command] = {
//    Behaviors.setup(context => new Node(context))
//  }
//}
//
//class Node(context: ActorContext[Node.Command]) extends AbstractBehavior[Node.Command](context) {
//  import Node._
//
//  context.setLoggerName("NodeActor")
//
//  // persistent state
//  var currentTerm = 0
//  var votedFor = None
//  var log = Set.empty
//
//  // volatile state on all servers
//  var commitIndex = 0
//  var lastApplied = 0
//
//  //volatile state on leaders
//  var nextIndex = Set.empty
//  var matchIndex = Set.empty
//
//  val actorClien = context.spawn(ClientGRPCActor)
//
//  override def onMessage(msg: Node.Command): Behavior[Node.Command] =
//    msg match {
//      case Request(msg, replyTo) =>
//        context.log.info(msg)
//        replyTo ! Response("tessssst")
//        this
//
//      case s @ SimpleRequest(msg) =>
//        context.log.info(msg)
//        this
//    }
//}
