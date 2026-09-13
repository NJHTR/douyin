<script setup lang="ts">
import BaseMusic from '../BaseMusic.vue'
import { _formatNumber, cloneDeep, _notice, _checkImgUrl } from '@/utils'
import bus, { EVENT_KEY } from '@/utils/bus'
import { Icon } from '@iconify/vue'
import { useClick } from '@/utils/hooks/useClick'
import { inject, onMounted, onUnmounted } from 'vue'
import { toggleVideoLike, toggleCollect } from '@/api/videos'
import { toggleFollowUser } from '@/api/user'

const props = defineProps({
  isMy: {
    type: Boolean,
    default: () => {
      return false
    }
  },
  item: {
    type: Object,
    default: () => {
      return {}
    }
  }
})

const position = inject<any>('position', {})

const emit = defineEmits(['update:item', 'goUserInfo', 'showComments', 'showShare', 'goMusic'])

function _updateItem(props, key, val) {
  const old = cloneDeep(props.item)
  old[key] = val
  emit('update:item', old)
  bus.emit(EVENT_KEY.UPDATE_ITEM, { position: position.value, item: old })
}

let liking = false

async function loved() {
  if (liking) return
  const awemeId = props.item.aweme_id
  if (!awemeId) return

  liking = true
  const wasLoved = props.item.is_loved
  // 乐观更新
  props.item.is_loved = !wasLoved
  props.item.statistics.digg_count += wasLoved ? -1 : 1
  _updateItem(props, 'is_loved', !wasLoved)

  try {
    const res = await toggleVideoLike(awemeId)
    if (res.success) {
      props.item.statistics.digg_count = res.data.likeCount
      _updateItem(props, 'is_loved', res.data.isLoved)
      bus.emit(EVENT_KEY.LIKE_UPDATED)
    } else throw new Error(res.msg || '暂时无法完成操作，请稍后重试')
  } catch (error) {
    props.item.is_loved = wasLoved
    props.item.statistics.digg_count += wasLoved ? 1 : -1
    _updateItem(props, 'is_loved', wasLoved)
    _notice(error instanceof Error ? error.message : '暂时无法完成操作，请稍后重试')
  } finally {
    liking = false
  }
}

let collecting = false

async function collected() {
  if (collecting) return
  const awemeId = props.item.aweme_id
  if (!awemeId) return

  collecting = true
  const wasCollected = props.item.is_collect
  // 乐观更新
  props.item.is_collect = !wasCollected
  props.item.statistics.collect_count += wasCollected ? -1 : 1
  _updateItem(props, 'is_collect', !wasCollected)

  try {
    const res = await toggleCollect(awemeId)
    if (res.success) {
      props.item.is_collect = res.data.isCollected
      props.item.statistics.collect_count = res.data.collectCount
      _updateItem(props, 'is_collect', res.data.isCollected)
      bus.emit(EVENT_KEY.COLLECT_UPDATED)
    } else throw new Error(res.msg || '暂时无法完成操作，请稍后重试')
  } catch {
    props.item.is_collect = wasCollected
    props.item.statistics.collect_count += wasCollected ? 1 : -1
    _updateItem(props, 'is_collect', wasCollected)
    _notice('暂时无法完成操作，请稍后重试')
  } finally {
    collecting = false
  }
}

async function attention(e) {
  const authorId = props.item.author?.uid
  if (!authorId) return
  const wasFollowing = Boolean(props.item.is_attention)
  const target = e.currentTarget as HTMLElement
  props.item.is_attention = true
  target.classList.add('attention')
  try {
    const res = await toggleFollowUser(authorId)
    if (!res.success) throw new Error(res.msg || '暂时无法完成操作，请稍后重试')
    const next = Boolean(res.data?.isAttention)
    props.item.is_attention = next
    _updateItem(props, 'is_attention', next)
  } catch (error) {
    props.item.is_attention = wasFollowing
    target.classList.toggle('attention', wasFollowing)
    _updateItem(props, 'is_attention', wasFollowing)
    _notice(error instanceof Error ? error.message : '暂时无法完成操作，请稍后重试')
  }
}

function showComments() {
  bus.emit(EVENT_KEY.OPEN_COMMENTS, props.item.aweme_id)
}

const vClick = useClick()

onMounted(() => {
  bus.on(EVENT_KEY.COMMENT_ADDED, (videoId: string) => {
    if (String(videoId) === String(props.item.aweme_id)) {
      props.item.statistics.comment_count++
      _updateItem(props, 'statistics', { ...props.item.statistics })
    }
  })
})
onUnmounted(() => {
  bus.off(EVENT_KEY.COMMENT_ADDED)
})
</script>

