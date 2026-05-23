package routes

import cats.effect.IO
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.headers.*
import org.http4s.implicits.uri
import org.typelevel.ci.CIString
import algebras.{Auth, FileStorage}
import domain.AppError
import org.http4s.multipart.Multipart

import java.util.UUID

class FileRoutes(storage: FileStorage[IO], auth: Auth[IO]):

  val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> Root / "files"          => listFiles(req)
    case req @ GET -> Root / "download" / id  => downloadPage(req, id)
    case req @ POST -> Root / "download" / id => downloadFile(req, id)
    case req @ POST -> Root / "upload"        => uploadFile(req)
  }

  private def userIdFromCookie(req: Request[IO]): IO[Option[UUID]] =
    req.cookies.find(_.name == "jwt") match
      case None    => IO.pure(None)
      case Some(c) => auth.validateToken(c.content).map(_.map(_.id))

  private def withAuth(req: Request[IO])(onSuccess: UUID => IO[Response[IO]]): IO[Response[IO]] =
    userIdFromCookie(req).flatMap {
      case None     => SeeOther(Location(uri"/login"))
      case Some(id) => onSuccess(id)
    }

  private def listFiles(req: Request[IO]): IO[Response[IO]] =
    withAuth(req) { _ =>
      storage.list.flatMap { files =>
        val data = files.map(f => (f.id.toString, f.originalName, f.size))
        html.filesPage(data)
      }
    }

  private def downloadPage(req: Request[IO], id: String): IO[Response[IO]] =
    withAuth(req)(_ => html.downloadPage(id))

  private def downloadFile(req: Request[IO], id: String): IO[Response[IO]] =
    withAuth(req) { _ =>
      req.as[UrlForm].flatMap { form =>
        val password = form.getFirstOrElse("password", "")
        storage.get(UUID.fromString(id), password).flatMap {
          case Right((meta, stream)) =>
            val disp = `Content-Disposition`("attachment", Map(CIString("filename") -> meta.originalName))
            Ok(stream)
              .map(_.withContentType(`Content-Type`(MediaType.application.`octet-stream`)).putHeaders(disp))
          case Left(AppError.FileNotFound)  => NotFound("File not found")
          case Left(AppError.WrongPassword) => SeeOther(Location(uri"/download".addPath(id)))
          case Left(_)                      => InternalServerError("Error")
        }
      }
    }

  private def uploadFile(req: Request[IO]): IO[Response[IO]] =
    withAuth(req) { userId =>
      req.as[Multipart[IO]].flatMap { multipart =>
        val parts = multipart.parts
        val filePart = parts.find(_.name == Some("file"))
        val passwordPart = parts.find(_.name == Some("password"))

        (filePart, passwordPart) match
          case (Some(file), Some(pass)) =>
            val fileName = file.filename.getOrElse("unnamed")
            val password = pass.body.through(fs2.text.utf8.decode).compile.string
            password.flatMap { pwd =>
              storage.save(userId, fileName, pwd.trim, file.body).flatMap {
                case Right(_) => SeeOther(Location(uri"/files"))
                case Left(_)  => InternalServerError("Upload failed")
              }
            }
          case _ => BadRequest("Missing file or password")
      }.recoverWith { case _ => BadRequest("Invalid multipart body") }
    }