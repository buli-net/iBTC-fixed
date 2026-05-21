package net.buli.ibtc

import android.content.Context
import org.bitcoinj.core.NetworkParameters
import org.bitcoinj.crypto.MnemonicCode
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.wallet.DeterministicKeyChain
import org.bitcoinj.wallet.DeterministicSeed
import org.bitcoinj.wallet.KeyChainGroup
import org.bitcoinj.wallet.Wallet
import java.io.File
import java.security.SecureRandom

object WalletManager {
    private val params: NetworkParameters = MainNetParams.get()
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
        return w.currentReceiveAddress().toString()
    }
}