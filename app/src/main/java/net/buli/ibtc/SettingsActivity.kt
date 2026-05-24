package net.buli.ibtc
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.*
import androidx.appcompat.app.AlertDialog

class SettingsActivity:BaseNavActivity(){
    private val p by lazy{getSharedPreferences("ibtc_prefs",0)}
    override fun onCreate(b:Bundle?){super.onCreate(b);setContentView(R.layout.activity_settings);setupNav(R.id.navSettings)
        findViewById<ImageView>(R.id.btnBack).setOnClickListener{startActivity(Intent(this,MainActivity::class.java));finish()}
        findViewById<LinearLayout>(R.id.itemBackupSeed).setOnClickListener{val e=EditText(this).apply{inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD};AlertDialog.Builder(this).setTitle("Nhập mật khẩu").setView(e).setPositiveButton("Xem"){_,_->if(e.text.toString()==p.getString("password",""))AlertDialog.Builder(this).setTitle("Seed phrase").setMessage(p.getString("seed","")).show()else Toast.makeText(this,"Sai mật khẩu",0).show()}.setNegativeButton("Hủy",null).show()}
        findViewById<LinearLayout>(R.id.itemChangePass).setOnClickListener{startActivity(Intent(this,ChangePasswordActivity::class.java))}
        findViewById<LinearLayout>(R.id.itemDelete).setOnClickListener{val v=layoutInflater.inflate(R.layout.dialog_delete,null);val e=v.findViewById<EditText>(R.id.etPassDel);AlertDialog.Builder(this).setTitle("Xóa ví").setView(v).setPositiveButton("Xóa"){_,_->if(e.text.toString()==p.getString("password","")){p.edit().clear().apply();startActivity(Intent(this,WelcomeActivity::class.java));finishAffinity()}}.setNegativeButton("Hủy",null).show()}
    }
}