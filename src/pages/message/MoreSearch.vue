<template>
  <div id="MoreSearch">
    <div class="content">
      <div class="scope-label">搜索用户（全站）；加好友需双方互相关注</div>
      <Search
        v-model="data.searchKey"
        right-text="取消"
        right-text-color="white"
        @notice="router.back"
        @search="search"
        :isShowRightText="true"
      />
      <div v-if="data.loading" class="state">搜索中...</div>
      <div v-else-if="data.error" class="state error">
        <span>{{ data.error }}</span>
        <button type="button" @click="search">重试</button>
      </div>
      <div v-else-if="data.searched && !data.results.length" class="state">暂无相关用户</div>
      <div v-else class="user-list">
        <div v-for="item in data.results" :key="item.uid" class="user-item">
          <img :src="avatarOf(item)" :alt="item.nickname || '用户头像'" class="avatar" />
          <div class="user-info">
            <div class="name">{{ item.nickname || '未命名用户' }}</div>
            <div class="account">SeekFlowid: {{ item.unique_id || item.short_id || item.uid }}</div>
          </div>
          <button
            v-if="item.is_friend"
            type="button"
            class="action muted"
            disabled
          >
            已是好友
          </button>
          <button
            v-else-if="item.friend_request_sent"
            type="button"
            class="action muted"
            disabled
          >
            已申请
          </button>
          <button
            v-else
            type="button"
            class="action"
            :disabled="data.submitting === item.uid"
            @click="addFriend(item)"
          >
            {{ data.submitting === item.uid ? '发送中' : '加好友' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
<script setup lang="ts">
import Search from '@/components/Search.vue'
import { searchUsers, sendFriendRequest } from '@/api/user'
import { _checkImgUrl, _notice } from '@/utils'
import { onMounted, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'

defineOptions({
  name: 'MoreSearch'
})

const router = useRouter()
const route = useRoute()
const data = reactive({
  searchKey: '',
  results: [] as any[],
  loading: false,
  searched: false,
  error: '',
  submitting: null as number | null
})

function avatarOf(user: any) {
  return _checkImgUrl(user.avatar_168x168?.url_list?.[0] || user.avatar_300x300?.url_list?.[0] || user.avatar)
}

async function search() {
  const keyword = data.searchKey.trim()
  data.searched = true
  data.error = ''
  if (!keyword) {
    data.results = []
    return
  }
  data.loading = true
  try {
    const res = await searchUsers(keyword)
    if (!res.success) throw new Error((res as any).msg || '搜索失败')
    data.results = Array.isArray(res.data) ? res.data : []
  } catch (error: any) {
    data.results = []
    data.error = error?.message || '搜索失败，请重试'
  } finally {
    data.loading = false
  }
}

async function addFriend(user: any) {
  const uid = Number(user.uid)
  if (!uid || data.submitting !== null) return
  data.submitting = uid
  try {
    const res = await sendFriendRequest(uid)
    if (!res.success) throw new Error((res as any).msg || '好友申请发送失败')
    user.friend_request_sent = true
    _notice('好友申请已发送')
  } catch (error: any) {
    _notice(error?.message || '好友申请发送失败，请重试')
  } finally {
    data.submitting = null
  }
}

onMounted(() => {
  const key = typeof route.query.key === 'string' ? route.query.key : ''
  data.searchKey = key
  if (key) search()
})
</script>

<style scoped lang="less">
#MoreSearch {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  top: 0;
  overflow: auto;
  color: white;
  font-size: 14rem;

  .content {
    padding: var(--page-padding);
  }

  .scope-label {
    color: var(--second-text-color);
    font-size: 12rem;
    margin: 6rem 0 8rem;
  }

  .state {
    padding: 42rem 0;
    color: var(--second-text-color);
    text-align: center;

    &.error {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 12rem;
    }

    button {
      border: 0;
      background: var(--primary-btn-color);
      color: white;
      padding: 6rem 16rem;
      border-radius: 4rem;
    }
  }

  .user-list {
    margin-top: 10rem;
  }

  .user-item {
    display: flex;
    align-items: center;
    min-height: 64rem;
    gap: 10rem;
    border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  }

  .avatar {
    width: 42rem;
    height: 42rem;
    border-radius: 50%;
    object-fit: cover;
  }

  .user-info {
    flex: 1;
    min-width: 0;
  }

  .name,
  .account {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .name {
    color: white;
    font-size: 14rem;
  }

  .account {
    color: var(--second-text-color);
    font-size: 11rem;
    margin-top: 4rem;
  }

  .action {
    border: 0;
    border-radius: 4rem;
    background: var(--primary-btn-color);
    color: white;
    padding: 6rem 10rem;
    flex-shrink: 0;

    &:disabled {
      opacity: 0.65;
    }

    &.muted {
      background: var(--second-btn-color-tran);
      color: var(--second-text-color);
    }
  }
}
</style>
