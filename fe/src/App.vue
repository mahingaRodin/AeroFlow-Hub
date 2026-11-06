<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  ApiError,
  beginTotpSetup,
  completeMfaLogin,
  confirmTotpSetup as confirmTotpSetupApi,
  createRequest,
  disableTotp,
  getMfaStatus,
  listRequests,
  login,
  registerCustomer,
  submitRequest,
  verifyCustomerEmail,
} from './api'
import type { CreateTravelClearanceRequest, TravelClearanceRequest, TravelClearanceStatus } from './types'

const token = ref(localStorage.getItem('aeroflow.accessToken') || '')
const username = ref(localStorage.getItem('aeroflow.username') || '')
const authMode = ref<'login' | 'register' | 'mfa'>('login')
const requests = ref<TravelClearanceRequest[]>([])
const loading = ref(false)
const saving = ref(false)
const showCreateForm = ref(false)
const loginError = ref('')
const authNotice = ref('')
const mfaChallengeToken = ref('')
const mfaCode = ref('')
const registrationForm = ref({ email: '', password: '' })
const pageError = ref('')
const successMessage = ref('')
const securityError = ref('')
const securityMessage = ref('')
const securityLoading = ref(false)
const mfaEnabled = ref(false)
const securityPassword = ref('')
const setupCode = ref('')
const disableCode = ref('')
const totpSecret = ref('')
const otpauthUri = ref('')
const recoveryCodes = ref<string[]>([])
const loginForm = ref({ email: '', password: '' })
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
    const result = await login(loginForm.value.email.trim(), loginForm.value.password)
    loginForm.value.password = ''
    if (result.mfaRequired) {
      if (!result.challengeToken) throw new Error('The server did not return an MFA challenge.')
      mfaChallengeToken.value = result.challengeToken
      authMode.value = 'mfa'
      return
    }
    if (!result.accessToken) throw new Error('The server did not return an access token.')
    await completeSignIn(result.accessToken, loginForm.value.email.trim())
  } catch (error) {
    loginError.value = error instanceof Error ? error.message : 'Unable to sign in.'
  } finally {
    loading.value = false
  }
}

async function completeSignIn(accessToken: string, identity: string) {
  token.value = accessToken
  username.value = identity
  localStorage.setItem('aeroflow.accessToken', accessToken)
  localStorage.setItem('aeroflow.username', identity)
  authMode.value = 'login'
  mfaChallengeToken.value = ''
  mfaCode.value = ''
  await loadRequests()
  await loadSecurityStatus()
}

async function verifyMfaLogin() {
  loginError.value = ''
  loading.value = true
  try {
    const result = await completeMfaLogin(mfaChallengeToken.value, mfaCode.value.trim())
    if (!result.accessToken) throw new Error('MFA verification did not return an access token.')
    await completeSignIn(result.accessToken, loginForm.value.email.trim())
  } catch (error) {
    loginError.value = error instanceof Error ? error.message : 'Unable to verify your MFA code.'
  } finally {
    loading.value = false
  }
}