<template>
  <div class="toolbar mb1r">
    <div class="avatar-ctn mb2r">
      <button
        type="button"
        class="avatar-button"
        aria-label="打开作者主页"
        v-click="() => bus.emit(EVENT_KEY.GO_USERINFO)"
      >
        <img
          class="avatar"
          :src="_checkImgUrl(item.author?.avatar_168x168?.url_list?.[0])"
          alt=""
        />
      </button>
      <transition name="fade">
        <button
          v-if="!item.is_attention && !isMy"
          type="button"
          v-click="attention"
          class="options"
          aria-label="关注作者"
        >
          <img class="no" src="../../assets/img/icon/add-light.png" alt="" />
          <img class="yes" src="../../assets/img/icon/ok-red.png" alt="" />
        </button>
      </transition>
    </div>
    <button
      type="button"
      class="love mb2r action-button"
      :aria-label="item.is_loved ? '取消点赞' : '点赞'"
      v-click="loved"
    >
      <div>
        <img src="../../assets/img/icon/love.svg" class="love-image" v-if="!item.is_loved" />
        <img src="../../assets/img/icon/loved.svg" class="love-image" v-if="item.is_loved" />
      </div>
      <span>{{ _formatNumber(item.statistics.digg_count) }}</span>
    </button>
    <button type="button" class="message mb2r action-button" aria-label="打开评论" v-click="showComments">
      <Icon icon="mage:message-dots-round-fill" class="icon" style="color: white" />
      <span>{{ _formatNumber(item.statistics.comment_count) }}</span>
    </button>
    <!--TODO     -->
    <button
      type="button"
      class="message mb2r action-button"
      :aria-label="item.is_collect ? '取消收藏' : '收藏'"
      v-click="collected"
    >
      <Icon
        v-if="item.is_collect"
        icon="ic:round-star"
        class="icon"
        style="color: rgb(252, 179, 3)"
      />
      <Icon v-else icon="ic:round-star" class="icon" style="color: white" />
      <span>{{ _formatNumber(item.statistics.collect_count) }}</span>
    </button>
    <button v-if="!props.isMy" type="button" class="share mb2r action-button" aria-label="分享视频" v-click="() => bus.emit(EVENT_KEY.SHOW_SHARE)">
      <img src="../../assets/img/icon/share-white-full.png" alt="" class="share-image" />
      <span>{{ _formatNumber(item.statistics.share_count) }}</span>
    </button>
    <button v-else type="button" class="share mb2r action-button" aria-label="更多操作" v-click="() => bus.emit(EVENT_KEY.SHOW_SHARE)">
      <img src="../../assets/img/icon/menu-white.png" alt="" class="share-image" />
    </button>
    <!--    <BaseMusic-->
    <!--        :cover="item.music.cover"-->
    <!--        v-click="$router.push('/home/music')"-->
    <!--    /> -->
    <BaseMusic />
  </div>
</template>

<style scoped lang="less">
.toolbar {
  //width: 40px;
  position: absolute;
  bottom: 0;
  right: 10rem;
  color: #fff;
  display: flex;
  flex-direction: column;
  align-items: center;

  .avatar-ctn {
    position: relative;

    @w: 45rem;

    .avatar {
      width: @w;
      height: @w;
      border: 3rem solid white;
      border-radius: 50%;
    }

    .avatar-button,
    .options {
      border: 0;
      padding: 0;
      background: transparent;
      cursor: pointer;
    }

    .options {
      position: absolute;
      border-radius: 50%;
      margin: auto;
      left: 0;
      right: 0;
      bottom: -5px;
      background: red;
      //background: black;
      width: 18rem;
      height: 18rem;
      display: flex;
      justify-content: center;
      align-items: center;
      transition: all 1s;

      img {
        position: absolute;
        width: 14rem;
        height: 14rem;
        transition: all 1s;
      }

      .yes {
        opacity: 0;
        transform: rotate(-180deg);
      }

      &.attention {
        background: white;

        .no {
          opacity: 0;
          transform: rotate(180deg);
        }

        .yes {
          opacity: 1;
          transform: rotate(0deg);
        }
      }
    }
  }

  .love,
  .message,
  .share {
    display: flex;
    flex-direction: column;
    justify-content: center;
    align-items: center;

    @width: 35rem;

    img {
      width: @width;
      height: @width;
    }

    span {
      font-size: 12rem;
    }
  }

  .action-button {
    min-width: 48rem;
    min-height: 48rem;
    border: 0;
    padding: 4rem;
    color: inherit;
    background: transparent;
    cursor: pointer;
    font: inherit;
  }

  .icon {
    font-size: 40rem;
  }

  .loved {
    background: red;
  }
}

@media (prefers-reduced-motion: reduce) {
  .toolbar *,
  .toolbar *::before,
  .toolbar *::after {
    animation-duration: 0.01ms !important;
    transition-duration: 0.01ms !important;
  }
}
</style>
