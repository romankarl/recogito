package models

import java.util.UUID
import play.api.Play.current
import play.api.db.slick.Config.driver.simple._
import play.api.Logger
import scala.slick.lifted.Tag

/** Annotation case class.
  *  
  * @author Rainer Simon <rainer.simon@ait.ac.at>
  */
case class Annotation(
    
    /** UUID **/
    uuid: UUID,
    
    /** Relation to the {{GeoDocument}} **/
    gdocId: Option[Int],
    
    /** Relation to the {{GeoDocumentPart}} (if gdoc has parts) **/
    gdocPartId: Option[Int],
    
    /** Status of this annotation **/
    status: AnnotationStatus.Value,
    
    /** Toponym identified by the geoparser **/
    toponym: Option[String],
    
    /** Offset of the toponym in the text **/
    offset: Option[Int],
    
    /** Gazetteer URI identified by the georesolver **/
    gazetteerURI: Option[String] = None, 
    
    /** Toponym/correction identified by human expert **/
    correctedToponym: Option[String] = None,
    
    /** Offset of the fixed toponym **/ 
    correctedOffset: Option[Int] = None,
    
    /** Gazetteer URI identified by human expert **/
    correctedGazetteerURI: Option[String] = None,
    
    /** Tags **/
    tags: Option[String] = None,
    
    /** A comment **/
    comment: Option[String] = None,
    
    /** Source URL for the toponym **/
    source: Option[String] = None,
    
    /** Reference(s) to (a) related annotation(s) 
      * 
      * Related annotations are referred to by their UUID. If multiple
      * annotations are related, this field contains multiple annotations
      * separated by comma.
      */
    private val _seeAlso: Option[String] = None
    
) {
  
  /** Helper val that tokenizes the value of the 'see_also' DB field into a Seq[String] **/
  lazy val seeAlso: Seq[String] = _seeAlso.map(_.split(",").toSeq).getOrElse(Seq.empty[String])
  
  /** Helper val that returns the 'valid' gazetteer URI - i.e. the correction, if any, or the automatch otherwise **/
  lazy val validGazetteerURI: Option[String] = 
    if (correctedGazetteerURI.isDefined && correctedGazetteerURI.get.trim.size > 0) correctedGazetteerURI 
    else gazetteerURI
  
  lazy val validToponym: Option[String] = 
    if (correctedToponym.isDefined && correctedToponym.get.trim.size > 0) correctedToponym 
    else toponym
  
}
  
/** Annotation database table **/
class Annotations(tag: Tag) extends Table[Annotation](tag, "annotations") with HasStatusColumn {

  def uuid = column[UUID]("uuid", O.PrimaryKey)
  
  def gdocId = column[Int]("gdoc", O.Nullable)
  
  def gdocPartId = column[Int]("gdoc_part", O.Nullable)
  
  def status = column[AnnotationStatus.Value]("status")
  
  def toponym = column[String]("toponym", O.Nullable)

  def offset = column[Int]("offset", O.Nullable)
  
  def gazetteerURI = column[String]("gazetteer_uri", O.Nullable)
  
  def correctedToponym = column[String]("toponym_corrected", O.Nullable)

  def correctedOffset = column[Int]("offset_corrected", O.Nullable)
  
  def correctedGazetteerURI = column[String]("gazetteer_uri_corrected", O.Nullable)
  
  def tags = column[String]("tags", O.Nullable)
  
  def comment = column[String]("comment", O.Nullable)
  
  def source = column[String]("source", O.Nullable)
  
  def _seeAlso = column[String]("see_also", O.Nullable)
  
  def * = (uuid, gdocId.?, gdocPartId.?, status, toponym.?, offset.?, gazetteerURI.?, correctedToponym.?, 
    correctedOffset.?, correctedGazetteerURI.?, tags.?, comment.?, source.?, _seeAlso.?) <> (Annotation.tupled, Annotation.unapply)
    
}

object Annotations extends HasStatusColumn {
  
  private val query = TableQuery[Annotations]
  
  private val sortByOffset = { a: Annotation =>
    val offset = if (a.correctedOffset.isDefined) a.correctedOffset else a.offset
    if (offset.isDefined)
      (a.gdocPartId, offset.get)
    else
      (a.gdocPartId, 0)
  }
  
  def create()(implicit s: Session) = query.ddl.create
  
  def insert(annotation: Annotation)(implicit s: Session) = query.insert(annotation)
  
  def insertAll(annotations: Seq[Annotation])(implicit s: Session) = query.insertAll(annotations:_*)
  
