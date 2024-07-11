package leptovasi

import cats.effect.{Deferred, IO, Ref, Resource}
import cats.syntax.option.*
import fs2.Stream
import fs2.io.file.{Files, Path}
import io.circe.parser.parse
import io.circe.syntax.*
import org.typelevel.log4cats.Logger

opaque type LayerWindows = Ref[IO, Map[Layer, Set[String]]]

extension (layerAppRef: LayerWindows)
  def findLayer(windowName: String, defaultLayer: Layer)(using Logger[IO]): IO[Layer] =
    layerAppRef.get.flatMap { layerApp =>
      layerApp.collectFirst {
        case (layer, appNames) if appNames.contains(windowName) => layer
      }.fold(layerAppRef.setWindowLayer(windowName, defaultLayer))(IO.pure)
    }

  def setWindowLayer(windowName: String, layer: Layer)(using Logger[IO]): IO[Layer] =
    layerAppRef.get.flatMap { layerApp =>
      layerApp.collectFirst {
        case (layer, windowNames) if windowNames.contains(windowName) => layer
      } match
        case Some(`layer`)  => IO.pure(layer) // Window already on given layer
        case Some(oldLayer) =>
          for
            _           <- Logger[IO].info(s"Changing $windowName from $oldLayer to $layer")
            oldLayerApps = layerApp(oldLayer) - windowName
            newLayerApps = layerApp.getOrElse(layer, Set.empty) + windowName
            _           <- layerAppRef.update(_ + (oldLayer -> oldLayerApps) + (layer -> newLayerApps))
            _           <- save
          yield layer
        case None           =>
          for
            _ <- Logger[IO].info(s"Adding $windowName to $layer")
            _ <- layerAppRef.update(_.updatedWith(layer)(_.fold(Set(windowName))(_ + windowName).some))
            _ <- save
          yield layer
    }

  def save: IO[Unit] =
    LayerWindows.applications.get.flatMap { path =>
      Stream
        .eval(layerAppRef.get)
        .map(_.asJson.spaces2)
        .through(Files[IO].writeUtf8(path))
        .compile
        .drain
    }

object LayerWindows:
  val applications: Deferred[IO, Path]               = Deferred.unsafe
  private def fromFile(path: Path): IO[LayerWindows] =
    Files[IO]
      .exists(path)
      .flatMap {
        case false => IO.pure(Map.empty[Layer, Set[String]])
        case true  =>
          Files[IO]
            .readUtf8(path)
            .compile
            .toList
            .flatMap(lines => IO.fromEither(parse(lines.mkString)))
            .flatMap(json => IO.fromEither(json.as[Map[Layer, Set[String]]]))
      }
      .flatMap(Ref.of[IO, Map[Layer, Set[String]]])

  def resource(filename: String): Resource[IO, LayerWindows] =
    val path = Path(filename)
    Resource.make(fromFile(path))(_.save).preAllocate(applications.complete(path))
