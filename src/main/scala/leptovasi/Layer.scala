package leptovasi

enum LayerType:
  case Base, Special, Momentary

enum Layer(val `type`: LayerType):
  case Base                extends Layer(LayerType.Base)
  case CanaryGaming        extends Layer(LayerType.Base)
  case QwertyGaming        extends Layer(LayerType.Base)
  case QwertyShiftedGaming extends Layer(LayerType.Base)
  case SpaceBackspaceSwap  extends Layer(LayerType.Special)
  case KeypadAndSymbols    extends Layer(LayerType.Momentary)
  case ExtraSymbols        extends Layer(LayerType.Momentary)
  case Movement            extends Layer(LayerType.Momentary)
  case Shortcuts           extends Layer(LayerType.Momentary)
