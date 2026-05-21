package net.buli.ibtc

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.bitcoinj.core.*
import org.bitcoinj.crypto.MnemonicCode
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.script.Script
import org.bitcoinj.wallet.DeterministicKeyChain
import org.bitcoinj.wallet.DeterministicSeed
import org.bitcoinj.wallet.KeyChainGroup
import org.bitcoinj.wallet.SendRequest
import org.bitcoinj.wallet.Wallet
import java.io.File
import java.security.SecureRandom

object WalletManager {
    private val params: NetworkParameters = MainNetParams.get()
    private val client = OkHttpClient()
    private fun walletFile(c:Context)=File(c.filesDir,"ibtc.wallet")

    fun hasWallet(c:Context)=walletFile(c).exists()

    fun createWallet(c:Context):List<String>{
        val entropy = ByteArray(16)
        SecureRandom().nextBytes(entropy)
        val mnemonic = MnemonicCode.INSTANCE.toMnemonic(entropy)
        val seed = DeterministicSeed(mnemonic, null, "", System.currentTimeMillis()/1000)
        val keyChain = DeterministicKeyChain.builder().seed(seed).build()
        val keyChainGroup = KeyChainGroup.builder(params).addChain(keyChain).build()
        val w = Wallet(params, keyChainGroup)
        w.saveToFile(walletFile(c))
        return mnemonic
    }
    
    fun importWallet(c:Context, words:String):Boolean{
        return try{
            val list = words.trim().split(" ")
            val seed = DeterministicSeed(list, null, "", 0L)
            val keyChain = DeterministicKeyChain.builder().seed(seed).build()
            val keyChainGroup = KeyChainGroup.builder(params).addChain(keyChain).build()
            val w = Wallet(params, keyChainGroup)
            w.saveToFile(walletFile(c))
            true
        }catch(e:Exception){false}
    }
    
    fun getAddress(c:Context):String{
        val w = Wallet.loadFromFile(walletFile(c))
        // Dùng bech32 bc1... phí rẻ hơn
        return w.currentReceiveAddress().toString()
    }
    
    suspend fun getBalance(address: String): String = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url("https://blockstream.info/api/address/$address").build()
            val res = client.newCall(req).execute()
            val json = res.body?.string() ?: return@withContext "Lỗi"
            val sats = json.substringAfter("chain_stats\":").substringAfter("funded_txo_sum\":").substringBefore(",").toLong() - 
                       json.substringAfter("chain_stats\":").substringAfter("spent_txo_sum\":").substringBefore(",").toLong()
            "%.8f".format(sats / 100000000.0)
        } catch (e: Exception) { "Lỗi mạng" }
    }

    fun send(c:Context, to:String, amountBtc:String):String{
        return try{
            val w = Wallet.loadFromFile(walletFile(c))
            val amount = Coin.parseCoin(amountBtc)
            val sendReq = SendRequest.to(Address.fromString(params, to), amount)
            w.completeTx(sendReq)
            w.commitTx(sendReq.tx)
            // Broadcast qua API
            val txHex = Utils.HEX.encode(sendReq.tx.bitcoinSerialize())
            val req = Request.Builder()
                .url("https://blockstream.info/api/tx")
                .post(okhttp3.RequestBody.create(null, txHex))
                .build()
            val txId = client.newCall(req).execute().body?.string() ?: "Lỗi broadcast"
            "Đã gửi! TXID: $txId"
        }catch(e:Exception){ "Lỗi: ${e.message}" }
    }
}