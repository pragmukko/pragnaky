name := "ClusterSupervisor"

version := "1.0"

scalaVersion := "2.11.7"

resolvers ++= Seq(
  "Akka Snapshot Repository" at "http://repo.akka.io/snapshots/",
  "Paho Official Releases" at "https://repo.eclipse.org/content/repositories/paho-releases/",
  "Paho Nightly Snapshots" at "https://repo.eclipse.org/content/repositories/paho-snapshots/",
  Resolver.bintrayRepo("hseeberger", "maven")
)

libraryDependencies ++= {
  Seq(
    "org.eclipse.paho"   %   "org.eclipse.paho.client.mqttv3"     % "1.0.3-SNAPSHOT",
    "default"  % "swarmakka_2.11" % "1.0"  artifacts(Artifact("swarmakka_2.11-assembly")),
    "org.fusesource" % "sigar" % "1.6.4" classifier("native") classifier("")
  )
}