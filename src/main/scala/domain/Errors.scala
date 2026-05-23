package domain

enum AppError:
  case InvalidCredentials
  case UserAlreadyExists
  case FileNotFound
  case WrongPassword
  case Unauthorized
  case InternalError(msg: String)

  def message: String = this match
    case InvalidCredentials  => "Invalid login or password"
    case UserAlreadyExists   => "User already exists"
    case FileNotFound        => "File not found"
    case WrongPassword       => "Wrong file password"
    case Unauthorized        => "Please log in"
    case InternalError(msg)  => msg