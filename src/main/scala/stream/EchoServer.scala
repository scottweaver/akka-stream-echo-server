package stream

import java.net.InetSocketAddress
import java.nio.charset.Charset

import akka.actor._
import akka.stream.{FlowMaterializer, OverflowStrategy}
import akka.stream.actor.ActorSubscriberMessage.{OnComplete, OnError, OnNext}
import akka.stream.actor.{WatermarkRequestStrategy, ActorSubscriber}
import akka.stream.io.StreamTcp
import akka.stream.scaladsl._
import akka.util.{ByteString, Timeout}
import scala.concurrent.duration._
import scala.util.{Failure, Success}


/**
 * Created by Scott T Weaver (scott.t.weaver@gmail.com) on 10/8/14.
 *
 * Implementation of the TcpEcho (server only) from the Typesafe Activator template akka-stream-scala.
 *
 * The intention here was to provide an example of the using the FlowGraph DSL to enhance a simple echo server.
 */
object EchoServer {
  /**
   * Use parameters `server 0.0.0.0 6001` to start server listening on port 6001.
   *
   */
  def main(args: Array[String]): Unit = {
    val serverAddress =
      if (args.length == 3) new InetSocketAddress(args(1), args(2).toInt)
      else new InetSocketAddress("127.0.0.1", 6000)

    val system = ActorSystem("Server")
    server(system, serverAddress)
  }

  /**
   * Creates a `Flow` using a `FlowGraph` the does performs multiple tasks on the `ByteString` as it
   * flows from `Source` to the `Sink`. In the end, the raw `ByteString` is echoed back to the client.
   *
   * You could easily do `conn handleWith createFlow` to get simple echo functionality.  However, this
   * example shows so perform common tasks on a stream as it is processed.
   *
   *
   * @param materializer
   * @return
   */
  def createFlow(remoteAddr: String)(implicit materializer: FlowMaterializer, system: ActorSystem) : Flow[ByteString, ByteString] = {

    val in = UndefinedSource[ByteString]
    val out = UndefinedSink[ByteString]

    /**
     * This is really not needed but gives an example of how to us multiple sinks to consume a Source.  For example
     * logging everything that flows through the echo server.
     */
    val loggingActor = system.actorOf(LoggingActor.props(remoteAddr))
    val loggingSink = SubscriberSink[String](ActorSubscriber(loggingActor))

    /**
     * We can use a Flow to convert the ByteString into a human-readable string that can
     * be sent to a logger.  The logger implementation could have easily done this however
     * doing this way is a great example of how to use a Flow.
     */
    val flowToString = Flow[ByteString].buffer(20, OverflowStrategy.backpressure)
                                           .map(_.decodeString(Charset.defaultCharset().toString))

    /**
     * This is an example of an OnComplete sink.  This works well if you need to perform clean up
     * when a user disconnects.  
     */
    val notifyOnLeaving = OnCompleteSink[ByteString] {
      case Success(_)  => println(s"<<< SYSTEM MESSAGE: User($remoteAddr) has disconnected. >>>")
      case Failure(ex) => println(s"<<< SYSTEM MESSAGE: An unexpected error has occurred $ex. >>>")
    }

    /**
      * This FlowGraph glues all of our processing bits together so we can perform
      * multiple operations on the same ByteString stream.
      */
    Flow[ByteString, ByteString]() { implicit b =>
      import akka.stream.scaladsl.FlowGraphImplicits._
      val bcast = Broadcast[ByteString]
      in ~> bcast ~> out  /** This performs the actual echo, this is synonymous with in.subscribe(out) */
            bcast ~> flowToString ~> loggingSink /** This logs all echo messages that pass through */
            bcast ~> notifyOnLeaving  /** We use an OnComplete sink to notify us when a user disconnects. */
      in -> out
    }

  }

  def server(system: ActorSystem, serverAddress: InetSocketAddress): Unit = {
    implicit val sys = system
    import system.dispatcher
    implicit val materializer = FlowMaterializer()
    implicit val timeout = Timeout(5.seconds)

    /**
     * This fires for each connection received.
     */
    val connectionDistributor = ForeachSink[StreamTcp.IncomingConnection] { conn =>
        println(s"Got a connection from ${conn.remoteAddress}!")
      conn handleWith createFlow(conn.remoteAddress.getHostString)
    }

    val serverBinding = StreamTcp().bind(serverAddress)
    val materializedServer = serverBinding.connections.to(connectionDistributor).run()

    serverBinding.localAddress(materializedServer).onComplete {
      case Success(address) =>
        println("Server started, listening on: " + address)
      case Failure(e) =>
        println(s"Server could not bind to $serverAddress: ${e.getMessage}")
        system.shutdown()
    }

  }
}

class LoggingActor(remoteAddr: String) extends ActorSubscriber with ActorLogging {

  val requestStrategy = WatermarkRequestStrategy(20)

  def receive = {
    case OnNext(message: String) => logFormattedMessage(message)
    case OnError(ex)             => log.error(s"Error encountered an error during logging $ex.")
    case OnComplete              => log.info(s"Logging shutting down for $remoteAddr.")
  }

  def logFormattedMessage(message: String) = {
    log.info(s"message logged from [$remoteAddr]: $message")
  }
}

object LoggingActor {
  def props(remoteAddr: String) = {
    Props(classOf[LoggingActor], remoteAddr)
  }
}
