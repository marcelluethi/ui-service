/**
  * Created by Luethi on 21.08.2016.
  */

package server

import java.awt.Color

import breeze.linalg.{DenseMatrix, DenseVector}
import scalismo.common.{DiscreteVectorField, UnstructuredPointsDomain, PointId}
import scalismo.geometry._
import scalismo.image.{DiscreteScalarImage, DiscreteImageDomain}
import scalismo.mesh.{TriangleList, TriangleMesh3D, TriangleCell, TriangleMesh}
import scalismo.registration
import scalismo.registration.{RigidTransformation, RotationTransform}
import scalismo.statisticalmodel.DiscreteLowRankGaussianProcess.Eigenpair
import scalismo.statisticalmodel.{StatisticalMeshModel, DiscreteLowRankGaussianProcess, MultivariateNormalDistribution}
import scalismo.ui.api._

import scala.collection.mutable


trait Serialize[S, T] {

  def toThrift (o : S) : T
  def fromThrift(o : T) : S
}


object GroupSerializer extends Serialize[Group, thrift.Group] {

  // maps group ids (here encoded as int) to Groups
  val groupMap = mutable.HashMap[Int, Group]()

    override def toThrift(g: Group): thrift.Group = {
      val group = new thrift.Group {
        override def id: Int = (name.hashCode % Int.MaxValue).toInt
        override def name: String = g.name
      }
      groupMap.update(group.id, g)

      group
    }

    override def fromThrift(thriftGroup: thrift.Group): Group = {
      groupMap(thriftGroup.id)
    }
}

object PointSerializer extends Serialize[Point3D, thrift.Point3D] {
  override def toThrift(o: Point3D): thrift.Point3D = ???

  override def fromThrift(thriftPoint: thrift.Point3D): Point3D = {
    scalismo.geometry.Point3D(thriftPoint.x, thriftPoint.y, thriftPoint.z)
  }
}


object VectorSerializer extends Serialize[Vector3D, thrift.Vector3D] {
  override def toThrift(o: Vector3D): thrift.Vector3D = ???

  override def fromThrift(tv: thrift.Vector3D): Vector3D = {
    Vector3D(tv.x, tv.y, tv.z)
  }
}

object IntVectorSerializer extends Serialize[IntVector3D, thrift.IntVector3D] {
  override def toThrift(o: IntVector3D): thrift.IntVector3D = ???

  override def fromThrift(thriftVector: thrift.IntVector3D): IntVector3D = {
    IntVector3D(thriftVector.i, thriftVector.j, thriftVector.k)
  }
}


object LandmarkSerializer extends Serialize[Landmark[_3D], thrift.Landmark] {//object TriangleMeshSerializer extends Serialize[TriangleMesh, thrift.TriangleMesh] {
//
//
//
//
//  override def fromTrift(o: thrift.TriangleMesh): TriangleMesh = ???
//
//}

  override def toThrift(o: Landmark[_3D]): thrift.Landmark = ???

  override def fromThrift(tlm: thrift.Landmark): Landmark[_3D] = {
    val pt = PointSerializer.fromThrift(tlm.point)

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
    lm
  }
}


object TriangleMeshSerializer extends Serialize[TriangleMesh[_3D], thrift.TriangleMesh] {
  override def toThrift(mesh: TriangleMesh[_3D]): thrift.TriangleMesh = ???
  override def fromThrift(tmesh: thrift.TriangleMesh): TriangleMesh[_3D] = {
    val pts = tmesh.vertices.map(tp => PointSerializer.fromThrift(tp))
    val cells = tmesh.topology.map(c => TriangleCell(PointId(c.id1), PointId(c.id2), PointId(c.id3)))
    val mesh = TriangleMesh3D(UnstructuredPointsDomain(pts.toIndexedSeq), TriangleList(cells.toIndexedSeq))
    mesh
  }
}

object ImageSerializer extends Serialize[DiscreteScalarImage[_3D, Short], thrift.Image] {
  override def toThrift(o: DiscreteScalarImage[_3D, Short]): thrift.Image = ???

  override def fromThrift(thriftImg: thrift.Image): DiscreteScalarImage[_3D, Short] = {
    val origin = PointSerializer.fromThrift(thriftImg.domain.origin)
    val size = IntVectorSerializer.fromThrift(thriftImg.domain.size)
    val spacing = VectorSerializer.fromThrift(thriftImg.domain.spacing)
    val domain = DiscreteImageDomain(origin, spacing,size)
    val values = thriftImg.data.toIndexedSeq
    DiscreteScalarImage(domain, values)
  }
}



object ShapeModelSerializer extends Serialize[StatisticalMeshModel, thrift.StatisticalShapeModel] {

  override def toThrift(o: StatisticalMeshModel): thrift.StatisticalShapeModel = ???

  override def fromThrift(ssm: thrift.StatisticalShapeModel): StatisticalMeshModel = {
    val refMesh = TriangleMeshSerializer.fromThrift(ssm.reference)
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
    StatisticalMeshModel(refMesh, dgp)



  }
}

object TriangleMeshViewSerializer extends Serialize[TriangleMeshView, thrift.TriangleMeshView] {

  val meshViewMap = mutable.HashMap[Int, TriangleMeshView]()


  override def toThrift(meshView: TriangleMeshView): thrift.TriangleMeshView = {

    val assignedId: Int = (meshView.inGroup.name + meshView.name).hashCode

    meshViewMap.update(assignedId, meshView)
    println("updated map with id " + assignedId)
    new thrift.TriangleMeshView {
      override def opacity: Double = meshView.opacity

      override def color: thrift.Color = new thrift.Color {
        val meshcolor = meshView.color
        override def r: Short = meshcolor.getRed.toShort
        override def b: Short = meshcolor.getBlue.toShort
        override def g: Short = meshcolor.getGreen.toShort
      }

      override def id: Int = assignedId

      override def lineWidth: Int = meshView.lineWidth
    }


  }

