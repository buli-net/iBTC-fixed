package net.buli.ibtc
import android.content.Context
import androidx.security.crypto.*
import java.security.MessageDigest

class SecurePrefs(ctx: Context) {
    private val master = MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
    private val prefs = EncryptedSharedPreferences.create(ctx,"ibtc_vault",master,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)

    fun saveSeed(s:String)=prefs.edit().putString("seed",s).apply()
    fun getSeed()=prefs.getString("seed","")?:""
    fun savePwd(p:String){ val h=MessageDigest.getInstance("SHA-256").digest(p.toByteArray()).joinToString(""){"%02x".format(it)}; prefs.edit().putString("ph",h).apply() }
    fun checkPwd(p:String):Boolean{ val h=MessageDigest.getInstance("SHA-256").digest(p.toByteArray()).joinToString(""){"%02x".format(it)}; return h==prefs.getString("ph","") }
    fun saveSat(s:Long)=prefs.edit().putLong("sat",s).apply()
    fun getSat()=prefs.getLong("sat",0L)
}