<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ApiError, createRequest, listRequests, login, submitRequest } from './api'
import type { CreateTravelClearanceRequest, TravelClearanceRequest, TravelClearanceStatus } from './types'

const token = ref(localStorage.getItem('aeroflow.accessToken') || '')
const username = ref(localStorage.getItem('aeroflow.username') || '')
const requests = ref<TravelClearanceRequest[]>([])
const loading = ref(false)
const saving = ref(false)
const showCreateForm = ref(false)
const loginError = ref('')
const pageError = ref('')
const successMessage = ref('')
const loginForm = ref({ username: '', password: '' })
const requestForm = ref<CreateTravelClearanceRequest>({
  purpose: '',
  destination: '',
  travelDate: '',
})

const today = new Date().toISOString().slice(0, 10)
const firstName = computed(() => username.value.split(/[.@_\s-]/)[0] || 'there')
const pendingCount = computed(
  () => requests.value.filter((request) => request.status === 'PENDING_SUPERVISOR').length,
)
const approvedCount = computed(
  () => requests.value.filter((request) => request.status === 'APPROVED').length,
)
const draftCount = computed(
  () => requests.value.filter((request) => request.status === 'DRAFT').length,
)
const recentRequests = computed(() => requests.value.slice(0, 6))

function formatDate(value: string): string {
  return new Intl.DateTimeFormat('en', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  }).format(new Date(`${value}T00:00:00`))
}

function statusLabel(status: TravelClearanceStatus): string {
  return {
    DRAFT: 'Draft',
    PENDING_SUPERVISOR: 'In review',
    APPROVED: 'Approved',
    REJECTED: 'Declined',
  }[status]
}

async function loadRequests() {
  if (!token.value) return
  loading.value = true
  pageError.value = ''
  try {
    requests.value = await listRequests(token.value)
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      signOut()
      loginError.value = 'Your session has expired. Please sign in again.'
    } else {
      pageError.value = error instanceof Error ? error.message : 'Could not load your requests.'
    }
  } finally {
    loading.value = false
  }
}

async function signIn() {
  loginError.value = ''
  loading.value = true
  try {
    token.value = await login(loginForm.value.username.trim(), loginForm.value.password)
    username.value = loginForm.value.username.trim()
    localStorage.setItem('aeroflow.accessToken', token.value)
    localStorage.setItem('aeroflow.username', username.value)
    loginForm.value.password = ''
    await loadRequests()
  } catch (error) {
    loginError.value = error instanceof Error ? error.message : 'Unable to sign in.'
  } finally {
    loading.value = false
  }
}

function signOut() {
  token.value = ''
  username.value = ''
  requests.value = []
  localStorage.removeItem('aeroflow.accessToken')
  localStorage.removeItem('aeroflow.username')
}

async function saveRequest() {
  if (!token.value) return
  saving.value = true
  pageError.value = ''
  try {
    const saved = await createRequest(token.value, requestForm.value)
    requests.value = [saved, ...requests.value]
    requestForm.value = { purpose: '', destination: '', travelDate: '' }
    showCreateForm.value = false
    successMessage.value = 'Your travel clearance request has been saved as a draft.'
    window.setTimeout(() => (successMessage.value = ''), 4000)
  } catch (error) {
    pageError.value = error instanceof Error ? error.message : 'Could not save your request.'
  } finally {
    saving.value = false
  }
}

async function submitDraft(request: TravelClearanceRequest) {
  pageError.value = ''
  try {
    const updated = await submitRequest(token.value, request.id)
    requests.value = requests.value.map((item) => (item.id === updated.id ? updated : item))
    successMessage.value = 'Your request has been sent to your supervisor.'
    window.setTimeout(() => (successMessage.value = ''), 4000)
  } catch (error) {
    pageError.value = error instanceof Error ? error.message : 'Could not submit this request.'
  }
}

onMounted(loadRequests)
</script>

