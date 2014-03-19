package controllers.io

import models._
import java.io.{ BufferedInputStream, File, FileInputStream, FileOutputStream, PrintWriter }
import java.util.UUID
import java.util.zip.{ ZipEntry, ZipOutputStream }
import play.api.Logger
import play.api.libs.json.Json
import play.api.libs.Files.TemporaryFile
import scala.slick.session.Session

/** Utility class to export GeoDocuments (with text and annotations) to a ZIP file **/
class ZipExporter {
  
  private val UTF8 = "UTF-8"
  private val TMP_DIR = System.getProperty("java.io.tmpdir")

  /** Exports one GeoDocument (with texts and annotations) to ZIP 
    *
    * @param gdoc the GeoDocument
    */
  def exportGDoc(gdoc: GeoDocument)(implicit session: Session): TemporaryFile = {
    exportGDocs(Seq(gdoc))
  } 

  /** Exports a list of GeoDocuments (with text and annotations) to ZIP 
    *
    * @param gdocs a Seq of GeoDocuments
    */
  def exportGDocs(gdocs: Seq[GeoDocument])(implicit session: Session): TemporaryFile = {
    val zipFile = new TemporaryFile(new File(TMP_DIR, UUID.randomUUID().toString + ".zip"))
    val zipStream = new ZipOutputStream(new FileOutputStream(zipFile.file, false))
    gdocs.foreach(doc => {
      Logger.info("Building ZIP package: " + doc.title + doc.language.map(" (" + _ + ")").getOrElse(""))
      exportOne(doc, zipStream) 
    })
    zipStream.close()
    Logger.info("Export complete")
    zipFile
  }
  
  /** Writes one GeoDocument to a ZIP stream 
    *
    * @param gdoc the GeoDocument
    * @param zipStream the ZIP output stream
    */
  private def exportOne(gdoc: GeoDocument, zipStream: ZipOutputStream)(implicit session: Session) = {   
    val gdocNamePrefix = escapeTitle(gdoc.title) + gdoc.language.map("_" + _).getOrElse("")
    
    // Get GeoDocument parts & texts from the DB
    val parts = GeoDocumentParts.findByGeoDocument(gdoc.id.get)
    val texts = GeoDocumentTexts.findByGeoDocument(gdoc.id.get) 
      
    // Add JSON metadata file to ZIP
    val metadata = createMetaFile(gdoc, parts, texts)   
    addToZip(metadata.file, gdocNamePrefix + ".json", zipStream)
    metadata.finalize();
    
    // Add texts to ZIP
    texts.foreach(text => {
      val textFile = new TemporaryFile(new File(TMP_DIR, "text_" + text.id.get + ".txt"))
      val textFileWriter = new PrintWriter(textFile.file)
      textFileWriter.write(new String(text.text, UTF8))
      textFileWriter.flush()
      textFileWriter.close()
      
      val textName = {
          if (text.gdocPartId.isDefined) 
            parts.find(_.id == text.gdocPartId).map(_.title)
          else
            Some(gdoc.title)
        } map (escapeTitle(_))
            
      addToZip(textFile.file, textName.map(n => gdocNamePrefix + File.separator + n + ".txt").get, zipStream)
      textFile.finalize()
    })
    
    // Add annotations
    val annotations = Annotations.findByGeoDocument(gdoc.id.get)
    if (annotations.size > 0) {
      val annotationsFile = new TemporaryFile(new File(TMP_DIR, "annotations_" + gdoc.id.get + ".csv"))
      val annotationsFileWriter = new PrintWriter(annotationsFile.file)
      annotationsFileWriter.write(new CSVSerializer().serializeAnnotationsAsDBBackup(annotations))
      annotationsFileWriter.flush()
      annotationsFileWriter.close()
      addToZip(annotationsFile.file, gdocNamePrefix + ".csv", zipStream)
      annotationsFile.finalize()
    }
  }
  
  /** Creates the document metadata JSON file for a GeoDocument 
    *
    * @param gdoc the GeoDocument
    * @param parts the parts of the GeoDocument
    * @param texts the texts associated with the GeoDocument
    */
  private def createMetaFile(gdoc: GeoDocument, parts: Seq[GeoDocumentPart], texts: Seq[GeoDocumentText])(implicit session: Session): TemporaryFile = {
    val gdocNamePrefix = escapeTitle(gdoc.title) + gdoc.language.map("_" + _).getOrElse("")
    
    val jsonParts = parts.map(part => {
      val text = texts.find(_.gdocPartId == part.id).map(_ => gdocNamePrefix + File.separator + escapeTitle(part.title) + ".txt")
      Json.obj(
        "title" -> part.title,
        "source" -> part.source,
        "text" -> text
      )
    })
    
    val gdocText = texts.find(t => t.gdocId == gdoc.id.get && t.gdocPartId == None)
    val annotations =
      if (Annotations.countForGeoDocument(gdoc.id.get) > 0) Some(gdocNamePrefix + ".csv") else None
        
    val jsonMeta = Json.obj(
      "title" -> gdoc.title,
      "author" -> gdoc.author,
      "date" -> gdoc.date,
      "date_comment" -> gdoc.dateComment,
      "description" -> gdoc.description,
      "language" -> gdoc.language,
      "source" -> gdoc.source,
      "text" -> gdocText.map(_ => gdocNamePrefix + File.separator + escapeTitle(gdoc.title) + ".txt"),
      "annotations" -> annotations,
      "parts" -> jsonParts
    )
    
    val metaFile = new TemporaryFile(new File(TMP_DIR, "meta_" + gdoc.id.get + ".json"))
    val metaFileWriter = new PrintWriter(metaFile.file)
    metaFileWriter.write(Json.prettyPrint(jsonMeta))
    metaFileWriter.flush()
    metaFileWriter.close()    
    metaFile
  }
   
  /** Adds a file to a ZIP archive
    *
    * @param file the file to add to the ZIP
    * @param filename the (relative) path and name of the file within the ZIP
    * @param zip the ZIP output stream 
    */
  private def addToZip(file: File, filename: String, zip: ZipOutputStream) = {
    zip.putNextEntry(new ZipEntry(filename))
    val in = new BufferedInputStream(new FileInputStream(file))
    var b = in.read()
    while (b > -1) {
      zip.write(b)
      b = in.read()
    }
    in.close()
    zip.closeEntry()
  }
  
  /** Utility method to escape document titles
    *
    * We keep this separate, so we have a central location to add stuff 
    * in the future if necessary.  
    * 
    * @param title the title
    */
  def escapeTitle(title: String): String =
    title.replace(" ", "_")

}