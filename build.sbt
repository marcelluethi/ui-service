import com.twitter.scrooge.ScroogeSBT


name := "ui-service"

version := "0.0.1"

organization := "ch.unibas.cs.gravis"

scalaVersion := "2.11.8"

resolvers ++= Seq("scalismo (private)" at "https://statismo.cs.unibas.ch/repository/private")

credentials += Credentials(Path.userHome / ".ivy2" / ".credentials-statismo-private")

libraryDependencies ++= Seq(
  "ch.unibas.cs.gravis" %% "scalismo-ui" % "develop-SNAPSHOT",
  "ch.unibas.cs.gravis" % "scalismo-native-all" % "3.0.+",
  "com.twitter" %% "scrooge-core" % "4.6.0",
  "org.apache.thrift" % "libthrift" % "0.8.0",
   "com.twitter" %% "finagle-thrift" % "6.34.0")



scroogeBuildOptions := Seq("--finagle", "--verbose")



