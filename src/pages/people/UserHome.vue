<template>
  <UserPanel
    v-if="!data.loading && !data.error"
    ref="userPanelRef"
    v-model:currentItem="data.currentItem"
    :active="!data.loading"
    :show-overflow-menu="false"
    @back="$router.back()"
    @showFollowSetting2="handleCancelFollow"
  />
  <Loading v-else-if="data.loading" :is-full-screen="false" />
  <div v-if="!data.loading && data.error" class="load-error">
    <span>{{ data.error }}</span>
    <button type="button" @click="loadUser(String(route.params.uid || ''))">重试</button>
  </div>
</template>
<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { panel, recordVisit } from '@/api/user'
import UserPanel from '@/components/UserPanel.vue'
import Loading from '@/components/Loading.vue'

defineOptions({ name: 'UserHome' })

const route = useRoute()
const userPanelRef = ref<InstanceType<typeof UserPanel>>()
const data = reactive({
  loading: true,
  error: '',
  currentItem: {
    author: { uid: null },
    aweme_list: []
  } as any
})

function handleCancelFollow() {
  userPanelRef.value?.cancelFollow()
}

async function loadUser(uid: string) {
  if (!uid) return
  data.loading = true
  data.error = ''
  try {
    const infoRes = await panel({ uid })
    if (!infoRes.success) throw new Error(infoRes.msg || infoRes.message || '用户资料加载失败')
    const u = infoRes.data
    const uidVal = u.uid != null ? u.uid : uid
    data.currentItem = {
      author: {
        uid: uidVal,
        nickname: u.nickname || '',
        unique_id: u.unique_id || '',
        short_id: u.short_id || '',
        signature: u.signature || '',
        gender: u.gender,
        province: u.province || '',
        city: u.city || '',
        avatar_168x168: u.avatar_168x168 || { url_list: [] },
        avatar_300x300: u.avatar_300x300 || { url_list: [] },
        cover_url: u.cover_url || [{ url_list: [] }],
        total_favorited: u.total_favorited || 0,
        following_count: u.following_count || 0,
        user_age: u.user_age ?? -1,
        mplatform_followers_count: u.follower_count || 0,
        follow_status: u.is_followed ? 1 : 0,
        is_following_me: u.is_following_me || false,
        is_friend: u.is_friend || false,
        friend_request_sent: u.friend_request_sent || false
      },
      aweme_list: []
    }
  } catch (e: any) {
    data.error = e?.message || '用户资料加载失败，请重试'
  } finally {
    data.loading = false
  }
  // 记录访客
  void recordVisit(Number(uid)).catch(() => {})
}

onMounted(() => {
  loadUser(route.params.uid as string)
})

watch(
  () => route.params.uid,
  (newUid) => {
    if (newUid) loadUser(newUid as string)
  }
)
</script>

<style scoped>
.load-error {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: #999;
}

.load-error button {
  border: 0;
  border-radius: 4px;
  padding: 7px 18px;
  color: white;
  background: #fe2c55;
}
</style>
