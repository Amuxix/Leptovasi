package leptovasi

import leptovasi.LayerStack.given
import leptovasi.hid.Device

import cats.Show
import cats.effect.{IO, Ref}
import cats.effect.Resource
import cats.syntax.show.*
import cats.syntax.traverse.*
import fs2.Stream
import org.typelevel.log4cats.Logger

import scala.concurrent.duration.FiniteDuration

class LayerChanger(
  currentLayerStack: Ref[IO, LayerStack],
  lastMessage: Ref[IO, Array[Byte]],
  layerWindows: LayerWindows,
)(using Logger[IO]):

  given Show[Byte] = Show.show { byte =>
    7.to(0, -1).map(1 << _).foldLeft("") { case (acc, mask) =>
      acc ++ (if (mask & byte) > 0 then "1" else "0")
    }
  }

  given Show[Array[Byte]] =
    Show.show(bytes => bytes.take(3).map(_.toChar).mkString ++ bytes.drop(3).map(_.show).mkString("_"))

  def handleSingleMessage(message: Array[Byte]): IO[Unit] = message.take(3).map(_.toChar).mkString match
    case "LSC" =>
      for
        activeLayers <- IO.fromEither(LayerStack.activeLayers(message.takeRight(2)))
        _            <- Logger[IO].trace(show"Handling layer stack change, active layers: ${activeLayers.mkString(", ")}")
        layerStack    = LayerStack.fromActiveLayers(activeLayers)
        _            <- Logger[IO].debug(show"Got layer stack change, active layers: ${activeLayers.mkString(", ")}, layer stack: $layerStack")
        _            <- layerStack.traverse(layerStack => setCurrentLayerStack(layerStack) *> setLayerStackForCurrentWindow(layerStack))
      yield ()
    case "LSS" =>
      for
        activeLayers <- IO.fromEither(LayerStack.activeLayers(message.takeRight(2)))
        _            <- Logger[IO].trace(show"Handling ayer stack set response, active layers: ${activeLayers.mkString(", ")}")
        layerStack    = LayerStack.fromActiveLayers(activeLayers)
        _            <- Logger[IO].debug(show"Got layer stack set response, active layers: ${activeLayers.mkString(", ")}, layer stack: $layerStack")
        _            <- layerStack.traverse(setCurrentLayerStack)
      yield ()
    case "ERR" => Logger[IO].error("Got ERROR")
    case ""    => IO.unit
    case other => Logger[IO].warn(show"Got unkown message $other")

  def handleAllMessages(messages: Array[Byte]): IO[Unit] =
    if messages.isEmpty then return IO.unit
    val message = messages.take(5)
    val handle  = lastMessage.get.flatMap {
      case `message` => IO.unit // Don't handle repeated messages
      case _         =>
        for
          _ <- Logger[IO].trace(show"Handling message $message")
          _ <- handleSingleMessage(message)
          _ <- lastMessage.set(message)
        yield ()
    }
    Logger[IO].trace(show"Full message $messages") *> handle

  def setLayerStackForCurrentWindow(layerStack: LayerStack): IO[Unit] =
    lazy val error = Logger[IO].error("Trying to set layer stack for current window but can't find current window!")
    WindowName.get.foldF(error)(layerWindows.setWindowLayerStack(_, layerStack).void)

  def setCurrentLayerStack(layerStack: LayerStack): IO[Unit] =
    Logger[IO].debug(show"Updating current layer stack to $layerStack") *> currentLayerStack.set(layerStack)

  private def changeLayer(device: Device, windowName: String)(target: LayerStack): IO[Unit] =
    currentLayerStack.get.flatMap {
      case `target` => IO.unit // Already on right window
      case current  =>
        for
          _ <- Logger[IO].info(show"Changing to $target for $windowName")
          _ <- Logger[IO].trace(show"Stack for $windowName was $current")
          _ <- device.write("LSS".toCharArray.map(_.toByte) ++ target.toByteArray)
        yield ()
    }

  def changeWindows(device: Device): IO[Unit] =
    WindowName.get.semiflatMap { windowName =>
      layerWindows.findLayerStack(windowName, LayerStack.base).flatMap(changeLayer(device, windowName))
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
        currentKeyboardLayer <- Ref.of[IO, LayerStack](LayerStack.base)
        lastMessage          <- Ref.of[IO, Array[Byte]](Array.empty)
      yield LayerChanger(currentKeyboardLayer, lastMessage, layerApp)
    }