async function signUp() {
  loginError.value = ''
  authNotice.value = ''
  loading.value = true
  try {
    const result = await registerCustomer(
      registrationForm.value.email.trim(),
      registrationForm.value.password,
    )
    authNotice.value = result.message
    authMode.value = 'login'
    loginForm.value.email = registrationForm.value.email.trim()
    registrationForm.value = { email: '', password: '' }
  } catch (error) {
    loginError.value = error instanceof Error ? error.message : 'Unable to create your account.'
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

async function loadSecurityStatus() {
  if (!token.value) return
  try {
    const status = await getMfaStatus(token.value)
    mfaEnabled.value = status.totpEnabled
  } catch (error) {
    securityError.value = error instanceof Error ? error.message : 'Could not load security settings.'
  }
}

async function startTotpSetup() {
  securityError.value = ''
  securityMessage.value = ''
  recoveryCodes.value = []
  securityLoading.value = true
  try {
    const setup = await beginTotpSetup(token.value, securityPassword.value)
    totpSecret.value = setup.secret
    otpauthUri.value = setup.otpauthUri
    securityPassword.value = ''
  } catch (error) {
    securityError.value = error instanceof Error ? error.message : 'Could not start authenticator setup.'
  } finally {
    securityLoading.value = false
  }
}

async function confirmTotpSetup() {
  securityError.value = ''
  securityLoading.value = true
  try {
    const result = await confirmTotpSetupApi(token.value, setupCode.value.trim())
    recoveryCodes.value = result.recoveryCodes
    mfaEnabled.value = true
    totpSecret.value = ''
    otpauthUri.value = ''
    setupCode.value = ''
    securityMessage.value = 'Authenticator app enabled. Save these recovery codes now; they will not be shown again.'
  } catch (error) {
    securityError.value = error instanceof Error ? error.message : 'Could not confirm authenticator setup.'
  } finally {
    securityLoading.value = false
  }
}

async function turnOffTotp() {
  securityError.value = ''
  securityLoading.value = true
  try {
    await disableTotp(token.value, securityPassword.value, disableCode.value.trim())
    mfaEnabled.value = false
    securityPassword.value = ''
    disableCode.value = ''
    recoveryCodes.value = []
    securityMessage.value = 'Authenticator app MFA has been disabled.'
  } catch (error) {
    securityError.value = error instanceof Error ? error.message : 'Could not disable MFA.'
  } finally {
    securityLoading.value = false
  }
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

onMounted(async () => {
  const verificationToken = new URLSearchParams(window.location.search).get('token')
  if (verificationToken) {
    window.history.replaceState({}, '', window.location.pathname)
    try {
      const result = await verifyCustomerEmail(verificationToken)
      authNotice.value = result.message
    } catch (error) {
      authNotice.value = error instanceof Error ? error.message : 'Email verification failed.'
      loginError.value = authNotice.value
      authNotice.value = ''
    }
  }
  await loadRequests()
})
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
      <form v-if="authMode === 'login'" class="login-card" @submit.prevent="signIn">
        <div class="eyebrow">WELCOME BACK</div>
        <h2>Sign in to AeroFlow Hub</h2>
        <p class="muted">Use your verified email address to continue.</p>
        <div v-if="authNotice" class="alert alert-success" role="status">{{ authNotice }}</div>
        <div v-if="loginError" class="alert alert-error" role="alert">{{ loginError }}</div>
        <label class="field">
          <span>Email address</span>
          <input v-model="loginForm.email" autocomplete="username" type="email" placeholder="you@example.com" required />
        </label>
        <label class="field">
          <span>Password</span>
          <input v-model="loginForm.password" autocomplete="current-password" type="password" placeholder="Your password" required />
        </label>
        <button class="button button-primary button-wide" type="submit" :disabled="loading">
          {{ loading ? 'Signing in…' : 'Sign in' }} <span aria-hidden="true">→</span>
        </button>
        <button class="auth-switch" type="button" @click="authMode = 'register'; loginError = ''; authNotice = ''">
          Create a customer account
        </button>
        <p class="login-help">Crew workspace access is managed by your AeroFlow Hub administrator.</p>
      </form>

      <form v-else-if="authMode === 'register'" class="login-card" @submit.prevent="signUp">
        <div class="eyebrow">CUSTOMER ACCOUNT</div>
        <h2>Create your account</h2>
        <p class="muted">We’ll email you a verification link before sign-in is enabled.</p>
        <div v-if="loginError" class="alert alert-error" role="alert">{{ loginError }}</div>
        <label class="field">
          <span>Email address</span>
          <input v-model="registrationForm.email" autocomplete="email" type="email" maxlength="254" required />
        </label>
        <label class="field">
          <span>Password</span>
          <input v-model="registrationForm.password" autocomplete="new-password" type="password" minlength="12" maxlength="72" required />
        </label>
        <button class="button button-primary button-wide" type="submit" :disabled="loading">
          {{ loading ? 'Creating account…' : 'Create account' }} <span aria-hidden="true">→</span>
        </button>
        <button class="auth-switch" type="button" @click="authMode = 'login'; loginError = ''">
          Already registered? Sign in
        </button>
      </form>

      <form v-else class="login-card" @submit.prevent="verifyMfaLogin">
        <div class="eyebrow">ACCOUNT PROTECTION</div>
        <h2>Verify it’s you</h2>
        <p class="muted">Enter a code from your authenticator app or one of your recovery codes.</p>
        <div v-if="loginError" class="alert alert-error" role="alert">{{ loginError }}</div>
        <label class="field">
          <span>Authenticator or recovery code</span>
          <input v-model="mfaCode" autocomplete="one-time-code" inputmode="text" maxlength="64" required />
        </label>
        <button class="button button-primary button-wide" type="submit" :disabled="loading">
          {{ loading ? 'Verifying…' : 'Verify and sign in' }} <span aria-hidden="true">→</span>
        </button>
        <button class="auth-switch" type="button" @click="authMode = 'login'; mfaChallengeToken = ''; mfaCode = ''; loginError = ''">
          Cancel and return to sign in
        </button>
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
        <a class="nav-item" href="#security"><span class="nav-icon">⌑</span> Account security</a>
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

        <section id="security" class="security-card">
          <div class="section-heading">
            <div>
              <h2>Account security</h2>
              <p class="muted">Protect your account with an authenticator app.</p>
            </div>
            <span class="security-status" :class="{ 'security-status-on': mfaEnabled }">
              {{ mfaEnabled ? 'MFA enabled' : 'MFA not enabled' }}
            </span>
          </div>

          <div class="security-content">
            <div v-if="securityError" class="alert alert-error" role="alert">{{ securityError }}</div>
            <div v-if="securityMessage" class="alert alert-success" role="status">{{ securityMessage }}</div>

            <div v-if="recoveryCodes.length" class="recovery-panel">
              <strong>Save your recovery codes</strong>
              <p>Each code works once. Store them somewhere safe; they will not be shown again.</p>
              <div class="recovery-codes">
                <code v-for="code in recoveryCodes" :key="code">{{ code }}</code>
              </div>
            </div>

            <div v-else-if="totpSecret" class="security-step">
              <p>In your authenticator app, add an account using this setup key:</p>
              <code class="totp-secret">{{ totpSecret }}</code>
              <a class="auth-switch" :href="otpauthUri">Open in authenticator app</a>
              <form @submit.prevent="confirmTotpSetup">
                <label class="field">
                  <span>6-digit authenticator code</span>
                  <input v-model="setupCode" autocomplete="one-time-code" inputmode="numeric" pattern="[0-9]{6}" maxlength="6" required />
                </label>
                <button class="button button-primary" type="submit" :disabled="securityLoading">
                  {{ securityLoading ? 'Confirming…' : 'Confirm and enable MFA' }}
                </button>
              </form>
            </div>

            <form v-else-if="mfaEnabled" class="security-step" @submit.prevent="turnOffTotp">
              <p>MFA is required at every sign-in. Disabling it requires your password and a current authenticator or recovery code.</p>
              <label class="field"><span>Password</span><input v-model="securityPassword" type="password" autocomplete="current-password" required /></label>
              <label class="field"><span>Authenticator or recovery code</span><input v-model="disableCode" autocomplete="one-time-code" required /></label>
              <button class="button button-secondary" type="submit" :disabled="securityLoading">
                {{ securityLoading ? 'Updating…' : 'Disable authenticator MFA' }}
              </button>
            </form>

            <form v-else class="security-step" @submit.prevent="startTotpSetup">
              <p>Enable an authenticator app to require a second factor every time you sign in.</p>
              <label class="field"><span>Confirm your password</span><input v-model="securityPassword" type="password" autocomplete="current-password" required /></label>
              <button class="button button-primary" type="submit" :disabled="securityLoading">
                {{ securityLoading ? 'Starting…' : 'Set up authenticator MFA' }}
              </button>
            </form>
          </div>
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
