package interpreters

import cats.effect.IO
import algebras.FileStorage
import domain.{FileMeta, AppError}
import fs2.Stream
import fs2.io.file.{Files => Fs2Files, Path => Fs2Path}
import org.mindrot.jbcrypt.BCrypt
import java.nio.file.{Files => JFiles, Paths => JPaths}
import java.time.Instant
import java.util.UUID
import scala.collection.concurrent.TrieMap
import scala.io.Source
import java.io.PrintWriter
import io.circe.syntax.*
import io.circe.parser.*
import io.circe.generic.auto.*

class FileStorageInterpreter(storageDir: String, metaFile: String) extends FileStorage[IO]:

  private val meta: TrieMap[UUID, FileMeta] = loadMeta()

  private def loadMeta(): TrieMap[UUID, FileMeta] =
    val path = JPaths.get(metaFile)
    if JFiles.exists(path) then
      val json = Source.fromFile(metaFile).mkString
      decode[List[FileMeta]](json).getOrElse(Nil)
        .foldLeft(TrieMap.empty[UUID, FileMeta])((m, f) => m += (f.id -> f))
    else TrieMap.empty

  private def saveMeta(): Unit =
    val json = meta.values.toList.asJson.spaces2
    val pw = PrintWriter(metaFile)
    pw.write(json)
    pw.close()

  def save(userId: UUID, fileName: String, password: String, data: Stream[IO, Byte]): IO[Either[AppError, FileMeta]] =
    val id = UUID.randomUUID
    val filePath = Fs2Path.fromNioPath(JPaths.get(storageDir, id.toString))
    JFiles.createDirectories(JPaths.get(storageDir))
    data.through(Fs2Files[IO].writeAll(filePath))
      .compile
      .toList
      .map { _ =>
        val fileSize = JFiles.size(JPaths.get(storageDir, id.toString))
        val fileMeta = FileMeta(
          id           = id,
          originalName = fileName,
          size         = fileSize,
          passwordHash = BCrypt.hashpw(password, BCrypt.gensalt()),
          uploadedBy   = userId,
          uploadedAt   = Instant.now
        )
        meta += (id -> fileMeta)
        saveMeta()
        Right(fileMeta)
      }

  def get(id: UUID, password: String): IO[Either[AppError, (FileMeta, Stream[IO, Byte])]] = IO {
    meta.get(id) match
      case None => Left(AppError.FileNotFound)
      case Some(fileMeta) if !BCrypt.checkpw(password, fileMeta.passwordHash) =>
        Left(AppError.WrongPassword)
      case Some(fileMeta) =>
        val path = Fs2Path.fromNioPath(JPaths.get(storageDir, id.toString))
        val stream = Fs2Files[IO].readAll(path)
        Right((fileMeta, stream))
  }

  def list: IO[List[FileMeta]] = IO(meta.values.toList.sortBy(_.uploadedAt))

  def delete(id: UUID, userId: UUID): IO[Either[AppError, Unit]] = IO {
    meta.get(id) match
      case None => Left(AppError.FileNotFound)
      case Some(m) if m.uploadedBy != userId => Left(AppError.Unauthorized)
      case Some(_) =>
        meta -= id
        saveMeta()
        JFiles.deleteIfExists(JPaths.get(storageDir, id.toString))
        Right(())
  }