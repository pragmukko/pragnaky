/*
* Copyright 2015-2016 Pragmukko Project [http://github.org/pragmukko]
* Licensed under the Apache License, Version 2.0 (the "License"); you may not
* use this file except in compliance with the License. You may obtain a copy of
* the License at
*
*    [http://www.apache.org/licenses/LICENSE-2.0]
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
* WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
* License for the specific language governing permissions and limitations under
* the License.
*/
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
val swarmV = "1.2.12"

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
      "com.typesafe.akka" %% "akka-actor" % akkaV/*,
      "com.typesafe.akka" %% "akka-stream-experimental" % akkaStreamV*/
    )

  )

lazy val web = project.in(file("web")).
  dependsOn(common).
  settings(commonSettings: _*).settings(
    name := "web",
    resolvers ++= Seq("Paho Nightly Snapshots" at "https://repo.eclipse.org/content/repositories/paho-snapshots/"),
    libraryDependencies ++= Seq(
      ("com.typesafe.akka"  %%  "akka-http-experimental" % akkaStreamV).excludeAll(ExclusionRule(organization="org.scala-lang", name="scala-compiler")),
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
      //("com.typesafe.akka"  %%  "akka-http-experimental" % akkaStreamV).excludeAll(ExclusionRule(organization="org.scala-lang", name="scala-compiler")),
      ("default"  % "swarmakka_2.11" % swarmV).excludeAll(
        ExclusionRule(organization="org.eclipse.paho", name="org.eclipse.paho.client.mqttv3"),
        ExclusionRule(organization="com.sandinh", name="paho-akka_2.11"),
        ExclusionRule(organization="de.heikoseeberger", name="akka-sse_2.11"),
        ExclusionRule(organization="org.scala-lang", name="scala-compiler"),
        ExclusionRule(organization="com.typesafe.akka", name="akka-http-core-experimental_2.11"),
        ExclusionRule(organization="com.typesafe.akka", name="akka-http-experimental_2.11"),
        ExclusionRule(organization="com.typesafe.akka", name="akka-http-spray-json-experimental_2.11"),
        ExclusionRule(organization="com.typesafe.akka", name="akka-stream-experimental_2.11")
      )
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
      "org.elasticsearch" % "elasticsearch" % "2.1.1",
      "com.ecwid.consul" % "consul-api" % "1.1.8"
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

