package server

import breeze.linalg.{DenseMatrix, DenseVector}
import com.twitter.finagle.ListeningServer
import com.twitter.finagle.Thrift
import com.twitter.util.{Await, Future, Promise}
import scalismo.common.{DiscreteVectorField, PointId, UnstructuredPointsDomain}
import scalismo.geometry.{IntVector3D, Point3D, Vector3D, _3D}
import scalismo.image.{DiscreteImageDomain, DiscreteScalarImage}
import scalismo.mesh._
import scalismo.statisticalmodel.DiscreteLowRankGaussianProcess.Eigenpair
import scalismo.statisticalmodel.{DiscreteLowRankGaussianProcess, StatisticalMeshModel}
import scalismo.ui.api.{Group, ScalismoUI}
import thrift.{StatisticalShapeModel, Ui, Group => TGroup, Image => TImage, Point3D => TPoint3D, TriangleMesh => TTriangleMesh}

import scala.collection.mutable


object FinagleThriftServerSampleApp extends App {

  val ui = ScalismoUI()

  // maps group ids (here encoded as int) to Groups
  val groupMap = mutable.HashMap[Int, Group]()

  // The server part is easy in this sample, so let's just
  // create a simple implementation
  val service = new Ui[Future] {

    override def showPointCloud(g : TGroup, p: Seq[TPoint3D], name : String): Future[Unit] = {
      val uipts = p.map(tp => scalismo.geometry.Point3D(tp.x, tp.y, tp.z))

      ui.show(groupMap(g.id), uipts.toIndexedSeq, name)
      Future.value(())
}

    override def showTriangleMesh(g : TGroup, m: TTriangleMesh, name : String): Future[Unit] = {
      val pts = m.vertices.map(tp => scalismo.geometry.Point3D(tp.x, tp.y, tp.z))
      val cells = m.topology.map(c => TriangleCell(PointId(c.id1), PointId(c.id2), PointId(c.id3)))
      val mesh = TriangleMesh3D(UnstructuredPointsDomain(pts.toIndexedSeq), TriangleList(cells.toIndexedSeq))
      ui.show(groupMap(g.id), mesh, name)
      Future.value(())
    }

    override def showImage(g : TGroup, img: TImage, name : String): Future[Unit] = {
      val origin = Point3D(img.domain.origin.x, img.domain.origin.y, img.domain.origin.z)
      val size = IntVector3D(img.domain.size.i, img.domain.size.j, img.domain.size.k)
      val spacing = Vector3D(img.domain.spacing.x, img.domain.spacing.y, img.domain.spacing.z)

      val domain = DiscreteImageDomain(origin, spacing,size)
      val values = img.data.toIndexedSeq
      val discreteImage = DiscreteScalarImage(domain, values)
      ui.show(groupMap(g.id), discreteImage, name)
      Future.value(())
    }

    override def createGroup(name: String): Future[TGroup] = {
      val nameOuter = name
      val g = ui.createGroup(name)

      val group = new TGroup {
        override def id: Int = (name.hashCode % Int.MaxValue).toInt
        override def name: String = nameOuter
      }
      groupMap.update(group.id, g)
      print(groupMap.keys.toIndexedSeq)
      println("return id " + group.id)
      val v = Future.value(group)
      v
    }

    override def showStatisticalShapeModel(g: TGroup, ssm: StatisticalShapeModel, name: String): Future[Unit] = {

      val pts = ssm.reference.vertices.map(tp => scalismo.geometry.Point3D(tp.x, tp.y, tp.z))
      val cells = ssm.reference.topology.map(c => TriangleCell(PointId(c.id1), PointId(c.id2), PointId(c.id3)))
      val refMesh = TriangleMesh3D(UnstructuredPointsDomain(pts.toIndexedSeq), TriangleList(cells.toIndexedSeq))

      val meanVec = DenseVector.zeros[Double](ssm.mean.size)


      for (i <- 0 until meanVec.size) {
        meanVec(i) = ssm.mean(i)
      }
      val eigenVals = ssm.klbasis.eigenvalues
      val pcaMatrix = DenseMatrix.zeros[Double](meanVec.size, ssm.klbasis.eigenvectors.size)
      for (i <- 0 until pcaMatrix.rows; j <- 0 until pcaMatrix.cols){
        pcaMatrix(i,j) = ssm.klbasis.eigenvectors(j)(i)
      }

      val meanField = DiscreteVectorField.fromDenseVector[_3D, _3D](refMesh.pointSet, meanVec)
      val eigenfuncs = for (i <- 0 until pcaMatrix.cols) yield {
        DiscreteVectorField.fromDenseVector[_3D, _3D](refMesh.pointSet, pcaMatrix(::, i).toDenseVector)
      }
      val eigenPairs = eigenVals.zip(eigenfuncs).map{case (eigenVal, eigenFun) => Eigenpair(eigenVal, eigenFun)}
      val dgp = DiscreteLowRankGaussianProcess(meanField,  eigenPairs)


      ui.show(groupMap(g.id), StatisticalMeshModel(refMesh, dgp), name)

      Future.value(())

    }
  }

  // Run the service implemented on the port 8080
  val server = Thrift.serveIface(":8000", service)

  // Keep waiting for the server and prevent the java process to exit
  // Q: What happens if we remove the await ?
  Await.ready(server)

}