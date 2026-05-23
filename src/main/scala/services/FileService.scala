package services

import cats.effect.IO
import algebras.FileStorage
import domain.{FileMeta, AppError}
import fs2.Stream
import java.util.UUID

class FileService(storage: FileStorage[IO]):

  def upload(userId: UUID, fileName: String, password: String, data: Stream[IO, Byte]): IO[Either[AppError, FileMeta]] =
    if fileName.trim.isEmpty || password.trim.isEmpty then
      IO.pure(Left(AppError.InternalError("File name and password required")))
    else storage.save(userId, fileName.trim, password, data)

  def download(id: UUID, password: String): IO[Either[AppError, (FileMeta, Stream[IO, Byte])]] =
    storage.get(id, password)

  def listFiles: IO[List[FileMeta]] =
    storage.list

  def delete(id: UUID, userId: UUID): IO[Either[AppError, Unit]] =
    storage.delete(id, userId)