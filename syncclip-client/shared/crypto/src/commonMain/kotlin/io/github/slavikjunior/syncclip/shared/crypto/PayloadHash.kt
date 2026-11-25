package io.github.slavikjunior.syncclip.shared.crypto

/**
 * Хеш открытого текста для поля `payloadHash`.
 *
 * Сервер хранит и пересылает его открытым; получатель обязан сверить хеш после
 * расшифровки, иначе подмена шифротекста в очереди останется незамеченной
 * (целостность GCM защищает только от подделки без ключа, но не от replay).
 */
public object PayloadHash {

    public fun of(text: String): String = Sha256.hexOf(text)
}
