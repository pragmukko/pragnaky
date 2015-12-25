name := "ClusterSupervisor"

version := "1.0"

scalaVersion := "2.11.7"

resolvers ++= Seq(
  "Akka Snapshot Repository" at "http://repo.akka.io/snapshots/",
  "anormcypher" at "http://repo.anormcypher.org/",
  "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases/",
  "Typesafe repository releases" at "http://repo.typesafe.com/typesafe/releases/"    
)

//import sbtassembly.AssemblyKeys._

val akkaV = "2.4.1"
val akkaStreamV = "2.0.1"
val swarmV = "1.2.3"

lazy val common = project.in(file("common")).
  settings(
    name := "common",
    version := "1.0",
    organization := "default",
    scalaVersion := "2.11.7",
    libraryDependencies ++= Seq(
      "io.spray" %% "spray-json" % "1.3.2"
    )

  )

lazy val pinger = project.in(file("pinger")).
  dependsOn(common).
  settings(
    name := "pinger",
    version := "1.0",
    organization := "default",
    scalaVersion := "2.11.7",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor" % akkaV,
      "com.typesafe.akka" %% "akka-stream-experimental" % akkaStreamV
    )

  )

lazy val db = project.in(file("db")).
  dependsOn(common).
  settings(
    name := "db",
    version := "1.0",
    organization := "default",
    scalaVersion := "2.11.7",
    libraryDependencies ++= Seq(
      "com.typesafe.akka"  %%  "akka-actor" % akkaV,
      "com.typesafe.play" %% "play-iteratees" % "2.3.10",
      ("org.reactivemongo" %% "reactivemongo" % "0.11.9").excludeAll(ExclusionRule(organization="org.scala-lang", name="scala-compiler")),
      "joda-time" % "joda-time" % "2.9.1",
      "org.joda" % "joda-convert" % "1.8.1",
      "io.spray" %% "spray-json" % "1.3.2"
    )
  )

lazy val telegen = project.in(file("telegen")).
  dependsOn(common, db).
  settings(
    name := "telegen",
    version := "1.0",
    //organization := "",
    scalaVersion := "2.11.7",
    mainClass in assembly := Some("TelemetryGenerator")
  )

lazy val web = project.in(file("web")).
  dependsOn(common, db).
  settings(
    name := "web",
    version := "0.1",
    organization := "default",
    scalaVersion := "2.11.7",
    resolvers ++= Seq("Paho Nightly Snapshots" at "https://repo.eclipse.org/content/repositories/paho-snapshots/"),
    libraryDependencies ++= Seq(
      ("com.typesafe.akka"  %%  "akka-http-experimental" % akkaStreamV).excludeAll(ExclusionRule(organization="org.scala-lang", name="scala-compiler")),
      "com.typesafe.akka" % "akka-agent_2.11" % akkaV,
      ("default"  % "swarmakka_2.11" % swarmV).excludeAll(
        ExclusionRule(organization="org.eclipse.paho", name="org.eclipse.paho.client.mqttv3"),
        ExclusionRule(organization="com.sandinh", name="paho-akka_2.11"),
        ExclusionRule(organization="de.heikoseeberger", name="akka-sse_2.11"),
        ExclusionRule(organization="org.scala-lang", name="scala-compiler")
      )

    ),
    mainClass in assembly := Some("web.RestNode")
  )

lazy val sigar = project.in(file("sigar")).
  dependsOn(common).
  settings(
    name := "sigar",
    version := "1.0",
    organization := "default",
    scalaVersion := "2.11.7",
    libraryDependencies ++= Seq(
      "io.kamon" % "sigar-loader" % "1.6.6-rev002"
    )
  )

lazy val agent = project.in(file("agent")).
  dependsOn(sigar,pinger).
  settings(
    name := "agent",
    version := "1.0",
    organization := "default",
    scalaVersion := "2.11.7",
    resolvers ++= Seq("Paho Nightly Snapshots" at "https://repo.eclipse.org/content/repositories/paho-snapshots/"),
    libraryDependencies ++= Seq(
      ("com.typesafe.akka"  %%  "akka-http-experimental" % akkaStreamV).excludeAll(ExclusionRule(organization="org.scala-lang", name="scala-compiler")),
      ("default"  % "swarmakka_2.11" % swarmV).exclude("org.eclipse.paho", "org.eclipse.paho.client.mqttv3").exclude("com.sandinh", "paho-akka_2.11").exclude("de.heikoseeberger", "akka-sse_2.11").exclude("org.scala-lang", "scala-compiler")

    ),
    mainClass in assembly := Some("Agent")
  )

lazy val restagent = project.in(file("restagent")).
  dependsOn(sigar,pinger).
  settings(
    name := "restagent",
    version := "1.0",
    organization := "default",
    scalaVersion := "2.11.7",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" % "akka-agent_2.11" % akkaV,
      ("com.typesafe.akka"  %%  "akka-http-experimental" % akkaStreamV).excludeAll(ExclusionRule(organization="org.scala-lang", name="scala-compiler"))
    ),
    mainClass in assembly := Some("RestAgent")
  )

lazy val supervisor = project.in(file("supervisor")).
  dependsOn(common, db).
  settings(
    name := "supervisor",
    version := "1.0",
    organization := "default",
    scalaVersion := "2.11.7",
    libraryDependencies ++= Seq(
      ("default"  % "swarmakka_2.11" % swarmV).excludeAll(
          ExclusionRule(organization="org.eclipse.paho", name="org.eclipse.paho.client.mqttv3"),
          ExclusionRule(organization="com.sandinh", name="paho-akka_2.11"),
          ExclusionRule(organization="de.heikoseeberger", name="akka-sse_2.11"),
          ExclusionRule(organization="org.scala-lang", name="scala-compiler"),
          ExclusionRule(organization="com.typesafe.akka", name="akka-http-core-experimental_2.11"),
          ExclusionRule(organization="com.typesafe.akka", name="akka-http-experimental_2.11"),
          ExclusionRule(organization="com.typesafe.akka", name="akka-http-spray-json-experimental_2.11")/*,
          ExclusionRule(organization="com.typesafe.akka", name="akka-stream-experimental_2.11")*/
      )
    ),
    mainClass in assembly := Some("Supervisor")
  )

