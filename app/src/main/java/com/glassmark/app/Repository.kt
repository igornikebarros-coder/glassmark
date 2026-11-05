package com.glassmark.app

import androidx.room.withTransaction
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Room é o cache local. Visitante (uid = ""): lê/grava só no Room.
 * Conta: grava no Firestore (users/{uid}/links e users/{uid}/collections) e um listener
 * mantém o Room atualizado (o Firestore já devolve as gravações pendentes na hora, inclusive offline).
 */
class Repository(private val db: GlassmarkDatabase) {
    private val fs = FirebaseFirestore.getInstance()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutex = Mutex()
    private var linksReg: ListenerRegistration? = null
    private var collReg: ListenerRegistration? = null
    private var activeUid: String = ""

    private fun userDoc(uid: String) = fs.collection("users").document(uid)
    private fun collId(name: String) = name.replace("/", "_")

    fun observeLinks(uid: String): Flow<List<LinkEntity>> = db.linkDao().observe(uid)
    fun observeCollections(uid: String): Flow<List<CollectionEntity>> = db.collectionDao().observe(uid)
    suspend fun guestCount(): Int = db.linkDao().guestCount()

    // ---- sincronização ----
    fun startSync(uid: String, onError: (String?) -> Unit) {
        stopSync()
        activeUid = uid
        linksReg = userDoc(uid).collection("links").addSnapshotListener { snap, err ->
            if (err != null) { onError(err.message ?: "erro"); return@addSnapshotListener }
            onError(null)
            if (snap != null) scope.launch { mutex.withLock { if (activeUid == uid) applyLinks(uid, snap.documents) } }
        }
        collReg = userDoc(uid).collection("collections").addSnapshotListener { snap, err ->
            if (err != null) { onError(err.message ?: "erro"); return@addSnapshotListener }
            if (snap != null) scope.launch { mutex.withLock { if (activeUid == uid) applyCollections(uid, snap.documents) } }
        }
    }

    fun stopSync() {
        linksReg?.remove(); collReg?.remove()
        linksReg = null; collReg = null
        activeUid = ""
    }

    private suspend fun applyLinks(uid: String, docs: List<DocumentSnapshot>) = db.withTransaction {
        val dao = db.linkDao()
        val existing = dao.all(uid).associateBy { it.docId }
        val seen = HashSet<String>()
        for (d in docs) {
            val url = d.getString("url") ?: continue
            seen += d.id
            dao.insert(
                LinkEntity(
                    id = existing[d.id]?.id ?: 0,
                    url = url,
                    title = d.getString("title").orEmpty(),
                    collection = d.getString("collection") ?: "Inbox",
                    savedAt = d.getLong("savedAt") ?: System.currentTimeMillis(),
                    isFavorite = d.getBoolean("isFavorite") ?: false,
                    ogImage = d.getString("ogImage").orEmpty(),
                    summary = d.getString("summary").orEmpty(),
                    hidden = d.getBoolean("hidden") ?: false,
                    tags = ((d.get("tags") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()).joinToString(","),
                    isRead = d.getBoolean("isRead") ?: false,
                    remindAt = d.getLong("remindAt") ?: 0L,
                    docId = d.id,
                    ownerUid = uid
                )
            )
        }
        existing.values.filter { it.docId !in seen }.forEach { dao.delete(uid, it.docId) }
    }

    private suspend fun applyCollections(uid: String, docs: List<DocumentSnapshot>) = db.withTransaction {
        val dao = db.collectionDao()
        val names = docs.mapNotNull { it.getString("name") }.toSet()
        dao.all(uid).filter { it.name !in names }.forEach { dao.delete(uid, it.name) }
        names.forEach { dao.insert(CollectionEntity(uid, it)) }
    }

    suspend fun clearCache(uid: String) {
        db.linkDao().clear(uid)
        db.collectionDao().clear(uid)
    }

    // ---- operações ----
    suspend fun addLink(uid: String, url: String, title: String, collection: String, onError: (String) -> Unit): LinkEntity {
        val link = LinkEntity(url = url, title = title, collection = collection.ifBlank { "Inbox" }, ownerUid = uid)
        if (uid.isEmpty()) {
            db.linkDao().insert(link)
            db.collectionDao().insert(CollectionEntity("", link.collection))
        } else {
            userDoc(uid).collection("links").document(link.docId).set(link.toMap())
                .addOnFailureListener { onError(it.message ?: "erro") }
            addCollection(uid, link.collection, onError)
        }
        return link
    }

    suspend fun addCollection(uid: String, name: String, onError: (String) -> Unit) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        if (uid.isEmpty()) db.collectionDao().insert(CollectionEntity("", clean))
        else userDoc(uid).collection("collections").document(collId(clean))
            .set(mapOf("name" to clean), SetOptions.merge())
            .addOnFailureListener { onError(it.message ?: "erro") }
    }

    suspend fun toggleFavorite(uid: String, link: LinkEntity, onError: (String) -> Unit) {
        if (uid.isEmpty()) db.linkDao().toggleFavorite("", link.docId)
        else userDoc(uid).collection("links").document(link.docId)
            .set(mapOf("isFavorite" to !link.isFavorite), SetOptions.merge())
            .addOnFailureListener { onError(it.message ?: "erro") }
    }

