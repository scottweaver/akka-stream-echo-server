
name := "akka-stream-echo-server"

version := "1.0"

scalaVersion := "2.11.2"

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

libraryDependencies ++= {
  val akkaV = "2.3.6"
  val akkaStreamV = "0.10"
  Seq(
    "com.typesafe.akka" %% "akka-actor"      % akkaV,
    "com.typesafe.akka" %% "akka-testkit"    % akkaV,
    "ch.qos.logback"    % "logback-classic"  % "1.1.2",
    "com.typesafe.akka" %  "akka-stream-experimental_2.11" % akkaStreamV
  )
}



