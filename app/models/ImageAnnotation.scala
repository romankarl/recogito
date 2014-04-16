package models

import scala.slick.lifted.Tag
import java.sql.Date
import play.api.db.slick.Config.driver.simple._

case class ImageAnnotation (id: Option[Int], user: String, created: Date, lastModified: Date, siteUrl: String, imgUrl: String, jsonString: String) {
  
  def changeId (id: Option[Int]) =
    ImageAnnotation (id, user, created, lastModified, siteUrl, imgUrl, jsonString)
  
  def changeJsonString (jsonString: String, lastModified: Date) =
    ImageAnnotation (id, user, created, lastModified, siteUrl, imgUrl, jsonString)
}

class ImageAnnotations(tag: Tag) extends Table[ImageAnnotation](tag, "image_annotations") {
  
  def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
  
  def user = column[String]("user")
  
  def created = column[Date]("created")
  
  def lastModified = column[Date]("lastModified")
  
  def imgUrl = column[String]("imgUrl")
  
  def siteUrl = column[String]("siteUrl")
  
  def jsonString = column[String]("jsonString", O.DBType("text"))
  
  def * = (id.?, user, created, lastModified, imgUrl, siteUrl, jsonString) <>
    (ImageAnnotation.tupled, ImageAnnotation.unapply)

}

object ImageAnnotations {
  
  private val query = TableQuery[ImageAnnotations]
  
  def create()(implicit s: Session) = query.ddl.create
  
  def drop()(implicit s: Session) = query.ddl.drop
  
  def insert(annotation: ImageAnnotation)(implicit s: Session) =
    query returning query.map(_.id) += annotation
  
  def update(annotation: ImageAnnotation)(implicit s: Session) = query.where(_.id === annotation.id).update(annotation)
    
  def delete(id: Int)(implicit s: Session) = query.where(_.id === id).delete
  
  def findById(id: Int)(implicit s: Session):Option[ImageAnnotation] = query.where(_.id === id).firstOption
 
  def findByImgUrl(imgUrl: String)(implicit s: Session):Seq[ImageAnnotation] = query.where(_.imgUrl === imgUrl).list
  
  def findBySiteUrl(siteUrl: String)(implicit s: Session):Seq[ImageAnnotation] = query.where(_.siteUrl === siteUrl).list
  
}