    suspend fun deleteLink(uid: String, link: LinkEntity, onError: (String) -> Unit) {
        if (uid.isEmpty()) db.linkDao().delete("", link.docId)
        else userDoc(uid).collection("links").document(link.docId).delete()
            .addOnFailureListener { onError(it.message ?: "erro") }
    }

    /** Grava um campo isolado (miniatura, resumo, oculto) sem sobrescrever o resto do link. */
    suspend fun setField(uid: String, docId: String, field: String, value: Any, onError: (String) -> Unit) {
        if (uid.isEmpty()) when (field) {
            "ogImage" -> db.linkDao().setOg("", docId, value as String)
            "summary" -> db.linkDao().setSummary("", docId, value as String)
            "hidden" -> db.linkDao().setHidden("", docId, value as Boolean)
            "isRead" -> db.linkDao().setRead("", docId, value as Boolean)
            "remindAt" -> db.linkDao().setRemind("", docId, value as Long)
            "title" -> db.linkDao().setTitle("", docId, value as String)
            "collection" -> { db.linkDao().setCollection("", docId, value as String); db.collectionDao().insert(CollectionEntity("", value)) }
        } else userDoc(uid).collection("links").document(docId).set(mapOf(field to value), SetOptions.merge())
            .addOnFailureListener { onError(it.message ?: "erro") }
    }

    /** Edita URL, título e coleção de um link (a coleção nova é criada se não existir). */
    suspend fun updateLink(uid: String, link: LinkEntity, onError: (String) -> Unit) {
        val l = link.copy(collection = link.collection.ifBlank { "Inbox" })
        if (uid.isEmpty()) {
            db.linkDao().insert(l)
            db.collectionDao().insert(CollectionEntity("", l.collection))
        } else {
            userDoc(uid).collection("links").document(l.docId).set(l.toMap(), SetOptions.merge())
                .addOnFailureListener { onError(it.message ?: "erro") }
            addCollection(uid, l.collection, onError)
        }
    }

    /** Renomeia a coleção: os links dela passam para o novo nome. */
    suspend fun renameCollection(uid: String, old: String, new: String, onError: (String) -> Unit) {
        val clean = new.trim()
        if (clean.isEmpty() || clean == old) return
        moveCollection(uid, old, clean, keepTarget = true, onError = onError)
    }

    /** Exclui a coleção; os links dentro dela NÃO são apagados, vão para a Inbox. */
    suspend fun deleteCollection(uid: String, name: String, onError: (String) -> Unit) {
        moveCollection(uid, name, "Inbox", keepTarget = false, onError = onError)
    }

    private suspend fun moveCollection(uid: String, old: String, target: String, keepTarget: Boolean, onError: (String) -> Unit) {
        if (uid.isEmpty()) {
            db.withTransaction {
                db.linkDao().moveCollection("", old, target)
                db.collectionDao().delete("", old)
                if (keepTarget) db.collectionDao().insert(CollectionEntity("", target))
            }
            return
        }
        val affected = db.linkDao().all(uid).filter { it.collection == old }
        val col = userDoc(uid).collection("collections")
        val ops = ArrayList<(com.google.firebase.firestore.WriteBatch) -> Unit>()
        if (keepTarget) ops += { b -> b.set(col.document(collId(target)), mapOf("name" to target), SetOptions.merge()) }
        ops += { b -> b.delete(col.document(collId(old))) }
        affected.forEach { l -> ops += { b -> b.set(userDoc(uid).collection("links").document(l.docId), mapOf("collection" to target), SetOptions.merge()) } }
        ops.chunked(400).forEach { chunk ->
            val batch = fs.batch()
            chunk.forEach { it(batch) }
            batch.commit().addOnFailureListener { onError(it.message ?: "erro") }
        }
    }

    /**
     * Envia os links/coleções locais (visitante) para a conta. As linhas locais só são apagadas
     * depois que o Firestore confirma a gravação; enquanto isso nada se perde.
     */
    suspend fun uploadGuestData(uid: String, onError: (String) -> Unit): Int {
        val links = db.linkDao().all("")
        val cols = db.collectionDao().all("")
        if (links.isEmpty() && cols.isEmpty()) return 0
        val ops = ArrayList<(com.google.firebase.firestore.WriteBatch) -> Unit>()
        links.forEach { l -> ops += { b -> b.set(userDoc(uid).collection("links").document(l.docId), l.toMap()) } }
        (cols.map { it.name } + links.map { it.collection }).toSet().forEach { n ->
            ops += { b -> b.set(userDoc(uid).collection("collections").document(collId(n)), mapOf("name" to n), SetOptions.merge()) }
        }
        ops.chunked(400).forEach { chunk ->
            val batch = fs.batch()
            chunk.forEach { it(batch) }
            batch.commit()
                .addOnSuccessListener {
                    scope.launch {
                        mutex.withLock {
                            links.forEach { db.linkDao().delete("", it.docId) }
                            cols.forEach { db.collectionDao().delete("", it.name) }
                        }
                    }
                }
                .addOnFailureListener { onError(it.message ?: "erro") }
        }
        return links.size
    }
}
