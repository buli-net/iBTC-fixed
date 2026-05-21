package net.buli.ibtc

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bitcoinj.core.*
import org.bitcoinj.core.listeners.DownloadProgressTracker
import org.bitcoinj.crypto.MnemonicCode
import org.bitcoinj.net.discovery.DnsDiscovery
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.script.Script.ScriptType
import org.bitcoinj.store.SPVBlockStore
import org.bitcoinj.wallet.DeterministicKeyChain
import org.bitcoinj.wallet.DeterministicSeed
import org.bitcoinj.wallet.KeyChainGroup
import org.bitcoinj.wallet.SendRequest
import org.bitcoinj.wallet.Wallet
import java.io.File
import java.security.SecureRandom
import java.util.Date

object WalletManager {
    private val params: NetworkParameters = MainNetParams.get()
    private fun walletFile(c:Context)=File(c.filesDir,"ibtc.wallet")
    private fun chainFile(c:Context)=File(c.filesDir,"ibtc.spvchain")

    fun hasWallet(c:Context)=walletFile(c).exists()

    fun createWallet(c:Context):List<String>{
        val entropy = ByteArray(16)
        SecureRandom().nextBytes(entropy)
        val mnemonic = MnemonicCode.INSTANCE.toMnemonic(entropy)
        val seed = DeterministicSeed(mnemonic, null, "", System.currentTimeMillis()/1000)
        val keyChain = DeterministicKeyChain.builder().seed(seed).build()
        val keyChainGroup = KeyChainGroup.builder(params, ScriptType.P2WPKH).addChain(keyChain).build()
        val w = Wallet(params, keyChainGroup)
        w.saveToFile(walletFile(c))
        return mnemonic
    }
    
    fun importWallet(c:Context, words:String):Boolean{
        return try{
            val list = words.trim().split(" ")
            val seed = DeterministicSeed(list, null, "", 0L)
            val keyChain = DeterministicKeyChain.builder().seed(seed).build()
            val keyChainGroup = KeyChainGroup.builder(params, ScriptType.P2WPKH).addChain(keyChain).build()
            val w = Wallet(params, keyChainGroup)
            w.saveToFile(walletFile(c))
            true
        }catch(e:Exception){false}
    }
    
    fun getAddress(c:Context):String{
        val w = Wallet.loadFromFile(walletFile(c))
        return w.currentReceiveAddress().toString()
    }

    suspend fun syncAndGetBalance(c: Context, onProgress: (String) -> Unit): String = withContext(Dispatchers.IO) {
        try {
            val w = Wallet.loadFromFile(walletFile(c))
            val blockStore = SPVBlockStore(params, chainFile(c))
            val chain = BlockChain(params, w, blockStore)
            val peers = PeerGroup(params, chain)
            peers.addWallet(w)
            
            val listener = object : DownloadProgressTracker() {
                override fun progress(pct: Double, blocksSoFar: Int, date: Date) {
                    onProgress("Sync: ${pct.toInt()}% - $blocksSoFar blocks")
                }
                override fun doneDownload() {
                    onProgress("Sync xong")
                }
            }
            peers.setUserAgent("iBTC", "2.0")
            peers.addPeerDiscovery(DnsDiscovery(params))
            peers.start()
            peers.startBlockChainDownload(listener)
            listener.await()
            peers.stop()
            w.saveToFile(walletFile(c))
            w.getBalance(Wallet.BalanceType.ESTIMATED).toPlainString()
        } catch (e: Exception) { "Lỗi: ${e.message}" }
    }

    fun send(c:Context, to:String, amountBtc:String):String{
        return try{
            val w = Wallet.loadFromFile(walletFile(c))
            val amount = Coin.parseCoin(amountBtc)
            val sendReq = SendRequest.to(Address.fromString(params, to), amount)
            w.completeTx(sendReq)
            w.commitTx(sendReq.tx)
            
            // Broadcast lên mạng BTC
            val blockStore = SPVBlockStore(params, chainFile(c))
            val chain = BlockChain(params, w, blockStore)
            val peers = PeerGroup(params, chain)
            peers.addWallet(w)
            peers.start()
            peers.broadcastTransaction(sendReq.tx).broadcast().get()
            peers.stop()
            w.saveToFile(walletFile(c))
            "Đã gửi! TXID: ${sendReq.tx.txId}"
        }catch(e:Exception){ "Lỗi: ${e.message}" }
    }
}