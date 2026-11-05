package com.glassmark.app

import android.app.Activity
import android.app.Application
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("glassmark", Context.MODE_PRIVATE)
    private val db = Room.databaseBuilder(app, GlassmarkDatabase::class.java, "glassmark.db")
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
    private val repo = Repository(db)
    private val auth = FirebaseAuth.getInstance()

    val user = MutableStateFlow<FirebaseUser?>(auth.currentUser)
    val customAvatarUri = MutableStateFlow(prefs.getString("custom_avatar_uri", null))
    // Aparência (persistida em SharedPreferences)
    val themeMode = MutableStateFlow(ThemeMode.fromKey(prefs.getString("theme_mode", null)))
    val amoledInDark = MutableStateFlow(prefs.getBoolean("amoled_in_dark", false))
    val appFont = MutableStateFlow(AppFont.fromKey(prefs.getString("app_font", null)))
    val textScale = MutableStateFlow(TextScale.fromKey(prefs.getString("text_scale", null)))
    val gridView = MutableStateFlow(prefs.getBoolean("grid_view", false))
    val cardSize = MutableStateFlow(CardSize.fromKey(prefs.getString("card_size", null)))
    val hiddenCollections = MutableStateFlow(prefs.getStringSet("hidden_collections", emptySet())?.toSet() ?: emptySet())
    val summarizing = MutableStateFlow<Set<String>>(emptySet())
    val aiMessage = MutableStateFlow<String?>(null)
    private val ogTried = HashSet<String>()
    private val ogGate = kotlinx.coroutines.sync.Semaphore(3)
    val guest = MutableStateFlow(prefs.getBoolean("guest", false))
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)       // erro/aviso nas telas de login
    val syncError = MutableStateFlow<String?>(null)     // erro de sincronização
    val offerUpload = MutableStateFlow(false)
    val guestCount = MutableStateFlow(0)
    val linksLoaded = MutableStateFlow(false)

    private val ownerUid: StateFlow<String> = user.map { it?.uid.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, auth.currentUser?.uid.orEmpty())

    val links: StateFlow<List<LinkEntity>> = ownerUid.flatMapLatest { repo.observeLinks(it).onEach { linksLoaded.value = true } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val collections: StateFlow<List<String>> = ownerUid.flatMapLatest { uid ->
        combine(repo.observeCollections(uid), repo.observeLinks(uid)) { cols, links ->
            (listOf("Inbox") + cols.map { it.name } + links.map { it.collection }).distinct().sortedWith(compareBy({ it != "Inbox" }, { it.lowercase() }))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf("Inbox"))

    private var lastUid = ""
    private val authListener = FirebaseAuth.AuthStateListener { a ->
        val u = a.currentUser
        user.value = u
        val newUid = u?.uid.orEmpty()
        if (newUid != lastUid) {
            val prev = lastUid
            lastUid = newUid
            repo.stopSync()
            syncError.value = null
            viewModelScope.launch {
                if (prev.isNotEmpty()) repo.clearCache(prev)
                if (newUid.isNotEmpty()) {
                    setGuest(false)
                    repo.startSync(newUid) { syncError.value = it }
                    guestCount.value = repo.guestCount()
                    if (guestCount.value > 0) offerUpload.value = true
                }
            }
        }
    }

    init { auth.addAuthStateListener(authListener) }

    override fun onCleared() {
        auth.removeAuthStateListener(authListener)
        repo.stopSync()
    }

    private fun setGuest(v: Boolean) {
        guest.value = v
        prefs.edit().putBoolean("guest", v).apply()
    }

    fun setThemeMode(mode: ThemeMode) { themeMode.value = mode; prefs.edit().putString("theme_mode", mode.key).apply() }
    fun setAmoledInDark(v: Boolean) { amoledInDark.value = v; prefs.edit().putBoolean("amoled_in_dark", v).apply() }
    fun setAppFont(f: AppFont) { appFont.value = f; prefs.edit().putString("app_font", f.key).apply() }
    fun setGridView(v: Boolean) { gridView.value = v; prefs.edit().putBoolean("grid_view", v).apply() }
    fun setCardSize(s: CardSize) { cardSize.value = s; prefs.edit().putString("card_size", s.key).apply() }
    private fun saveHidden(s: Set<String>) { hiddenCollections.value = s; prefs.edit().putStringSet("hidden_collections", s).apply() }
    fun hideCollection(name: String) = saveHidden(hiddenCollections.value + name)
    fun unhideCollection(name: String) = saveHidden(hiddenCollections.value - name)
    fun setLinkHidden(link: LinkEntity, v: Boolean) { viewModelScope.launch { repo.setField(ownerUid.value, link.docId, "hidden", v, ::report) } }

    /** Busca a imagem de prévia (og:image) uma vez por link; "-" marca que não há imagem. */
    fun fetchOg(link: LinkEntity) {
        if (link.ogImage.isNotEmpty() || !ogTried.add(link.docId)) return
        viewModelScope.launch {
            ogGate.acquire()
            try {
                val img = PageFetcher.fetch(link.url)?.ogImage ?: "-"
                repo.setField(ownerUid.value, link.docId, "ogImage", img, ::report)
            } finally { ogGate.release() }
        }
    }

    fun summarize(link: LinkEntity) {
        if (link.docId in summarizing.value) return
        summarizing.value = summarizing.value + link.docId
        viewModelScope.launch {
            try {
                val info = PageFetcher.fetch(link.url)
                if (info == null) { aiMessage.value = "Não consegui abrir a página para resumir."; return@launch }
                AiSummary.summarize(link.url, info)
                    .onSuccess { repo.setField(ownerUid.value, link.docId, "summary", it, ::report) }
                    .onFailure { aiMessage.value = it.message }
            } finally { summarizing.value = summarizing.value - link.docId }
        }
    }

    fun setTextScale(scale: TextScale) { textScale.value = scale; prefs.edit().putString("text_scale", scale.key).apply() }
    fun saveCustomAvatar(uri: String?) { customAvatarUri.value = uri; prefs.edit().putString("custom_avatar_uri", uri).apply() }

    fun continueAsGuest() { message.value = null; setGuest(true) }
    fun goToLogin() { message.value = null; setGuest(false) }

    // ---- autenticação ----
    private fun launchAuth(block: suspend () -> Unit) {
        viewModelScope.launch {
            busy.value = true; message.value = null
            try { block() } catch (e: Exception) { message.value = friendly(e) } finally { busy.value = false }
        }
    }

    fun signIn(email: String, password: String) = launchAuth {
        if (email.isBlank() || password.isBlank()) { message.value = "Preencha e-mail e senha."; return@launchAuth }
        auth.signInWithEmailAndPassword(email.trim(), password).await()
    }

    fun signUp(name: String, email: String, password: String) = launchAuth {
        if (name.isBlank() || email.isBlank() || password.isBlank()) { message.value = "Preencha nome, e-mail e senha."; return@launchAuth }
        val res = auth.createUserWithEmailAndPassword(email.trim(), password).await()
        val u = res.user
        if (u != null) {
            u.updateProfile(userProfileChangeRequest { displayName = name.trim() }).await()
            FirebaseFirestore.getInstance().collection("users").document(u.uid)
                .set(mapOf("name" to name.trim(), "email" to email.trim()), SetOptions.merge())
        }
    }

    fun resetPassword(email: String, onDone: () -> Unit) = launchAuth {
        if (email.isBlank()) { message.value = "Informe o e-mail da conta."; return@launchAuth }
        auth.sendPasswordResetEmail(email.trim()).await()
        onDone()
    }

    fun signInWithGoogle(activity: Activity) = launchAuth {
        val webClientId = webClientId(activity)
        if (webClientId.isBlank()) {
            message.value = "Login com Google ainda não configurado (falta o client ID web no google-services.json)."
            return@launchAuth
        }
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(webClientId).build())
            .build()
        val cred = CredentialManager.create(activity).getCredential(activity, request).credential
        if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            val g = GoogleIdTokenCredential.createFrom(cred.data)
            auth.signInWithCredential(GoogleAuthProvider.getCredential(g.idToken, null)).await()
        } else {
            message.value = "Não foi possível entrar com o Google."
        }
    }

    private fun webClientId(ctx: Context): String {
        val res = ctx.resources
        val id = res.getIdentifier("default_web_client_id", "string", ctx.packageName)
        if (id != 0) return res.getString(id)
        return ctx.getString(R.string.glassmark_web_client_id)
    }

    fun logout() {
        auth.signOut()
        setGuest(false)
    }

    private fun friendly(e: Exception): String = when (e) {
        is GetCredentialCancellationException -> "Login cancelado."
        is NoCredentialException -> "Nenhuma conta Google encontrada neste aparelho."
        is GetCredentialException -> "Não foi possível entrar com o Google. Tente novamente."
        is FirebaseNetworkException -> "Sem conexão com a internet."
        is FirebaseTooManyRequestsException -> "Muitas tentativas. Aguarde um pouco e tente de novo."
        is FirebaseAuthException -> when (e.errorCode) {
            "ERROR_INVALID_EMAIL" -> "E-mail inválido."
            "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL", "ERROR_USER_NOT_FOUND" -> "E-mail ou senha incorretos."
            "ERROR_EMAIL_ALREADY_IN_USE" -> "Já existe uma conta com este e-mail."
            "ERROR_WEAK_PASSWORD" -> "Senha fraca: use pelo menos 6 caracteres."
            "ERROR_USER_DISABLED" -> "Esta conta foi desativada."
            "ERROR_OPERATION_NOT_ALLOWED" -> "Este método de login não está ativado no Firebase."
            else -> "Erro de autenticação (${e.errorCode})."
        }
        else -> e.message?.let { "Erro: $it" } ?: "Algo deu errado."
    }

    // ---- links ----
    private fun report(msg: String) { syncError.value = msg }

    fun addLink(url: String, title: String, collection: String) {
        viewModelScope.launch { repo.addLink(ownerUid.value, url, title, collection, ::report) }
    }
    fun addCollection(name: String) { viewModelScope.launch { repo.addCollection(ownerUid.value, name, ::report) } }
    fun toggleFavorite(link: LinkEntity) { viewModelScope.launch { repo.toggleFavorite(ownerUid.value, link, ::report) } }
    fun updateLink(link: LinkEntity) { viewModelScope.launch { repo.updateLink(ownerUid.value, link, ::report) } }
    fun renameCollection(old: String, new: String) { if (old in hiddenCollections.value) saveHidden(hiddenCollections.value - old + new); viewModelScope.launch { repo.renameCollection(ownerUid.value, old, new, ::report) } }
    fun deleteCollection(name: String) { if (name in hiddenCollections.value) unhideCollection(name); viewModelScope.launch { repo.deleteCollection(ownerUid.value, name, ::report) } }
    fun deleteLink(link: LinkEntity) { viewModelScope.launch { repo.deleteLink(ownerUid.value, link, ::report) } }

    fun uploadGuest() {
        val uid = ownerUid.value
        offerUpload.value = false
        if (uid.isEmpty()) return
        viewModelScope.launch { repo.uploadGuestData(uid, ::report) }
    }
    fun dismissUpload() { offerUpload.value = false }
}
