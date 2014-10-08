package stream

import java.net.InetSocketAddress
import java.nio.charset.Charset

import akka.actor._
import akka.io.IO
import akka.pattern.ask
import akka.stream.MaterializerSettings
import akka.stream.io.StreamTcp
import akka.stream.io.StreamTcp.IncomingTcpConnection
import akka.stream.scaladsl2._
import akka.util.{ByteString, Timeout}
import org.reactivestreams.{Publisher, Subscriber}

import scala.concurrent.duration._
import scala.util.{Failure, Success}


/**
 * Created by Scott T Weaver (scott.t.weaver@gmail.com) on 10/8/14.
 *
 * Implementation of the TcpEcho (server only) from the Typesafe Activator template akka-stream-scala.
 *
 * The intention here was to provide an example of the new FlowGraph DSL introduced in akka-stream 0.7 as the version
 * in the Activator template is dated, relatively speaking.
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
   * You could easily do pub.subscribe(sub) to get the echo server to work.  However, if you want
   * to do more than just echo, e.g. log all the data that gets echoed, you can hook additional sinks
   * on to the Publisher using a FlowGraph and broadcast it.
   *
   *
   * @param pub
   * @param sub
   * @param materializer
   * @return
   */
  def byteStringHandling(pub: Publisher[ByteString], sub: Subscriber[ByteString], remoteAddr: InetSocketAddress)(implicit materializer: FlowMaterializer, system: ActorSystem) = {
    val in = PublisherSource(pub)
    val out = SubscriberSink(sub)

    /**
     * This is really not needed but gives an example of how to us multiple sinks to consume a Source.  For example
     * logging everything that flows through the echo server.
     */
    val loggingActor = system.actorOf(LoggingActor.props(remoteAddr))
    val loggingSink = ForeachSink[String] {
      case message: String => {
        loggingActor ! message
      }
    }

    /**
     * We can use a FlowFrom to convert the ByteString into a human-readable string that can
     * be sent to a logger.  The logger implementation could have easily done this however
     * doing this way is a great example of how to use a FlowFrom.
     */
    val flowToString = FlowFrom[ByteString].map(_.decodeString(Charset.defaultCharset().toString))

    /**
     * This is an example of an OnComplete sink.  This works well if you need to perform clean up
     * when a user disconnects.  
     */
    val notifyOnLeaving = OnCompleteSink[ByteString] {
      case Success(_)  => loggingActor ! s"SYSTEM MESSAGE: User(${remoteAddr}}) has disconnected."
      case Failure(ex) => loggingActor ! s"SYSTEM MESSAGE: An unexpected error has occurred ${ex}."
    }

    /**
      * This FlowGraph glues all of our processing bits together so we can perform
      * multiple operations on the same ByteString stream.
      */
    val echoGraph = FlowGraph { implicit b =>
      import akka.stream.scaladsl2.FlowGraphImplicits._
      val bcast = Broadcast[ByteString]
      in ~> bcast ~> out  /** This performs the actual echo, this is synonymous with in.subscribe(out) */
            bcast ~> flowToString ~> loggingSink /** This logs all echo messages that pass through */
            bcast ~> notifyOnLeaving  /** We use an OnComplete sink to notify us when a user disconnects. */
    }.run()

  }

  def server(system: ActorSystem, serverAddress: InetSocketAddress): Unit = {
    implicit val sys = system
    implicit val ec = system.dispatcher
    val settings = MaterializerSettings(sys)
    implicit val materializer = FlowMaterializer(settings)
    implicit val timeout = Timeout(5.seconds)

    val serverFuture = IO(StreamTcp) ? StreamTcp.Bind(localAddress = serverAddress)

    serverFuture.onSuccess {
      case serverBinding: StreamTcp.TcpServerBinding =>
        println("Server started, listening on: " + serverBinding.localAddress)

        /**
         * This fires for each connection received then delegates the Input(Publisher) and Output (Subscriber)
         * to a FlowGraph.
         */
        val fes = ForeachSink[IncomingTcpConnection] {
          case conn: IncomingTcpConnection => println(s"Got a connection from ${conn.remoteAddress}!")
            byteStringHandling(conn.inputStream, conn.outputStream, conn.remoteAddress)
        }

        /** We don't need complicated FlowGraph to flow from IncomingTcpConnection to our actual processing
          * so we can just create a FlowFrom and hook our Source and Sink directly to it.
          */
        val flow = FlowFrom(serverBinding.connectionStream).withSink(fes).run()
    }

    serverFuture.onFailure {
      case e: Throwable =>
        println(s"Server could not bind to $serverAddress: ${e.getMessage}")
        system.shutdown()
    }

  }
}

class LoggingActor(remoteAddr: InetSocketAddress) extends Actor with ActorLogging {
  def receive = {
    case message: String => logFormattedMessage(message)
    case wtf: Any => logFormattedMessage(wtf.toString)
  }

  def logFormattedMessage(message: String) = {
    log.info(s"message logged from [${remoteAddr}]: ${message}")
  }
}

object LoggingActor {
  def props(remoteAddr: InetSocketAddress) = {
    Props(classOf[LoggingActor], remoteAddr)
  }
}
