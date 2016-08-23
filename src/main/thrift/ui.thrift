
namespace cpp ui // The generated c++ code will be put inside example namespace

typedef list<double> DoubleVector

typedef list<DoubleVector> ListOfDoubleVectors


struct Group {
    1: required i32 id;
    2: required string name;
}

struct Point3D {
   1: required double x;
   2: required double y;
   3: required double z;
}

struct Vector3D {
    1: required double x;
    2: required double y;
    3: required double z;
}

struct IntVector3D {
    1: required i32 i;
    2: required i32 j;
    3: required i32 k;
}


struct UncertaintyCovariance {
    1: required Vector3D variances;
    2: required Vector3D principalAxis1;
    3: required Vector3D principalAxis2;
    4: required Vector3D principalAxis3;
}

struct Landmark {
    1: required string name;
    2: required Point3D point;
    3: required UncertaintyCovariance uncertainty;
}

typedef list<Point3D> PointList

struct TriangleCell {
    1: required i32 id1;
    2: required i32 id2;
    3: required i32 id3;
}

struct Color {
    1: required i16 r;
    2: required i16 g;
    3: required i16 b;
}


typedef list<TriangleCell> TriangleCellList


struct TriangleMesh {
    1: required PointList vertices;
    2: required TriangleCellList topology;
}

struct TriangleMeshView {
    1: required  i32 id;
    2: required Color color;
    3: required i32 lineWidth;
    4: required double opacity;

}

struct ImageDomain {
    1: required Point3D origin;
    2: required IntVector3D size;
    3: required Vector3D spacing;
}

typedef list<i16> ImageData

struct Image {
    1: required ImageDomain domain;
    2: required ImageData data;
}

struct ImageView {
    1: required i32 id;
    2: required double window;
    3: required double level;
    4: required double opacity;
}


struct KLBasis {
    1: required DoubleVector eigenvalues
    2: required ListOfDoubleVectors eigenvectors
}

struct StatisticalShapeModel {
    1: required TriangleMesh reference;
    2: required DoubleVector mean;
    3: required KLBasis klbasis;
}


struct EulerTransform {
    1: required Point3D center;
    2: required double angleX;
    3: required double angleY;
    4: required double angleZ;
}

struct TranslationTransform {
    1: required double x;
    2: required double y;
    3: required double z;
}

struct RigidTransformation {
    1: required EulerTransform rotation;
    2: required TranslationTransform translation;
}

struct ShapeTransformation {
    1: required DoubleVector coefficients;
}

struct ShapeModelTransformationView {
    1: required i32 id;
    2: required ShapeTransformation shapeTransformation;
    3: required RigidTransformation poseTransformation;
}


struct ShapeModelView {
    1: required TriangleMeshView meshView;
    2: required ShapeModelTransformationView shapeModelTransformationView;
}


service UI {
  Group createGroup(1:string name);
  void showPointCloud(1: Group g, 2:PointList p, 3:string name);
  TriangleMeshView showTriangleMesh(1: Group g, 2:TriangleMesh m, 3:string name);
  ImageView showImage(1: Group g, 2:Image img, 3:string name);
  void showLandmark(1 : Group g, 2 : Landmark landmark, 3 : string name);
  ShapeModelView showStatisticalShapeModel(1 : Group g, 2:StatisticalShapeModel ssm, 3:string name);
  void updateShapeModelTransformation(1: ShapeModelTransformationView smtv);
  void updateTriangleMeshView(1: TriangleMeshView tvm);
  void updateImageView(1 : ImageView iv);
}


