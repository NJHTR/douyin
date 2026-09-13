import { _stopPropagation } from '@/utils'

type ClickHandlers = {
  down: (event: PointerEvent) => void
  up: (event: PointerEvent) => void
  cancel: () => void
  value?: (event: PointerEvent) => void
}

const handlers = new WeakMap<HTMLElement, ClickHandlers>()

// Pointer events keep the custom directive responsive after a vertical swipe.
// Store stable handlers so Vue can remove them during unmount.
export function useClick() {
  return {
    mounted(el: HTMLElement, binding: any) {
      const entry: ClickHandlers = {
        value: binding.value,
        down: (event) => _stopPropagation(event),
        up: (event) => {
          _stopPropagation(event)
          entry.value?.(event)
        },
        cancel: () => undefined
      }
      handlers.set(el, entry)
      el.addEventListener('pointerdown', entry.down)
      el.addEventListener('pointerup', entry.up)
      el.addEventListener('pointercancel', entry.cancel)
    },
    updated(el: HTMLElement, binding: any) {
      const entry = handlers.get(el)
      if (entry) entry.value = binding.value
    },
    unmounted(el: HTMLElement) {
      const entry = handlers.get(el)
      if (!entry) return
      el.removeEventListener('pointerdown', entry.down)
      el.removeEventListener('pointerup', entry.up)
      el.removeEventListener('pointercancel', entry.cancel)
      handlers.delete(el)
    }
  }
}
