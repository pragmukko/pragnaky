name := "ClusterSupervisor"

version := "1.0"

scalaVersion := "2.11.7"

resolvers ++= Seq(
  "Akka Snapshot Repository" at "http://repo.akka.io/snapshots/",
  "Paho Official Releases" at "https://repo.eclipse.org/content/repositories/paho-releases/",
  "Paho Nightly Snapshots" at "https://repo.eclipse.org/content/repositories/paho-snapshots/",
  "anormcypher" at "http://repo.anormcypher.org/",
  "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases/",
  Resolver.bintrayRepo("hseeberger", "maven")
)

mainClass in assembly := Some("Agent")

libraryDependencies ++= {
  Seq(
    "org.eclipse.paho"   %   "org.eclipse.paho.client.mqttv3"     % "1.0.3-SNAPSHOT",
    "default"  % "swarmakka_2.11" % "1.2.1"  artifacts Artifact("swarmakka_2.11"),
    "io.kamon" % "sigar-loader" % "1.6.6-rev002",
    "org.reactivemongo" %% "reactivemongo" % "0.11.8",
    "joda-time" % "joda-time" % "2.9.1",
    "org.joda" % "joda-convert" % "1.8"

  )
}
