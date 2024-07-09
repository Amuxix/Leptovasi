package leptovasi

import leptovasi.hid.Device

import cats.effect.{IO, Ref}
import cats.effect.Resource
import cats.syntax.foldable.*
import fs2.Stream
import org.typelevel.log4cats.Logger

import scala.concurrent.duration.FiniteDuration

class LayerChanger(
  currentKeyboardLayer: Ref[IO, Layer],
  lastMessage: Ref[IO, String],
  layerWindows: LayerWindows,
)(using Logger[IO]):
  val layerChange = "LC(\\d)".r
  val layerSet    = "LS(\\d)".r

  extension (string: String) def toLayer: Option[Layer] = string.toIntOption.map(Layer.fromOrdinal)

  def handleSingleMessage(message: String): IO[Unit] =
    message match
      case layerChange(layer) =>
        layer.toLayer
          .fold(Logger[IO].error(s"Got Layer Change but layer is invalid $message!"))(setCurrentLayer)
      case layerSet(layer)    =>
        layer.toLayer
          .fold(Logger[IO].error(s"Got Layer Set but layer is invalid $message!"))(setLayerForCurrentWindow)
      case "ERR"              => Logger[IO].error("Got ERROR")
      case ""                 => IO.unit
      case other              => Logger[IO].warn(s"Got unkown message $other")

  def handleAllMessages(messages: String): IO[Unit] =
    val handle = messages.grouped(3).toList.traverse_ { message =>
      lastMessage.get.flatMap {
        case `message` => IO.unit // Don't handle repeated messages
        case _         =>
          for
            _ <- Logger[IO].trace(s"Handling message $message")
            _ <- handleSingleMessage(message)
            _ <- lastMessage.set(message)
          yield ()
      }
    }
    Logger[IO].trace(s"Full message $messages") *> handle

  def setLayerForCurrentWindow(layer: Layer): IO[Unit] =
    lazy val error = Logger[IO].error("Trying to set layer for current window but can't find current window!")
    WindowName.get.foldF(error)(layerWindows.setWindowLayer(_, layer).void)

  def setCurrentLayer(layer: Layer): IO[Unit] =
    Logger[IO].debug(s"Updating current layer to $layer") *> currentKeyboardLayer.set(layer)

  private def changeLayer(device: Device, windowName: String)(target: Layer): IO[Unit] =
    currentKeyboardLayer.get.flatMap {
      case `target` => IO.unit // Already on right window
      case _        =>
        for
          _ <- Logger[IO].info(s"Changing to $target for $windowName")
          _ <- device.write(s"LC${target.ordinal}")
        yield ()
    }

  def changeWindows(device: Device): IO[Unit] =
    WindowName.get.semiflatMap { windowName =>
      layerWindows.findLayer(windowName, Layer.Base).flatMap(changeLayer(device, windowName))
    }.value.void

  def readMessages(device: Device, timeout: FiniteDuration): IO[Unit] =
    device.read(timeout).semiflatMap(handleAllMessages).value.void

  def loop(device: Device, config: Configuration): IO[Unit] =
    val changeWindowStream = Stream.fixedRateStartImmediately[IO](config.windowChangeInterval).evalMap(_ => changeWindows(device))
    val readMessageStream  =
      Stream.fixedRateStartImmediately[IO](config.readInterval).evalMap(_ => readMessages(device, config.readInterval))
    changeWindowStream.concurrently(readMessageStream).compile.drain

object LayerChanger:
  def resource(config: Configuration)(using Logger[IO]): Resource[IO, LayerChanger] =
    LayerWindows.resource(config.applications).evalMap { layerApp =>
      for
        currentKeyboardLayer <- Ref.of[IO, Layer](Layer.Base)
        lastMessage          <- Ref.of[IO, String]("")
      yield LayerChanger(currentKeyboardLayer, lastMessage, layerApp)
    }
