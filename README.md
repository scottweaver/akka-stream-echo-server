 ## What is this?
 This an implementation of an echo server based off of the the TcpEcho (server only) from the typesafe Activator template akka-stream-scala.
 
 The intention here was to provide an example of the new FlowGraph DSL (currently called DSL2) introduced in akka-stream 0.7 as the version in the Activator template is dated, relatively speaking.

 The EchoServer code itself attemtps to demonstrate th following idioms/ideas found within FlowGraph DSL: Broadcast, FlowFrom, Sources and Sinks.

 ## Usage:
 1. sbt "run-main stream.EchoServer"
 2. Telent to 127.0.0.1 6000
 3. Proceed to talk to yourself.