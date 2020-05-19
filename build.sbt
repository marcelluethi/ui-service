import com.twitter.scrooge.ScroogeSBT

name := "ui-service"

version := "0.0.1"

organization := "ch.unibas.cs.gravis"

scalaVersion := "2.12.11"

resolvers += Resolver.bintrayRepo("unibas-gravis", "maven")

libraryDependencies ++= Seq(
  "ch.unibas.cs.gravis" %% "scalismo-ui" % "0.14.0",
  "ch.unibas.cs.gravis" % "scalismo-native-all" % "4.0.+",
  "org.apache.thrift" % "libthrift" % "0.10.0",
  "com.twitter" %% "finagle-thrift" % "20.4.1",
  "com.twitter" %% "scrooge-core" % "20.4.1"
)

