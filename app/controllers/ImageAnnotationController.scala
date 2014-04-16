package controllers

import play.api.db.slick._
import play.api.mvc.Controller
import models.ImageAnnotations
import play.api.libs.json.Json
import models.ImageAnnotation
import models.Users
import play.api.libs.json.JsObject
import java.sql.Date
import play.api.mvc.Action
import play.api.Logger

object ImageAnnotationController extends Controller with Secured {
  
  def index() = Action {
    Ok(views.html.image_annotation())
  }

  def get(id: Int) = DBAction { implicit session =>
    val annotation = ImageAnnotations.findById(id)
    if (annotation.isDefined) {
      Ok(augmentedJSON(annotation.get))
    } else {
      NotFound
    }
  }

  def update(id: Int) = protectedDBAction(Secure.REJECT) { username => implicit requestWithSession =>
    val imgAnnotation: Option[ImageAnnotation] = ImageAnnotations.findById(id)
    if (imgAnnotation.isEmpty)
      BadRequest(Json.obj("success" -> false, "message" -> "Annotation does not exist"))
    else if (username != imgAnnotation.get.user)
      Forbidden
    else {
      val body = requestWithSession.request.body.asJson
      if (body.isEmpty)
        BadRequest(Json.obj("success" -> false, "message" -> "Missing JSON body"))
      else {
        val json = body.get.as[JsObject]
        val now = new Date(System.currentTimeMillis)
        val reducedJSON = json - "id" - "user" - "created" - "lastModified"
        val updatedAnnotation = imgAnnotation.get.changeJsonString(Json.stringify(reducedJSON), now)
        ImageAnnotations.update(updatedAnnotation)
        Ok(augmentedJSON(updatedAnnotation))
      }
    }
  }

  def delete(id: Int) = protectedDBAction(Secure.REJECT) { username => implicit requestWithSession =>
    val imgAnnotation: Option[ImageAnnotation] = ImageAnnotations.findById(id)
    if (imgAnnotation.isEmpty)
      BadRequest(Json.obj("success" -> false, "message" -> "Annotation does not exist"))
    else if (username != imgAnnotation.get.user)
      Forbidden
    else {
      ImageAnnotations.delete(id)
      Ok(Json.obj("success" -> true))
    }
  }
  
  def forSiteUrl(siteUrl: String) = DBAction { implicit session =>
    val annotations = ImageAnnotations.findBySiteUrl(siteUrl)
    Ok(Json.toJson(annotations.map(augmentedJSON(_))))
  }

  def create = protectedDBAction(Secure.REJECT) { username => implicit requestWithSession =>
    val user = Users.findByUsername(username)
    val body = requestWithSession.request.body.asJson
    if (body.isDefined) {
      val json = body.get.as[JsObject]
      val now = new Date(System.currentTimeMillis)
      val imgUrl = (json \ "src").as[String]
      val siteUrl = (json \ "context").as[String]
      val annotation = ImageAnnotation(None, username, now, now, imgUrl, siteUrl, Json.stringify(json))
      val id = ImageAnnotations.insert(annotation)
      Ok(augmentedJSON(annotation.changeId(Some(id))))
    } else
      BadRequest(Json.obj("success" -> true, "message" -> "Missing JSON body"))
  }
  
  def augmentedJSON(imageAnnotation: ImageAnnotation) =
    Json.parse(imageAnnotation.jsonString).as[JsObject] ++
    Json.obj("id" -> imageAnnotation.id.get,
      "user" -> imageAnnotation.user,
      "created" -> imageAnnotation.created,
      "lastModified" -> imageAnnotation.lastModified)
  
}