import { defineStore } from 'pinia'
import { friends, panel } from '@/api/user'
import { login as loginApi, register as registerApi } from '@/api/auth'
import enums from '@/utils/enums'
import { _notice } from '@/utils'
import { connectSocket, disconnectSocket } from '@/utils/socket'

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))

/** 统一用户数据格式：将后端 UserVO 或 mock 数据规范化为模板所需的字段 */
function normalizeUser(u: any): any {
  const avatar168 = u.avatar_168x168?.url_list?.[0] || u.avatar || ''
  return {
    ...u,
    id: u.uid ?? u.id,
    name: u.nickname || u.name || '',
    avatar: avatar168,
    account: u.unique_id || u.account || '',
    avatar_168x168: u.avatar_168x168 || { url_list: [avatar168] }
  }
}

export const useBaseStore = defineStore('base', {
  state: () => {
    return {
      bodyHeight: document.body.clientHeight,
      bodyWidth: document.body.clientWidth,
      maskDialog: false,
      maskDialogMode: 'dark',
      version: '17.1.0',
      excludeNames: [],
      judgeValue: 20,
      homeRefresh: 60,
      loading: false,
      routeData: null,
      users: [],
      token: localStorage.getItem('token') || '',
      // A persisted token is only a candidate session. The account is considered
      // signed in after the profile endpoint confirms it.
      isLoggedIn: false,
      authReady: false,
      profileLoaded: false,
      restoring: false,
      userinfo: {
        nickname: '',
        desc: '',
        user_age: '',
        signature: '',
        unique_id: '',
        province: '',
        city: '',
        gender: '',
        school: {
          name: '',
          department: null,
          joinTime: null,
          education: null,
          displayType: enums.DISPLAY_TYPE.ALL
        },
        avatar_168x168: {
          url_list: []
        },
        avatar_300x300: {
          url_list: []
        },
        cover_url: [
          {
            url_list: []
          }
        ],
        white_cover_url: [
          {
            url_list: []
          }
        ],
        has_password: false,
        role: 'user'
      },
      friends: { all: [], recent: [], eachOther: [] },
      message: ''
    }
  },
  getters: {
    isAdmin(): boolean {
      return this.userinfo.role === 'ADMIN'
    },
    selectFriends() {
      return this.friends.all.filter((v) => v.select)
    }
  },
  actions: {
    async init() {
      await this.restoreSession()
      if (!this.isLoggedIn) return
      const r2 = await friends()
      if (!r2.success) return
      const data = r2.data as any
      // 兼容 mock 返回数组、后端返回 { all, recent, eachOther } 两种格式
      const rawAll: any[] = Array.isArray(data) ? data : data.all || []
      const rawRecent: any[] = Array.isArray(data) ? [] : data.recent || []
      const rawEachOther: any[] = Array.isArray(data) ? [] : data.eachOther || []
      this.friends = {
        all: rawAll.map(normalizeUser),
        recent: rawRecent.map(normalizeUser),
        eachOther: rawEachOther.map(normalizeUser)
      }
      this.users = this.friends.all
    },
    async restoreSession() {
      if (this.restoring) return
      this.restoring = true
      this.authReady = false
      const token = localStorage.getItem('token') || this.token
      if (!token) {
        this.token = ''
        this.isLoggedIn = false
        this.profileLoaded = false
        this.authReady = true
        this.restoring = false
        return
      }

      this.token = token
      let result: any = null
      // The API may still be starting when the browser is reopened. Retry a few
      // times instead of turning a temporary network error into a fake session.
      for (let attempt = 0; attempt < 3; attempt += 1) {
        result = await panel()
        if (result.success || Number(result.code) === 401) break
        if (attempt < 2) await sleep(500 * 2 ** attempt)
      }

      // Logout or another login may have replaced the token while the request
      // was in flight. Never apply a stale profile response to the new session.
      if (localStorage.getItem('token') !== token) {
        this.restoring = false
        this.authReady = true
        return
      }

      if (result?.success) {
        this.userinfo = Object.assign(this.userinfo, normalizeUser(result.data || {}))
        this.isLoggedIn = true
        this.profileLoaded = true
        localStorage.setItem('role', this.userinfo.role || 'user')
        connectSocket().catch(() => {})
      } else if (Number(result?.code) === 401) {
        // Only an explicit authentication failure invalidates the persisted token.
        this.token = ''
        this.isLoggedIn = false
        this.profileLoaded = false
        localStorage.removeItem('token')
        localStorage.removeItem('role')
        disconnectSocket()
      } else {
        // Keep the token for a later focus/online retry, but never expose an empty
        // profile as a logged-in account.
        this.isLoggedIn = false
        this.profileLoaded = false
      }
      this.authReady = true
      this.restoring = false
    },
    async login(email: string, password: string): Promise<{ success: boolean; msg?: string }> {
      const r = await loginApi(email, password)
      if (r.success) {
        const token = r.data.token
        if (token) {
          this.token = token
          this.isLoggedIn = false
          this.profileLoaded = false
          this.authReady = false
          localStorage.setItem('token', token)
          const p = await panel()
          if (p.success) {
            this.userinfo = Object.assign(this.userinfo, normalizeUser(p.data || {}))
            this.isLoggedIn = true
            this.profileLoaded = true
            localStorage.setItem('role', this.userinfo.role || 'user')
          } else if (Number(p.code) === 401) {
            this.token = ''
            localStorage.removeItem('token')
          }
          this.authReady = true
          connectSocket().catch(() => {})
          return { success: true }
        }
      }
      return { success: false, msg: (r.data as any)?.msg || '登录失败' }
    },
    async register(
      email: string,
      code: string,
      password?: string,
      nickname?: string,
      role?: string,
      shopName?: string
    ): Promise<{ success: boolean; msg?: string }> {
      const r = await registerApi(email, code, password, nickname, role, shopName)
      if (r.success) {
        _notice('注册成功，请登录')
        return { success: true }
      }
      return { success: false, msg: (r.data as any)?.msg || '注册失败' }
    },
    logout() {
      disconnectSocket()
      this.token = ''
      this.isLoggedIn = false
      this.profileLoaded = false
      localStorage.removeItem('token')
      localStorage.removeItem('role')
      this.userinfo = {
        nickname: '',
        desc: '',
        user_age: '',
        signature: '',
        unique_id: '',
        province: '',
        city: '',
        gender: '',
        school: {
          name: '',
          department: null,
          joinTime: null,
          education: null,
          displayType: enums.DISPLAY_TYPE.ALL
        },
        avatar_168x168: { url_list: [] },
        avatar_300x300: { url_list: [] },
        cover_url: [{ url_list: [] }],
        white_cover_url: [{ url_list: [] }],
        has_password: false,
        role: 'user'
      }
      _notice('已退出登录')
    },
    setUserinfo(val) {
      this.userinfo = Object.assign(this.userinfo, val)
    },
    setMaskDialog(val) {
      this.maskDialog = val.state
      if (val.mode) {
        this.maskDialogMode = val.mode
      }
    },
    updateExcludeNames(val) {
      if (val.type === 'add') {
        if (!this.excludeNames.find((v) => v === val.value)) {
          this.excludeNames.push(val.value)
        }
      } else {
        const resIndex = this.excludeNames.findIndex((v) => v === val.value)
        if (resIndex !== -1) {
          this.excludeNames.splice(resIndex, 1)
        }
      }
    }
  }
})
