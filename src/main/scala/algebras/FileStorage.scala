package algebras

import domain.{FileMeta, AppError}
import fs2.Stream
import java.util.UUID

trait FileStorage[F[_]]:
  def save(userId: UUID, fileName: String, password: String, data: Stream[F, Byte]): F[Either[AppError, FileMeta]]
  def get(id: UUID, password: String): F[Either[AppError, (FileMeta, Stream[F, Byte])]]
  def list: F[List[FileMeta]]
  def delete(id: UUID, userId: UUID): F[Either[AppError, Unit]]