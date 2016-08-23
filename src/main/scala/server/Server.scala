package server

import breeze.linalg.{DenseMatrix, DenseVector}
import com.twitter.finagle.ListeningServer
import com.twitter.finagle.Thrift
import com.twitter.util.{Await, Future, Promise}
import org.apache.commons.math3.geometry.euclidean.threed.PolyhedronsSet.TranslationTransform
import scalismo.common.{DiscreteVectorField, PointId, UnstructuredPointsDomain}
import scalismo.geometry._
import scalismo.image.{DiscreteImageDomain, DiscreteScalarImage}
import scalismo.mesh._
import scalismo.registration
import scalismo.registration.{RigidTransformation, RotationTransform}
import scalismo.statisticalmodel.DiscreteLowRankGaussianProcess.Eigenpair
import scalismo.statisticalmodel.{MultivariateNormalDistribution, DiscreteLowRankGaussianProcess, StatisticalMeshModel}
import scalismo.ui.api._
import thrift.ShapeModelView

//import thrift.{EulerTransform => TEulerTransform, Group => TGroup, Image => TImage, Point3D => TPoint3D, RigidTransformation => TRigidTransformation, ShapeModelTransformationView => TShapeModelTransformationView, ShapeTransformation => TShapeTransformation, StatisticalShapeModel => TStatisticalShapeModel, TranslationTransform => TTranslationTransform, TriangleMesh => TTriangleMesh, Landmark => TLandmark, ShapeModelView => TShapeModelView, Color => TColor, ImageView => TImageView, TriangleMeshView => TTriangleMeshView, Ui}

import scala.collection.mutable


object FinagleThriftServerSampleApp extends App {

  val ui = ScalismoUI()


  // The server part is easy in this sample, so let's just
  // create a simple implementation
  val service = new thrift.Ui[Future] {

    override def createGroup(name: String): Future[thrift.Group] = {
      val nameOuter = name
      val g = ui.createGroup(name)
      val group = GroupSerializer.toThrift(g)
      val v = Future.value(group)
      v
    }


    override def showPointCloud(g : thrift.Group, p: Seq[thrift.Point3D], name : String): Future[Unit] = {

      val uipts =  p.map(PointSerializer.fromThrift(_))
      val group = GroupSerializer.updateFromThrift(g)
      ui.show(group, uipts.toIndexedSeq, name)
      Future.value(())
    }

    override def showLandmark(g : thrift.Group, tlm: thrift.Landmark, name : String): Future[Unit] = {

      val group  = GroupSerializer.updateFromThrift(g)
      val lm = LandmarkSerializer.fromThrift(tlm)

      ui.show(group,lm, name)
      Future.value(())
    }


    override def showTriangleMesh(g : thrift.Group, m: thrift.TriangleMesh, name : String): Future[thrift.TriangleMeshView] = {

      val group = GroupSerializer.updateFromThrift(g)
      val mesh = TriangleMeshSerializer.fromThrift(m)
      val meshView= ui.show(group, mesh, name)

      val tMeshView : thrift.TriangleMeshView= TriangleMeshViewSerializer.toThrift(meshView)

      Future.value(tMeshView)
    }

    override def showImage(g : thrift.Group, thriftImg: thrift.Image, name : String): Future[thrift.ImageView] = {
      val group = GroupSerializer.updateFromThrift(g)
      val discreteImage = ImageSerializer.fromThrift(thriftImg)
      val imageView = ui.show(group, discreteImage, name)

      val tImageView = ImageViewSerializer.toThrift(imageView)

      Future.value(tImageView)
    }


    override def showStatisticalShapeModel(g: thrift.Group, tssm: thrift.StatisticalShapeModel, name: String): Future[thrift.ShapeModelView] = {


      val group = GroupSerializer.updateFromThrift(g)
      val ssm = ShapeModelSerializer.fromThrift(tssm)
      val ssmView   = ui.show(group, ssm, name)

      val meshId: Int = (g.name + name).hashCode()

      val tssmview = ShapeModelViewSerializer.toThrift(ssmView)

      Future.value(tssmview)

    }

    override def updateShapeModelTransformation(tsmtv: thrift.ShapeModelTransformationView): Future[Unit] = {

      val shapeModelTransformView = ShapeModelTransformViewSerializer.updateFromThrift(tsmtv)

      Future.value(())
    }

    override def updateTriangleMeshView(tvm: thrift.TriangleMeshView): Future[Unit] = {
      val tvt = TriangleMeshViewSerializer.updateFromThrift(tvm)
      Future.value(())
    }

    override def updateImageView(iv: thrift.ImageView): Future[Unit] = {
      val imgv = ImageViewSerializer.updateFromThrift(iv)
      Future.value(())
    }

    override def removeGroup(g: thrift.Group): Future[Unit] = {
      Future.value(GroupSerializer.remove(g))
    }

    override def removeTriangleMesh(tmv: thrift.TriangleMeshView): Future[Unit] = {
      Future.value(TriangleMeshViewSerializer.remove(tmv))
    }

    override def removeImage(iv: thrift.ImageView): Future[Unit] = {
      Future.value(ImageViewSerializer.remove(iv))
    }

    override def removeShapeModelTransformation(smv: thrift.ShapeModelTransformationView): Future[Unit] = {
      Future.value(ShapeModelTransformViewSerializer.remove(smv))
    }

    override def removeShapeModel(smv: ShapeModelView): Future[Unit] = {
      removeTriangleMesh(smv.meshView)
      removeShapeModelTransformation(smv.shapeModelTransformationView)
      Future.value(())
    }
  }



  // Run the service implemented on the port 8080
  val server = Thrift.serveIface(":8000", service)

  // Keep waiting for the server and prevent the java process to exit
  // Q: What happens if we remove the await ?
  Await.ready(server)

}