export const MESSAGE_TYPE = {
  TEXT: 1,
  IMAGE: 2,
  AUDIO: 3,
  VIDEO: 4
} as const

export type MessageCapability = keyof typeof MESSAGE_TYPE
export type ConversationKind = 'PRIVATE' | 'GROUP'

const SUPPORTED_BY_CONVERSATION: Record<ConversationKind, readonly MessageCapability[]> = {
  PRIVATE: ['TEXT', 'IMAGE', 'AUDIO', 'VIDEO'],
  GROUP: ['TEXT', 'IMAGE', 'AUDIO', 'VIDEO']
}

export function getSupportedMessageCapabilities(kind: ConversationKind) {
  return SUPPORTED_BY_CONVERSATION[kind]
}

export function supportsMessageCapability(kind: ConversationKind, capability: MessageCapability) {
  return SUPPORTED_BY_CONVERSATION[kind].includes(capability)
}
