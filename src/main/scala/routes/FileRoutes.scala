package routes

import cats.effect.IO
import config.ServerConfig
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.headers.*
import org.http4s.implicits.uri
import org.http4s.multipart.{Multipart, Part}
import org.typelevel.ci.CIString
import algebras.{FileStorage, Auth}
import domain.{AppError, FileMeta}
import fs2.Stream
import java.util.UUID

class FileRoutes(storage: FileStorage[IO], auth: Auth[IO], cfg: ServerConfig):

  val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> Root / "files"          => listFiles(req)
    case req @ GET -> Root / "download" / id  => downloadPage(req, id)
    case req @ POST -> Root / "download" / id => downloadFile(req, id)
    case req @ POST -> Root / "upload"        => uploadFile(req)
  }

  private def userIdFromCookie(req: Request[IO]): IO[Option[UUID]] =
    req.cookies.find(_.name == cfg.cookieName) match
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
      extractPassword(req).flatMap { password =>
        storage.get(UUID.fromString(id), password).flatMap(downloadResponse(id))
      }
    }

  private def extractPassword(req: Request[IO]): IO[String] =
    req.as[UrlForm].map(_.getFirstOrElse(cfg.passwordFieldName, ""))

  private def downloadResponse(id: String)(result: Either[AppError, (FileMeta, Stream[IO, Byte])]): IO[Response[IO]] =
    result match
      case Right((meta, stream)) =>
        val disp = `Content-Disposition`("attachment", Map(CIString("filename") -> meta.originalName))
        Ok(stream)
          .map(_.withContentType(`Content-Type`(MediaType.application.`octet-stream`)).putHeaders(disp))
      case Left(AppError.FileNotFound)  => NotFound("File not found")
      case Left(AppError.WrongPassword) => SeeOther(Location(uri"/download".addPath(id)))
      case Left(_)                      => InternalServerError("Error")

  private def uploadFile(req: Request[IO]): IO[Response[IO]] =
    withAuth(req)(userId => handleUpload(userId, req))

  private def handleUpload(userId: UUID, req: Request[IO]): IO[Response[IO]] =
    req.as[Multipart[IO]].flatMap(mp => saveUploadedFile(userId, mp))
      .recoverWith { case _ => BadRequest("Invalid multipart body") }

  private def saveUploadedFile(userId: UUID, mp: Multipart[IO]): IO[Response[IO]] =
    val (filePart, passIO) = extractParts(mp)
    (filePart, passIO) match
      case (Some(file), pass) =>
        val fileName = file.filename.getOrElse(cfg.defaultFileName)
        pass.flatMap(pwd => storage.save(userId, fileName, pwd.trim, file.body))
          .flatMap {
            case Right(_) => SeeOther(Location(uri"/files"))
            case Left(_)  => InternalServerError("Upload failed")
          }
      case _ => BadRequest("Missing file or password")

  private def extractParts(mp: Multipart[IO]): (Option[Part[IO]], IO[String]) =
    val filePart = mp.parts.find(_.name == Some(cfg.fileFieldName))
    val passPart = mp.parts.find(_.name == Some(cfg.passwordFieldName))
    val passIO   = passPart.map(p => p.body.through(fs2.text.utf8.decode).compile.string).getOrElse(IO.pure(""))
    (filePart, passIO)