package leptovasi

import io.circe.{Decoder, Encoder, KeyDecoder, KeyEncoder}
import pureconfig.ConfigReader

enum Layer:
  case Base, CanaryGaming, QwertyGaming, QwertyShiftedGaming

object Layer:
  given ConfigReader[Layer] = ConfigReader[Int].map(Layer.fromOrdinal)
  given Encoder[Layer]      = Encoder[String].contramap(_.toString)
  given Decoder[Layer]      = Decoder[String].map(Layer.valueOf)
  given KeyEncoder[Layer]   = KeyEncoder[String].contramap(_.toString)
  given KeyDecoder[Layer]   = KeyDecoder[String].map(Layer.valueOf)
