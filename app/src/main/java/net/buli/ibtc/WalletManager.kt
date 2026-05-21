package net.buli.ibtc

import android.content.Context
import org.bitcoinj.core.NetworkParameters
import org.bitcoinj.crypto.MnemonicCode
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.wallet.DeterministicSeed
import org.bitcoinj.wallet.Wallet
import java.io.File
import java.security.SecureRandom

object WalletManager {
    private val params: NetworkParameters = MainNetParams.get()
    private fun walletFile(c:Context)=File(c.filesDir,"ibtc.wallet")

    fun hasWallet(c:Context)=walletFile(c).exists()

    fun createWallet(c:Context):List<String>{
        val seed = DeterministicSeed(SecureRandom(), 128, "", System.currentTimeMillis()/1000)
        val w = Wallet.fromSeed(params, seed)
        w.saveToFile(walletFile(c))
        return seed.mnemonicCode!!
    }
    
    fun importWallet(c:Context, words:String):Boolean{
        return try{
            val seed = DeterministicSeed(words.trim().split(" "),null,"",0L)
            val w = Wallet.fromSeed(params,seed)
            w.saveToFile(walletFile(c)); true
        }catch(e:Exception){false}
    }
    
    fun getAddress(c:Context):String{
        val w = Wallet.loadFromFile(walletFile(c))
        return w.currentReceiveAddress().toString()
    }
}