// THAY TOÀN BỘ WalletManager.kt bằng bản này
package net.buli.ibtc

import android.content.Context
import org.bitcoinj.core.Address
import org.bitcoinj.core.Coin
import org.bitcoinj.kits.WalletAppKit
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.wallet.DeterministicSeed
import org.bitcoinj.wallet.SendRequest
import org.bitcoinj.wallet.Wallet
import java.io.File
import java.net.URL
import java.security.SecureRandom

data class WalletInfo(val id: String, var name: String, val seed: String)

class WalletManager(private val ctx: Context) {
    private val params = MainNetParams.get()
    private val prefs = ctx.getSharedPreferences("ibtc_wallets", Context.MODE_PRIVATE)
    private var kit: WalletAppKit? = null
    private var wallet: Wallet? = null
    @Volatile private var ready = false

    fun hasWallets() = getAll().isNotEmpty()
    fun getAll(): List<WalletInfo> {
        val ids = prefs.getStringSet("ids", emptySet()) ?: emptySet()
        return ids.map { WalletInfo(it, prefs.getString("n_$it","Ví")!!, prefs.getString("s_$it","")!!) }
    }
    private fun saveIds(ids:Set<String>){ prefs.edit().putStringSet("ids", ids).apply() }

    fun create(name:String): WalletInfo {
        val id = System.currentTimeMillis().toString()
        val entropy = ByteArray(16); SecureRandom().nextBytes(entropy)
        val seed = DeterministicSeed(entropy, "", System.currentTimeMillis()/1000)
        val mnemonic = seed.mnemonicCode!!.joinToString(" ")
        val info = WalletInfo(id, name.ifBlank{"Ví mới"}, mnemonic)
        saveIds(getAll().map{it.id}.toMutableSet().apply{add(id)})
        prefs.edit().putString("n_$id",info.name).putString("s_$id",mnemonic).apply()
        setActive(id); return info
    }
    fun import(name:String, mnemonic:String): WalletInfo? = try {
        DeterministicSeed(mnemonic.trim().split("\\s+".toRegex()), null, "", 0)
        val id = System.currentTimeMillis().toString()
        val info = WalletInfo(id, name.ifBlank{"Ví import"}, mnemonic.trim())
        saveIds(getAll().map{it.id}.toMutableSet().apply{add(id)})
        prefs.edit().putString("n_$id",info.name).putString("s_$id",info.seed).apply()
        setActive(id); info
    } catch (_:Exception){ null }

    fun setActive(id:String){ prefs.edit().putString("active",id).apply() }
    fun getActive() = getAll().find{ it.id == prefs.getString("active",null) }
    fun rename(id:String,n:String){ prefs.edit().putString("n_$id",n).apply() }
    fun delete(id:String){
        try{ kit?.stopAsync()?.awaitTerminated() }catch(_:Exception){}
        saveIds(getAll().map{it.id}.toMutableSet().apply{remove(id)})
        prefs.edit().remove("n_$id").remove("s_$id").apply()
        File(ctx.filesDir,"wallets").listFiles()?.filter{it.name.contains(id)}?.forEach{it.delete()}
        if(prefs.getString("active",null)==id) getAll().firstOrNull()?.let{setActive(it.id)}
        kit=null; wallet=null; ready=false
    }
    fun switchTo(id:String){ setActive(id); init() }

    fun isReady() = ready
    fun init() {
        ready = false
        try{ kit?.stopAsync() }catch(_:Exception){}
        val info = getActive() ?: return
        val dir = File(ctx.filesDir,"wallets").apply{mkdirs()}
        val seed = DeterministicSeed(info.seed.split(" "), null, "", 0)
        val walletFile = File(dir, "wallet-${info.id}.wallet")
        
        kit = object : WalletAppKit(params, dir, "wallet-${info.id}") {
            override fun onSetupCompleted() {
                wallet = this.wallet()
                ready = true // SẴN SÀNG NGAY, không đợi sync
            }
        }.apply {
            if (!walletFile.exists()) restoreWalletFromSeed(seed) // chỉ restore lần đầu
            setAutoSave(true)
            setBlockingStartup(false)
            startAsync() // chạy nền, không block
        }
    }

    fun getAddress() = if(isReady()) wallet!!.currentReceiveAddress().toString() else ""
    fun getBalance() = if(isReady()) wallet!!.getBalance(Wallet.BalanceType.ESTIMATED).value/1e8 else 0.0
    fun getSeed() = getActive()?.seed ?: ""
    
    fun sync() { if(isReady()) Thread{ try{ kit?.peerGroup()?.downloadBlockChain() }catch(_:Exception){} }.start() }
    
    fun price() = try{ URL("https://api.binance.com/api/v3/ticker/price?symbol=BTCUSDT").readText()
        .let{ """"price":"([0-9.]+)"""".toRegex().find(it)?.groupValues?.get(1)?.toDouble() ?: 0.0 } }catch(_:Exception){0.0}

    fun send(to:String, amt:Double, fee:Long):String = try {
        if(!isReady()) "Ví chưa sẵn sàng"
        else {
            val req = SendRequest.to(Address.fromString(params,to), Coin.valueOf((amt*1e8).toLong()))
            req.feePerKb = Coin.valueOf(fee*1000)
            val tx = wallet!!.sendCoins(kit!!.peerGroup(), req)
            tx.broadcastComplete.get()
            "Đã gửi! TXID: ${tx.tx.txId}"
        }
    } catch(e:Exception){ "Lỗi: ${e.message}" }
}