  override def fromThrift(o: thrift.TriangleMeshView): TriangleMeshView = {
    println("retrieve view with id " + o.id)
    val meshView = meshViewMap(o.id)
    meshView.color = new Color(o.color.r, o.color.g, o.color.b)
    meshView.lineWidth = o.lineWidth
    meshView.opacity = o.opacity
    meshView
  }
}


object ImageViewSerializer extends Serialize[ImageView, thrift.ImageView] {

  val imageViewMap = mutable.HashMap[Int, ImageView]()


  override def toThrift(imgView: ImageView): thrift.ImageView = {
    val assignedId: Int = (imgView.inGroup.name + imgView.name).hashCode
    if (!imageViewMap.contains(assignedId)) {
      imageViewMap.update(assignedId, imgView)
    }


    val tImageView : thrift.ImageView = new thrift.ImageView {
      override def level: Double = imgView.level

      override def window: Double = imgView.window

      override def opacity : Double = imgView.opacity

      override def id: Int = assignedId
    }

    tImageView
  }

  override def fromThrift(o: thrift.ImageView): ImageView = {
    val imageView = imageViewMap.get(o.id).get
    imageView.level = o.level
    imageView.window = o.window
    imageView.opacity = o.opacity
    imageView
  }
}

object ShapeModelTransformViewSerializer extends Serialize[ShapeModelTransformationView, thrift.ShapeModelTransformationView] {

  val shapeModelTransformViewMap = mutable.HashMap[Int, ShapeModelTransformationView]()

  override def toThrift(smtv: ShapeModelTransformationView): thrift.ShapeModelTransformationView = {
    val assignedId: Int = (smtv.inGroup.name + smtv.name).hashCode

    if (!shapeModelTransformViewMap.contains(assignedId)) {
      shapeModelTransformViewMap.update(assignedId, smtv)
    }
    val tvview : thrift.ShapeModelTransformationView = new thrift.ShapeModelTransformationView {
      override def id: Int = assignedId

      override def poseTransformation: thrift.RigidTransformation = {
        new thrift.RigidTransformation {
          override def rotation: thrift.EulerTransform = new thrift.EulerTransform {
            // we have rotation around z, y, x in scalismo
            override def angleX: Double = smtv.poseTransformationView.transformation.rotation.parameters(2)

            override def angleY: Double = smtv.poseTransformationView.transformation.rotation.parameters(1)

            override def angleZ: Double = smtv.poseTransformationView.transformation.rotation.parameters(0)

            override def center: thrift.Point3D = new thrift.Point3D {
              override def x: Double = smtv.poseTransformationView.transformation.rotation.center.x

              override def y: Double = smtv.poseTransformationView.transformation.rotation.center.y

              override def z: Double = smtv.poseTransformationView.transformation.rotation.center.z
            }
          }

          override def translation: thrift.TranslationTransform = new thrift.TranslationTransform {
            override def x: Double = smtv.poseTransformationView.transformation.translation.parameters(0)

            override def y: Double = smtv.poseTransformationView.transformation.translation.parameters(1)

            override def z: Double = smtv.poseTransformationView.transformation.translation.parameters(2)
          }
        }
      }

      override def shapeTransformation: thrift.ShapeTransformation = new thrift.ShapeTransformation {
        override def coefficients: Seq[Double] = smtv.shapeTransformationView.coefficients.toArray.toSeq
      }


    }

    tvview
  }

   override def fromThrift(smtv: thrift.ShapeModelTransformationView): ShapeModelTransformationView = {

     val shapeModelTransformView = shapeModelTransformViewMap.get(smtv.id).get
     shapeModelTransformView.shapeTransformationView.coefficients = DenseVector(smtv.shapeTransformation.coefficients.toArray)
     val center: scalismo.geometry.Point3D = PointSerializer.fromThrift(smtv.poseTransformation.rotation.center)

     // the rotation parameters are ordered z y x in scalismo
     val rotation = RotationTransform(smtv.poseTransformation.rotation.angleZ, smtv.poseTransformation.rotation.angleY, smtv.poseTransformation.rotation.angleX, centre = center)

     val translation = registration.TranslationTransform(Vector3D(smtv.poseTransformation.translation.x, smtv.poseTransformation.translation.y, smtv.poseTransformation.translation.z))
     val rigidTransformation = RigidTransformation(translation, rotation)
     shapeModelTransformView.poseTransformationView.transformation = rigidTransformation;
     shapeModelTransformView

   }
}


object ShapeModelViewSerializer extends Serialize[StatisticalMeshModelViewControls, thrift.ShapeModelView] {

  override def toThrift(ssmView: StatisticalMeshModelViewControls): thrift.ShapeModelView = {



      val tMeshView = TriangleMeshViewSerializer.toThrift(ssmView.meshView)
      val tssmv = ShapeModelTransformViewSerializer.toThrift(ssmView.shapeModelTransformationView)

      val tssmview : thrift.ShapeModelView = new thrift.ShapeModelView {
        override def shapeModelTransformationView: thrift.ShapeModelTransformationView = tssmv
        override def meshView: thrift.TriangleMeshView = tMeshView
      }


    tssmview
  }

  override def fromThrift(ssmView: thrift.ShapeModelView): StatisticalMeshModelViewControls = ???

}