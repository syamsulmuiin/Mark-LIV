package com.jarvis.companion

import android.app.*
import android.content.*
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.json.JSONObject
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.*
import java.util.concurrent.TimeUnit
import javax.net.ssl.*

class MainActivity : AppCompatActivity() {
    private lateinit var status: TextView; private lateinit var pairUrl: EditText; private lateinit var command: EditText
    private var ws: WebSocket? = null
    private val prefs by lazy { getSharedPreferences("jarvis-device", MODE_PRIVATE) }
    private val client by lazy { lanClient() }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state); setContentView(R.layout.activity_main)
        status=findViewById(R.id.status); pairUrl=findViewById(R.id.pairUrl); command=findViewById(R.id.command)
        findViewById<Button>(R.id.pair).setOnClickListener { pairOrConnect(pairUrl.text.toString()) }
        findViewById<Button>(R.id.send).setOnClickListener { ws?.send(JSONObject().put("type","jarvis.command").put("text",command.text.toString()).toString()) }
        findViewById<Button>(R.id.accessibility).setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        if (Build.VERSION.SDK_INT>=33) requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 7)
        intent?.data?.let { pairUrl.setText(it.toString()); pairOrConnect(it.toString()) }
        if (intent?.data==null && prefs.contains("server")) connect()
    }

    private fun identity(): Triple<String,ByteArray,ByteArray> {
        var id=prefs.getString("device_id",null); var priv=prefs.getString("private",null)
        if(id==null||priv==null){ val k=Ed25519PrivateKeyParameters(SecureRandom()); id=UUID.randomUUID().toString(); priv=b64(k.encoded); prefs.edit().putString("device_id",id).putString("private",priv).apply() }
        val p=Ed25519PrivateKeyParameters(unb64(priv),0); return Triple(id,p.encoded,p.generatePublicKey().encoded)
    }
    private fun sign(data:ByteArray):String { val p=Ed25519PrivateKeyParameters(identity().second,0); val s=Ed25519Signer(); s.init(true,p); s.update(data,0,data.size); return b64(s.generateSignature()) }
    private fun verify(pub:String,data:ByteArray,sig:String):Boolean = try { val v=Ed25519Signer(); v.init(false,Ed25519PublicKeyParameters(unb64(pub),0)); v.update(data,0,data.size); v.verifySignature(unb64(sig)) } catch(_:Exception){false}

    private fun pairOrConnect(raw:String) {
        try {
            val u=Uri.parse(raw); val server=if(u.scheme=="jarvis") u.getQueryParameter("server") else "${u.scheme}://${u.authority}"
            val code=u.getQueryParameter("code")?.uppercase() ?: error("Missing pairing code")
            val serverKey=u.getQueryParameter("server_key") ?: error("QR is missing JARVIS trust key; create a fresh QR")
            val serverId=u.getQueryParameter("server_id") ?: ""
            prefs.edit().putString("server",server).putString("server_key",serverKey).putString("server_id",serverId).apply()
            val ident=identity(); val peer=JSONObject().put("device_id",ident.first).put("name",Build.MODEL).put("public_key",b64(ident.third))
            val offerReq=Request.Builder().url("$server/api/pairing/offer/$code").build()
            client.newCall(offerReq).enqueue(object:Callback{
                override fun onFailure(c:Call,e:java.io.IOException)=ui("Pairing failed: ${e.message}")
                override fun onResponse(c:Call,r:Response){ val o=JSONObject(r.body?.string()?:"{}"); val nonce=o.optString("nonce"); if(nonce.isBlank()){ui("Pairing code expired");return}
                    val caps=org.json.JSONArray(listOf("jarvis.command","notification","vibration","clipboard.write","open_url","app.launch","android.ui.inspect","android.ui.click","android.ui.text","android.ui.scroll","android.ui.global"))
                    val body=JSONObject().put("code",code).put("peer",peer).put("signature",sign("$nonce:$code".toByteArray())).put("capabilities",caps)
                    val req=Request.Builder().url("$server/api/pairing/accept").post(body.toString().toRequestBody("application/json".toMediaType())).build()
                    client.newCall(req).enqueue(object:Callback{override fun onFailure(c:Call,e:java.io.IOException)=ui("Pair failed: ${e.message}"); override fun onResponse(c:Call,r:Response){ if(!r.isSuccessful){ui("Pair rejected: ${r.code}");return}; prefs.edit().putBoolean("paired",true).apply(); connect() }})
                }
            })
        } catch(e:Exception){ ui(e.message?:"Invalid pairing URL") }
    }

    private fun connect(){ val server=prefs.getString("server",null)?:return; val id=identity().first; val wsBase=server.replaceFirst("https://","wss://").replaceFirst("http://","ws://")
        ws=client.newWebSocket(Request.Builder().url("$wsBase/ws/device?device_id=$id").build(),object:WebSocketListener(){
            override fun onMessage(w:WebSocket,text:String){ val m=JSONObject(text); when(m.optString("type")){
                "challenge"->{ val ch=m.getString("challenge"); val serverKey=prefs.getString("server_key","")!!; val serverId=prefs.getString("server_id","")!!; if(!verify(serverKey,"$id:$ch".toByteArray(),m.optString("server_signature"))){ ui("Server identity verification failed"); w.close(4003,"bad server proof"); return }; w.send(JSONObject().put("type","proof").put("signature",sign(ch.toByteArray())).toString()) }
                "ready"->ui("Paired and connected to JARVIS")
                "capability.call"->executeCapability(w,m)
            }}
            override fun onFailure(w:WebSocket,t:Throwable,r:Response?){ui("Disconnected: ${t.message}")}
        }) }

    private fun executeCapability(w:WebSocket,m:JSONObject){ val cap=m.optString("capability"); val a=m.optJSONObject("args")?:JSONObject(); var ok=true; var result="done"; try { when(cap){
        "notification"->{ val nm=getSystemService(NotificationManager::class.java); val cid="jarvis"; if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(NotificationChannel(cid,"JARVIS",NotificationManager.IMPORTANCE_DEFAULT)); nm.notify((System.currentTimeMillis()%Int.MAX_VALUE).toInt(),Notification.Builder(this,cid).setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("JARVIS").setContentText(a.optString("text")).build()) }
        "vibration"->{ val v=if(Build.VERSION.SDK_INT>=31)getSystemService(VibratorManager::class.java).defaultVibrator else @Suppress("DEPRECATION") getSystemService(VIBRATOR_SERVICE) as Vibrator; v.vibrate(VibrationEffect.createOneShot(a.optLong("ms",300),VibrationEffect.DEFAULT_AMPLITUDE)) }
        "clipboard.write"->{ (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("JARVIS",a.optString("text"))) }
        "open_url"->{ startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(a.getString("url"))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        "app.launch"->{ val pkg=a.getString("package"); val i=packageManager.getLaunchIntentForPackage(pkg)?:error("App not installed: $pkg"); startActivity(i) }
        "android.ui.inspect"->{ val svc=JarvisAccessibilityService.instance?:error("Accessibility control is disabled on the phone"); result=svc.inspect(a.optInt("max_nodes",120)).toString() }
        "android.ui.click"->{ val svc=JarvisAccessibilityService.instance?:error("Accessibility control is disabled on the phone"); result=svc.click(a.optString("text"),a.optString("view_id")) }
        "android.ui.text"->{ val svc=JarvisAccessibilityService.instance?:error("Accessibility control is disabled on the phone"); result=svc.setText(a.getString("text"),a.optString("target_text"),a.optString("view_id")) }
        "android.ui.scroll"->{ val svc=JarvisAccessibilityService.instance?:error("Accessibility control is disabled on the phone"); result=svc.scroll(a.optString("direction","down")) }
        "android.ui.global"->{ val svc=JarvisAccessibilityService.instance?:error("Accessibility control is disabled on the phone"); result=svc.global(a.getString("action")) }
        else->{ok=false;result="Unsupported capability: $cap"}
    }}catch(e:Exception){ok=false;result=e.message?:e.toString()}; w.send(JSONObject().put("type","capability.result").put("call_id",m.optString("call_id")).put("ok",ok).put("result",result).toString()) }

    private fun ui(s:String)=runOnUiThread{status.text=s}
    private fun b64(b:ByteArray)=Base64.getUrlEncoder().withoutPadding().encodeToString(b)
    private fun unb64(s:String)=Base64.getUrlDecoder().decode(s)
    private fun lanClient():OkHttpClient { val tm=object:X509TrustManager{override fun getAcceptedIssuers()=arrayOf<X509Certificate>();override fun checkClientTrusted(c:Array<X509Certificate>,a:String){};override fun checkServerTrusted(c:Array<X509Certificate>,a:String){}}; val sc=SSLContext.getInstance("TLS");sc.init(null,arrayOf<TrustManager>(tm),SecureRandom());return OkHttpClient.Builder().sslSocketFactory(sc.socketFactory,tm).hostnameVerifier{_,_->true}.pingInterval(20,TimeUnit.SECONDS).build() }
}
