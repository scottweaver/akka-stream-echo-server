## What is this?

This an implementation of an echo server based off of the the TcpEcho (server only) from the typesafe Activator template akka-stream-scala.
 
The intention here was to provide an example of how to use the FlowGraph DSL to add additional functionality to the original TcpEcho server. 

The EchoServer code itself attempts to demonstrate th following idioms/ideas found within FlowGraph DSL: Broadcast, FlowFrom, Sources and Sinks.

## Usage:

1. sbt "run-main stream.EchoServer"
2. Telnet to 127.0.0.1 6000
3. Proceed to talk to yourself.