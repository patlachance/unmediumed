package unmediumed.parse

import org.ccil.cowan.tagsoup.jaxp.SAXParserImpl

import scala.util.Try
import scala.xml._

class HtmlParser {
  private val parser: SAXParser = SAXParserImpl.newInstance(null)

  @throws(classOf[HtmlParseFailedException])
  def parse(html: String): MediumPost = {
    Try {
      val source: InputSource = Source.fromString(Option(html).getOrElse(""))
      val rootElement: Elem = XML.loadXML(source, parser)
      MediumPost(extractMeta(rootElement), extractMarkdown(rootElement))
    } getOrElse {
      throw new HtmlParseFailedException("Unable to parse Medium post")
    }
  }

  private def extractMeta(rootElement: Elem): Map[String, String] = {
    val title = (rootElement \\ "title").headOption
      .map(_.text)
      .getOrElse(throw new NoSuchElementException("Title not found"))

    val description = (rootElement \\ "meta")
      .find(_.attribute("name").map(_.text).contains("description"))
      .flatMap(_.attribute("content").map(_.text))
      .getOrElse(throw new NoSuchElementException("Description not found"))

    val canonical = (rootElement \\ "link")
      .find(_.attribute("rel").map(_.text).contains("canonical"))
      .flatMap(_.attribute("href").map(_.text))
      .getOrElse(throw new NoSuchElementException("Canonical link not found"))

    Map("title" -> title, "description" -> description, "canonical" -> canonical)
  }

  private def extractMarkdown(rootElement: Elem): Seq[MarkdownElement] = {
    (rootElement \\ "section" \\ "_").collect {
      case e if e.label == "p" => ParagraphMarkdownElement(getText(e))
      case e if e.label == "h1" => HeaderMarkdownElement(1, getText(e))
      case e if e.label == "h2" => HeaderMarkdownElement(2, getText(e))
      case e if e.label == "h3" => HeaderMarkdownElement(3, getText(e))
      case e if e.label == "h4" => HeaderMarkdownElement(4, getText(e))
      case e if e.label == "h5" => HeaderMarkdownElement(5, getText(e))
      case e if e.label == "h6" => HeaderMarkdownElement(6, getText(e))
      case e if e.label == "img" => ImageMarkdownElement(getAttribute("src", e))
      case e if e.label == "ul" => UnorderedMarkdownElement((e \\ "li").map(getText))
      case e if e.label == "ol" => OrderedMarkdownElement((e \\ "li").map(getText))
      case e if e.label == "blockquote" => BlockquoteMarkdownElement(e.text)
      case e if e.label == "pre" => CodeblockMarkdownElement(getText(e))
    } match {
      // drop the footer and return markdown elements if non empty
      case elements if elements.nonEmpty => elements.dropRight(1)
      case _ => throw new HtmlParseFailedException("Unable to extract markdown elements")
    }
  }

  private def getText(element: Node): String = {
    element.child.map {
      case c if c.label == "strong" => "<strong>" + c.text + "</strong>"
      case c if c.label == "em" => "<em>" + c.text + "</em>"
      case c if c.label == "a" => "<a href=\"" + getAttribute("href", c) + "\">" + c.text + "</a>"
      case c if c.label == "br" => "\n"
      case c => c.text
    }.mkString
  }

  private def getAttribute(name: String, element: Node, default: String = ""): String = {
    element.attribute(name).map(_.toString).getOrElse(default)
  }
}

class HtmlParseFailedException(message: String = null, cause: Throwable = null) extends Exception(message, cause)
