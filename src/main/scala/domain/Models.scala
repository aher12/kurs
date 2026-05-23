package domain

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import java.time.Instant
import java.util.UUID

final case class User(
                       id:       UUID,
                       login:    String,
                       password: String
                     )

object User:
  given Decoder[User] = deriveDecoder
  given Encoder[User] = deriveEncoder

final case class FileMeta(
                           id:           UUID,
                           originalName: String,
                           size:         Long,
                           passwordHash: String,
                           uploadedBy:   UUID,
                           uploadedAt:   Instant
                         )

object FileMeta:
  given Decoder[FileMeta] = deriveDecoder
  given Encoder[FileMeta] = deriveEncoder

final case class LoginRequest(login: String, password: String)

object LoginRequest:
  given Decoder[LoginRequest] = deriveDecoder

final case class RegisterRequest(login: String, password: String)

object RegisterRequest:
  given Decoder[RegisterRequest] = deriveDecoder