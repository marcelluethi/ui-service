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
import scalismo.ui.api.{Group, ScalismoUI, ShapeModelTransformationView}
import thrift.{Ui, EulerTransform => TEulerTransform, Group => TGroup, Image => TImage, Point3D => TPoint3D, RigidTransformation => TRigidTransformation, ShapeModelTransformationView => TShapeModelTransformationView, ShapeTransformation => TShapeTransformation, StatisticalShapeModel => TStatisticalShapeModel, TranslationTransform => TTranslationTransform, TriangleMesh => TTriangleMesh, Landmark => TLandmark}

import scala.collection.mutable


object FinagleThriftServerSampleApp extends App {

  val ui = ScalismoUI()

  // maps group ids (here encoded as int) to Groups
  val groupMap = mutable.HashMap[Int, Group]()
  val shapeModelTransformViewMap = mutable.HashMap[Int, ShapeModelTransformationView]()

  // The server part is easy in this sample, so let's just
  // create a simple implementation
  val service = new Ui[Future] {

    override def showPointCloud(g : TGroup, p: Seq[TPoint3D], name : String): Future[Unit] = {
      val uipts = p.map(tp => scalismo.geometry.Point3D(tp.x, tp.y, tp.z))

      ui.show(groupMap(g.id), uipts.toIndexedSeq, name)
      Future.value(())
    }

    override def showLandmark(g : TGroup, tlm: TLandmark, name : String): Future[Unit] = {
      val pt = Point3D(tlm.point.x, tlm.point.y, tlm.point.z)

      val pc1 = DenseVector.zeros[Double](3);
      pc1(0) = tlm.uncertainty.principalAxis1.x;
      pc1(1) = tlm.uncertainty.principalAxis1.y;
      pc1(2) = tlm.uncertainty.principalAxis1.z;
      val pc2 = DenseVector.zeros[Double](3);
      pc2(0) = tlm.uncertainty.principalAxis2.x;
      pc2(1) = tlm.uncertainty.principalAxis2.y;
      pc2(2) = tlm.uncertainty.principalAxis2.z;
      val pc3 = DenseVector.zeros[Double](3);
      pc3(0) = tlm.uncertainty.principalAxis3.x;
      pc3(1) = tlm.uncertainty.principalAxis3.y;
      pc3(2) = tlm.uncertainty.principalAxis3.z;

      val mean = DenseVector.zeros[Double](3)
      val principalComponents =       Seq((pc1, tlm.uncertainty.variances.x),
        (pc2, tlm.uncertainty.variances.y),
      (pc3, tlm.uncertainty.variances.z))

      val uncertainty = MultivariateNormalDistribution(mean, principalComponents)
      val lm = Landmark(tlm.name, pt, None, Some(uncertainty))//p.map(tp => scalismo.geometry.Point3D(tp.x, tp.y, tp.z))

      ui.show(groupMap(g.id),lm, name)
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

    override def showStatisticalShapeModel(g: TGroup, ssm: TStatisticalShapeModel, name: String): Future[TShapeModelTransformationView] = {

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


      val ssmView = ui.show(groupMap(g.id), StatisticalMeshModel(refMesh, dgp), name)

      val tvview : TShapeModelTransformationView = new TShapeModelTransformationView {
        override def id: Int = (g.name + name).hashCode()

        override def poseTransformation: TRigidTransformation = {
          new TRigidTransformation {
            override def rotation: TEulerTransform = new TEulerTransform {
              // we have rotation around z, y, x in scalismo
              override def angleX: Double = ssmView.shapeModelTransformationView.poseTransformationView.transformation.rotation.parameters(2)
              override def angleY: Double = ssmView.shapeModelTransformationView.poseTransformationView.transformation.rotation.parameters(1)
              override def angleZ: Double = ssmView.shapeModelTransformationView.poseTransformationView.transformation.rotation.parameters(0)

              override def center: TPoint3D = new TPoint3D {
                override def x: Double = ssmView.shapeModelTransformationView.poseTransformationView.transformation.rotation.center.x
                override def y: Double = ssmView.shapeModelTransformationView.poseTransformationView.transformation.rotation.center.y
                override def z: Double = ssmView.shapeModelTransformationView.poseTransformationView.transformation.rotation.center.z
              }
            }
            override def translation: TTranslationTransform = new TTranslationTransform {
              override def x: Double = ssmView.shapeModelTransformationView.poseTransformationView.transformation.translation.parameters(0)
              override def y: Double = ssmView.shapeModelTransformationView.poseTransformationView.transformation.translation.parameters(1)
              override def z: Double = ssmView.shapeModelTransformationView.poseTransformationView.transformation.translation.parameters(2)
            }
          }
        }

        override def shapeTransformation: TShapeTransformation = new TShapeTransformation {
          override def coefficients: Seq[Double] = ssmView.shapeModelTransformationView.shapeTransformationView.coefficients.toArray.toSeq
        }
      }
      shapeModelTransformViewMap.update(tvview.id, ssmView.shapeModelTransformationView)
      Future.value(tvview)

    }

    override def updateShapeModelTransformation(smtv: TShapeModelTransformationView): Future[Unit] = {
      val shapeModelTransformView = shapeModelTransformViewMap(smtv.id)
      shapeModelTransformView.shapeTransformationView.coefficients = DenseVector(smtv.shapeTransformation.coefficients.toArray)
      val center : scalismo.geometry.Point3D = Point3D(smtv.poseTransformation.rotation.center.x,smtv.poseTransformation.rotation.center.y, smtv.poseTransformation.rotation.center.z)
      // the rotation parameters are ordered z y x in scalismo
      val rotation = RotationTransform(smtv.poseTransformation.rotation.angleZ,smtv.poseTransformation.rotation.angleY, smtv.poseTransformation.rotation.angleX, centre = center)
      println("translation: " + smtv.poseTransformation.translation)
      val translation = registration.TranslationTransform(Vector3D(smtv.poseTransformation.translation.x, smtv.poseTransformation.translation.y, smtv.poseTransformation.translation.z))
      val rigidTransformation = RigidTransformation(translation, rotation)
      shapeModelTransformView.poseTransformationView.transformation = rigidTransformation;

      Future.value(())
    }
  }

  // Run the service implemented on the port 8080
  val server = Thrift.serveIface(":8000", service)

  // Keep waiting for the server and prevent the java process to exit
  // Q: What happens if we remove the await ?
  Await.ready(server)

}