package leptovasi

import cats.Show
import cats.syntax.either.*
import io.circe.{Decoder, Encoder, KeyDecoder, KeyEncoder}

case class LayerStack(baseLayer: Layer, specialLayers: Layer*):
  lazy val toShort: Short           = (baseLayer.ordinal +: specialLayers.map(_.ordinal)).foldLeft(0)(_ | 1 << _).toShort
  lazy val toByteArray: Array[Byte] = Array(toShort.toByte, (toShort >> 8).toByte)

object LayerStack:
  val base = LayerStack(Layer.Base)

  private def activeLayersFromShort(short: Short): Seq[Layer] =
    (0 to 15).collect {
      case n if ((1 << n) & short) > 0 => Layer.fromOrdinal(n)
    }

  def activeLayers(bytes: Array[Byte]): Either[Exception, Seq[Layer]] = bytes match
    case Array(low, high) =>
      Right(activeLayersFromShort(((high << 8).toShort | (low & 0x00ff).toShort).toShort))
    case _                =>
      Left(new Exception("Array has wrong size"))

  def fromActiveLayers(layers: Seq[Layer]): Option[LayerStack] =
    val baseLayers    = layers.filter(_.`type` == LayerType.Base)
    val specialLayers = layers.filter(_.`type` == LayerType.Special)
    Option.when(baseLayers.size == 1)(LayerStack(baseLayers.head, specialLayers*))

  private def fromShort(short: Short): Option[LayerStack] =
    fromActiveLayers(activeLayersFromShort(short))

  given Encoder[LayerStack] = Encoder[Short].contramap(_.toShort)

  given Decoder[LayerStack] = Decoder[Short].emap(LayerStack.fromShort(_).toRight("Invalid LayerStack"))

  given KeyEncoder[LayerStack] = KeyEncoder[Short].contramap(_.toShort)

  given KeyDecoder[LayerStack] = KeyDecoder[Short].map(LayerStack.fromShort(_).getOrElse(throw new Exception("Invalid LayerStack")))

  given Show[LayerStack] =
    Show.show(stack => s"${stack.baseLayer}${if stack.specialLayers.isEmpty then "" else stack.specialLayers.mkString(", ", ", ", "")}")