  /** Retrieve an annotation with the specified UUID (= primary key) **/
  def findByUUID(uuid: UUID)(implicit s: Session): Option[Annotation] =
    query.where(_.uuid === uuid.bind).firstOption

  /** Delete an annotation with the specified UUID (= primary key) **/
  def delete(uuid: UUID)(implicit s: Session) = 
    query.where(_.uuid === uuid.bind).delete
    
    
    
  /** Retrieve all annotations on a specific GeoDocument **/
  def findByGeoDocument(id: Int)(implicit s: Session): Seq[Annotation] =
    query.where(_.gdocId === id).list.sortBy(sortByOffset)      
  
  /** Count all annotations on a specific GeoDocument **/
  def countForGeoDocument(id: Int)(implicit s: Session): Int = 
    Query(query.where(_.gdocId === id).length).first
    
  /** Update an annotation **/
  def update(annotation: Annotation)(implicit s: Session) =
    query.where(_.uuid === annotation.uuid.bind).update(annotation)
  
  /** Delete all annotations on a specific GeoDocument **/
  def deleteForGeoDocument(id: Int)(implicit s: Session) =
    query.where(_.gdocId === id).delete
    
    
  
  /** Retrieve all annotations for a specific source URI **/
  def findBySource(source: String)(implicit s: Session): Seq[Annotation] =
    query.where(_.source === source).list.sortBy(sortByOffset)
    
  /** Retrieve all annotations on a specific GeoDocument that have (a) specific status(es) **/
  def findByGeoDocumentAndStatus(id: Int, status: AnnotationStatus.Value*)(implicit s: Session): Seq[Annotation] =
    query.where(_.gdocId === id).filter(_.status inSet status).list.sortBy(sortByOffset)    
  
  /** Count all annotations on a specific GeoDocument that have (a) specific status(es) **/
  def countForGeoDocumentAndStatus(id: Int, status: AnnotationStatus.Value*)(implicit s: Session): Int =
    Query(query.where(_.gdocId === id).filter(_.status inSet status).length).first
    
    
    
  /** Retrieve all annotations on a specific GeoDocumentPart **/    
  def findByGeoDocumentPart(id: Int)(implicit s: Session): Seq[Annotation] =
    query.where(_.gdocPartId === id).list.sortBy(sortByOffset)
  
  /** Count all annotations on a specific GeoDocumentPart **/
  def countForGeoDocumentPart(id: Int)(implicit s: Session): Int =
    Query(query.where(_.gdocPartId === id).length).first
    
  
    
  /** Retrieve all annotations on a specific GeoDocumentPart that have (a) specific status(es) **/
  def findByGeoDocumentPartAndStatus(id: Int, status: AnnotationStatus.Value*)(implicit s: Session): Seq[Annotation] =
    query.where(_.gdocPartId === id).filter(_.status inSet status).list.sortBy(sortByOffset)
    
  /** Count all annotations on a specific GeoDocumentPart that have (a) specific status(es) **/
  def countForGeoDocumentPartAndStatus(id: Int, status: AnnotationStatus.Value*)(implicit s: Session): Int =
    Query(query.where(_.gdocPartId === id).filter(_.status inSet status).length).first
    
    
    
  /** Helper method to retrieve annotations that overlap the specified annotation **/
  def getOverlappingAnnotations(annotation: Annotation)(implicit s: Session) = {
    val toponym = if (annotation.correctedToponym.isDefined) annotation.correctedToponym else annotation.toponym
    val offset = if (annotation.correctedOffset.isDefined) annotation.correctedOffset else annotation.offset
    
    if (toponym.isDefined && offset.isDefined) {
      val all = if (annotation.gdocPartId.isDefined)
                  findByGeoDocumentPart(annotation.gdocPartId.get)
                else if (annotation.gdocId.isDefined)
                  findByGeoDocument(annotation.gdocId.get)
                else
                  findBySource(annotation.source.get)
                  
      all.filter(a => {
        val otherToponym = if (a.correctedToponym.isDefined) a.correctedToponym else a.toponym
        val otherOffset = if (a.correctedOffset.isDefined) a.correctedOffset else a.offset
        if (otherToponym.isDefined && otherOffset.isDefined) {
          val start = scala.math.max(otherOffset.get, offset.get)
          val end = scala.math.min(otherOffset.get + otherToponym.get.size, offset.get + toponym.get.size)
          (end - start) > 0
        } else {
          false
        }
      }).filter(_.uuid != annotation.uuid)
    } else {
      Seq.empty[Annotation]
    }
  }
  
  def newUUID: UUID = UUID.randomUUID
  
}
