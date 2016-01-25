name := "pragnaky"

scalaVersion := "2.11.7"

resolvers ++= Seq(
  "Akka Snapshot Repository" at "http://repo.akka.io/snapshots/",
  "anormcypher" at "http://repo.anormcypher.org/",
  "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases/",
  "Typesafe repository releases" at "http://repo.typesafe.com/typesafe/releases/"    
)

lazy val commonSettings = Seq(
  organization := "default",
  version := "1.0",
  scalaVersion := "2.11.7",
  assemblyMergeStrategy in assembly := {
    case "logback.xml" => MergeStrategy.first
    case PathList("org", "joda", "time", xs @ _*) => MergeStrategy.first
    case x => (assemblyMergeStrategy in assembly).value(x)
  }
)

val akkaV = "2.4.1"
val akkaStreamV = "2.0.2"
val swarmV = "1.2.8"

lazy val common = project.in(file("common")).
  settings(commonSettings: _*).settings(
    name := "common",
    libraryDependencies ++= Seq(
      "io.spray" %% "spray-json" % "1.3.2"
    )

  )

lazy val pinger = project.in(file("pinger")).
  dependsOn(common).
  settings(commonSettings: _*).settings(
    name := "pinger",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor" % akkaV,
      "com.typesafe.akka" %% "akka-stream-experimental" % akkaStreamV
    )

  )

lazy val web = project.in(file("web")).
  dependsOn(common).
  settings(commonSettings: _*).settings(
    name := "web",
    resolvers ++= Seq("Paho Nightly Snapshots" at "https://repo.eclipse.org/content/repositories/paho-snapshots/"),
    libraryDependencies ++= Seq(
      ("com.typesafe.akka"  %%  "akka-http-experimental" % akkaStreamV).excludeAll(ExclusionRule(organization="org.scala-lang", name="scala-compiler")),
      "com.typesafe.akka" % "akka-agent_2.11" % akkaV,
      "org.elasticsearch" % "elasticsearch" % "2.1.1",
      ("default"  % "swarmakka_2.11" % swarmV).excludeAll(
        ExclusionRule(organization="org.eclipse.paho", name="org.eclipse.paho.client.mqttv3"),
        ExclusionRule(organization="com.sandinh", name="paho-akka_2.11"),
        ExclusionRule(organization="de.heikoseeberger", name="akka-sse_2.11"),
        ExclusionRule(organization="org.scala-lang", name="scala-compiler")
      )

    ),
    mainClass in assembly := Some("web.ClusterAwareRestService")
  )

lazy val sigar = project.in(file("sigar")).
  dependsOn(common).
  settings(commonSettings: _*).settings(
    name := "sigar",
    libraryDependencies ++= Seq(
      "io.kamon" % "sigar-loader" % "1.6.6-rev002"
    )
  )

lazy val agent = project.in(file("agent")).
  dependsOn(sigar,pinger).
  settings(commonSettings: _*).settings(
    name := "agent",
    resolvers ++= Seq("Paho Nightly Snapshots" at "https://repo.eclipse.org/content/repositories/paho-snapshots/"),
    libraryDependencies ++= Seq(
      ("com.typesafe.akka"  %%  "akka-http-experimental" % akkaStreamV).excludeAll(ExclusionRule(organization="org.scala-lang", name="scala-compiler")),
      ("default"  % "swarmakka_2.11" % swarmV).exclude("org.eclipse.paho", "org.eclipse.paho.client.mqttv3").exclude("com.sandinh", "paho-akka_2.11").exclude("de.heikoseeberger", "akka-sse_2.11").exclude("org.scala-lang", "scala-compiler")

    ),
    mainClass in assembly := Some("Agent")
  )

lazy val supervisor = project.in(file("supervisor")).
  dependsOn(common).
  settings(commonSettings: _*).settings(
    name := "supervisor",
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
      ),
      "org.elasticsearch" % "elasticsearch" % "2.1.1"
    ),
    mainClass in assembly := Some("Supervisor")
  )

lazy val telegen = project.in(file("telegen")).
  dependsOn(common, supervisor).
  settings(
    name := "telegen",
    version := "1.0",
    //organization := "",
    scalaVersion := "2.11.7",
    mainClass in assembly := Some("TelemetryGenerator")
  )

