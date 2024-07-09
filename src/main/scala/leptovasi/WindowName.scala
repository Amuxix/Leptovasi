package leptovasi

import cats.data.OptionT
import cats.effect.IO
import cats.syntax.foldable.*
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.{Kernel32, User32, WinDef}
import com.sun.jna.ptr.IntByReference

object WindowName:
  val get: OptionT[IO, String] =
    val PROCESS_VM_READ           = 0x0010
    val PROCESS_QUERY_INFORMATION = 0x0400
    val windowName                = for
      user32             <- IO(User32.INSTANCE)
      windowHandle       <- IO(user32.GetForegroundWindow)
      pid                 = new IntByReference()
      _                  <- IO(user32.GetWindowThreadProcessId(windowHandle, pid))
      processHandleOpt   <-
        IO(Option(Kernel32.INSTANCE.OpenProcess(PROCESS_VM_READ | PROCESS_QUERY_INFORMATION, true, pid.getValue)))
      nameBytes           = new Array[Byte](512)
      _                  <- processHandleOpt.traverse_ { processHandle =>
                              IO(Psapi.INSTANCE.GetModuleBaseNameW(processHandle.getPointer, Pointer.NULL, nameBytes, nameBytes.length))
                            }
      windowNameExtension = new String(nameBytes).filter(!_.isControl).toLowerCase
      windowName          = windowNameExtension.splitAt(windowNameExtension.lastIndexOf("."))(0)
    yield Option.unless(windowName.isBlank)(windowName)
    OptionT(windowName)
