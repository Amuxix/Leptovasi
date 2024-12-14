package leptovasi

import cats.effect.{Deferred, IO, Ref, Resource}
import cats.syntax.option.*
import cats.syntax.show.*
import fs2.Stream
import fs2.io.file.{Files, Path}
import io.circe.parser.parse
import io.circe.syntax.*
import org.typelevel.log4cats.Logger

opaque type LayerWindows = Ref[IO, Map[LayerStack, Set[String]]]

extension (layerAppRef: LayerWindows)
  def findLayerStack(windowName: String, defaultStack: LayerStack)(using Logger[IO]): IO[LayerStack] =
    layerAppRef.get.flatMap { layerApp =>
      layerApp.collectFirst {
        case (layerStack, appNames) if appNames.contains(windowName) => layerStack
      }.fold(layerAppRef.setWindowLayerStack(windowName, defaultStack))(IO.pure)
    }

  def setWindowLayerStack(windowName: String, layerStack: LayerStack)(using Logger[IO]): IO[LayerStack] =
    layerAppRef.get.flatMap { layerApp =>
      layerApp.collectFirst {
        case (layerStack, windowNames) if windowNames.contains(windowName) => layerStack
      } match
        case Some(`layerStack`)  => IO.pure(layerStack) // Window already on given layer stack
        case Some(oldLayerStack) =>
          for
            _           <- Logger[IO].info(show"Changing $windowName from $oldLayerStack to $layerStack")
            oldLayerApps = layerApp(oldLayerStack) - windowName
            newLayerApps = layerApp.getOrElse(layerStack, Set.empty) + windowName
            _           <- layerAppRef.update(_ + (oldLayerStack -> oldLayerApps) + (layerStack -> newLayerApps))
            _           <- save
          yield layerStack
        case None                =>
          for
            _ <- Logger[IO].info(show"Adding $windowName to $layerStack")
            _ <- layerAppRef.update(_.updatedWith(layerStack)(_.fold(Set(windowName))(_ + windowName).some))
            _ <- save
          yield layerStack
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
        case false => IO.pure(Map.empty[LayerStack, Set[String]])
        case true  =>
          Files[IO]
            .readUtf8(path)
            .compile
            .toList
            .flatMap(lines => IO.fromEither(parse(lines.mkString)))
            .flatMap(json => IO.fromEither(json.as[Map[LayerStack, Set[String]]]))
      }
      .flatMap(Ref.of[IO, Map[LayerStack, Set[String]]])

  def resource(filename: String): Resource[IO, LayerWindows] =
    val path = Path(filename)
    Resource.make(fromFile(path))(_.save).preAllocate(applications.complete(path).void)
