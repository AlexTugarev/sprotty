import {ContainerModule} from "inversify"
import {TYPES} from "../../base/types"
import {CenterKeyboardListener, CenterCommand, FitToScreenCommand} from "./center-fit"
import {ScrollMouseListener} from "./scroll"
import {ViewportCommand} from "./viewport"
import {ZoomMouseListener} from "./zoom"

export * from './center-fit'
export * from './scroll'
export * from './viewport'
export * from './zoom'

export const viewportFeature = Symbol('viewportFeature')

export const viewportModule = new ContainerModule(bind => {
    bind(TYPES.ICommand).toConstructor(CenterCommand)
    bind(TYPES.ICommand).toConstructor(FitToScreenCommand)
    bind(TYPES.ICommand).toConstructor(ViewportCommand)
    bind(TYPES.KeyListener).to(CenterKeyboardListener)
    bind(TYPES.MouseListener).to(ScrollMouseListener)
    bind(TYPES.MouseListener).to(ZoomMouseListener)
})