<template>
  <main v-if="!token" class="login-layout">
    <section class="login-brand">
      <a class="brand brand-light" href="#">
        <span class="brand-mark">A</span>
        <span>AeroFlow<span class="brand-muted"> Hub</span></span>
      </a>
      <div class="login-message">
        <div class="eyebrow eyebrow-light">CREW OPERATIONS, IN SYNC</div>
        <h1>Move your<br />crew forward.</h1>
        <p>One clear view for travel approvals, crew movement, and the work that keeps every flight moving.</p>
      </div>
      <div class="login-footer">A calmer way to keep operations in motion.</div>
      <div class="login-orbit orbit-one"></div>
      <div class="login-orbit orbit-two"></div>
    </section>

    <section class="login-panel">
      <form class="login-card" @submit.prevent="signIn">
        <div class="eyebrow">WELCOME BACK</div>
        <h2>Sign in to your workspace</h2>
        <p class="muted">Use your AeroFlow Hub account to continue.</p>
        <div v-if="loginError" class="alert alert-error" role="alert">{{ loginError }}</div>
        <label class="field">
          <span>Username</span>
          <input v-model="loginForm.username" autocomplete="username" placeholder="Your username" required />
        </label>
        <label class="field">
          <span>Password</span>
          <input v-model="loginForm.password" autocomplete="current-password" type="password" placeholder="Your password" required />
        </label>
        <button class="button button-primary button-wide" type="submit" :disabled="loading">
          {{ loading ? 'Signing in…' : 'Sign in' }} <span aria-hidden="true">→</span>
        </button>
        <p class="login-help">Need access? Contact your AeroFlow Hub administrator.</p>
      </form>
      <div class="login-legal">Secure crew operations · AeroFlow Hub</div>
    </section>
  </main>

  <div v-else class="app-shell">
    <aside class="sidebar">
      <a class="brand" href="#" aria-label="AeroFlow Hub home">
        <span class="brand-mark">A</span>
        <span>AeroFlow<span class="brand-muted"> Hub</span></span>
      </a>

      <div class="workspace-label">WORKSPACE</div>
      <nav class="side-nav" aria-label="Main navigation">
        <a class="nav-item active" href="#overview"><span class="nav-icon">◫</span> Overview</a>
        <a class="nav-item" href="#requests"><span class="nav-icon">▤</span> My requests <span class="nav-count">{{ requests.length }}</span></a>
      </nav>

      <div class="sidebar-bottom">
        <div class="help-card">
          <span class="help-icon">✳</span>
          <strong>Here to help</strong>
          <p>Questions about a clearance? Reach out to your operations team.</p>
        </div>
        <button class="profile-button" type="button" @click="signOut">
          <span class="avatar">{{ username.slice(0, 1).toUpperCase() }}</span>
          <span class="profile-details"><strong>{{ username }}</strong><small>Sign out</small></span>
          <span class="profile-menu">↗</span>
        </button>
      </div>
    </aside>

    <main id="overview" class="main-content">
      <header class="topbar">
        <div class="breadcrumb">Workspace <span>/</span> <strong>Overview</strong></div>
        <div class="topbar-right"><span class="live-dot"></span> Operations portal <span class="topbar-divider"></span><span class="today-date">{{ new Intl.DateTimeFormat('en', { weekday: 'short', month: 'short', day: 'numeric' }).format(new Date()) }}</span></div>
      </header>

      <section class="page-content">
        <div class="welcome-row">
          <div>
            <div class="eyebrow">YOUR OPERATIONS AT A GLANCE</div>
            <h1>Good day, {{ firstName }} <span class="wave">✳</span></h1>
            <p class="muted">Here’s what’s happening with your crew travel clearances.</p>
          </div>
          <button class="button button-primary" type="button" @click="showCreateForm = true">
            <span class="plus">＋</span> New travel request
          </button>
        </div>

        <div v-if="pageError" class="alert alert-error" role="alert">{{ pageError }}</div>
        <div v-if="successMessage" class="alert alert-success" role="status">{{ successMessage }}</div>

        <div class="stats-grid">
          <article class="stat-card">
            <div class="stat-top"><span class="stat-icon icon-blue">▤</span><span class="stat-caption">ALL TIME</span></div>
            <div class="stat-value">{{ requests.length }}</div>
            <div class="stat-label">Travel requests</div>
            <div class="stat-foot">Your submitted clearances</div>
          </article>
          <article class="stat-card">
            <div class="stat-top"><span class="stat-icon icon-amber">◷</span><span class="stat-caption">AWAITING ACTION</span></div>
            <div class="stat-value">{{ pendingCount }}</div>
            <div class="stat-label">In supervisor review</div>
            <div class="stat-foot">We’ll keep you posted</div>
          </article>
          <article class="stat-card">
            <div class="stat-top"><span class="stat-icon icon-green">✓</span><span class="stat-caption">CLEARED TO GO</span></div>
            <div class="stat-value">{{ approvedCount }}</div>
            <div class="stat-label">Approved requests</div>
            <div class="stat-foot">Ready for your next journey</div>
          </article>
          <article class="stat-card">
            <div class="stat-top"><span class="stat-icon icon-violet">✎</span><span class="stat-caption">NOT SUBMITTED</span></div>
            <div class="stat-value">{{ draftCount }}</div>
            <div class="stat-label">Drafts in progress</div>
            <div class="stat-foot">Pick up where you left off</div>
          </article>
        </div>

        <section id="requests" class="requests-card">
          <div class="section-heading">
            <div>
              <h2>Recent travel requests</h2>
              <p class="muted">Keep track of your crew clearance submissions.</p>
            </div>
            <button class="text-button" type="button" @click="loadRequests" :disabled="loading">
              <span class="refresh-icon">↻</span> {{ loading ? 'Refreshing' : 'Refresh' }}
            </button>
          </div>

          <div v-if="loading && !requests.length" class="empty-state">
            <span class="loading-spinner"></span>
            <p>Loading your requests…</p>
          </div>
          <div v-else-if="!requests.length" class="empty-state">
            <div class="empty-icon">✈</div>
            <h3>Your next trip starts here</h3>
            <p>You don’t have any travel requests yet. Create one to get your clearance process moving.</p>
            <button class="button button-secondary" type="button" @click="showCreateForm = true">Create a request <span aria-hidden="true">→</span></button>
          </div>
          <div v-else class="table-wrap">
            <table>
              <thead>
                <tr><th>REQUEST</th><th>DESTINATION</th><th>TRAVEL DATE</th><th>STATUS</th><th class="action-heading">ACTION</th></tr>
              </thead>
              <tbody>
                <tr v-for="request in recentRequests" :key="request.id">
                  <td><div class="request-name">{{ request.purpose }}</div><div class="request-id">#{{ request.id.slice(0, 8).toUpperCase() }}</div></td>
                  <td><span class="destination-mark">⌖</span> {{ request.destination }}</td>
                  <td>{{ formatDate(request.travelDate) }}</td>
                  <td><span class="status-pill" :class="`status-${request.status.toLowerCase()}`"><span class="status-dot"></span>{{ statusLabel(request.status) }}</span></td>
                  <td class="action-cell">
                    <button v-if="request.status === 'DRAFT'" class="table-action" type="button" @click="submitDraft(request)">Submit for review <span aria-hidden="true">→</span></button>
                    <span v-else class="muted">—</span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <div v-if="requests.length > 6" class="table-footer">Showing 6 of {{ requests.length }} requests</div>
        </section>

        <footer class="page-footer"><span>© 2026 AeroFlow Hub</span><span>Built for the people who keep us moving <span class="footer-heart">✳</span></span></footer>
      </section>
    </main>

    <div v-if="showCreateForm" class="modal-backdrop" @click.self="showCreateForm = false">
      <section class="request-modal" role="dialog" aria-modal="true" aria-labelledby="request-title">
        <div class="modal-heading">
          <div><div class="eyebrow">CREW TRAVEL</div><h2 id="request-title">New travel request</h2></div>
          <button class="close-button" type="button" aria-label="Close dialog" @click="showCreateForm = false">×</button>
        </div>
        <p class="muted modal-intro">Add your trip details. You can review and submit the request when you’re ready.</p>
        <form @submit.prevent="saveRequest">
          <label class="field"><span>Travel purpose</span><input v-model="requestForm.purpose" maxlength="500" placeholder="e.g. Crew positioning" required /></label>
          <label class="field"><span>Destination</span><input v-model="requestForm.destination" maxlength="150" placeholder="City or airport" required /></label>
          <label class="field"><span>Travel date</span><input v-model="requestForm.travelDate" type="date" :min="today" required /></label>
          <div class="modal-actions">
            <button class="button button-quiet" type="button" @click="showCreateForm = false">Cancel</button>
            <button class="button button-primary" type="submit" :disabled="saving">{{ saving ? 'Saving…' : 'Save as draft' }} <span aria-hidden="true">→</span></button>
          </div>
        </form>
      </section>
    </div>
  </div>
</template>
