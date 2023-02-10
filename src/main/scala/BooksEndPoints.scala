import java.util.UUID

import cats.effect.{ExitCode, IO}
import sttp.tapir._
import sttp.tapir.json.circe._
import sttp.tapir.generic.auto._
import io.circe.generic.auto._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.HttpRoutes
import sttp.tapir.swagger.bundle.SwaggerInterpreter
import sttp.tapir.server.http4s._
import org.http4s.implicits._
import org.http4s.server._

import cats.effect.unsafe.implicits.global

import scala.collection.mutable
import scala.collection.mutable.ListBuffer


object BooksEndPoints extends App {

  case class Year(year: Int)
  case class Book(id: UUID, title: String, author: String, year: Year)
  case class BookRequest(title: String, author: String, year: Year)

  val exampleBookRequest = BookRequest("Functional Programming in Scala", "Runar", Year(2015))

  val books: mutable.ListBuffer[Book] = ListBuffer()
  val bookTitles = mutable.HashSet[String]()

  val addBookEndPoint = endpoint
    .post
    .in("book")
    .in(jsonBody[BookRequest].example(exampleBookRequest))
    .errorOut(stringBody)
    .out(jsonBody[Book])

  val getBooksEndPoint = endpoint
    .get
    .in("books")
    .in(query[Option[Int]]("year"))
    .in(query[Option[Int]]("limit"))
    .in(query[Option[String]]("title"))
    .errorOut(stringBody)
    .out(jsonBody[List[Book]])

  val getBookEndPoint = endpoint
    .get
    .in("book")
    .in(path[UUID]("id"))
    .errorOut(stringBody)
    .out(jsonBody[Book])

  def getBooks(year: Option[Int], limit: Option[Int], title: Option[String]): IO[Either[String, List[Book]]] = {

    val books1: List[Book] = year.map(y => books.filter(_.year.year == y)).getOrElse(books).toList
    val books2: List[Book] = title.map(t => books1.filter(_.title.equalsIgnoreCase(t))).getOrElse(books1)
    val books3: List[Book] = limit.map(l => books1.take(l)).getOrElse(books2)
    books3 match {
      case Nil => IO(Left("Not Found"))
      case _ => IO(Right(books3))
    }
  }

  def getBook(bookId: UUID): IO[Either[String, Book]] = {
    books.find(_.id == bookId) match {
      case Some(book) => IO(Right(book))
      case None => IO(Left("error"))
    }
  }

  def addBook(bookRequest: BookRequest): IO[Either[String, Book]] = {
    bookTitles.find(_.equalsIgnoreCase(bookRequest.title)) match {
      case Some(title) => IO(Left(s"Book with title $title already exists"))
      case None =>
        val book = Book(UUID.randomUUID(), bookRequest.title, bookRequest.author, bookRequest.year)
        bookTitles.add(book.title);
        books.addOne(book)
        IO(Right(book))
    }
  }


  implicit val ec = scala.concurrent.ExecutionContext.Implicits.global

  val getBooksRoute: HttpRoutes[IO] = {
    Http4sServerInterpreter[IO]().toRoutes(getBooksEndPoint.serverLogic { case (year, limit, title) => getBooks(year, limit, title) })
  }
  val getBookRoute = Http4sServerInterpreter[IO].toRoutes(getBookEndPoint.serverLogic[IO](getBook))
  val addBookRoute = Http4sServerInterpreter[IO].toRoutes(addBookEndPoint.serverLogic{book => addBook(book)})



  val swaggerUIRoutes: HttpRoutes[IO] =
    Http4sServerInterpreter[IO]().toRoutes(
      SwaggerInterpreter().fromEndpoints[IO](List(getBooksEndPoint, getBookEndPoint, addBookEndPoint), "Books Service", "1.0.0"))

  import cats.syntax.semigroupk._
  val allRoutes = getBooksRoute <+> getBookRoute <+> addBookRoute <+> swaggerUIRoutes


    BlazeServerBuilder[IO](ec)
      .bindHttp(8080, "localhost")
      .withHttpApp(Router("/" -> allRoutes).orNotFound)
      .resource
    .use{server => {
      IO {
        println("Go to: http://localhost:8080/docs")
        println("Press any key to exit ...")
        scala.io.StdIn.readLine()
      }
    }}
      .as(ExitCode.Success)
      .unsafeRunSync()
}
