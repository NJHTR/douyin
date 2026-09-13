import { describe, expect, it } from 'vitest'
import {
  getSupportedMessageCapabilities,
  supportsMessageCapability
} from '../messageCapabilities'

describe('message capability contract', () => {
  it('keeps private and group media capabilities in parity', () => {
    expect(getSupportedMessageCapabilities('PRIVATE')).toEqual(
      getSupportedMessageCapabilities('GROUP')
    )
    expect(supportsMessageCapability('GROUP', 'IMAGE')).toBe(true)
  })

  it('rejects capabilities that are not part of the contract', () => {
    expect(supportsMessageCapability('PRIVATE', 'TEXT')).toBe(true)
    expect(supportsMessageCapability('GROUP', 'FILE' as never)).toBe(false)
  })
})
